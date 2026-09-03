#!/usr/bin/env python3
import argparse
import copy
import importlib.util
import sys
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from huggingface_hub import hf_hub_download

UPSTREAM_COMMIT = "d99d86607c7c05540c74e815e5a88847f7e667db"
MODEL_REPO = "novendrastywn/FaceLiVT"
VARIANTS = {
    "xs": ("facelivtv2-xs.pt", "facelivtv2_xs", "XS"),
    "s": ("facelivtv2-s.pt", "facelivtv2_s", "S"),
    "m": ("facelivtv2-m.pt", "facelivtv2_m", "M"),
}
EXPECTED_OUTPUTS = {
    "embedding": (1, 512),
    "block_stats": (18, 4),
    "stage_stats": (4, 4),
    "prehead": (1, 1284),
}


def load_module(upstream: Path):
    path = upstream / "backbones" / "facelivtv2.py"
    module_name = "facelivtv2_upstream"
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    # timm.register_model() looks up the decorated function's module in sys.modules.
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def feature_stats(x: torch.Tensor, reference: torch.Tensor) -> torch.Tensor:
    abs_x = torch.abs(x)
    mean_abs = torch.mean(abs_x)
    rms = torch.sqrt(torch.mean(x * x) + 1.0e-12)
    sparsity = torch.mean((abs_x < 0.05).to(dtype=x.dtype))
    delta_ratio = torch.mean(torch.abs(x - reference)) / (torch.mean(torch.abs(reference)) + 1.0e-6)
    return torch.stack((mean_abs, rms, sparsity, delta_ratio))


class DiagnosticWrapper(torch.nn.Module):
    """Same deployed FaceLiVT graph, with compact observability outputs added."""
    def __init__(self, net):
        super().__init__()
        self.net = net

    def forward(self, x):
        block_rows = []
        stage_rows = []
        for stage_idx in range(self.net.num_stage):
            x = self.net.patch_embedds[stage_idx](x)
            stage_input = x
            for block in self.net.stages[stage_idx].blocks:
                block_input = x
                x = block(x)
                block_rows.append(feature_stats(x, block_input))
            stage_rows.append(feature_stats(x, stage_input))
        prehead = self.net.pre_head(x).flatten(1)
        embedding = self.net.head(prehead)
        return embedding, torch.stack(block_rows), torch.stack(stage_rows), prehead


def cosine(a, b):
    a = np.asarray(a, dtype=np.float32).reshape(-1)
    b = np.asarray(b, dtype=np.float32).reshape(-1)
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-12))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--upstream", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--variant", choices=sorted(VARIANTS), default="s")
    args = p.parse_args()

    model_file, factory_name, label = VARIANTS[args.variant]
    upstream = Path(args.upstream).resolve()
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    weights = hf_hub_download(repo_id=MODEL_REPO, filename=model_file)
    mod = load_module(upstream)
    factory = getattr(mod, factory_name)
    model = factory(num_classes=512, distillation=False, pretrained=False)
    try:
        state = torch.load(weights, map_location="cpu", weights_only=True)
    except TypeError:
        state = torch.load(weights, map_location="cpu")
    model.load_state_dict(state, strict=True)
    model.eval()

    dummy = torch.linspace(-1.0, 1.0, 3 * 112 * 112, dtype=torch.float32).reshape(1, 3, 112, 112)
    with torch.no_grad():
        ref = model(dummy).cpu().numpy()

    deployed = copy.deepcopy(model)
    deployed = mod.reparameterize(deployed)
    deployed.eval()
    with torch.no_grad():
        dep = deployed(dummy).cpu().numpy()
    reparam_cos = cosine(ref, dep)
    if reparam_cos < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} reparameterization fidelity failed: cosine={reparam_cos}")

    diagnostic = DiagnosticWrapper(deployed)
    diagnostic.eval()
    with torch.no_grad():
        diag_embedding, pt_blocks, pt_stages, pt_prehead = diagnostic(dummy)
    wrapper_cos = cosine(dep, diag_embedding.cpu().numpy())
    if wrapper_cos < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} diagnostic wrapper changed embedding: cosine={wrapper_cos}")
    if tuple(pt_blocks.shape) != EXPECTED_OUTPUTS["block_stats"]:
        raise RuntimeError(f"unexpected PyTorch block_stats shape: {tuple(pt_blocks.shape)}")
    if tuple(pt_stages.shape) != EXPECTED_OUTPUTS["stage_stats"]:
        raise RuntimeError(f"unexpected PyTorch stage_stats shape: {tuple(pt_stages.shape)}")
    if tuple(pt_prehead.shape) != EXPECTED_OUTPUTS["prehead"]:
        raise RuntimeError(f"unexpected PyTorch prehead shape: {tuple(pt_prehead.shape)}")

    torch.onnx.export(
        diagnostic,
        dummy,
        str(output),
        input_names=["input"],
        output_names=["embedding", "block_stats", "stage_stats", "prehead"],
        opset_version=18,
        do_constant_folding=True,
        dynamo=False,
    )
    onnx_model = onnx.load(str(output))
    onnx.checker.check_model(onnx_model)

    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    actual_names = [o.name for o in session.get_outputs()]
    if actual_names != list(EXPECTED_OUTPUTS):
        raise RuntimeError(f"unexpected ONNX outputs: {actual_names}")
    ort_values = session.run(actual_names, {"input": dummy.numpy()})
    ort_by_name = dict(zip(actual_names, ort_values))
    for name, expected_shape in EXPECTED_OUTPUTS.items():
        shape = tuple(ort_by_name[name].shape)
        if shape != expected_shape:
            raise RuntimeError(f"FaceLiVTv2-{label} {name} shape {shape} != {expected_shape}")

    ort_embedding = ort_by_name["embedding"]
    ort_cos = cosine(dep, ort_embedding)
    max_abs = float(np.max(np.abs(dep - ort_embedding)))
    if ort_cos < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} ONNX fidelity failed: cosine={ort_cos}")

    print(
        f"FaceLiVTv2-{label} export OK: "
        f"reparam_cos={reparam_cos:.8f}, wrapper_cos={wrapper_cos:.8f}, "
        f"onnx_cos={ort_cos:.8f}, max_abs={max_abs:.8g}, "
        f"outputs=embedding{ort_by_name['embedding'].shape},"
        f"block_stats{ort_by_name['block_stats'].shape},"
        f"stage_stats{ort_by_name['stage_stats'].shape},"
        f"prehead{ort_by_name['prehead'].shape}, bytes={output.stat().st_size}"
    )


if __name__ == "__main__":
    main()

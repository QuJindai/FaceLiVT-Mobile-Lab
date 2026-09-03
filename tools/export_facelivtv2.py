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


def load_module(upstream: Path):
    path = upstream / "backbones" / "facelivtv2.py"
    module_name = "facelivtv2_upstream"
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def stats4(x: torch.Tensor) -> torch.Tensor:
    mean_abs = x.abs().mean()
    rms = torch.sqrt((x * x).mean() + 1e-12)
    mean = x.mean()
    std = torch.sqrt(((x - mean) * (x - mean)).mean() + 1e-12)
    near_zero = (x.abs() < 1e-3).to(x.dtype).mean()
    return torch.stack((mean_abs, rms, std, near_zero))


def block_stats(before: torch.Tensor, after: torch.Tensor) -> torch.Tensor:
    base = stats4(after)
    diff = after - before
    relative_delta = torch.sqrt((diff * diff).sum() + 1e-12) / (
        torch.sqrt((before * before).sum() + 1e-12) + 1e-6
    )
    return torch.cat((base, relative_delta.reshape(1)))


class DiagnosticWrapper(torch.nn.Module):
    """Runs the real deployed backbone while exposing compact activation summaries."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, x):
        block_rows = []
        stage_rows = []
        for stage_idx in range(self.model.num_stage):
            x = self.model.patch_embedds[stage_idx](x)
            stage = self.model.stages[stage_idx]
            for block in stage.blocks:
                before = x
                x = block(x)
                block_rows.append(block_stats(before, x))
            stage_rows.append(stats4(x))

        prehead = self.model.pre_head(x).flatten(1)
        prehead_stats = stats4(prehead)
        embedding = self.model.head(prehead)
        return embedding, torch.stack(block_rows), torch.stack(stage_rows), prehead_stats


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a.ravel(), b.ravel()) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-12))


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

    diagnostic = DiagnosticWrapper(deployed).eval()
    with torch.no_grad():
        diag_ref = diagnostic(dummy)
    diag_embedding = diag_ref[0].cpu().numpy()
    if cosine(dep, diag_embedding) < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} diagnostic wrapper changed embedding")
    if tuple(diag_ref[1].shape) != (18, 5) or tuple(diag_ref[2].shape) != (4, 4) or tuple(diag_ref[3].shape) != (4,):
        raise RuntimeError(
            f"FaceLiVTv2-{label} bad diagnostic shapes: "
            f"blocks={tuple(diag_ref[1].shape)} stages={tuple(diag_ref[2].shape)} prehead={tuple(diag_ref[3].shape)}"
        )

    torch.onnx.export(
        diagnostic,
        dummy,
        str(output),
        input_names=["input"],
        output_names=["embedding", "block_stats", "stage_stats", "prehead_stats"],
        opset_version=18,
        do_constant_folding=True,
        dynamo=False,
    )
    onnx_model = onnx.load(str(output))
    onnx.checker.check_model(onnx_model)

    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    ort_embedding, ort_blocks, ort_stages, ort_prehead = session.run(None, {"input": dummy.numpy()})
    ort_cos = cosine(dep, ort_embedding)
    max_abs = float(np.max(np.abs(dep - ort_embedding)))
    if ort_blocks.shape != (18, 5) or ort_stages.shape != (4, 4) or ort_prehead.shape != (4,):
        raise RuntimeError(
            f"FaceLiVTv2-{label} ONNX diagnostic shapes invalid: "
            f"{ort_blocks.shape}, {ort_stages.shape}, {ort_prehead.shape}"
        )
    if not (np.isfinite(ort_blocks).all() and np.isfinite(ort_stages).all() and np.isfinite(ort_prehead).all()):
        raise RuntimeError(f"FaceLiVTv2-{label} diagnostic output contains non-finite values")

    print(
        f"FaceLiVTv2-{label} R4 export OK: "
        f"reparam_cos={reparam_cos:.8f}, onnx_cos={ort_cos:.8f}, "
        f"max_abs={max_abs:.8g}, blocks={ort_blocks.shape}, stages={ort_stages.shape}, "
        f"prehead={ort_prehead.shape}, bytes={output.stat().st_size}"
    )
    if ort_cos < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} ONNX fidelity failed: cosine={ort_cos}")


if __name__ == "__main__":
    main()

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
    # timm.register_model() looks up the decorated function's module in sys.modules.
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


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
    cosine = float(np.dot(ref.ravel(), dep.ravel()) / (np.linalg.norm(ref) * np.linalg.norm(dep) + 1e-12))
    if cosine < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} reparameterization fidelity failed: cosine={cosine}")

    torch.onnx.export(
        deployed,
        dummy,
        str(output),
        input_names=["input"],
        output_names=["embedding"],
        opset_version=18,
        do_constant_folding=True,
        dynamo=False,
    )
    onnx_model = onnx.load(str(output))
    onnx.checker.check_model(onnx_model)

    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    ort_out = session.run(None, {"input": dummy.numpy()})[0]
    ort_cos = float(np.dot(dep.ravel(), ort_out.ravel()) / (np.linalg.norm(dep) * np.linalg.norm(ort_out) + 1e-12))
    max_abs = float(np.max(np.abs(dep - ort_out)))
    print(
        f"FaceLiVTv2-{label} export OK: "
        f"reparam_cos={cosine:.8f}, onnx_cos={ort_cos:.8f}, "
        f"max_abs={max_abs:.8g}, bytes={output.stat().st_size}"
    )
    if ort_cos < 0.99999:
        raise RuntimeError(f"FaceLiVTv2-{label} ONNX fidelity failed: cosine={ort_cos}")


if __name__ == "__main__":
    main()

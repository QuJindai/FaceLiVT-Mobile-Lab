#!/usr/bin/env python3
import argparse
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort

EXPECTED = {
    "embedding": [1, 512],
    "block_stats": [18, 5],
    "stage_stats": [4, 4],
    "prehead_stats": [4],
}


def fail(message: str) -> None:
    raise SystemExit("R4 DIAGNOSTIC ONNX FAIL: " + message)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("model")
    args = parser.parse_args()
    path = Path(args.model)
    if not path.exists():
        fail(f"missing model {path}")

    model = onnx.load(str(path))
    onnx.checker.check_model(model)
    graph_outputs = {output.name for output in model.graph.output}
    if graph_outputs != set(EXPECTED):
        fail(f"outputs={sorted(graph_outputs)} expected={sorted(EXPECTED)}")

    session = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])
    input_meta = session.get_inputs()[0]
    dummy = np.linspace(-1.0, 1.0, 3 * 112 * 112, dtype=np.float32).reshape(1, 3, 112, 112)
    values = session.run(list(EXPECTED), {input_meta.name: dummy})

    for name, value in zip(EXPECTED, values):
        expected = EXPECTED[name]
        if list(value.shape) != expected:
            fail(f"{name} shape={list(value.shape)} expected={expected}")
        if not np.isfinite(value).all():
            fail(f"{name} contains non-finite values")

    embedding, blocks, stages, prehead = values
    norm = float(np.linalg.norm(embedding[0]))
    if not (norm > 1e-6):
        fail("embedding norm is zero")
    if np.allclose(blocks[:, 4], 0.0):
        fail("all relative_delta values are zero; diagnostics are not observing real block changes")
    if np.allclose(stages[:, 1], 0.0):
        fail("all stage RMS values are zero")
    if float(prehead[1]) <= 0.0:
        fail("pre-head RMS is zero")

    print(
        f"R4 DIAGNOSTIC ONNX PASS: {path.name} "
        f"embedding={embedding.shape} blocks={blocks.shape} stages={stages.shape} prehead={prehead.shape}"
    )


if __name__ == "__main__":
    main()

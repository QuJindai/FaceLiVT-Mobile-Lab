# FaceLiVT Mobile Lab R4 (0.4.0)

R4 unifies the handset microscope around three evidence layers:

- five-point geometry: LE / RE / N / ML / MR, similarity transform, eye distance, scale, rotation and alignment residual;
- enrollment relations: dynamic NxN per-model cosine matrices, stability/coverage/outlier metrics, XS-S and M-S signed delta matrices, and per-model PCA warnings;
- model internals: XS/S/M architecture metadata, 18 blocks grouped 3/3/9/3, four stage summaries, 1284D pre-head and final 512D identity embedding.

The existing R3.2 model-selection epoch remains the authority for model-dependent microscope state. Normal recognition asks the ONNX model only for `embedding`; only the currently focused microscope model asks for compact `block_stats`, `stage_stats` and `prehead` outputs.

Final delivery is gated by the repository's full Android workflow on the exact release head and again on the merged `main` commit.

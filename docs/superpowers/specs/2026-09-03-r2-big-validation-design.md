# FaceLiVT Mobile Lab R2 Design

## Goal
Deliver one handset build that can answer, in a single real-world session, how much low-resolution camera degradation can be tolerated and which FaceLiVTv2 size gives the best speed/accuracy trade-off on S24U.

## Scope
R2 adds four capabilities to the existing R1 loop without requiring micro-test APKs:

1. **Three-model comparison** — FaceLiVTv2-XS, -S and -M official checkpoints are converted to ONNX in CI. Enrollment creates a separate template for every model from the same five aligned face samples. Single-model and three-model comparison modes are supported.
2. **Temporal fusion** — recognition fuses up to five recent unit embeddings only while ML Kit reports the same tracking ID. A tracking-ID change resets the buffer to prevent mixing two people.
3. **Low-resolution detector assist** — after virtual-camera degradation, frames with short side below 360 px are interpolated to 360 px short side before detection/alignment. No raw high-resolution pixels re-enter the pipeline; the assist only magnifies the already degraded image.
4. **Session instrumentation/export** — rolling detection/inference/total latency, effective recognition FPS, battery temperature and Android thermal status are shown. Every recognition sample is logged and can be exported as CSV with model, quality profile, face pixel size, scores and timings.

## Data flow

`CameraX raw frame -> virtual degradation -> low-res assist (degraded pixels only) -> ML Kit detector -> five-point alignment -> XS/S/M embedding(s) -> tracking-scoped temporal fusion -> model-specific FaceStore -> threshold decision -> UI + SessionLogger`

## Storage migration
R1 stored one vector per identity. R2 uses keys containing the model variant, e.g. `id_<hash>_S_vec`. Existing R1 S templates are read as a fallback and migrated lazily to the new S key. XS and M templates are created on the next enrollment.

## UI
The main screen keeps the live preview and degraded-frame thumbnail, and adds:
- model mode spinner: `S`, `XS`, `M`, `XS/S/M compare`;
- rolling performance/thermal line;
- explicit simulated resolution versus detector-assist resolution;
- `导出CSV` button.

Comparison mode reports one line per model: `model / top-1 / cosine / fused frames / inference ms`. Enrollment always captures all three models so comparison remains mathematically valid.

## Acceptance gates
- Unit tests cover temporal-buffer reset/fusion, low-resolution assist sizing, rolling statistics and CSV escaping.
- CI converts all three official checkpoints and requires PyTorch reparameterization cosine >= 0.99999 and ONNX cosine >= 0.99999 for each.
- Android unit tests and `assembleDebug` pass on Java 17/API 36.
- Artifact is arm64-v8a-only for the S24U validation build to avoid shipping unused native ABIs.
- No user validation is requested until the consolidated R2 APK is available.
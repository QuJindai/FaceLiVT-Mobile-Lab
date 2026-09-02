# FaceLiVT Mobile Lab R2

Android on-device face-recognition validation lab for testing how far modern lightweight face models can tolerate cheap-camera image quality. The phone camera remains high quality for preview, but **all detection, alignment and recognition consume an intentionally degraded analysis frame**.

## R2 validation loop

- FaceLiVTv2 **XS / S / M** official checkpoints, each converted to ONNX during CI
- independent 512-D template space for each backbone; embeddings are never compared across models
- one 5-frame enrollment automatically creates XS, S and M templates from the same aligned faces
- run modes: S, XS, M, or **XS / S / M Compare**
- virtual camera tiers: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p
- resolution reduction + JPEG degradation occurs before detection and recognition
- for sub-360p frames, detector assistance only upscales the **already degraded pixels** to a 360px short side; it never reads the original high-resolution frame
- five-point eye/nose/mouth alignment to 112x112
- per-tracking-ID temporal fusion of up to 5 embeddings; a tracking-ID change resets fusion
- adjustable cosine threshold and per-model Top-1 result
- rolling detection / inference / total latency and derived FPS
- battery temperature and Android thermal status
- session CSV export with image quality, face pixels, per-model similarity, acceptance, timing and thermal fields
- arm64-v8a APK target for the S24U-class handset validation path

## Reproducible model build

GitHub Actions pins upstream FaceLiVT at commit `d99d86607c7c05540c74e815e5a88847f7e667db`. It downloads the author's public `facelivtv2-xs.pt`, `facelivtv2-s.pt` and `facelivtv2-m.pt` checkpoints, applies the upstream deployment reparameterization, exports fixed-shape `1x3x112x112` ONNX files, and requires both:

- original PyTorch vs reparameterized graph cosine >= `0.99999`
- reparameterized PyTorch vs ONNX Runtime cosine >= `0.99999`

The final CI then runs unit tests, builds the APK, verifies all three model assets are actually packaged, and rejects native libraries from any ABI other than `arm64-v8a`.

Upstream: https://github.com/novendrastywn/FaceLiVT

## One consolidated handset acceptance

1. Install R2 and grant Camera permission.
2. Enter one or more test IDs and use `三模型录入×5`; each enrollment creates all three model templates at once.
3. Select `XS / S / M 对比`, then use the app normally while changing camera-quality tiers and distance as needed. The app continuously records true degraded face-pixel size, three model scores, latency and thermal data.
4. At the end, tap `导出 CSV` once. This single session is intended to replace separate feature-by-feature handset checks.

R1 FaceLiVTv2-S templates are lazily migrated into the R2 S template namespace. XS and M require an R2 enrollment because different backbones must not share embedding templates.

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

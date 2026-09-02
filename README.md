# FaceLiVT Mobile Lab

Android on-device face-recognition experiment built around **FaceLiVTv2-S**. The phone camera stays high quality, while the recognition pipeline intentionally degrades each analysis frame to emulate cheap RGB cameras.

## What the first APK validates

- CameraX live capture (front/back camera)
- selectable virtual camera tiers: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p
- resolution reduction + JPEG degradation before detection/recognition
- bundled ML Kit face detector for the prototype detection/alignment stage
- 5-point eye/nose/mouth similarity alignment to 112x112
- FaceLiVTv2-S 512-D embedding through ONNX Runtime, fully on-device
- 5-frame enrollment per identity
- local cosine 1:N matching with adjustable threshold
- live source/simulated resolution, face pixel size, detection latency and embedding latency
- thumbnail showing the actual degraded frame seen by the pipeline

## Reproducible model build

The APK does not commit a converted model. GitHub Actions pins upstream FaceLiVT at commit `d99d86607c7c05540c74e815e5a88847f7e667db`, downloads `facelivtv2-s.pt` from the author's Hugging Face repository, reparameterizes the network, exports fixed-shape 1x3x112x112 ONNX, verifies PyTorch-vs-ONNX cosine fidelity, then packages the ONNX into the debug APK.

Upstream code: https://github.com/novendrastywn/FaceLiVT

## Suggested handset test

1. Install the debug APK and grant Camera permission.
2. Keep 480p selected first; enter a name and tap `录入×5`.
3. After enrollment, walk from close range to several meters and note `脸 WxH px` and similarity.
4. Repeat at 360p / 240p / 180p / 144p.
5. Add a second person and check false accepts while lowering/raising the threshold.

The useful result is not just whether it recognizes; record the lowest simulated camera resolution and smallest face-pixel size at which the identity remains stable.

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model card states `CC BY-SA 4.0` for the model repository/weights. This prototype preserves model attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment still requires a separate review of model-weight and training-data rights; this repository is currently an engineering validation prototype.

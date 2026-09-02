# FaceLiVT Mobile Lab Design

## Goal
Build a single-install Android APK that validates modern small-model face recognition on a phone while deliberately reducing the camera stream to emulate inexpensive RGB cameras.

## Validation question
At what simulated camera quality and face-pixel size does FaceLiVTv2-S stop producing stable identity embeddings on a modern handset?

## Architecture
CameraX supplies a high-quality source frame. Before recognition, every analysis frame is passed through a virtual-camera degradation stage that reduces the frame short side and re-encodes JPEG. The degraded frame is used for face detection, five-point face alignment, FaceLiVTv2-S embedding, and 1:N cosine matching. The preview remains high quality and a separate thumbnail shows exactly what the recognition pipeline receives.

## Components
- `DegradationProfile` / `FrameDegrader`: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p profiles with JPEG quality reduction.
- `FaceAligner` / `SimilarityTransform`: five landmarks mapped to ArcFace-style 112x112 canonical coordinates with a least-squares similarity transform, plus crop fallback.
- `FaceRecognizer`: ONNX Runtime Android, RGB CHW 112x112 normalized with mean/std 0.5, producing a normalized 512-D FaceLiVTv2-S embedding.
- `FaceStore`: local multi-sample identity enrollment and cosine 1:N matching. Five unit embeddings are summed without intermediate normalization so samples remain equally weighted.
- `MainActivity`: camera lifecycle, enrollment, live recognition, quality selection, threshold, and human-readable metrics.
- CI export: pin upstream FaceLiVT commit, download the official S checkpoint, apply upstream reparameterization, export ONNX, compare numerical fidelity, build APK, upload artifact.

## First-release acceptance
- APK installs on Android 8+ and requests only camera permission.
- Front/back camera live preview works.
- User can enroll an identity from five frames and immediately recognize it locally.
- Each quality profile changes the actual model input pipeline, not only the preview.
- UI reports source resolution, simulated resolution, detected face bounding-box pixels, detector latency, embedding latency, threshold, and identity count.
- Build fails if FaceLiVT reparameterization or ONNX conversion cosine fidelity falls below 0.99999.
- GitHub Actions unit tests and debug APK build pass.

## Scope boundary
This release is a recognition-quality experiment, not production biometric access control. It intentionally omits liveness/anti-spoofing, encrypted biometric storage, multi-camera synchronization, and remote identity services.

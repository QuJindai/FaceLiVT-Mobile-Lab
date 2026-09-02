# FaceLiVT Mobile Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a reproducible Android debug APK that runs FaceLiVTv2-S on-device and stress-tests recognition under controllable virtual low-cost camera quality.

**Architecture:** CameraX captures frames; degradation occurs before detection and embedding; ML Kit supplies prototype landmarks; a five-point similarity transform creates the 112x112 model input; ONNX Runtime executes a CI-converted official FaceLiVTv2-S checkpoint; local cosine matching handles enrollment/recognition.

**Tech Stack:** Java 17, Android API 26-36, CameraX 1.6.2, ML Kit face-detection 16.1.7, ONNX Runtime Android 1.29.0, FaceLiVTv2-S, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-facelivt-mobile-lab-design.md`

## Global Constraints
- FaceLiVT upstream pinned to `d99d86607c7c05540c74e815e5a88847f7e667db`.
- Model is `facelivtv2-s.pt`, 512-D, fixed 112x112 RGB input.
- Recognition stays fully on-device after installation.
- Degraded frame must be the frame used by both detector and recognizer.
- First APK must include the complete validation loop rather than separate micro-test APKs.

---

### Task 1: Math and alignment core
**Files:** `SimilarityTransform.java`, `FaceAligner.java`, `SimilarityTransformTest.java`
**Interfaces:** `SimilarityTransform.fit(float[] src, float[] dst) -> float[6]`; `FaceAligner.align(Bitmap, Face) -> Bitmap`.
- [x] Write a unit test that recovers a known scale/rotation/translation similarity transform.
- [x] Implement least-squares similarity fitting.
- [x] Map five ML Kit landmarks to canonical 112x112 coordinates and retain crop fallback.
- [ ] Run `gradle :app:testDebugUnitTest` in CI and require PASS.

### Task 2: Embedding and identity store
**Files:** `VectorMath.java`, `VectorMathTest.java`, `FaceRecognizer.java`, `FaceStore.java`.
**Interfaces:** `embed(Bitmap) -> float[512]`; `addSample(String,float[])`; `bestMatch(float[]) -> Match`.
- [x] Test normalization and cosine behavior.
- [x] Implement ONNX preprocessing and 512-D normalized embedding.
- [x] Store the raw sum of unit embeddings so all five enrollment samples are equally weighted.
- [ ] Verify unit tests in CI.

### Task 3: Virtual cheap-camera pipeline and UI
**Files:** `DegradationProfile.java`, `FrameDegrader.java`, `MainActivity.java`, `activity_main.xml`.
**Interfaces:** `FrameDegrader.apply(Bitmap, DegradationProfile) -> Bitmap`.
- [x] Implement short-side downscale and JPEG re-encoding profiles from native to 144p.
- [x] Feed degraded frames into detection/alignment/embedding.
- [x] Add five-frame enrollment, adjustable threshold, camera switching and readable metrics.
- [x] Show a thumbnail of the degraded frame seen by the pipeline while keeping the normal preview intact.
- [ ] Validate CameraX/ML Kit API compilation in CI.

### Task 4: Reproducible model conversion and APK CI
**Files:** `tools/export_facelivtv2.py`, `.github/workflows/android.yml`, Gradle files.
**Interfaces:** output `app/src/main/assets/facelivtv2_s.onnx`; artifact `app-debug.apk`.
- [x] Download the official FaceLiVTv2-S checkpoint from the author's Hugging Face repo.
- [x] Apply upstream deployment reparameterization and require cosine >= 0.99999 against the original PyTorch graph.
- [x] Export opset-18 ONNX and require ONNX Runtime cosine >= 0.99999.
- [x] Build with API 36 / Java 17 and upload the debug APK artifact.
- [ ] Inspect workflow logs; patch any real compile/export failure; rerun until green.

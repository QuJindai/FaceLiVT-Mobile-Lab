# FaceLiVT Mobile Lab R2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one R2 APK with XS/S/M comparison, five-frame temporal fusion, degraded-only low-resolution detector assist, rolling performance/thermal metrics, and CSV export.

**Architecture:** Extend R1 around pure-Java utilities first, then generalize ONNX model loading and model-specific templates, then wire the consolidated UI/camera pipeline. CI exports three official checkpoints and builds an arm64-v8a APK.

**Tech Stack:** Java 17, Android API 26-36, CameraX, ML Kit face detection, ONNX Runtime Android, FaceLiVTv2 XS/S/M, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-r2-big-validation-design.md`

## Global Constraints
- Upstream FaceLiVT commit remains `d99d86607c7c05540c74e815e5a88847f7e667db`.
- Official files are `facelivtv2-xs.pt`, `facelivtv2-s.pt`, `facelivtv2-m.pt`.
- All embeddings are 512-D and model spaces must never be mixed.
- Low-resolution assist may only use the already degraded bitmap.
- R2 handset artifact is arm64-v8a-only.
- User receives one consolidated R2 APK, not micro-test APKs.

---

### Task 1: Pure-Java reliability core
**Files:**
- Create: `TemporalEmbeddingBuffer.java`, `LowResPolicy.java`, `PerformanceWindow.java`, `SessionCsv.java`
- Test: matching `*Test.java` files under `app/src/test/...`

**Interfaces:**
- `TemporalEmbeddingBuffer(int capacity)`, `push(int trackingId, float[] unitEmbedding) -> float[]`, `size() -> int`
- `LowResPolicy.assistedSize(int width, int height) -> int[2]`
- `PerformanceWindow(int capacity)`, `add(long detectMs,long inferMs,long totalMs)`, average getters
- `SessionCsv.escape(String) -> String`

- [ ] Write tests first for same-ID fusion, ID reset, 360-short-side assist, no downscale above 360, rolling-window eviction and CSV quote escaping.
- [ ] Trigger branch CI and verify RED because production classes do not exist.
- [ ] Implement the minimal classes.
- [ ] Re-run unit tests and require GREEN.

### Task 2: Multi-model inference and template storage
**Files:**
- Create: `ModelVariant.java`, `RecognizerBank.java`
- Modify: `FaceRecognizer.java`, `FaceStore.java`

**Interfaces:**
- `ModelVariant` maps XS/S/M to asset names and labels.
- `RecognizerBank.embed(ModelVariant, Bitmap) -> TimedEmbedding`
- `FaceStore.addSample(String, ModelVariant, float[])`
- `FaceStore.bestMatch(ModelVariant, float[]) -> Match`

- [ ] Generalize FaceRecognizer to accept an asset/model variant.
- [ ] Lazily cache one session per variant in RecognizerBank.
- [ ] Store separate raw embedding sums per identity per model.
- [ ] Preserve R1 S-template fallback and lazy migration.

### Task 3: Camera pipeline, temporal fusion and metrics
**Files:**
- Modify: `MainActivity.java`, `activity_main.xml`
- Create: `ThermalProbe.java`, `SessionLogger.java`

**Interfaces:**
- `ThermalProbe.snapshot() -> Snapshot`
- `SessionLogger.record(...)`; `exportCsv(Context) -> String`

- [ ] Upscale only the degraded bitmap to 360 px short side when needed, then run detector/alignment on that assisted bitmap.
- [ ] Use ML Kit tracking ID to scope independent five-frame fusion buffers per model.
- [ ] Enrollment always generates XS/S/M templates from the same aligned crop.
- [ ] Add S/XS/M/compare mode and show per-model score + inference latency.
- [ ] Add rolling detect/infer/total time, recognition FPS, battery temperature and thermal status.
- [ ] Log session samples and add one-tap CSV export.

### Task 4: Three-model reproducible CI and APK
**Files:**
- Modify: `tools/export_facelivtv2.py`, `.github/workflows/android.yml`, `app/build.gradle`, `README.md`

- [ ] Add `--variant {xs,s,m}` and official checkpoint mapping to exporter.
- [ ] Export and fidelity-check all three ONNX models in CI.
- [ ] Enable the feature branch for CI during development.
- [ ] Restrict native ABI to `arm64-v8a`.
- [ ] Run `:app:testDebugUnitTest :app:assembleDebug` and require PASS.
- [ ] Upload final APK artifact and record size/SHA-256.

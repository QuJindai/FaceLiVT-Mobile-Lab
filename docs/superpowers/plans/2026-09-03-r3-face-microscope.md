# R3 Face Microscope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build separate Enrollment and Recognition microscope pages that expose image quality, template stability, embedding geometry, Top-K matching, timing and formula chains on-device.

**Architecture:** Keep the existing single-activity CameraX pipeline but split the UI into two page containers controlled by explicit tabs. Add focused analysis classes for quality scoring, enrollment-session aggregation, template geometry and recognition trends; custom Views render the microscope graphics without new chart libraries.

**Tech Stack:** Java 17, Android Views/XML, CameraX 1.6.2, ML Kit Face Detection 16.1.7, ONNX Runtime Android 1.29.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-03-r3-face-microscope-design.md`

## Global Constraints
- Keep minSdk 26 and targetSdk 36.
- Preserve XS/S/M model support and arm64-v8a packaging.
- Preserve R1/R2 template readability.
- Respect status/navigation bar insets on S24U portrait.
- No cloud upload, active liveness model, or on-device training in R3.
- No intermediate handset validation requests.

---

### Task 1: Quality and template microscope domain

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/FaceQuality.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentSession.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/EmbeddingProjector.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceStore.java`
- Test: `app/src/test/java/com/qujindai/facelivtlab/FaceQualityTest.java`
- Test: `app/src/test/java/com/qujindai/facelivtlab/EnrollmentSessionTest.java`
- Test: `app/src/test/java/com/qujindai/facelivtlab/EmbeddingProjectorTest.java`

**Interfaces:**
- `FaceQuality.evaluate(Bitmap aligned, Face face, int degradedW, int degradedH)` -> immutable `FaceQuality.Snapshot`.
- `EnrollmentSession.add(ModelVariant, float[], FaceQuality.Snapshot)` and `summary(ModelVariant)` -> centroid/stability/dispersion/matrix/projection.
- `EmbeddingProjector.project(List<float[]>)` -> `float[][]` normalized 2D coordinates.
- `FaceStore.replaceTemplate(String, ModelVariant, float[] centroid, int sampleCount)` commits the R3 centroid.
- `FaceStore.topMatches(ModelVariant, float[], int)` returns ordered matches.

- [ ] Write tests for quality score bounds/pose penalties, weighted centroid/stability, projection shape/finite values and Top-K ordering.
- [ ] Run unit tests and confirm RED because R3 classes/interfaces do not exist.
- [ ] Implement the minimum domain classes and FaceStore extensions.
- [ ] Run all unit tests and confirm GREEN.

### Task 2: Microscope visual components

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/SimilarityMatrixView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/EmbeddingScatterView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/TopKBarView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/TrendChartView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/FaceOverlayView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/RecognitionTrend.java`
- Test: `app/src/test/java/com/qujindai/facelivtlab/RecognitionTrendTest.java`

**Interfaces:**
- Matrix view consumes `float[][]` plus sample labels.
- Scatter view consumes `float[][]` plus centroid coordinate.
- Top-K view consumes `List<FaceStore.Match>` plus threshold.
- Trend view consumes rolling similarity/quality series.
- Overlay view consumes detector-space face box and landmarks and maps them to displayed preview coordinates.

- [ ] Write RED test for bounded 30-point recognition trend behavior.
- [ ] Implement visual Views and trend buffer.
- [ ] Compile/debug build to verify Android resource/class integration.

### Task 3: Two-page R3 UI and enrollment microscope

**Files:**
- Replace: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java`
- Create: `app/src/main/res/drawable/r3_panel.xml`
- Create: `app/src/main/res/drawable/r3_tab_active.xml`
- Create: `app/src/main/res/drawable/r3_tab_inactive.xml`
- Modify: `app/src/main/res/values/styles.xml`

**Interfaces:**
- Tabs call `showPage(Page.ENROLLMENT|RECOGNITION)` and expose only one workflow's controls at a time.
- Enrollment capture collects five quality snapshots and three embeddings per sample without writing FaceStore until the session completes.
- Completion calls `replaceTemplate` for XS/S/M and renders archive summary, per-sample quality lines, matrix, projection and substituted formula chain.

- [ ] Build the two page containers with separate controls and microscope panels.
- [ ] Wire enrollment capture to `EnrollmentSession`.
- [ ] Render sample archive and model-specific summary.
- [ ] Preserve S24U insets and readable button states.
- [ ] Run UI contract and Android build.

### Task 4: Recognition microscope

**Files:**
- Modify: `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/SessionLogger.java`

**Interfaces:**
- Recognition measures `detectMs`, `alignMs`, `inferMs`, `matchMs` and displays the stage chain.
- Recognition computes live `FaceQuality.Snapshot`, Top-3, top1/top2 margin, current threshold and quality gate.
- Recognition updates Top-K bars, rolling trend and formula substitution on every processed frame.

- [ ] Add stage timings and live quality gate.
- [ ] Add Top-3 and margin logic.
- [ ] Update rolling trend and pipeline text.
- [ ] Extend CSV with quality/margin/timing microscope fields while preserving existing export.
- [ ] Run all unit tests and APK build.

### Task 5: CI gate, documentation and final APK

**Files:**
- Modify: `.github/workflows/android.yml`
- Create: `tools/verify_r3_microscope.py`
- Modify: `README.md`
- Modify: `app/build.gradle`

**Interfaces:**
- CI gate verifies two page IDs, four microscope custom Views, formula-chain labels and S24U inset hooks before model export.
- Version becomes `0.3.0` / versionCode `3`.

- [ ] Write R3 static UI/architecture contract gate.
- [ ] Run it RED against incomplete state if needed, then GREEN after integration.
- [ ] Run full XS/S/M fidelity export, unit tests, APK build, asset/ABI verification.
- [ ] Open PR, merge to `main`, rerun full CI on the merge commit.
- [ ] Download the `main` artifact and verify final APK SHA-256 locally.
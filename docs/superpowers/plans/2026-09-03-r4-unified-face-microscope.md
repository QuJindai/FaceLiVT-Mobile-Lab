# FaceLiVT Mobile Lab R4 Unified Microscope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build R4 `0.4.0` so the handset can observe model structure/internal feature evolution, cross-model enrollment relations, and five-point alignment geometry while preserving R3.2 model linkage and recognition behavior.

**Architecture:** Keep one ONNX per FaceLiVTv2 variant and expose optional compact diagnostic outputs from the same graph. Split calculations into focused immutable domain classes and focused custom Views; `MainActivity` only wires frame data into those components. Geometry diagnostics remain model-independent while internal block/stage diagnostics follow the R3.2 microscope selection epoch.

**Tech Stack:** Android Java 17, CameraX, ML Kit Face Detection, ONNX Runtime Android 1.29.0, Python/PyTorch/ONNX exporter, JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-r4-unified-face-microscope-design.md`

## Global Constraints

- Version name must be `0.4.0` and version code `6`.
- Keep minSdk `26`, target/compile SDK `36`, ABI `arm64-v8a` only.
- Keep FaceLiVT upstream pinned at `d99d86607c7c05540c74e815e5a88847f7e667db`.
- XS/S/M remain independent 512-D identity spaces; never compute cross-model embedding cosine.
- Keep exactly one ONNX asset per variant; diagnostics are optional outputs of that same graph.
- Normal embedding inference requests only `embedding`; only the microscope focus requests diagnostic outputs.
- Preserve R3.2 `MicroscopeSelectionState` stale-frame rejection.
- No full intermediate feature maps are stored or rendered.

---

## File structure

### New domain files

- `app/src/main/java/com/qujindai/facelivtlab/ModelArchitectureSpec.java` — static model metadata and block/stage indexing.
- `app/src/main/java/com/qujindai/facelivtlab/ModelDiagnostics.java` — validated runtime diagnostic tensors and summaries.
- `app/src/main/java/com/qujindai/facelivtlab/CrossModelEnrollmentComparison.java` — model-wise relation metrics and signed delta matrices.
- `app/src/main/java/com/qujindai/facelivtlab/FaceAlignmentDiagnostics.java` — five-point transform metrics.

### New view files

- `app/src/main/java/com/qujindai/facelivtlab/ModelArchitectureView.java`
- `app/src/main/java/com/qujindai/facelivtlab/BlockDiagnosticsView.java`
- `app/src/main/java/com/qujindai/facelivtlab/CrossModelComparisonView.java`
- `app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometryView.java`

### Modified runtime files

- `tools/export_facelivtv2.py` — diagnostic graph outputs and fidelity/shape checks.
- `app/src/main/java/com/qujindai/facelivtlab/FaceRecognizer.java` — named-output inference and diagnostic parsing.
- `app/src/main/java/com/qujindai/facelivtlab/RecognizerBank.java` — timed embedding vs timed diagnostic calls.
- `app/src/main/java/com/qujindai/facelivtlab/FaceAligner.java` — return aligned bitmap + diagnostics.
- `app/src/main/java/com/qujindai/facelivtlab/FaceOverlayView.java` — label LE/RE/N/ML/MR.
- `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java` — wire domain outputs to views and preserve model epoch linkage.
- `app/src/main/res/layout/activity_main.xml` — add R4 microscope panels and R4 title.
- `app/build.gradle` — version `0.4.0` / code `6`.

### New tests/contracts

- `app/src/test/java/com/qujindai/facelivtlab/ModelArchitectureSpecTest.java`
- `app/src/test/java/com/qujindai/facelivtlab/ModelDiagnosticsTest.java`
- `app/src/test/java/com/qujindai/facelivtlab/CrossModelEnrollmentComparisonTest.java`
- `app/src/test/java/com/qujindai/facelivtlab/FaceAlignmentDiagnosticsTest.java`
- `tools/verify_r4_unified_microscope.py`

---

### Task 1: Architecture metadata and cross-model enrollment comparison

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelArchitectureSpec.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/CrossModelEnrollmentComparison.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/ModelArchitectureSpecTest.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/CrossModelEnrollmentComparisonTest.java`

**Interfaces:**
- Produces: `ModelArchitectureSpec.forVariant(ModelVariant)` returning metadata with `depths`, `widths`, `mixerTypes`, `blockCount`, `approxParamsM`, `preheadDim`, `embeddingDim`.
- Produces: `CrossModelEnrollmentComparison.from(EnumMap<ModelVariant, EnrollmentSession.Summary>)` returning per-model `meanPair`, `minPair`, `outlierIndex`, and signed `deltaXsVsS`, `deltaMVsS` matrices.

- [ ] **Step 1: Write failing architecture tests**

```java
@Test public void variantsShareDepthButDifferWidth() {
    ModelArchitectureSpec xs = ModelArchitectureSpec.forVariant(ModelVariant.XS);
    ModelArchitectureSpec s = ModelArchitectureSpec.forVariant(ModelVariant.S);
    ModelArchitectureSpec m = ModelArchitectureSpec.forVariant(ModelVariant.M);
    assertArrayEquals(new int[]{3,3,9,3}, xs.depths);
    assertArrayEquals(new int[]{3,3,9,3}, s.depths);
    assertArrayEquals(new int[]{3,3,9,3}, m.depths);
    assertEquals(18, xs.blockCount);
    assertArrayEquals(new int[]{32,64,128,256}, xs.widths);
    assertArrayEquals(new int[]{48,96,192,320}, s.widths);
    assertArrayEquals(new int[]{56,112,224,448}, m.widths);
    assertEquals(1284, s.preheadDim);
    assertEquals(512, s.embeddingDim);
}
```

- [ ] **Step 2: Write failing cross-model comparison tests**

Use synthetic 3x3 matrices with known off-diagonal values and assert:

```java
assertEquals(0.80f, result.byVariant.get(ModelVariant.S).meanPair, 1e-6f);
assertEquals(0.70f, result.byVariant.get(ModelVariant.S).minPair, 1e-6f);
assertEquals(0, result.byVariant.get(ModelVariant.S).outlierIndex);
assertEquals(xs[0][1] - s[0][1], result.deltaXsVsS[0][1], 1e-6f);
assertEquals(m[2][1] - s[2][1], result.deltaMVsS[2][1], 1e-6f);
```

- [ ] **Step 3: Run targeted tests and verify RED**

Run: `gradle :app:testDebugUnitTest --tests '*ModelArchitectureSpecTest' --tests '*CrossModelEnrollmentComparisonTest'`

Expected: compilation failure because the two production classes do not yet exist.

- [ ] **Step 4: Implement `ModelArchitectureSpec`**

Use immutable cloned arrays. Define XS/S/M exactly as in the spec. Mixer labels are `RepMix`, `RepMix`, `MHLA`, `MHLA`; approximate parameter values are `2.90f`, `4.62f`, `7.04f`.

- [ ] **Step 5: Implement `CrossModelEnrollmentComparison`**

For each matrix, exclude the diagonal. Compute each sample's mean cosine to all other samples; the minimum such mean is the outlier. Validate matrix sizes match before computing deltas; if a model is absent, expose an empty delta matrix rather than inventing values.

- [ ] **Step 6: Run targeted tests and verify GREEN**

Run: `gradle :app:testDebugUnitTest --tests '*ModelArchitectureSpecTest' --tests '*CrossModelEnrollmentComparisonTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

Commit message: `feat: add R4 model and enrollment comparison domains`

---

### Task 2: Five-point alignment diagnostics

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/FaceAlignmentDiagnostics.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceAligner.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/FaceAlignmentDiagnosticsTest.java`

**Interfaces:**
- Produces: `FaceAligner.AlignmentResult alignWithDiagnostics(Bitmap source, Face face)`.
- `AlignmentResult` contains `Bitmap aligned` and `FaceAlignmentDiagnostics diagnostics`.
- Preserve compatibility: existing `FaceAligner.align(...)` delegates to `alignWithDiagnostics(...).aligned`.

- [ ] **Step 1: Write failing pure-math diagnostic test**

Add a package-visible/static constructor that accepts source points, canonical points, and fitted affine values. Perfect canonical-to-canonical points must give `meanResidualPx < 1e-4`, `maxResidualPx < 1e-4`, `scale ~= 1`, and `rotationDeg ~= 0`.

- [ ] **Step 2: Write perturbed-point test**

Perturb the mouth-right point by 4 px and assert `meanResidualPx > 0` and `maxResidualPx >= meanResidualPx`.

- [ ] **Step 3: Run targeted test and verify RED**

Run: `gradle :app:testDebugUnitTest --tests '*FaceAlignmentDiagnosticsTest'`

Expected: missing production class/API.

- [ ] **Step 4: Implement geometry calculation**

Given affine `[a,b,tx,c,d,ty]`:

```java
float scale = (float)Math.sqrt(a*a + c*c);
float rotationDeg = (float)Math.toDegrees(Math.atan2(c, a));
```

Transform each source landmark into canonical coordinates and compute mean/max Euclidean residual. Eye distance is the source-space Euclidean distance between LE and RE.

- [ ] **Step 5: Modify `FaceAligner`**

When all five landmarks exist and transform succeeds, return five-point diagnostics. On fallback crop, return `landmarkCount` actually available, `usedFallback=true`, and `NaN` for transform-specific metrics that cannot be justified.

- [ ] **Step 6: Run targeted tests and verify GREEN**

Run: `gradle :app:testDebugUnitTest --tests '*FaceAlignmentDiagnosticsTest'`

Expected: PASS.

- [ ] **Step 7: Commit**

Commit message: `feat: add five-point alignment diagnostics`

---

### Task 3: ONNX internal diagnostic outputs and runtime parsing

**Files:**
- Modify: `tools/export_facelivtv2.py`
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelDiagnostics.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceRecognizer.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/RecognizerBank.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/ModelDiagnosticsTest.java`

**Interfaces:**
- Produces: `FaceRecognizer.embedWithDiagnostics(Bitmap)` returning `ModelDiagnostics.Result` with normalized embedding, 18x4 block stats, 4x4 stage stats, 1284-D prehead, and prehead summary.
- Produces: `RecognizerBank.TimedDiagnostics diagnose(ModelVariant, Bitmap)` with inference time and parsed diagnostics.
- Existing `embed(...)` stays embedding-only.

- [ ] **Step 1: Write failing parser-shape tests**

```java
@Test public void requiresExpectedDiagnosticShapes() {
    float[][] blocks = new float[18][4];
    float[][] stages = new float[4][4];
    float[] prehead = new float[1284];
    ModelDiagnostics d = ModelDiagnostics.of(blocks, stages, prehead);
    assertEquals(18, d.blockStats.length);
    assertEquals(4, d.stageStats.length);
    assertEquals(1284, d.prehead.length);
}
```

Also assert invalid 17x4, 4x3, and 100-D inputs throw `IllegalArgumentException`.

- [ ] **Step 2: Run Java test and verify RED**

Run: `gradle :app:testDebugUnitTest --tests '*ModelDiagnosticsTest'`

Expected: class missing.

- [ ] **Step 3: Implement `ModelDiagnostics`**

Store defensive copies and derive pre-head `meanAbs`, `rms`, and `sparsity` using the same threshold `0.05` as the exporter.

- [ ] **Step 4: Wrap deployed PyTorch model in exporter**

Create a diagnostic wrapper whose forward path manually traverses each stage/block so it can collect compact statistics without retaining full feature maps:

```python
class DiagnosticWrapper(torch.nn.Module):
    def forward(self, x):
        block_rows = []
        stage_rows = []
        for stage_idx in range(self.net.num_stage):
            x = self.net.patch_embedds[stage_idx](x)
            stage_in = x
            for block in self.net.stages[stage_idx].blocks:
                block_in = x
                x = block(x)
                block_rows.append(stats(x, block_in))
            stage_rows.append(stats(x, stage_in))
        prehead = self.net.pre_head(x).flatten(1)
        embedding = self.net.head(prehead)
        return embedding, torch.stack(block_rows), torch.stack(stage_rows), prehead
```

`stats()` returns `[mean_abs, rms, sparsity, delta_ratio]` and uses only ONNX-friendly tensor ops.

- [ ] **Step 5: Export and verify names/shapes/fidelity**

Export output names exactly `embedding`, `block_stats`, `stage_stats`, `prehead`. Load with ONNX Runtime and assert shapes `(1,512)`, `(18,4)`, `(4,4)`, `(1,1284)`. Compare ONNX embedding against deployed original model and require cosine `>= 0.99999`.

- [ ] **Step 6: Update Android `FaceRecognizer`**

Refactor bitmap preprocessing into one helper. `embed()` calls `session.run(inputMap, Set.of("embedding"))`. `embedWithDiagnostics()` requests all four outputs by name and parses them by output name, never by assumed result order.

- [ ] **Step 7: Update `RecognizerBank`**

Add `TimedDiagnostics` and `diagnose()`. In Compare mode callers can use `diagnose()` only for the focused model while using `embed()` for the other two.

- [ ] **Step 8: Run Java tests and exporter smoke on all variants**

Run Java: `gradle :app:testDebugUnitTest --tests '*ModelDiagnosticsTest'`

Run Python for XS/S/M using the same pinned upstream path as CI. Expected: all shapes valid and embedding cosine `>=0.99999`.

- [ ] **Step 9: Commit**

Commit message: `feat: expose FaceLiVT internal microscope outputs`

---

### Task 4: R4 custom microscope views and layout

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelArchitectureView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/BlockDiagnosticsView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/CrossModelComparisonView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometryView.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceOverlayView.java`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Interfaces:**
- `ModelArchitectureView.setVariant(ModelArchitectureSpec)`.
- `BlockDiagnosticsView.setData(ModelArchitectureSpec, ModelDiagnostics)` and `clearForVariant(ModelArchitectureSpec)`.
- `CrossModelComparisonView.setData(CrossModelEnrollmentComparison)`.
- `AlignmentGeometryView.setData(FaceAlignmentDiagnostics)` and `clear()`.

- [ ] **Step 1: Add layout IDs and R4 title before production wiring**

Add enrollment IDs `enrollmentArchitecture`, `crossModelComparison`; recognition IDs `recognitionArchitecture`, `alignmentGeometry`, `blockDiagnostics`. Change visible title to `FaceLiVT R4 · 人脸显微镜`.

- [ ] **Step 2: Implement architecture view**

Render four stage bars labelled `S1 3x RepMix`, `S2 3x RepMix`, `S3 9x MHLA`, `S4 3x MHLA`, with widths and summary text such as `S · 4.62M · 18 Blocks · 1284D -> 512D`.

- [ ] **Step 3: Implement block diagnostics view**

Render 18 compact columns grouped 3/3/9/3. For each block show delta-ratio as bar height and encode mean-abs/rms/sparsity in the detail line for the selected block. Add four stage summary values and pre-head summary text. No full feature map rendering.

- [ ] **Step 4: Implement cross-model comparison view**

Render XS/S/M rows with params, meanPair, minPair, `Sstable`, `Coverage`, and outlier sample label. Render signed `XS-S` and `M-S` NxN delta heatmaps stacked vertically. Add text: `5 samples -> 5x5; matrix size follows sample count, not model layers` and PCA warning.

- [ ] **Step 5: Implement alignment geometry view and overlay labels**

Display `5/5`, method, eye distance, scale, rotation, mean/max residual, and formula `p'=sRp+t`. Update `FaceOverlayView` to draw `LE/RE/N/ML/MR` labels next to the five dots.

- [ ] **Step 6: Compile resources**

Run: `gradle :app:compileDebugJavaWithJavac`

Expected: PASS before MainActivity wiring if IDs/classes are syntactically correct.

- [ ] **Step 7: Commit**

Commit message: `feat: add R4 microscope visual panels`

---

### Task 5: Wire unified microscope into enrollment and recognition flows

**Files:**
- Modify: `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java`

**Interfaces:**
- Consumes all Task 1-4 APIs.
- Produces complete R4 frame flow while preserving `MicroscopeSelectionState` snapshot/epoch semantics.

- [ ] **Step 1: Bind R4 views and model metadata**

Bind all five new view IDs. On enrollment observation-model change, update both architecture view and the selected model's matrix/PCA/formula. On recognition model/focus change, synchronously call `recognitionArchitecture.setVariant(...)` and `blockDiagnostics.clearForVariant(...)` before waiting for a new frame.

- [ ] **Step 2: Switch alignment call to diagnostics result**

Replace:

```java
Bitmap aligned = FaceAligner.align(detectorBitmap, face);
```

with:

```java
FaceAligner.AlignmentResult alignment = FaceAligner.alignWithDiagnostics(detectorBitmap, face);
Bitmap aligned = alignment.aligned;
FaceAlignmentDiagnostics geometry = alignment.diagnostics;
```

Update `alignmentGeometry` every detected frame. Geometry remains model-independent.

- [ ] **Step 3: Use focused diagnostic inference**

Capture `MicroscopeSelectionState.Snapshot frameSelection` before model inference. For each variant in the captured mode:

- if `variant == frameSelection.focus`, call `recognizerBank.diagnose(variant, aligned)` and use its embedding for matching/fusion;
- otherwise call `recognizerBank.embed(variant, aligned)`.

Before publishing model-specific UI, keep the existing `microscopeSelection.isCurrent(frameSelection)` guard.

- [ ] **Step 4: Publish block/stage/prehead diagnostics**

When the frame is current, update `blockDiagnostics` from the focused model's `ModelDiagnostics`. Update the pipeline text to include `18 blocks -> 4 stages -> 1284D -> 512D` and keep quality/geometry explicitly labelled model-independent.

- [ ] **Step 5: Publish cross-model enrollment comparison**

After five-sample completion, build an `EnumMap<ModelVariant, EnrollmentSession.Summary>`, feed it to `CrossModelEnrollmentComparison.from(...)`, and render the cross-model panel. The existing selected-model matrix/PCA stays visible above it.

- [ ] **Step 6: Update enrollment/recognition explanatory copy**

Explicitly distinguish:

`5 facial landmarks != 5 enrollment samples != 18 model blocks != 512 embedding dimensions`.

- [ ] **Step 7: Run all unit tests and assemble APK**

Run: `gradle :app:testDebugUnitTest :app:assembleDebug --stacktrace`

Expected: PASS.

- [ ] **Step 8: Commit**

Commit message: `feat: wire R4 unified face microscope`

---

### Task 6: R4 version, CI contract, documentation, and final main verification

**Files:**
- Modify: `app/build.gradle`
- Create: `tools/verify_r4_unified_microscope.py`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`

**Interfaces:**
- Produces final artifact `FaceLiVT-Mobile-Lab-R4-debug.apk`.

- [ ] **Step 1: Write R4 contract verifier**

The script must require:

- version code `6`, version name `0.4.0`;
- all four new domain classes and four new view classes;
- layout IDs for architecture, geometry, block diagnostics, and cross-model comparison;
- `embedWithDiagnostics`, `block_stats`, `stage_stats`, `prehead` strings;
- `MicroscopeSelectionState.Snapshot frameSelection` and stale-frame guard remain present;
- explanatory copy distinguishes 5 landmarks, 5 samples, 18 blocks, and 512 dimensions.

- [ ] **Step 2: Update exporter check in CI**

After each XS/S/M export, run an ONNX inspection helper or the exporter's built-in assertions so CI fails if any diagnostic output name/shape is missing.

- [ ] **Step 3: Update APK artifact/version naming**

CI copies debug APK to `FaceLiVT-Mobile-Lab-R4-debug.apk`, verifies three ONNX assets and arm64-only ABI, prints SHA-256 and size, and uploads artifact `FaceLiVT-Mobile-Lab-R4-debug-apk`.

- [ ] **Step 4: Update README**

Document R4 architecture microscope, the one-model/multi-output design, 5-point geometry formula, dynamic NxN sample matrix, signed delta matrices, and PCA comparability warning.

- [ ] **Step 5: Run complete branch CI**

Required steps all PASS: R2.1 UI contract, R3 microscope contract, R3.1 calibration contract, R3.2 linkage contract, R4 unified microscope contract, XS/S/M export fidelity and diagnostic shapes, Java unit tests, APK build, model assets, arm64-only ABI, artifact upload.

- [ ] **Step 6: Review PR diff for scope**

Only R4 domain/view/runtime/export/CI/docs files should change. No unrelated refactors.

- [ ] **Step 7: Squash merge to `main`**

Use expected feature-head SHA. After merge, wait for the new `main` workflow run and require every gate to PASS again.

- [ ] **Step 8: Download and independently verify the main artifact**

Unzip artifact, compute APK SHA-256, confirm three model assets exist, and confirm no native ABI other than `arm64-v8a`.

---

## Self-review

- Spec coverage: all three requested microscope axes are mapped to Tasks 1-5; packaging/main verification is Task 6.
- No duplicate diagnostic models are introduced.
- No cross-model embedding cosine is introduced; only same-cell matrix deltas are compared.
- Five-point geometry is separated from model diagnostics.
- R3.2 model-selection epoch remains the only authority for model-dependent UI.
- Every new calculation has a unit-test or CI contract gate.

# R4 Deep Face Microscope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build R4/0.4.0 as one integrated face-recognition engineering microscope that exposes five-point alignment quality, independent XS/S/M enrollment geometry, cross-model matrix deltas, and real FaceLiVTv2 internal block/stage activation summaries without changing the final 512-D identity decision.

**Architecture:** Preserve the existing R3.2 camera/degradation/recognition path and model-selection epoch. Add a compact diagnostic inference path that exposes real 18-block and 4-stage statistics from the same pinned FaceLiVTv2 deployment graph, plus model-independent geometry diagnostics computed from the five alignment landmarks. Keep raw feature maps ephemeral; Android stores only compact statistics. Enrollment and recognition pages consume the same model-focus snapshot so every model-specific panel switches together.

**Tech Stack:** Android Java 17, CameraX 1.6.2, ML Kit face detection 16.1.7, ONNX Runtime Android 1.29.0, PyTorch 2.6 CPU exporter, ONNX opset 18, JUnit 4.13.2, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-r4-deep-face-microscope-design.md`

## Global Constraints

- Target version is `versionCode 6`, `versionName '0.4.0'`.
- S24U-class package remains `arm64-v8a` only.
- FaceLiVT upstream remains pinned at `d99d86607c7c05540c74e815e5a88847f7e667db`.
- XS/S/M remain independent 512-D identity spaces; never cross-cosine raw embeddings between variants.
- Stage depths are `[3,3,9,3]`, total backbone blocks `18`, stage types `[RepMix,RepMix,MHLA,MHLA]`.
- Geometry/image-quality diagnostics are model-independent; embedding/template/model-structure diagnostics are model-specific and must follow `MicroscopeSelectionState`.
- Final identity decision remains the existing normalized 512-D cosine decision.
- Full raw intermediate feature maps are never retained beyond the diagnostic inference call.
- R3.2 stale-frame epoch rejection must remain effective for every new model-specific R4 panel.

---

### Task 1: Diagnostic FaceLiVTv2 export and Android runtime

**Files:**
- Modify: `tools/export_facelivtv2.py`
- Create: `app/src/main/java/com/qujindai/facelivtlab/DeepModelStats.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceRecognizer.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/RecognizerBank.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/DeepModelStatsTest.java`
- Create: `tools/verify_r4_diagnostic_onnx.py`

**Interfaces:**
- Produces `DeepModelStats` with `BlockStat[18]`, `StageStat[4]`, `preHeadMeanAbs`, `preHeadRms`, `preHeadStd`, `preHeadNearZero`.
- Produces `FaceRecognizer.DiagnosticResult diagnose(Bitmap aligned)` returning the same normalized 512-D embedding plus `DeepModelStats`.
- Produces `RecognizerBank.TimedDiagnostic diagnose(ModelVariant variant, Bitmap aligned)`.

- [ ] **Step 1: Write RED Android tests for diagnostic shape and metadata**

Create `DeepModelStatsTest` asserting:

```java
@Test public void facelivtTopologyHasEighteenBlocksInFourStages() {
    DeepModelStats stats = DeepModelStats.empty(ModelVariant.S);
    assertEquals(18, stats.blocks.length);
    assertArrayEquals(new int[]{3,3,9,3}, stats.stageDepths);
    assertEquals(4, stats.stages.length);
}

@Test public void blockIndicesMapToExpectedStages() {
    assertEquals(0, DeepModelStats.stageForBlock(0));
    assertEquals(1, DeepModelStats.stageForBlock(3));
    assertEquals(2, DeepModelStats.stageForBlock(6));
    assertEquals(3, DeepModelStats.stageForBlock(15));
    assertEquals(3, DeepModelStats.stageForBlock(17));
}
```

- [ ] **Step 2: Run unit tests and confirm RED**

Run in CI/build environment:

```bash
gradle :app:testDebugUnitTest --tests '*DeepModelStatsTest' --stacktrace
```

Expected: compile failure because `DeepModelStats` does not exist.

- [ ] **Step 3: Extend exporter with real forward hooks and compact stats**

Wrap the reparameterized FaceLiVTv2 model so the ONNX graph outputs:

```text
embedding        [1,512]
block_stats      [18,5]
stage_stats      [4,4]
prehead_stats    [4]
```

For each block input `x` and output `y`, export:

```python
mean_abs = y.abs().mean()
rms = torch.sqrt((y * y).mean() + 1e-12)
std = y.std(unbiased=False)
near_zero = (y.abs() < 1e-3).float().mean()
relative_delta = torch.linalg.vector_norm(y - x) / (torch.linalg.vector_norm(x) + 1e-6)
```

For each stage output export `[mean_abs,rms,std,near_zero]`. Capture the tensor immediately before the final classifier and export the same four pre-head statistics.

The wrapper must preserve `embedding` numerically versus the existing deployed graph with cosine `>=0.99999`.

- [ ] **Step 4: Add exporter verification**

`tools/verify_r4_diagnostic_onnx.py` loads each generated XS/S/M graph and requires:

```python
assert output_shapes['embedding'] == [1, 512]
assert output_shapes['block_stats'] == [18, 5]
assert output_shapes['stage_stats'] == [4, 4]
assert output_shapes['prehead_stats'] == [4]
assert np.isfinite(block_stats).all()
assert np.isfinite(stage_stats).all()
```

It also compares ONNX `embedding` to deployed PyTorch and fails below cosine `0.99999`.

- [ ] **Step 5: Implement Android diagnostic data types and parsing**

`DeepModelStats` owns immutable copies of the four diagnostic outputs and topology metadata. `FaceRecognizer.embed()` continues requesting only `embedding`; `diagnose()` requests all diagnostic outputs and parses float arrays into `DeepModelStats`.

- [ ] **Step 6: Run exporter and Android tests to GREEN**

Run:

```bash
python tools/export_facelivtv2.py --variant xs --upstream upstream-facelivt --output /tmp/xs.onnx
python tools/verify_r4_diagnostic_onnx.py /tmp/xs.onnx
python tools/export_facelivtv2.py --variant s --upstream upstream-facelivt --output /tmp/s.onnx
python tools/verify_r4_diagnostic_onnx.py /tmp/s.onnx
python tools/export_facelivtv2.py --variant m --upstream upstream-facelivt --output /tmp/m.onnx
python tools/verify_r4_diagnostic_onnx.py /tmp/m.onnx
gradle :app:testDebugUnitTest --stacktrace
```

Expected: all PASS and final embeddings retain fidelity.

- [ ] **Step 7: Commit**

```bash
git add tools/export_facelivtv2.py tools/verify_r4_diagnostic_onnx.py app/src/main/java/com/qujindai/facelivtlab/DeepModelStats.java app/src/main/java/com/qujindai/facelivtlab/FaceRecognizer.java app/src/main/java/com/qujindai/facelivtlab/RecognizerBank.java app/src/test/java/com/qujindai/facelivtlab/DeepModelStatsTest.java
git commit -m "feat: expose real FaceLiVT block diagnostics"
```

---

### Task 2: Five-point alignment geometry microscope

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometry.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceAligner.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceOverlayView.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/AlignmentGeometryTest.java`

**Interfaces:**
- Produces `FaceAligner.Result alignWithGeometry(Bitmap source, Face face)` containing `Bitmap aligned` and `AlignmentGeometry geometry`.
- `AlignmentGeometry` exposes `landmarkCount`, five source points, transformed points, `eyeDistancePx`, `rollDeg`, `scale`, `translationPx`, `meanResidualPx`, `maxResidualPx`, `usedFallback`.

- [ ] **Step 1: Write RED geometry tests**

Use synthetic five-point coordinates where the transform is known. Assert exact identity-transform residual near zero and a deliberately perturbed mouth point produces `maxResidualPx > meanResidualPx`.

```java
assertEquals(5, g.landmarkCount);
assertFalse(g.usedFallback);
assertEquals(0f, g.meanResidualPx, 1e-3f);
```

- [ ] **Step 2: Verify RED**

Run:

```bash
gradle :app:testDebugUnitTest --tests '*AlignmentGeometryTest' --stacktrace
```

Expected: compile failure because `AlignmentGeometry` / `alignWithGeometry` do not exist.

- [ ] **Step 3: Implement geometry computation**

Use the existing ArcFace 5-point target and the exact affine coefficients returned by `SimilarityTransform.fit`. Compute transformed source points and:

```text
E_align = mean_i ||p'_i - p_hat_i||2
E_max   = max_i  ||p'_i - p_hat_i||2
roll    = atan2(y_right_eye-y_left_eye, x_right_eye-x_left_eye)
scale   = sqrt(a^2+c^2) from affine [a,b,tx,c,d,ty]
translation = sqrt(tx^2+ty^2)
```

If any of the five landmarks is missing or transform fitting fails, preserve existing crop fallback and return `usedFallback=true`, residuals as `NaN`, and actual landmark count.

- [ ] **Step 4: Overlay labels**

`FaceOverlayView` must render the five points with compact labels `LE`, `RE`, `N`, `ML`, `MR` and preserve the existing face box.

- [ ] **Step 5: Run tests GREEN**

Run all unit tests and confirm no fallback/alignment regression.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometry.java app/src/main/java/com/qujindai/facelivtlab/FaceAligner.java app/src/main/java/com/qujindai/facelivtlab/FaceOverlayView.java app/src/test/java/com/qujindai/facelivtlab/AlignmentGeometryTest.java
git commit -m "feat: add five-point geometry microscope"
```

---

### Task 3: Enrollment matrix diagnostics and cross-model comparison

**Files:**
- Modify: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentSession.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelComparisonStats.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/MatrixDeltaView.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/ModelComparisonStatsTest.java`

**Interfaces:**
- Extend `EnrollmentSession.Summary` with `minPairCosine`, `meanPairCosine`, `outlierIndex`, `outlierMeanCosine`.
- Produce `ModelComparisonStats.from(EnumMap<ModelVariant, EnrollmentSession.Summary>)`.
- Produce `ModelComparisonStats.delta(ModelVariant a, ModelVariant b)` returning same-size scalar cosine matrix difference.

- [ ] **Step 1: Write RED tests for 5×5 semantics and outlier detection**

Construct five synthetic normalized embeddings with one outlier and assert:

```java
assertEquals(5, summary.similarityMatrix.length);
assertEquals(5, summary.similarityMatrix[0].length);
assertEquals(0, summary.outlierIndex);
assertTrue(summary.minPairCosine < summary.meanPairCosine);
```

Also assert delta matrix element-wise subtraction is exact and shape follows sample count rather than a hard-coded five.

- [ ] **Step 2: Verify RED**

Run targeted JUnit test and expect missing fields/class.

- [ ] **Step 3: Implement summary statistics**

For off-diagonal pairs compute min and mean cosine. For each sample compute mean cosine to all other samples; lowest mean is `outlierIndex`. Preserve current stability/coverage/template commit behavior.

- [ ] **Step 4: Implement comparison model and delta view**

Comparison row fields:

```text
variant, parameterCountM, blockCount=18, stability, coverage,
minPairCosine, meanPairCosine, outlierIndex, outlierMeanCosine
```

Delta matrices operate only on scalar cosine matrices with matching sample count. `MatrixDeltaView` renders positive/negative differences around zero and labels the exact pair, e.g. `ΔM_XS,S`.

- [ ] **Step 5: Run tests GREEN**

Run all JUnit tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/qujindai/facelivtlab/EnrollmentSession.java app/src/main/java/com/qujindai/facelivtlab/ModelComparisonStats.java app/src/main/java/com/qujindai/facelivtlab/MatrixDeltaView.java app/src/test/java/com/qujindai/facelivtlab/ModelComparisonStatsTest.java
git commit -m "feat: compare enrollment geometry across models"
```

---

### Task 4: Model topology and 18-block visual microscope

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelTopology.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/BlockMicroscopeView.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/ModelComparisonView.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/ModelTopologyTest.java`

**Interfaces:**
- `ModelTopology.forVariant(ModelVariant)` returns params, widths, stage depths/types, block count, final feature dim 1284 and embedding dim 512.
- `BlockMicroscopeView.setStats(ModelTopology topology, DeepModelStats stats)`.
- `ModelComparisonView.setRows(List<ModelComparisonStats.Row> rows)`.

- [ ] **Step 1: Write RED topology tests**

Require:

```java
XS widths = [32,64,128,256], params≈2.9
S  widths = [48,96,192,320], params≈4.62
M  widths = [56,112,224,448], params≈7.0
all depths = [3,3,9,3]
all blockCount = 18
finalFeatureDim = 1284
embeddingDim = 512
```

- [ ] **Step 2: Verify RED**

Run targeted test; expect missing `ModelTopology`.

- [ ] **Step 3: Implement topology model**

Keep all model facts in one class so UI copy and tests cannot drift independently.

- [ ] **Step 4: Implement block microscope view**

Render 18 blocks grouped with visible stage separators:

```text
B01-B03 RepMix | B04-B06 RepMix | B07-B15 MHLA | B16-B18 MHLA
```

Per block show `relativeDelta` and `rms`; selected block expands `meanAbs/std/nearZero`. Stage summary shows width and `[meanAbs,rms,std,nearZero]`.

- [ ] **Step 5: Implement model comparison view**

Render one compact row each for XS/S/M: `params | 18 blocks | Sstable | Coverage | min pair | mean pair | outlier`.

- [ ] **Step 6: Run tests GREEN**

Run all unit tests.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/qujindai/facelivtlab/ModelTopology.java app/src/main/java/com/qujindai/facelivtlab/BlockMicroscopeView.java app/src/main/java/com/qujindai/facelivtlab/ModelComparisonView.java app/src/test/java/com/qujindai/facelivtlab/ModelTopologyTest.java
git commit -m "feat: add 18-block model microscope views"
```

---

### Task 5: Integrate R4 microscope into enrollment and recognition pages

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/SessionLogger.java`
- Create: `tools/verify_r4_microscope.py`

**Interfaces:**
- Enrollment page receives `AlignmentGeometry`, current model `EnrollmentSession.Summary`, all-model `ModelComparisonStats`, optional latest `DeepModelStats`.
- Recognition page receives current focus `DeepModelStats`, `AlignmentGeometry`, existing Top-K/Probe/PCA/quality/performance evidence.

- [ ] **Step 1: Write RED R4 UI/contract verifier**

Require source/layout anchors for:

```text
R4 / 0.4.0
5点几何显微镜
对齐残差
模型结构显微镜
18 blocks
[3,3,9,3]
ΔM_XS,S
ΔM_M,S
5×5 来源于 5 张样本
512D cosine
PCA 坐标不可跨模型直接比较
```

Also require diagnostic calls use `MicroscopeSelectionState.Snapshot` and stale UI writes test `microscopeSelection.isCurrent(snapshot)`.

- [ ] **Step 2: Run verifier and confirm RED**

Run:

```bash
python tools/verify_r4_microscope.py
```

Expected: FAIL because R4 panels are not wired.

- [ ] **Step 3: Integrate geometry data into camera chain**

Replace internal call sites that need diagnostics with `FaceAligner.alignWithGeometry`. Continue passing only the aligned bitmap to recognition. Show `5/5`, inter-eye distance, roll, scale, translation, mean/max residual or explicit fallback-crop status.

- [ ] **Step 4: Integrate model diagnostics without normal-path regression**

Normal continuous recognition remains `embed()` for every processed frame. Deep diagnostic capture is throttled and only runs for the currently focused model, e.g. at most once per second while a microscope page is visible. Capture the current selection snapshot before diagnostic inference and drop results if its epoch is stale on UI delivery.

- [ ] **Step 5: Integrate enrollment comparison**

After enrollment completion and whenever inspect model changes:

- update the focused model's 5×5 matrix/PCA/formula;
- update the three-model comparison rows;
- update `ΔM_XS,S` and `ΔM_M,S`;
- label current outlier sample;
- show the sentence `5×5 来源于 5 张录入样本，不是模型结构`.

- [ ] **Step 6: Integrate recognition model structure microscope**

On XS/S/M switch, immediately clear old deep stats and show pending state for the new focus. Once new diagnostic stats arrive, update header, 18-block strip, stage summary and `1284D→512D` line. Geometry/image quality remain labelled model-independent.

- [ ] **Step 7: Extend CSV evidence**

Add geometry fields (`landmark_count`, `fallback_crop`, `eye_distance_px`, `align_mean_residual_px`, `align_max_residual_px`) and focused-model diagnostic summary (`stage1_rms...stage4_rms`, `prehead_rms`) without dumping 18×5 raw stats to every CSV row.

- [ ] **Step 8: Run contract and Android tests GREEN**

Run:

```bash
python tools/verify_ui_contract.py
python tools/verify_r3_microscope.py
python tools/verify_r31_calibration.py
python tools/verify_model_microscope_linkage.py
python tools/verify_r4_microscope.py
gradle :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: all PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/qujindai/facelivtlab/MainActivity.java app/src/main/java/com/qujindai/facelivtlab/SessionLogger.java tools/verify_r4_microscope.py
git commit -m "feat: integrate R4 deep face microscope"
```

---

### Task 6: Version, CI, documentation, and release gate

**Files:**
- Modify: `app/build.gradle`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`
- Modify: `tools/verify_r31_calibration.py` only if its version assertion is intentionally a floor rather than an exact R3.1 version.

**Interfaces:**
- Produces artifact `FaceLiVT-Mobile-Lab-R4-debug-apk` containing `FaceLiVT-Mobile-Lab-R4-debug.apk`.

- [ ] **Step 1: Set release version**

```gradle
versionCode 6
versionName '0.4.0'
```

- [ ] **Step 2: Add R4 CI contract and diagnostic-ONNX verification**

Workflow sequence must be:

```text
R2.1 UI contract
R3 microscope contract
R3.1 calibration contract
R3.2 model-linkage contract
R4 microscope contract
export XS/S/M diagnostic ONNX + fidelity verification
JUnit + assembleDebug
APK three-model asset check
arm64-v8a-only check
SHA-256
artifact upload
```

- [ ] **Step 3: Update README**

Document:

- five landmarks and similarity-transform residual;
- 5 samples → dynamic N×N cosine matrix;
- XS/S/M same depth `3+3+9+3=18 blocks`, different widths;
- real block/stage diagnostic stats and no raw feature-map retention;
- per-model PCA non-comparability;
- cross-model scalar matrix deltas;
- final decision still 512-D cosine.

- [ ] **Step 4: Run full branch CI**

Require every step and artifact upload to PASS. Read logs and capture XS/S/M embedding fidelity, final APK SHA-256 and size.

- [ ] **Step 5: Review branch diff**

Compare `main...feat/r4-deep-microscope`. Reject unrelated files, temporary patch workflows/scripts, debug secrets, or binary model assets committed to Git.

- [ ] **Step 6: Open and squash-merge R4 PR**

PR body includes R4 scope and branch CI evidence. Merge only if mergeable and branch CI is green.

- [ ] **Step 7: Re-run full CI on merged `main`**

Treat the `main` run as the release authority. Do not deliver a feature-branch APK as final.

- [ ] **Step 8: Download and independently verify main artifact**

Confirm:

```text
APK filename = FaceLiVT-Mobile-Lab-R4-debug.apk
XS/S/M ONNX assets present
only lib/arm64-v8a/* native libraries
APK SHA-256 matches main CI log
```

- [ ] **Step 9: Deliver final APK**

Provide one direct sandbox APK link plus the original main Actions ZIP, version/commit/run ID/SHA-256, and a concise list of R4 microscope capabilities.

---

## Plan Self-Review

- Spec coverage: geometry microscope, 5-point residual, independent model spaces, dynamic N×N matrix semantics, delta matrices, outlier diagnostics, 18-block real activation summaries, stage/pre-head statistics, model-selection linkage, PCA semantics, CSV evidence, version/CI/release are each mapped to Tasks 1-6.
- Placeholder scan: no TBD/TODO/"implement later" instructions remain.
- Type consistency: `DeepModelStats`, `AlignmentGeometry`, `ModelComparisonStats`, `ModelTopology`, `FaceRecognizer.DiagnosticResult`, `RecognizerBank.TimedDiagnostic`, and `FaceAligner.Result` are defined before later integration tasks consume them.
- Scope: all tasks produce one coherent R4 APK and do not add unrelated authentication, server, or cloud subsystems.

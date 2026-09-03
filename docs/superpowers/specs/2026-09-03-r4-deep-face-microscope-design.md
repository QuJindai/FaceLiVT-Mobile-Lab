# FaceLiVT Mobile Lab R4 · Deep Face Microscope Design

Date: 2026-09-03
Status: Approved in-chat design, written specification pending final user review
Target: Android / S24U-class arm64 handset
Version target: `0.4.0` / `versionCode 6`

## 1. Purpose

R4 turns the existing R3.2 face-recognition microscope into one coherent engineering instrument that explains three different layers of evidence without mixing their semantics:

1. **Geometry microscope** — whether face detection and five-point alignment are trustworthy.
2. **Embedding/template microscope** — what the five enrollment samples look like inside each model's independent 512-D identity space.
3. **Model-structure microscope** — how information evolves through the real FaceLiVTv2 backbone before the final 512-D embedding.

R4 must preserve all R3.2 behavior: cheap-camera degradation before detection, independent XS/S/M templates, temporal fusion, quality gates, coverage/stability, threshold calibration, model-switch linkage, and stale-frame epoch rejection.

## 2. Non-goals

R4 will **not**:

- display every raw feature-map pixel/channel from all 18 blocks;
- compare XS/S/M 512-D coordinates directly across different model spaces;
- treat a 2-D PCA location as the identity decision;
- reinterpret the 5×5 enrollment matrix as model architecture;
- make full diagnostic inference the default path if it measurably penalizes normal recognition.

The final identity decision remains the existing 512-D cosine decision.

## 3. FaceLiVTv2 model facts shown in the UI

All three current backbones share the same four-stage depth:

```text
Stage depths = [3, 3, 9, 3]
Total backbone blocks = 18
Stage types = [RepMix, RepMix, MHLA, MHLA]
Final feature = 1284-D
Identity embedding = 512-D
```

Widths differ:

```text
XS: [32,  64, 128, 256]   ≈ 2.9M parameters
S : [48,  96, 192, 320]   ≈ 4.62M parameters
M : [56, 112, 224, 448]   ≈ 7.0M parameters
```

R4 labels these as **same depth, different width** models. “18 blocks” must not be described as exactly “18 neural-network layers”, because each block contains multiple operations/residual branches.

## 4. Architecture

### 4.1 Single model-selection source of truth

`MicroscopeSelectionState` remains authoritative for:

- recognition model mode;
- current microscope focus XS/S/M;
- selection epoch used to reject in-flight results from a previous model.

Every model-specific R4 panel must consume the same captured snapshot/epoch. Geometry/image-quality panels are explicitly labelled **model-independent**.

### 4.2 Diagnostic ONNX outputs

CI continues to download the pinned official FaceLiVTv2 XS/S/M checkpoints and export one ONNX asset per variant.

The R4 exporter wraps the original deployment model and exposes:

- `embedding`: original 512-D final output;
- `block_stats`: compact real activation statistics for all 18 blocks;
- `stage_stats`: compact statistics after the four stages;
- `prehead_stats`: compact statistics for the 1284-D pre-head representation.

The diagnostic outputs are derived from the **real forward tensors** in the same exported graph; they are not synthetic estimates based only on the final embedding.

Normal recognition requests only `embedding`. Microscope diagnostic capture requests the additional outputs. The Android runtime must verify that normal-path latency is not materially regressed by unused diagnostic outputs; if ORT does not prune unused diagnostic branches sufficiently, CI/runtime will fall back to a separate diagnostic session/model only when the deep microscope is opened.

No raw full feature maps are retained beyond the inference call.

### 4.3 Block statistics

For each block input `x` and output `y`, R4 records a compact vector such as:

```text
mean_abs      = mean(|y|)
rms           = sqrt(mean(y²))
std           = std(y)
near_zero     = mean(|y| < ε)
relative_delta= ||y-x||₂ / (||x||₂ + ε)
```

Because block input/output shapes are identical within a block, `relative_delta` is directly meaningful for all 18 blocks.

The UI visualizes these as an 18-block strip grouped `3 | 3 | 9 | 3`, with stage boundaries and RepMix/MHLA labels.

### 4.4 Stage statistics

Patch-merging changes shape and channel width, so direct elementwise delta across stage boundaries is not used. Each stage instead reports normalized activation summaries and shape/channel metadata.

## 5. Geometry microscope

### 5.1 Five points

The five displayed points are explicitly named:

1. left eye center;
2. right eye center;
3. nose base;
4. left mouth corner;
5. right mouth corner.

They are alignment landmarks, not enrollment samples and not identity dimensions.

### 5.2 Similarity transform

Detected points `pᵢ` are fitted to the existing ArcFace-style 112×112 reference points `p̂ᵢ` using:

```text
p'ᵢ = s R pᵢ + t
```

The geometry panel displays:

- `5/5` landmark completeness;
- inter-eye distance in source/detector pixels;
- estimated roll;
- transform scale `s`;
- translation magnitude;
- alignment residual.

Alignment residual is:

```text
E_align = (1/5) Σ ||p'ᵢ - p̂ᵢ||₂
```

R4 also records max-point residual to expose one badly drifting landmark hidden by a good mean.

### 5.3 Geometry decision

The panel separates:

```text
Landmark completeness
Alignment residual
Image quality
Model identity score
```

so a low recognition score can be diagnosed as detector/alignment degradation versus backbone degradation.

Fallback crop remains available when five landmarks are incomplete, but the microscope must show `5pt unavailable → fallback crop` rather than presenting a fake five-point residual.

## 6. Enrollment vector/template microscope

### 6.1 Why the matrix is 5×5

With `N=5` accepted enrollment samples, each variant produces five independent 512-D embeddings:

```text
f₁ ... f₅ ∈ R⁵¹²
```

The matrix is:

```text
Mᵢⱼ = cos(fᵢ, fⱼ)
M ∈ R^(N×N)
```

Therefore current matrix size is 5×5 **because N=5 samples**, not because the model is 5×5. The visualization remains data-driven if sample count changes later.

### 6.2 Independent model spaces

R4 keeps three independent matrices:

```text
M_XS, M_S, M_M
```

XS/S/M embeddings are never cross-cosined because their 512-D spaces are different.

Switching model focus must immediately replace:

- matrix values;
- sample-to-centroid cosine values;
- stability/dispersion;
- embedding coverage;
- PCA projection;
- formulas/labels.

### 6.3 Cross-model comparison

R4 adds a compact comparison panel that compares **model-level statistics**, not raw embedding coordinates:

```text
variant | params | block depth | Sstable | Coverage | min pair | mean pair | max outlier gap
```

For the same sample indices, matrix differences are valid because the matrix entries are scalar cosines:

```text
ΔM_XS,S = M_XS - M_S
ΔM_M,S  = M_M  - M_S
```

The UI uses these delta matrices to reveal which sample relationships improve or degrade as width increases.

### 6.4 Outlier sample

The sample with the lowest mean cosine to the other accepted samples is explicitly marked as the current outlier candidate. This is diagnostic only; existing hard-gate/coverage logic still governs template commit unless a later version adds automatic sample replacement.

## 7. PCA visualization semantics

Each model has its own PCA fit:

```text
XS 512-D → PCA_XS → 2-D
S  512-D → PCA_S  → 2-D
M  512-D → PCA_M  → 2-D
```

The UI must state:

- rotation/mirroring/scale of one model's 2-D plot cannot be compared directly with another model's plot;
- cluster compactness, relative sample/centroid geometry, explained variance, and probe trajectory are meaningful within one model;
- final recognition still uses 512-D cosine.

## 8. Model-structure microscope UI

A new model structure panel appears on both enrollment and recognition microscope pages.

Header:

```text
FaceLiVTv2-S · 4.62M · 18 blocks · [3,3,9,3]
width [48,96,192,320] · final 1284D → 512D
```

Block strip:

```text
Stage1 RepMix: B01 B02 B03
Stage2 RepMix: B04 B05 B06
Stage3 MHLA  : B07 ... B15
Stage4 MHLA  : B16 B17 B18
```

Each block tile/ribbon shows at least `relative_delta` and activation energy; tapping/selecting a block expands its metrics without dumping raw feature maps.

A stage summary shows whether a low-resolution input first exhibits a strong representation collapse/change in early, middle, or late stages.

## 9. Recognition microscope integration

The recognition evidence chain becomes:

```text
frame
→ cheap-camera degradation
→ detector
→ 5 landmarks
→ similarity transform + E_align
→ 112×112 aligned face
→ image quality gate
→ selected FaceLiVTv2 18-block structure
→ 1284-D pre-head
→ 512-D embedding
→ 5-frame temporal fusion
→ Top-K cosine
→ calibrated threshold / decision
```

Current-frame and rolling timing scopes stay separate. Deep-diagnostic time is shown separately from normal identity inference time so diagnostic instrumentation is not mistaken for production inference cost.

## 10. Data model additions

New immutable domain objects should isolate UI from inference details:

- `AlignmentDiagnostics`
- `BlockActivationStats`
- `ModelTrace`
- `ModelArchitectureSpec`
- `CrossModelEnrollmentComparison`

`FaceAligner` returns both aligned bitmap and optional alignment diagnostics through a result object while keeping a compatibility helper if needed.

`FaceRecognizer` adds a diagnostic inference method returning final embedding plus `ModelTrace`; normal `embed()` remains unchanged semantically.

## 11. CSV / evidence export

R4 extends CSV with model-independent geometry and model-specific trace summaries:

```text
landmarks_complete
inter_eye_px
alignment_mean_residual_px
alignment_max_residual_px
alignment_scale
block_max_relative_delta
block_max_relative_delta_index
stage1_energy ... stage4_energy
prehead_energy
```

The 18×metric detailed trace can be serialized as an additional compact JSON field/file when deep diagnostics are enabled rather than exploding the main CSV into hundreds of columns.

## 12. Performance constraints

- Normal recognition must continue to use the final 512-D output path and preserve R3.2 cadence.
- Deep trace collection is opt-in/visible when the model microscope is on screen.
- No persistent full feature maps.
- No second copy of XS/S/M model weights unless selective ONNX outputs prove unable to avoid diagnostic overhead.
- Existing arm64-v8a packaging remains.

## 13. Testing and CI gates

R4 adds tests/contracts for:

1. architecture specs: XS/S/M all `[3,3,9,3]`, 18 blocks, correct widths;
2. five-point residual formula and fallback semantics;
3. matrix dimension equals sample count `N`, not model size;
4. XS/S/M independent matrices and comparison deltas;
5. model switch invalidates old trace epoch as well as old Top-K/Probe state;
6. diagnostic ONNX outputs exist for XS/S/M and final embedding remains numerically equivalent to the original deployed model;
7. trace tensor shape is exactly the documented 18-block structure;
8. normal-path embedding does not change when diagnostics are not requested;
9. APK contains all three R4 model assets and only arm64-v8a native libraries.

Final delivery is accepted only after the feature branch and merged `main` both pass the full existing R2.1/R3/R3.1/R3.2 gates plus the new R4 gates.

## 14. Success criteria

On one S24U screen flow, an engineer can answer all of the following without reading source code:

- What are the five orange points and how well did they align this frame?
- Is recognition degradation caused before the model or inside the model?
- Why is the enrollment matrix 5×5?
- Does switching XS/S/M actually change the independent matrix/vector evidence?
- Are XS/S/M deeper models or wider models?
- At which of the 18 real blocks/stages does low-resolution information change most?
- What remains model-independent versus model-specific?
- Which displayed formula/value actually drives the final identity decision?

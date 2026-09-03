# FaceLiVT Mobile Lab R4 Unified Microscope Design

## Goal

Upgrade the current R3.2 handset workbench into a unified face-recognition microscope that explains three layers of evidence in one workflow:

1. model architecture and internal feature evolution;
2. enrollment embeddings and cross-model relation matrices;
3. five-point geometry, alignment quality, and its effect on recognition.

R4 remains an Android on-device engineering validation tool for S24U-class arm64 handsets. It must preserve R3.2 model-switch linkage, stale-frame epoch rejection, cheap-camera degradation, enrollment hard gates/coverage, empirical threshold calibration, and 512-D cosine identity decisions.

## Version and platform

- Version name: `0.4.0`
- Version code: `6`
- Android minSdk: `26`
- Android target/compile SDK: `36`
- Native ABI: `arm64-v8a` only
- FaceLiVT upstream stays pinned at commit `d99d86607c7c05540c74e815e5a88847f7e667db`.
- FaceLiVTv2 XS/S/M remain three independent identity spaces and three independent templates.

## Model architecture microscope

R4 must make the model structure explicit in both enrollment and recognition pages.

| Variant | Approx params | Depths | Blocks | Stage widths | Stage mixer types | Pre-head | Embedding |
| --- | ---: | --- | ---: | --- | --- | ---: | ---: |
| XS | ~2.9M | `[3,3,9,3]` | 18 | `[32,64,128,256]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |
| S | ~4.62M | `[3,3,9,3]` | 18 | `[48,96,192,320]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |
| M | ~7.0M | `[3,3,9,3]` | 18 | `[56,112,224,448]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |

The UI must explicitly state that XS/S/M have the same depth and differ mainly in width. It must also distinguish `18 model blocks`, `5 enrollment samples`, `5 facial landmarks`, and `512 embedding dimensions` so these numbers cannot be confused.

### One ONNX model per variant

R4 must not duplicate model weights into a separate diagnostic model. Each existing ONNX asset will expose optional diagnostic outputs in addition to the final embedding:

- `embedding`: `[1,512]`
- `block_stats`: `[18,4]`
- `stage_stats`: `[4,4]`
- `prehead`: `[1,1284]`

Normal recognition requests only `embedding`. The focused microscope model requests the additional outputs. In Compare mode, only the current microscope focus requests diagnostics; the other variants remain embedding-only.

### Diagnostic statistics

For each block output `x_out` and its block input `x_in`, export four compact statistics:

1. `mean_abs = mean(abs(x_out))`
2. `rms = sqrt(mean(x_out^2))`
3. `sparsity = mean(abs(x_out) < 0.05)`
4. `delta_ratio = mean(abs(x_out - x_in)) / (mean(abs(x_in)) + 1e-6)`

The same four statistics are exported for each of the four stage outputs. These are engineering observability signals, not identity scores.

The ONNX exporter must verify that adding these diagnostic outputs does not change the final 512-D embedding. PyTorch deployed embedding vs ONNX embedding cosine must remain `>= 0.99999` for XS/S/M.

## Enrollment embedding microscope

The current enrollment target remains five accepted, quality-gated, novel samples. Therefore the default relation matrix is 5x5, but the matrix implementation remains dynamic NxN.

For model `v` and enrollment embeddings `f_i^v`:

`M_ij^v = cosine(f_i^v, f_j^v)`

The UI must state: `5 samples => 5x5 relation matrix; this is not model depth and not landmark count.`

Each model has its own independent matrix and its own PCA projection because XS/S/M 512-D coordinate systems are different.

### Cross-model comparison

For each variant, compute and show:

- `meanPair`: mean off-diagonal sample cosine;
- `minPair`: minimum off-diagonal sample cosine;
- `Sstable`;
- `Coverage`;
- `outlier`: sample whose mean cosine to other samples is lowest.

Also compute signed delta matrices relative to S:

- `Delta_XS,S = M_XS - M_S`
- `Delta_M,S = M_M - M_S`

The signed delta heatmaps are directly comparable because each cell represents the same sample pair, even though the underlying 512-D spaces differ.

The PCA plot must carry a visible warning: absolute 2-D position/orientation across XS/S/M must not be compared because each model has its own PCA basis. Only cluster shape, relative separation, stability, and outlier behavior are meaningful.

## Five-point geometry microscope

The five orange landmarks are:

1. left eye center;
2. right eye center;
3. nose base;
4. left mouth corner;
5. right mouth corner.

They are used for geometric normalization, not direct identity classification.

R4 introduces `FaceAlignmentDiagnostics` and an alignment result containing both the aligned 112x112 bitmap and geometry diagnostics.

For detected points `p_i` and canonical ArcFace points `p_hat_i`, fit a similarity transform:

`p'_i = s R p_i + t`

Show:

- landmark completeness `n/5`;
- whether five-point alignment or fallback crop was used;
- original eye distance in pixels;
- fitted scale `s`;
- fitted rotation angle in degrees;
- mean alignment residual in canonical 112x112 pixels;
- maximum alignment residual in canonical 112x112 pixels.

Residual formula:

`E_align = mean_i || p'_i - p_hat_i ||_2`

This geometry panel must update every frame and be visibly upstream of model diagnostics so a score drop can be attributed to either alignment/landmark failure or model behavior.

The face overlay must label the five points with compact names: `LE`, `RE`, `N`, `ML`, `MR`.

## Recognition microscope data flow

For each frame:

`camera -> degradation -> face detect -> 5 landmarks -> similarity alignment -> 112x112 -> quality gate -> FaceLiVT focused model -> 18 block diagnostics -> 4 stage diagnostics -> 1284D pre-head -> 512D embedding -> temporal fusion -> Top-K cosine -> decision`

The focused model is controlled by the existing R3.2 `MicroscopeSelectionState`. A frame captures one selection snapshot. If model/focus changes before UI publication, the stale frame is rejected.

Image-quality metrics remain model-independent. Geometry diagnostics are detector/alignment-dependent and therefore also model-independent. Model architecture and internal diagnostics are model-dependent and must switch with XS/S/M.

## UI structure

### Enrollment page

Keep current enrollment quality controls and add, in this order after the observation-model selector:

1. `ModelArchitectureView` for selected XS/S/M;
2. existing model-specific archive/formula panel;
3. existing sample matrix;
4. existing 512D->2D projection with cross-model PCA warning;
5. new `CrossModelComparisonView` with XS/S/M summary rows and delta matrices.

### Recognition page

Keep current decision microscope and add:

1. `AlignmentGeometryView` immediately after detector input/landmark visualization;
2. `ModelArchitectureView` after model selector/threshold area;
3. `BlockDiagnosticsView` showing 18 blocks grouped 3/3/9/3, plus 4-stage summary and 1284D->512D transition;
4. existing Top-K, trend, calibration, Probe PCA, formula chain.

Model switching must synchronously clear model-specific block/stage/pre-head diagnostics and show `waiting for <variant> diagnostic frame` until the next valid focused-model frame arrives.

## New domain components

- `ModelArchitectureSpec`: immutable metadata for XS/S/M depths, widths, mixer types, approximate parameters, pre-head and embedding dimensions.
- `ModelDiagnostics`: immutable parsed runtime output for block/stage stats and pre-head summary.
- `CrossModelEnrollmentComparison`: derives per-model pair metrics, outlier index, and signed delta matrices.
- `FaceAlignmentDiagnostics`: immutable five-point/fallback geometry result.
- `FaceAligner.AlignmentResult`: aligned bitmap plus `FaceAlignmentDiagnostics`.

Views must consume these domain objects and not reimplement calculations.

## Testing and CI gates

R4 adds a dedicated contract verifier and unit tests.

Required unit coverage:

- architecture metadata is exactly 18 blocks with `[3,3,9,3]` for XS/S/M and variant-specific widths;
- cross-model comparison computes mean/min pair, outlier, and signed delta matrices correctly;
- similarity-transform diagnostics report near-zero residual for perfect synthetic points and positive residual for perturbed points;
- R3.2 selection-state tests continue to pass;
- model diagnostic output parsing validates `[18,4]`, `[4,4]`, and 1284D shapes.

Required exporter/CI checks for each XS/S/M ONNX:

- outputs include `embedding`, `block_stats`, `stage_stats`, `prehead`;
- shapes are `[1,512]`, `[18,4]`, `[4,4]`, `[1,1284]`;
- final embedding cosine vs deployed PyTorch is `>=0.99999`;
- APK contains exactly the three FaceLiVTv2 ONNX assets and only arm64-v8a native libraries.

## Non-goals

- No full intermediate feature maps are retained or rendered.
- No duplicate diagnostic ONNX assets.
- No cross-model cosine between XS/S/M embeddings.
- No claim that PCA coordinates are comparable across model variants.
- No change to the current biometric threshold semantics beyond existing engineering calibration behavior.

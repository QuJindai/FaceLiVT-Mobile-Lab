# FaceLiVT Mobile Lab R4

Android on-device face-recognition **deep microscope and calibration workbench** for validating FaceLiVTv2 lightweight face models under intentionally degraded cheap-camera image quality.

The S24U camera remains high quality for preview, but detection, alignment and recognition consume the intentionally degraded analysis frame. R4 keeps the R3.2 model-selection epoch so stale results from a previous backbone cannot overwrite the current microscope.

## R4: three microscopes in one chain

R4 unifies three evidence layers instead of showing only a final recognition score:

```text
cheap-camera frame
→ face detector
→ 5-point geometry microscope
→ 112×112 aligned face
→ FaceLiVTv2 model-structure microscope
→ 512-D identity embedding
→ template/vector microscope
→ cosine / threshold / decision
```

The final identity decision is still the normalized **512-D cosine** result. Geometry, PCA and internal activation plots are diagnostic evidence rather than alternate identity classifiers.

## 1. Five-point geometry microscope

The five orange alignment landmarks are explicitly:

1. `LE` — left eye center
2. `RE` — right eye center
3. `N` — nose base
4. `ML` — left mouth corner
5. `MR` — right mouth corner

They are fitted to the existing ArcFace-style 112×112 template with a similarity transform:

```text
p'i = s R pi + t
```

The microscope reports:

- landmark completeness `0..5 / 5`
- inter-eye distance
- estimated roll
- transform scale
- translation magnitude
- mean alignment residual
- maximum single-point residual
- explicit fallback-crop status

Alignment residual is:

```text
E_align = (1/5) Σ ||p'i - p_hat_i||2
```

This separates detector/alignment degradation from backbone-recognition degradation. If five usable landmarks are unavailable, the existing robust crop fallback remains active and the UI says so instead of inventing a five-point residual.

## 2. Enrollment vector and matrix microscope

Each accepted enrollment sample is embedded independently by XS, S and M. The three 512-D spaces are independent; raw embeddings from different backbones are never cross-cosined.

With `N` accepted samples, a focused model produces:

```text
fi ∈ R^512
Mij = cos(fi, fj)
M ∈ R^(N×N)
```

The current enrollment target is five samples, therefore the visible matrix is **5×5 because there are five samples**, not because the neural network is 5×5. If enrollment sample count changes, the matrix size changes with it.

For each model R4 reports:

- sample-to-centroid cosine
- stability and dispersion
- embedding/pose coverage
- minimum pair cosine
- mean pair cosine
- lowest-mean-cosine outlier candidate
- quality-weighted centroid
- 512D→2D PCA projection

Each model fits its own PCA basis. Rotation, reflection and absolute coordinates in XS/S/M 2-D plots are **not directly comparable across models**. Cluster shape within one model is diagnostic; final identity comparison remains 512-D cosine.

### Cross-model scalar comparison

R4 adds a side-by-side model table:

```text
variant | params | 18 blocks | stability | coverage | min pair | mean pair | outlier
```

It also visualizes scalar relationship deltas for the same enrollment sample indices:

```text
ΔM_XS,S = M_XS - M_S
ΔM_M,S  = M_M  - M_S
```

Those delta matrices are meaningful because each matrix cell is a scalar cosine relationship between the same pair of enrollment samples; raw XS/S/M embedding coordinates are still never mixed.

## 3. Real FaceLiVTv2 model-structure microscope

The current three backbones share the same depth and differ mainly in width:

| Variant | Parameters | Stage depths | Widths | Final feature | Identity embedding |
| --- | ---: | --- | --- | ---: | ---: |
| XS | ~2.90M | `[3,3,9,3]` | `[32,64,128,256]` | 1284D | 512D |
| S | ~4.62M | `[3,3,9,3]` | `[48,96,192,320]` | 1284D | 512D |
| M | ~7.0M | `[3,3,9,3]` | `[56,112,224,448]` | 1284D | 512D |

All have **18 backbone blocks = 3+3+9+3**:

```text
Stage 1: B01-B03  RepMix
Stage 2: B04-B06  RepMix
Stage 3: B07-B15  MHLA
Stage 4: B16-B18  MHLA
```

“18 blocks” is not presented as exactly “18 neural-network layers”: each block contains multiple operations/residual branches.

### Real intermediate statistics

The CI exporter wraps the pinned, reparameterized upstream model and exports real diagnostic tensors in the same ONNX graph:

```text
embedding      [1,512]
block_stats    [18,5]
stage_stats    [4,4]
prehead_stats  [4]
```

For each real block input `x` and output `y`, R4 records only compact statistics:

```text
mean_abs       = mean(|y|)
rms            = sqrt(mean(y^2))
std            = std(y)
near_zero      = mean(|y| < 1e-3)
relative_delta = ||y-x||2 / (||x||2 + eps)
```

The 18-block strip shows activation energy and representation change grouped by the four stages. Tapping a block expands its statistics. Stage summaries and the `1284D → 512D` pre-head path are also visible.

Raw full feature maps are not retained after diagnostic inference. Continuous recognition still uses the embedding-only output; deep diagnostics are throttled for the currently focused model.

## Model switching and Compare mode

`MicroscopeSelectionState` remains the single source of truth for S / XS / M selection. Switching model:

- changes every model-specific microscope panel
- clears old temporal fusion, Top-K, trends and Probe projection
- clears the old 18-block view while waiting for the new model
- refreshes that model's calibration
- rejects in-flight results from an earlier selection epoch

Image-quality and five-point geometry evidence are explicitly labelled model-independent. In Compare mode, the microscope retains the most recently explicit XS/S/M focus rather than silently pinning S.

## Existing calibrated-microscope behavior retained

R4 keeps the R3.1/R3.2 behavior:

- enrollment hard gates so good pose/face size cannot hide poor pixels
- stability and coverage as separate template qualities
- near-duplicate enrollment frames rejected unless both embedding and pose add novelty
- single-identity `margin=N/A`
- empirical local threshold / FAR / FRR / approximate EER when enough identities exist
- fixed-PCA live Probe trajectory
- current-frame versus 30-frame mean timing scopes
- Top-K bars and decision formula chain
- 5-frame temporal embedding fusion
- cheap-camera tiers from native/1080p down to 144p
- low-resolution detector assistance only upscales already-degraded pixels
- battery temperature and Android thermal status

## CSV evidence

R4 CSV retains quality, pose, decision and timing fields and adds compact geometry/deep-model evidence:

```text
landmark_count
fallback_crop
eye_distance_px
align_roll_deg
align_scale
align_translation_px
align_mean_residual_px
align_max_residual_px
stage1_rms ... stage4_rms
prehead_rms
```

The 18×5 raw block-stat table is deliberately not duplicated into every CSV row.

## Reproducible model build

GitHub Actions pins FaceLiVT upstream at:

```text
d99d86607c7c05540c74e815e5a88847f7e667db
```

It downloads `facelivtv2-xs.pt`, `facelivtv2-s.pt` and `facelivtv2-m.pt`, applies upstream deployment reparameterization, exports fixed `1×3×112×112` diagnostic ONNX graphs and requires:

- original PyTorch vs reparameterized embedding cosine >= `0.99999`
- deployed PyTorch vs diagnostic wrapper embedding cosine >= `0.99999`
- deployed PyTorch vs ONNX Runtime embedding cosine >= `0.99999`
- diagnostic shapes exactly `18×5`, `4×4`, `4`
- finite non-zero diagnostic statistics

Final CI runs the handset UI contract, R3 microscope contract, R3.1 calibration contract, R3.2 model-linkage contract, R4 deep-microscope contract, unit tests, XS/S/M diagnostic export/fidelity verification, Android APK build, three-model asset verification and arm64-v8a-only verification.

Upstream: `https://github.com/novendrastywn/FaceLiVT`

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

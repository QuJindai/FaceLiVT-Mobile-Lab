# FaceLiVT Mobile Lab R4

Android on-device face-recognition **unified microscope and calibration workbench** for validating FaceLiVTv2 lightweight models under intentionally degraded cheap-camera image quality.

The high-quality handset camera is only the source. Detection, alignment and recognition consume the deliberately degraded analysis frame.

## R4: geometry + embeddings + model internals

R4 merges three observability layers while retaining all R3.2 safeguards.

### 1. Model architecture microscope

R4 makes the three FaceLiVTv2 variants explicit:

| Variant | Approx params | Depths | Main blocks | Stage widths | Mixers | Pre-head | Identity embedding |
| --- | ---: | --- | ---: | --- | --- | ---: | ---: |
| XS | ~2.90M | `[3,3,9,3]` | 18 | `[32,64,128,256]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |
| S | ~4.62M | `[3,3,9,3]` | 18 | `[48,96,192,320]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |
| M | ~7.04M | `[3,3,9,3]` | 18 | `[56,112,224,448]` | RepMix, RepMix, MHLA, MHLA | 1284D | 512D |

XS/S/M therefore share the same depth and mainly scale model width.

The UI deliberately separates four easily-confused quantities:

```text
5 facial landmarks != 5 enrollment samples != 18 model blocks != 512 embedding dimensions
```

### One ONNX asset per model, optional microscope outputs

R4 does **not** ship a second diagnostic copy of each model. The same XS/S/M ONNX files expose:

```text
embedding    [1, 512]
block_stats  [18, 4]
stage_stats  [4, 4]
prehead      [1, 1284]
```

Normal inference requests only `embedding`. The current microscope-focus model requests the additional diagnostic outputs. In Compare mode the other backbones stay on the embedding-only path.

Each block/stage row contains:

```text
mean_abs
RMS
sparsity = mean(|x| < 0.05)
delta_ratio = mean(|x_out-x_in|) / (mean(|x_in|)+1e-6)
```

These values are engineering observability signals, not identity confidence scores. Full intermediate feature maps are not retained.

### 2. Enrollment embedding microscope

Enrollment still collects five quality-gated, non-duplicate samples. For a selected model:

```text
M_ij = cosine(f_i, f_j)
```

Five samples therefore produce a **5x5** relation matrix. The implementation remains dynamic NxN; this matrix size is unrelated to model depth or the five facial landmarks.

XS/S/M each have their own 512D feature space, template, matrix and PCA basis. Cross-model embedding cosine is never used.

R4 adds a same-sample-pair comparison panel with:

- mean pair cosine
- minimum pair cosine
- template stability `Sstable`
- coverage
- lowest-consistency/outlier sample
- signed `DeltaMatrix XS-S`
- signed `DeltaMatrix M-S`

The delta matrices are meaningful because every cell refers to the same enrollment sample pair. PCA absolute coordinates are **not** comparable across variants; only cluster shape, relative spread and outlier behavior should be compared.

### 3. Five-point geometry microscope

The five landmarks are:

```text
LE = left eye
RE = right eye
N  = nose base
ML = left mouth corner
MR = right mouth corner
```

They drive geometric normalization, not direct identity classification.

For detected points `p_i` and canonical ArcFace points `p_hat_i`, R4 fits:

```text
p'_i = s R p_i + t
```

and reports:

- landmark completeness `n/5`
- five-point similarity alignment vs fallback crop
- eye distance
- fitted scale
- fitted rotation
- mean canonical residual
- maximum canonical residual

The residual is:

```text
E_align = mean_i ||p'_i - p_hat_i||_2
```

This makes it possible to separate a detector/landmark/alignment problem from a backbone-recognition problem.

## Existing R3.2 capabilities retained

- separate Enrollment microscope and Recognition microscope pages
- model-linked XS/S/M selection state
- selection epoch rejects stale in-flight frames after a model/focus switch
- Compare mode retains the last explicit XS/S/M microscope focus
- virtual camera tiers: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p
- resolution + JPEG degradation before detection/recognition
- low-resolution detector assistance only upscales already-degraded pixels
- five-point alignment to 112x112
- enrollment hard gates, stability and coverage
- tracking-scoped five-frame temporal embedding fusion
- Top-K bars, quality/similarity trends and numeric formula chains
- single-identity margin shown as N/A instead of inventing Top2=0
- empirical handset threshold calibration using genuine/impostor reference scores
- fixed-PCA live Probe visualization; final decision remains 512D cosine
- explicit current-frame and 30-frame timing scopes
- microscope CSV and Android thermal/battery telemetry
- arm64-v8a APK target for S24U-class validation

## Reproducible model build

GitHub Actions pins FaceLiVT upstream at commit `d99d86607c7c05540c74e815e5a88847f7e667db` and downloads the public `facelivtv2-xs.pt`, `facelivtv2-s.pt` and `facelivtv2-m.pt` checkpoints.

For each model CI:

1. loads the official checkpoint strictly;
2. applies upstream deployment reparameterization;
3. checks original vs deployed embedding cosine >= `0.99999`;
4. wraps the same deployed network with compact diagnostic outputs;
5. verifies the wrapper does not change the 512D embedding;
6. exports ONNX opset 18;
7. checks output names and shapes;
8. requires deployed PyTorch vs ONNX Runtime embedding cosine >= `0.99999`.

Final CI also runs the handset UI contract, R3 microscope contract, R3.1 calibration contract, R3.2 model-linkage contract, R4 unified-microscope contract, all JUnit tests, Android APK build, three-model asset verification and arm64-only ABI verification.

Upstream: https://github.com/novendrastywn/FaceLiVT

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

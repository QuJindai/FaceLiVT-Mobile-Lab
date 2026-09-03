# FaceLiVT Mobile Lab R3.1

Android on-device face-recognition **microscope and calibration workbench** for validating FaceLiVTv2 lightweight face models under intentionally degraded cheap-camera image quality.

The phone camera remains high quality only for preview. Detection, alignment and recognition consume the degraded analysis frame.

## R3.1: from microscope to calibrated microscope

R3.1 keeps the separate `录入显微镜` and `检测显微镜` workflows, and corrects the issues exposed by S24U handset testing.

### Enrollment: quality cannot be averaged away

A sample first passes explicit hard gates before it may participate in template construction. Enrollment is stricter than recognition so excellent pose/landmarks/face size cannot hide very poor pixels.

Enrollment currently requires:

```text
Q >= .55
Qsharp >= .28
Qlight >= .28
Qcontrast >= .25
Qpose >= .55
Qlandmark >= .80
Qsize >= .45
```

The composite score remains visible:

```text
Qi = .25 Qsharp + .15 Qlight + .10 Qcontrast
   + .20 Qpose + .15 Qlandmark + .15 Qsize
```

### Enrollment: stability and coverage are separate

Five identical-looking consecutive frames are no longer rewarded as a strong template. After the first accepted sample, another frame counts only when both its embedding and head pose add useful novelty.

The dossier reports:

- `Sstable`: mean cosine from samples to the quality-weighted centroid
- `D = 1 - Sstable`: centroid dispersion
- `Cemb`: identity-space spread
- `Cpose`: yaw/pitch pose spread
- `Coverage = sqrt(Cemb * Cpose)`

Template commit requires:

```text
N >= 5
Qavg >= .55
Sstable >= .70
Coverage >= .35
all per-frame hard gates PASS
```

XS, S and M still use independent 512-D spaces and all three must pass before the new template replaces the old one.

### Recognition: single-identity margin is N/A

R3.1 no longer invents `Top2=0` when only one identity exists. With one candidate the UI explicitly shows:

```text
Top2 = none
margin = N/A
```

Recognition can still perform the identity/quality decision, but the microscope states that one identity cannot evaluate 1:N discrimination.

### Empirical threshold calibration

For identities re-enrolled by R3.1, the app stores local reference embeddings and genuine sample-to-centroid scores. With at least two such identities it builds a small empirical impostor distribution from cross-identity template cosine scores and reports:

- suggested identity threshold
- empirical FAR
- empirical FRR
- approximate EER
- genuine/impostor sample counts
- distribution separation gap

The suggested threshold can be applied from the handset. This is an engineering diagnostic for the current device/dataset, **not a production biometric certification**.

### Fixed-PCA live Probe microscope

The recognition page can project the current fused Probe embedding into the same fixed 2D PCA coordinate system as the enrolled samples and centroid. It shows the live Probe point, a short Probe trajectory, and PC1/PC2 explained variance.

The UI explicitly states that 2D projection is visualization only; the final identity decision remains the original **512-D cosine**.

### Performance scopes are explicit

Timing is no longer mixed. The page separately shows:

```text
current frame: detect / align / infer / match / total
30-frame mean: detect / align / infer / match / total
```

## Existing microscope capabilities

- FaceLiVTv2 **XS / S / M** official checkpoints converted to ONNX during CI
- independent per-backbone identity templates
- run modes: S, XS, M, or XS/S/M compare
- virtual camera tiers: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p
- resolution + JPEG degradation before detection/recognition
- low-resolution detector assistance only upscales already-degraded pixels
- five-point eye/nose/mouth alignment to 112x112
- tracking-scoped five-frame temporal embedding fusion
- sample similarity matrix and enrollment 512D→2D projection
- Top-K bars, quality/similarity temporal trend, numeric formula chains
- microscope CSV with quality, pose, decision margin and per-stage timing
- battery temperature / Android thermal status
- arm64-v8a APK target for S24U-class handset validation

## Reproducible model build

GitHub Actions pins FaceLiVT upstream at commit `d99d86607c7c05540c74e815e5a88847f7e667db`. It downloads the public `facelivtv2-xs.pt`, `facelivtv2-s.pt` and `facelivtv2-m.pt` checkpoints, applies the upstream deployment reparameterization, exports fixed-shape `1x3x112x112` ONNX files, and requires both:

- original PyTorch vs reparameterized graph cosine >= `0.99999`
- reparameterized PyTorch vs ONNX Runtime cosine >= `0.99999`

Final CI runs the R2.1 handset UI contract, R3 microscope contract, R3.1 calibration contract, all unit tests, Android APK build, three-model asset verification and arm64-only ABI verification.

Upstream: https://github.com/novendrastywn/FaceLiVT

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

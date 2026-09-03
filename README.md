# FaceLiVT Mobile Lab R3

Android on-device face-recognition **microscope workbench** for testing modern lightweight face models under cheap-camera image quality. The phone camera remains high quality for preview, but all detection, alignment and recognition consume an intentionally degraded analysis frame.

R3 separates the product into two observable workflows instead of mixing enrollment and recognition on one screen.

## 01 · Enrollment Microscope / 录入显微镜

Enrollment first builds a quality dossier, then decides whether a new template is good enough to replace the existing one.

Each five-frame session records:

- source/degraded camera tier and effective face pixels
- aligned 112x112 face samples S1-S5
- sharpness, brightness, contrast, pose, five-landmark visibility and face-size quality
- yaw / pitch / roll
- composite sample quality `Q`
- FaceLiVTv2 XS / S / M embeddings in separate 512-D spaces
- quality-weighted centroid for each model
- every sample's cosine to its centroid
- template stability and dispersion
- pairwise sample similarity matrix
- 512-D → 2-D embedding projection with the template centroid shown separately
- a human-readable enrollment conclusion and persisted quality dossier

Enrollment formula chain shown in the UI with live numeric substitution:

```text
Qi = .25 Qsharp + .15 Qlight + .10 Qcontrast
   + .20 Qpose + .15 Qlandmark + .15 Qsize
alpha_i = max(Qi, .05)
c = normalize(sum(alpha_i * f_i) / sum(alpha_i))
Sstable = mean(cos(fi, c))
D = mean(1 - cos(fi, c))
Pass = N >= 5 AND Qavg >= .55 AND Sstable >= .70
```

Only when XS, S and M all pass the enrollment gate are their quality-weighted centroids committed. A failed enrollment keeps the quality dossier visible but does not overwrite an existing good template.

## 02 · Recognition Microscope / 检测显微镜

Recognition exposes the live decision chain rather than only showing a name and score:

```text
frame
→ camera degradation
→ face detection
→ five-point alignment
→ probe quality gate
→ FaceLiVTv2 embedding
→ tracking-scoped 5-frame fusion
→ Top-K cosine matching
→ identity + quality decision
```

The page shows:

- the detector's actual degraded input frame
- detected face box and five landmarks
- aligned 112x112 probe face
- live quality dimensions and yaw/pitch/roll
- detect / align / infer / match stage timings
- Top-3 identities and similarities with the identity-threshold line
- `Top1 - Top2` decision margin
- rolling similarity and quality trend
- current fused-frame count
- final identity-gate and quality-gate pass/fail reasons

Recognition formula chain shown with current values:

```text
sk = cos(f_probe, c_k)
k* = argmax(sk)
margin = s_top1 - s_top2
Accept = (s_top1 >= T_id) AND (Q_probe >= .35)
```

The microscope CSV exports the same decision evidence: image quality, pose, Top1/Top2, margin, threshold, quality gate, fusion depth, detect/align/infer/match/total timing and thermal data.

## Cheap-camera validation path

- FaceLiVTv2 **XS / S / M** official checkpoints, each converted to ONNX during CI
- independent template space for each backbone; embeddings are never compared across models
- run modes: S, XS, M, or **XS / S / M Compare**
- virtual camera tiers: native, 1080p, 720p, 480p, 360p, 240p, 180p, 144p
- resolution reduction + JPEG degradation occurs before detection and recognition
- below 360p, detector assistance only upscales the **already degraded pixels**; it never reads the original high-resolution frame
- five-point eye/nose/mouth alignment to 112x112
- per-tracking-ID temporal fusion of up to five embeddings
- battery temperature and Android thermal status
- arm64-v8a target for the S24U handset validation path

## Reproducible model build

GitHub Actions pins upstream FaceLiVT at commit `d99d86607c7c05540c74e815e5a88847f7e667db`. It downloads the author's public `facelivtv2-xs.pt`, `facelivtv2-s.pt` and `facelivtv2-m.pt` checkpoints, applies upstream deployment reparameterization, exports fixed-shape `1x3x112x112` ONNX files, and requires:

- original PyTorch vs reparameterized graph cosine >= `0.99999`
- reparameterized PyTorch vs ONNX Runtime cosine >= `0.99999`

CI also runs the R2.1 handset safe-area contract, the R3 two-page microscope contract, unit tests, Android APK build, three-model asset verification and arm64-only native-library verification.

Upstream: https://github.com/novendrastywn/FaceLiVT

## Consolidated handset use

1. Open `录入显微镜`, choose a camera-quality tier, enter an ID and capture one five-frame quality dossier.
2. Inspect sample quality, matrix, embedding projection and formula result. A passing session writes XS/S/M templates automatically.
3. Open `检测显微镜`, choose S / XS / M / compare mode and use the camera normally; the page continuously exposes the whole recognition chain.
4. At the end, export one microscope CSV if deeper analysis is needed.

No feature-by-feature handset testing is required.

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

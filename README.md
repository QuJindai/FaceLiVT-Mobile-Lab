# FaceLiVT Mobile Lab R5

Android on-device face-recognition **identity-guard, learning-history, deep-microscope and calibration workbench** for validating FaceLiVTv2 lightweight face models under intentionally degraded cheap-camera image quality.

R5 keeps the R4 geometry / vector / 18-block microscope and adds a hard enrollment rule: **an existing face must not be silently enrolled as a new identity**. Enrollment now begins with a three-model Identity Guard, and every successful R5 five-frame learning session becomes a replayable immutable `V1`, `V2`, ... history version.

The S24U preview remains high quality, but detection, alignment and recognition consume the intentionally degraded analysis frame. Final identity decisions still use model-local normalized **512-D cosine**; diagnostic plots do not replace the biometric decision chain.

## R5 enrollment lifecycle

```text
camera frame
→ cheap-camera degradation
→ face detect
→ five-point alignment / probe quality
→ XS / S / M model-local identity search
→ Identity Guard
   ├─ CLEAR      → new identity enrollment allowed
   ├─ SUSPECTED  → new identity locked; continue confirmation / choose existing candidate
   └─ EXISTING   → new identity locked; replay / append / delete+re-enroll
```

There is deliberately **no force-create-new path** from `SUSPECTED` or `EXISTING`.

### Guard states

`CLEAR`

- empty identity library: first valid aligned Probe can clear the guard;
- non-empty identity library: requires five consecutive valid frames below the suspect threshold;
- only this state enables `开始质量录入 ×5` for a new identity.

`SUSPECTED`

- any model-local candidate at or above its suspect threshold blocks creation;
- UI shows per-model Top-1 evidence, real margin when available, temporal clear/confirm counts, and candidate identity buttons;
- the user may continue confirmation or explicitly enter an existing candidate; there is no duplicate bypass.

`EXISTING`

Automatic promotion requires:

- complete usable five-point geometry;
- at least three consecutive confirming frames;
- for libraries with 2+ identities, at least 2 of XS/S/M strongly vote for the same identity on each confirming frame;
- for a one-identity library, all three models must strongly confirm the identity.

Once existing identity context is entered, the enrollment name is read-only and the available lifecycle actions are:

- `保留现有`
- `追加学习 ×5`
- `删除并重新录入`

### Guard thresholds

R5 uses conservative engineering defaults derived from the current identity threshold `Tid`:

```text
Tsuspect  = clamp(max(Tid + 0.05, 0.55), 0, 0.99)
Texisting = clamp(max(Tid + 0.10, 0.62), 0, 0.99)
```

If a model has an empirical local calibration threshold, it can only make the Guard stricter.

With at least two identities, a model strong vote additionally requires a real Top-1/Top-2 margin:

```text
margin >= 0.08
```

With one identity, Top-2 does not exist, so margin remains `N/A`; R5 does not invent a synthetic second candidate.

XS/S/M embeddings are **never cross-cosined**. Each backbone searches only its own template space, and the Guard aggregates identity names plus scalar model-local evidence.

## R5 immutable five-frame learning history

Every successful R5 enrollment or append session stores exactly five accepted aligned `112×112` face thumbnails plus compact learning evidence as an immutable version:

```text
identity
├─ V1
│  ├─ record.txt
│  ├─ s1.webp
│  ├─ s2.webp
│  ├─ s3.webp
│  ├─ s4.webp
│  └─ s5.webp
├─ V2
└─ ...
```

Storage is under the app-private files directory. Raw camera frames are not retained, and no external-storage permission is required. Identity names are not used directly as filesystem directory names; the history store uses a SHA-256-derived safe directory key.

A stored version contains enough evidence to reconstruct the R4 enrollment microscope:

- five per-frame quality snapshots;
- five alignment-geometry records;
- five XS embeddings, five S embeddings and five M embeddings;
- per-model centroid and sample-to-centroid cosine;
- stability / dispersion;
- embedding and pose coverage;
- pairwise cosine summary and outlier candidate;
- profile, timestamp, version and effective-sample counts.

Selecting an older `Vn` replays that immutable evidence and five thumbnails. **History playback never changes the currently active recognition template.**

### Legacy R4 identities

Existing R4 identities remain recognizable after installing R5. R4 did not persist five aligned historical thumbnails, so R5 states this explicitly instead of fabricating evidence:

```text
旧版本没有保存五帧图像；追加学习或删除重录后可建立完整学习档案。
```

An append or delete/re-enroll creates the first complete R5 history version for that identity.

## Append Learning ×5

Append Learning captures a new five-frame session under the same hard quality and novelty gates. The new session must pass independently before it can affect the active template.

R5 first preflights the complete XS/S/M update in memory. For each model:

```text
w_old = min(existingEffectiveSamples, 15)
w_new = 5
c_active_new = normalize(w_old * c_old + 5 * c_newSession)
effectiveSamplesAfter = min(existingEffectiveSamples + 5, 20)
```

All three model fusions and drift checks must succeed **before** immutable `Vn` history is published. This prevents a computational failure in one model from leaving a new history version that claims success while only part of the active template set was updated.

After successful preflight, R5 publishes the new immutable version, updates XS/S/M active templates, refreshes reference/calibration evidence and reports:

```text
Vn → Vn+1
w_old / w_new
effective sample count
cos(c_old, c_active_new)
```

Old history versions are never rewritten.

## Delete & Re-enroll

`删除并重新录入` requires an explicit destructive confirmation. The identity lifecycle coordinator removes:

- active XS template + effective count;
- active S template + effective count;
- active M template + effective count;
- legacy S vector/count keys;
- identity membership;
- R3/R4 human-readable archive;
- R3.1 reference embeddings for XS/S/M;
- every R5 history metadata record;
- every stored R5 aligned thumbnail.

Only after deletion does R5 start a fresh five-frame `REPLACE_AFTER_DELETE` capture under the same identity text.

## Existing R4 three-layer microscope retained

R5 retains the R4 evidence chain:

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

### 1. Five-point geometry microscope

Alignment landmarks are:

1. `LE` — left eye center
2. `RE` — right eye center
3. `N` — nose base
4. `ML` — left mouth corner
5. `MR` — right mouth corner

They are fitted to the ArcFace-style 112×112 template with:

```text
p'i = s R pi + t
```

The microscope reports landmark completeness, inter-eye distance, roll, transform scale, translation magnitude, mean/max residual and explicit fallback-crop status.

Automatic Guard `EXISTING` promotion requires real five-point geometry; fallback crop may raise a warning but cannot by itself auto-promote an existing identity.

### 2. Enrollment vector/matrix microscope

For one selected backbone and `N` accepted samples:

```text
fi ∈ R^512
Mij = cos(fi, fj)
M ∈ R^(N×N)
```

The normal enrollment target is five accepted samples, therefore the visible matrix is 5×5 because there are five samples, not because the neural network is 5×5.

For each model R5 retains:

- sample-to-centroid cosine;
- stability and dispersion;
- embedding/pose coverage;
- minimum/mean pair cosine;
- outlier candidate;
- quality-weighted centroid;
- model-local 512D→2D PCA projection.

Each backbone fits its own PCA basis. XS/S/M 2-D coordinates are not directly comparable; final identity comparison remains model-local 512-D cosine.

### 3. Real FaceLiVTv2 model-structure microscope

The current backbones share depth and differ mainly in width:

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

The pinned CI exporter records compact real intermediate statistics from the same diagnostic ONNX graph:

```text
embedding      [1,512]
block_stats    [18,5]
stage_stats    [4,4]
prehead_stats  [4]
```

Per block it reports mean absolute activation, RMS, standard deviation, near-zero ratio and relative representation change. Full feature maps are not retained after diagnostic inference.

## Model switching and stale-result protection

`MicroscopeSelectionState` remains the model-selection source of truth. Switching model clears old temporal fusion, Top-K, trends, Probe projection and model microscope state while rejecting in-flight results from an earlier selection epoch.

R5 adds an independent Guard generation. Page changes, profile changes, camera changes, tracking changes and no-face gaps invalidate temporal Guard evidence so stale identity-confirmation results cannot unlock or overwrite the current enrollment context.

If ML Kit temporarily provides no tracking ID, R5 uses one stable unknown-tracking sentinel for contiguous visible frames instead of inventing a different synthetic tracking ID every frame; a no-face gap clears that evidence.

## Existing calibrated-microscope behavior retained

R5 keeps the R3.1/R3.2/R4 behavior:

- enrollment hard gates so good pose/face size cannot hide poor pixels;
- stability and coverage as separate template qualities;
- near-duplicate enrollment rejection requiring embedding and pose novelty;
- single-identity `margin=N/A`;
- empirical local threshold / FAR / FRR / approximate EER when enough identities exist;
- fixed-PCA live Probe trajectory;
- current-frame versus 30-frame mean timing scopes;
- Top-K bars and decision formula chain;
- five-frame temporal recognition fusion;
- cheap-camera tiers from native/1080p down to 144p;
- low-resolution detector assistance only upscales already-degraded pixels;
- battery temperature and Android thermal status.

Empirical FAR/FRR/EER values are small-sample **engineering diagnostics**, not biometric certification claims.

## CSV evidence

CSV retains quality, pose, decision, timing, geometry and deep-model evidence including:

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

- original PyTorch vs reparameterized embedding cosine >= `0.99999`;
- deployed PyTorch vs diagnostic wrapper embedding cosine >= `0.99999`;
- deployed PyTorch vs ONNX Runtime embedding cosine >= `0.99999`;
- diagnostic shapes exactly `18×5`, `4×4`, `4`;
- finite non-zero diagnostic statistics.

Final CI runs:

- handset UI contract;
- R3 microscope contract;
- R3.1 calibration contract;
- R3.2 model-linkage contract;
- R4 deep-microscope contract;
- R5 Identity Guard / history / atomic-append contract;
- Android JVM unit tests;
- XS/S/M diagnostic export and fidelity verification;
- Android APK build;
- three-model asset verification;
- arm64-v8a-only verification;
- R5 artifact upload.

Release target:

```text
versionCode 7
versionName 0.5.0
FaceLiVT-Mobile-Lab-R5-debug.apk
```

Upstream: `https://github.com/novendrastywn/FaceLiVT`

## Licensing note

FaceLiVT upstream source code is published under BSD-3-Clause. The author's Hugging Face model repository states CC BY-SA 4.0 for the model repository/weights. This project is an engineering validation prototype and preserves attribution in `app/src/main/assets/MODEL_LICENSE_NOTICE.txt`. Commercial deployment requires a separate review of model-weight and training-data rights.

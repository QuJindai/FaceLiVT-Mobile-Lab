# R5 Identity Guard + History Replay Design

Date: 2026-09-03
Branch: `feat/r5-identity-guard-history`
Target: `0.5.0` / versionCode `7`

## 1. Goal

R5 prevents the same person from being enrolled repeatedly under multiple identities and turns duplicate detection into a microscope workflow.

On the Enrollment Microscope page, the app performs identity lookup before allowing a new enrollment. A confident match to an existing identity must restore that identity's historical five-frame learning evidence and hard-block "save as new identity". An ambiguous match must also block new enrollment until repeated frames clear the ambiguity or the user explicitly selects an existing candidate.

All processing and history remain on-device. R5 must preserve R4 XS/S/M model separation, model-local 512-D spaces, R3.2 stale-frame rejection, and R4 geometry/model microscopes.

## 2. Identity Guard states

### CLEAR
The face is sufficiently cleared against the existing library.

- New enrollment is allowed only in CLEAR.
- If the library is empty, Guard becomes CLEAR immediately once a valid aligned probe is available; there is no five-frame waiting penalty for an empty library.
- With a non-empty library, CLEAR requires five consecutive valid guard frames with no model exceeding the suspected threshold for any identity.

### SUSPECTED
Evidence suggests an existing identity but is not strong enough for an automatic duplicate decision.

- New enrollment is disabled.
- Allowed actions: continue confirmation, or manually select one of the displayed existing candidates.
- There is no force-create-new path.
- The card shows XS/S/M Top-1, score, margin/N/A, temporal votes, quality, and the reason for ambiguity.

### EXISTING
The probe is confidently the same person as an existing identity.

- New enrollment is hard-disabled.
- The matched identity becomes the enrollment microscope context.
- Historical learning evidence is restored immediately.
- Allowed actions: Keep Existing, Append Learning, Delete & Re-enroll.
- There is no save-as-new identity path.

## 3. Guard evidence

Guard evaluates only valid enrollment-page probes:
- `FaceQuality.passesProbeGate()` passes;
- face is aligned to 112x112;
- valid 5-point geometry is required for an automatic EXISTING verdict; fallback crop may contribute only to SUSPECTED/read-only evidence;
- current guard generation and model-selection epoch must still match when the async result reaches UI.

Every valid guard frame runs XS, S, and M independently. Each model searches only its own 512-D template space through `FaceStore.topMatches(...)`. Embeddings are never compared across models.

### 3.1 Threshold policy

For each model:
- `Tid` = current recognition threshold;
- `Tsuspect = clamp(max(Tid + 0.05, 0.55), 0, 0.99)`;
- `Texisting = clamp(max(Tid + 0.10, 0.62), 0, 0.99)`.

If a model has an empirical suggested threshold, use the stricter value after applying the same offset and clamp. These remain engineering defaults, not certified FAR/EER claims. Threshold logic lives in one policy class and is unit-tested.

If at least two identities are present for a model, a strong EXISTING vote requires Top-1 >= `Texisting` and margin >= 0.08. With a one-identity library, margin remains `N/A`; stronger temporal/model agreement replaces margin rather than inventing one.

### 3.2 Temporal consensus

Guard keeps the latest five valid frames.

EXISTING requires:
- same candidate identity across at least 3 consecutive valid frames;
- at least 2 of 3 models provide strong votes for that identity in each confirming frame;
- no competing identity receives equally strong evidence in the window.

SUSPECTED is entered if any model exceeds `Tsuspect`, if two models agree on a candidate without enough strong evidence, if temporal candidates conflict, or if quality/geometry is sufficient to warn but not to declare EXISTING.

CLEAR with a non-empty library requires five consecutive valid frames with all model scores below `Tsuspect` for all existing identities.

Guard evidence resets when tracked face, camera, degradation profile, guard context, enrollment state, delete/append action, or active template generation changes.

## 4. Versioned learning archive

R4 already persists active centroids, text archives, and model-specific reference embeddings, but not the five aligned images. R5 adds `EnrollmentHistoryStore`.

Every successful five-frame enrollment session becomes an immutable version:

`Identity -> V1, V2, V3, ...`

Each version stores:
- version number and timestamp;
- degradation profile;
- five 112x112 aligned face thumbnails;
- per-frame FaceQuality fields;
- per-frame AlignmentGeometry fields;
- XS/S/M five-sample 512-D embeddings;
- per-model centroid and sample-to-centroid cosine values;
- R4 summary fields: Qavg, stability, dispersion, embedding coverage, pose coverage, combined coverage, min pair, mean pair, outlier index;
- effective active-template sample count before and after the version, so append fusion has a deterministic persisted weight source.

N×N cosine matrices and model-local PCA are recomputed from stored embeddings rather than duplicated as image assets.

### 4.1 Image persistence

Only the aligned 112x112 thumbnails are persisted, under app-private internal storage:

`files/enrollment_history/<safeIdentity>/v<version>/s1.webp ... s5.webp`

R5 does not persist raw full-resolution camera frames. No external-storage permission is added.

### 4.2 Store API

`EnrollmentHistoryStore` supports:
- saveVersion;
- latest;
- versions;
- loadVersion;
- loadFiveFrames;
- deleteIdentity;
- internal/test-only deleteVersion;
- legacy fallback when an R3/R4 identity has templates but no R5 image history.

Legacy identities remain recognizable. If no historical images exist, UI explicitly says: `旧版本没有保存五帧图像；追加学习或删除重录后可建立完整学习档案。` No fake historical frames are generated.

## 5. History replay

When Guard reaches EXISTING, the enrollment page changes into historical microscope mode and restores the latest version by default.

It displays:
- identity name, version, timestamp;
- historical S1-S5 aligned images;
- per-frame quality and geometry;
- selected XS/S/M N×N cosine matrix;
- selected model PCA;
- centroid/sample cosine chain;
- stability, coverage, min/mean pair, outlier;
- XS/S/M comparison and delta matrices;
- whether the viewed version is the latest active learning version.

A compact V1/V2/... selector can view older immutable versions without changing the active template.

## 6. Existing-identity actions

### Keep Existing
Read-only. No template or history mutation.

### Append Learning
Allowed only after automatic EXISTING or explicit manual selection of a SUSPECTED candidate.

Flow:
1. freeze current model-local active centroids as `cold`;
2. collect a fresh five-frame R4-quality/novelty-qualified session;
3. if the session passes, save it as the next immutable version;
4. compute each model's new-session centroid `cnewSession`;
5. update active centroid conservatively:
   `cactive_new = normalize(w_old*cold + w_new*cnewSession)`;
6. use persisted effective sample count as weight source:
   `w_old = min(existingEffectiveSamples, 15)`, `w_new = 5`;
7. persist `effectiveSamplesAfter = min(existingEffectiveSamples + 5, 20)` for future append weighting.

The microscope shows Vn -> Vn+1, `cos(cold,cactive_new)` drift, probe cosine to old/new centroids, and old/new model statistics.

A failed new five-frame session does not create a persistent version and does not update the active template.

### Delete & Re-enroll
Destructive and requires explicit confirmation.

On confirmation:
- delete all XS/S/M active templates for the identity;
- delete R3/R4 reference records and text archive;
- delete all R5 versions, metadata, and stored thumbnails;
- remove the identity name from FaceStore when no template remains;
- reset Guard;
- prefill the same identity name and immediately enter a fresh five-frame enrollment flow.

No hidden backup is retained by this action.

## 7. SUSPECTED manual resolution

Show up to three candidate identities.

Allowed actions:
- Continue Confirmation;
- `这是已有身份 -> <candidate>`.

Manual selection only opens that existing identity's historical context. It does not merge identities or modify templates by itself. New identity creation remains blocked until Guard later reaches CLEAR.

## 8. Enrollment UI

Add an Identity Guard card above the enrollment controls showing:
- CLEAR / SUSPECTED / EXISTING;
- candidate identity;
- XS/S/M scores and strong/weak vote state;
- temporal consensus count;
- margin or N/A;
- exact block/clear reason.

EXISTING:
- synchronize name field to matched identity and make it read-only for this context;
- disable normal `开始质量录入 x5`;
- show `保留现有`, `追加学习 x5`, `删除并重新录入`;
- reuse the existing S1-S5 strip for historical frames and label it with history version.

SUSPECTED:
- disable normal enrollment;
- show candidate resolution controls.

CLEAR:
- hide history actions;
- enable new enrollment once name is valid.

## 9. Identity lifecycle support

`FaceStore` gains explicit template deletion and effective-sample-count access/update. It removes an identity name only after all model templates are gone.

`EnrollmentArchiveStore` gains delete-text, delete-reference, and delete-all-legacy-data methods.

Partial deletion is treated as a test failure because stale template/reference data would allow future duplicate identities.

## 10. Sources of truth

- `FaceStore`: current active recognition templates and effective active sample counts.
- `EnrollmentHistoryStore`: immutable learning versions and five-frame replay evidence.
- `EnrollmentArchiveStore`: legacy text/reference compatibility until migration is complete.

History versions never mutate after successful commit. Active templates may evolve through Append Learning.

## 11. Concurrency

Identity Guard has its own generation token in addition to the R3.2/R4 model-selection epoch.

Async guard results are discarded if guard generation, tracked face, page, camera/profile, action context, or active template generation changed before UI commit. Old frames must never reopen a deleted identity or overwrite a newly started enrollment state.

## 12. Privacy

- all data remains on-device;
- only 112x112 aligned enrollment thumbnails are persisted;
- no raw full-resolution frame is persisted;
- history files are app-private;
- delete removes thumbnails and metadata;
- CSV contains no face image bytes.

## 13. Migration

Existing R4 installs upgrade in place.

- current templates remain usable;
- R3.1 reference embeddings/calibration remain usable;
- text archives remain visible;
- legacy identities without images show the explicit legacy-history notice;
- the first successful Append Learning creates V1 R5 history while preserving the old active centroid as pre-append comparison evidence;
- Delete & Re-enroll removes both legacy and R5 data.

## 14. Tests and CI

Unit tests cover:
- CLEAR/SUSPECTED/EXISTING transitions;
- empty-library immediate CLEAR;
- three-model agreement;
- one-identity margin N/A;
- temporal consensus and resets;
- high-confidence and suspected states blocking new enrollment;
- history codec round-trip;
- five thumbnail save/load/delete;
- immutable versions;
- legacy fallback;
- effective sample count persistence;
- bounded append fusion;
- complete identity deletion across FaceStore, EnrollmentArchiveStore, and EnrollmentHistoryStore;
- stale guard generation rejection.

Add `tools/verify_r5_identity_guard.py` to enforce source wiring, no force-new path, version `0.5.0`/code 7, history persistence, three existing-identity actions, complete deletion support, guard stale-result rejection, and legacy fallback message.

Existing R3/R3.1/R3.2/R4 contracts remain green and are made version-forward-compatible only where older version literals are hard-coded.

Full CI still runs Android tests, XS/S/M diagnostic ONNX export/fidelity, APK assembly, three-model asset checks, and arm64-v8a-only verification.

## 15. Acceptance criteria

R5 is complete only when `main` satisfies all of the following:

1. Existing faces cannot be enrolled as new identities.
2. SUSPECTED faces cannot be force-created as new identities.
3. Empty libraries do not impose an unnecessary five-frame guard delay.
4. EXISTING restores historical learning evidence automatically.
5. New R5 enrollments persist and reload all five aligned frames after restart.
6. Matrix/PCA/quality/geometry can be reconstructed from persisted evidence.
7. Append Learning creates a new immutable version and conservatively updates the active template using deterministic persisted weights.
8. Delete & Re-enroll removes templates, references, archive, thumbnails, history metadata, and identity membership before fresh enrollment.
9. Legacy R4 identities still recognize and clearly report missing historical images.
10. R3.2/R4 model linkage and stale-frame protection remain intact.
11. Final APK comes from a green `main` CI run.

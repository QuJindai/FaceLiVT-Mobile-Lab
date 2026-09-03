# R5 Identity Guard + History Replay Design

Date: 2026-09-03
Branch: `feat/r5-identity-guard-history`
Target version: `0.5.0` / versionCode `7`

## 1. Goal

R5 prevents the same person from being enrolled repeatedly under multiple identities and turns duplicate detection into a microscope workflow rather than a warning dialog.

When a face appears on the Enrollment Microscope page, the app performs identity lookup before allowing a new enrollment. If the probe is confidently the same person as an existing identity, the app must restore that identity's previous five enrollment frames and historical learning evidence and block "save as new identity". If the result is ambiguous, the app blocks new enrollment until repeated frames resolve the ambiguity or the user explicitly selects an existing identity.

The workflow must remain fully on-device and compatible with the existing R4 XS/S/M model separation, 512-D template spaces, R3.2 stale-frame epoch protection, and R4 geometry/model microscopes.

## 2. User-visible states

The enrollment page has an Identity Guard state machine with three externally visible states.

### 2.1 CLEAR

Meaning: repeated valid probe frames provide enough evidence that the face is not already represented by the current identity library.

Behavior:
- "New identity" enrollment is enabled only after CLEAR is reached.
- Existing name entry remains available.
- The page shows the evidence that produced CLEAR, not just a green label.

### 2.2 SUSPECTED

Meaning: one or more models are similar to an existing identity, or models/frames disagree, but evidence is insufficient for a confident duplicate decision.

Behavior:
- New identity enrollment is disabled.
- Allowed actions are:
  1. continue sampling to resolve the ambiguity;
  2. manually select one of the existing candidate identities.
- The UI shows per-model Top-1, score, margin availability, temporal vote counts, probe quality, and reason for ambiguity.
- There is no "force create new" action in SUSPECTED.

### 2.3 EXISTING

Meaning: the probe is confidently the same person as an existing identity.

Behavior:
- New identity enrollment is hard-disabled.
- The detected existing identity becomes the enrollment microscope context.
- Historical five-frame learning records are restored immediately.
- Allowed actions are:
  1. keep existing record;
  2. append learning;
  3. delete and re-enroll.
- There is no "save as another/new identity" path.

## 3. Guard evidence and temporal decision

Identity Guard is evaluated only on probe frames that are suitable for identity lookup:
- `FaceQuality.passesProbeGate()` must pass;
- a face must be aligned to 112x112;
- valid five-point geometry is preferred; fallback alignment may be shown but does not contribute to a high-confidence EXISTING decision;
- model-selection epoch must still be current when the result reaches the UI.

For each valid guard frame, XS, S, and M are all evaluated independently in their own 512-D spaces. Each model obtains Top-K from `FaceStore`.

The guard never compares embeddings across models. It compares only model-local identity results and then aggregates identity names and confidence evidence.

### 3.1 Per-model score thresholds

R5 distinguishes a suspected threshold from an existing threshold. Defaults are deliberately stricter than ordinary recognition because a false duplicate merge is more damaging than a temporary block.

For each model:
- `Tid` is the current recognition identity threshold.
- `Tsuspect = max(Tid + 0.05, 0.55)`.
- `Texisting = max(Tid + 0.10, 0.62)`.

If an empirical threshold is available for a model, the guard uses the stricter of the configured rule above and the calibrated recommendation plus the same safety offset.

These are engineering defaults, not certified biometric FAR thresholds. They must be centralized in one guard policy class and be unit-tested.

### 3.2 Margin rule

If there are at least two identities in a model's candidate set, a model contributes a strong EXISTING vote only when:
- Top-1 score >= `Texisting`; and
- Top-1 minus Top-2 >= 0.08.

With only one identity in the library, margin is unavailable and must remain `N/A`; in that case the app requires stronger temporal/model agreement rather than inventing a synthetic margin.

### 3.3 Temporal consensus

The guard maintains a rolling window of the latest five valid enrollment-page probe frames.

EXISTING requires:
- the same identity is the candidate in at least 3 consecutive valid frames; and
- at least 2 of 3 models provide strong votes for that identity in each confirming frame; and
- no competing identity receives an equally strong vote in the same window.

SUSPECTED is entered when any of the following is true:
- at least one model exceeds `Tsuspect` for an existing identity;
- at least two models name the same candidate but strong-vote conditions are incomplete;
- temporal frames disagree between likely existing candidates;
- quality/geometry is sufficient to warn but insufficient to declare EXISTING.

CLEAR requires:
- five consecutive valid frames; and
- no model exceeds `Tsuspect` for any existing identity in those frames.

The guard resets its temporal evidence when:
- the tracked face changes;
- camera changes;
- degradation profile changes;
- model assets are reinitialized;
- the user selects a different manual existing identity;
- enrollment starts, completes, is cancelled, or history action changes the active template.

## 4. Versioned identity learning archive

R4 currently persists template centroids, text archive, and model-specific reference embeddings, but not the five aligned face images. R5 introduces a versioned learning archive.

Each successful five-frame enrollment session becomes an immutable learning version:

`Identity -> V1, V2, V3, ...`

Each version stores:
- version number;
- timestamp;
- enrollment degradation profile;
- five 112x112 aligned face thumbnails;
- per-frame `FaceQuality.Snapshot` fields;
- per-frame `AlignmentGeometry` fields;
- XS/S/M 512-D embeddings for all five frames;
- per-model sample-to-centroid cosine values;
- per-model centroid;
- per-model similarity matrix can be recomputed from stored embeddings;
- per-model PCA can be recomputed from stored embeddings;
- R4 summary statistics: Qavg, stability, dispersion, embedding coverage, pose coverage, combined coverage, min pair, mean pair, outlier index.

### 4.1 Image persistence

Aligned face thumbnails are stored only in app-private internal storage, not external/shared storage:

`files/enrollment_history/<safeIdentity>/v<version>/s1.webp ... s5.webp`

Images are 112x112 WebP/PNG-quality thumbnails intended for microscope replay, not original camera frames. R5 does not persist the full raw camera frame.

No new storage permission is required.

### 4.2 Metadata persistence

A new `EnrollmentHistoryStore` owns version metadata and file lifecycle. Large binary images remain in files; compact metadata/embeddings are serialized separately with a versioned codec.

The store API must support:
- `saveVersion(...)`
- `latest(identity)`
- `versions(identity)`
- `loadVersion(identity, version)`
- `loadFiveFrames(identity, version)`
- `deleteIdentity(identity)`
- `deleteVersion(identity, version)` only for internal maintenance/tests, not normal UI
- migration/fallback from R3/R4 identities that have templates/reference embeddings but no persisted images.

Legacy identities without saved images remain recognizable. Their history panel states explicitly: "旧版本没有保存五帧图像；重新录入或追加学习后可建立完整学习档案." The app must not invent historical images.

## 5. Historical microscope replay

When Identity Guard reaches EXISTING, the enrollment page changes from "new enrollment" mode to "history replay" mode.

The page restores the latest learning version by default and shows:
- identity name;
- version and enrollment time;
- S1-S5 stored aligned face images;
- frame quality and five-point geometry for each image;
- selected XS/S/M N x N cosine matrix;
- PCA scatter for that selected model;
- centroid/sample cosine chain;
- stability/coverage/min/mean/outlier statistics;
- XS/S/M model comparison and delta matrices;
- active template status.

A compact version selector allows viewing older V1/V2/... learning sessions without changing the active identity template.

History replay is read-only until the user chooses Keep / Append Learning / Delete & Re-enroll.

## 6. Actions for an existing identity

### 6.1 Keep existing record

- No storage/template changes.
- Identity Guard remains EXISTING while that face remains present.
- The user can continue observing historical learning evidence.

### 6.2 Append learning

Append Learning is allowed only after Identity Guard has already reached EXISTING for the same identity or the user manually selected that existing identity from SUSPECTED.

Flow:
1. freeze the currently active old template as `cold` for comparison;
2. collect a fresh five-frame session using the existing R4 hard-gate + novelty rules;
3. save the fresh session as the next immutable history version;
4. derive each model's new-session centroid `cnewSession`;
5. update the active template with a conservative normalized fusion:
   `cactive_new = normalize(w_old * cactive_old + w_new * cnewSession)`;
6. default weights use historical and new sample evidence counts, capped so one append cannot dominate the old identity in a single step;
7. display old-vs-new diagnostics before final commit.

R5 uses the following initial fusion rule:
- `w_old = min(existingEffectiveSamples, 15)`;
- `w_new = 5`;
- both centroids are unit-normalized before fusion.

The microscope displays:
- `cos(cold, cactive_new)` drift;
- current probe cosine to old and new centroids;
- per-model old/new stability and coverage;
- old and new five-frame matrices side by side where space permits;
- version transition `Vn -> Vn+1`.

If the new five-frame session fails R4 enrollment quality, it is archived only as a failed attempt if needed for debugging in-memory, but it must not create a new persistent learning version and must not update the template.

### 6.3 Delete and re-enroll

This is destructive and requires an explicit confirmation action in the enrollment UI.

On confirmation:
- delete all XS/S/M active templates for that identity;
- delete R3/R4 reference records for that identity;
- delete textual enrollment archive for that identity;
- delete all R5 history metadata and stored five-frame thumbnails for that identity;
- remove the identity name from `FaceStore` if no templates remain;
- reset Identity Guard;
- immediately enter a fresh five-frame new-enrollment flow using the same identity name prefilled.

The delete action does not silently retain a hidden copy. R5 is an engineering lab, so deletion semantics must be real and testable.

## 7. Manual resolution in SUSPECTED

The SUSPECTED panel shows up to three candidate identities with model evidence.

Allowed actions:
- `继续确认`: keep collecting valid guard frames;
- `这是已有身份 -> <candidate>`: manually select one candidate and open its historical microscope.

Manual selection does not merge identities or change templates. It only resolves the guard context to an existing identity so the user can choose Keep / Append / Delete & Re-enroll.

There is no direct "new identity anyway" button while SUSPECTED.

If five additional valid frames all fall below `Tsuspect`, the state may transition to CLEAR and new enrollment becomes available.

## 8. Enrollment page UI changes

Add a dedicated Identity Guard card above the existing enrollment controls.

The card shows:
- state: CLEAR / SUSPECTED / EXISTING;
- candidate identity;
- XS/S/M scores and vote strength;
- temporal consensus, e.g. `3/3 frames -> same identity`;
- margin or `N/A`;
- the exact reason the state is blocked or cleared.

In EXISTING:
- name field is synchronized to the matched identity and made read-only for the existing-history context;
- the normal "开始质量录入 x5" button is replaced/disabled;
- show actions: `保留现有`, `追加学习 x5`, `删除并重新录入`;
- restore five historical frame thumbnails into the same S1-S5 strip rather than adding a second unrelated gallery;
- label the strip clearly as historical, including version number.

In CLEAR:
- historical action controls are hidden;
- new-enrollment button is enabled once the name is valid.

In SUSPECTED:
- new-enrollment button remains disabled;
- candidate resolution controls are visible.

## 9. FaceStore / archive deletion support

`FaceStore` gains explicit identity lifecycle methods:
- delete one model template;
- delete all model templates for an identity;
- remove identity name only when no template remains.

`EnrollmentArchiveStore` gains:
- delete textual archive;
- delete model reference records;
- delete all legacy enrollment microscope data for an identity.

Deletion must be covered by unit tests because partial deletion would re-create duplicate identities later.

## 10. Active template and history source of truth

`FaceStore` remains the source of truth for active recognition templates.

`EnrollmentHistoryStore` is the source of truth for learning history and the five-frame replay.

A history version is immutable once successfully committed. Append Learning creates a new version rather than rewriting the old one.

The active template may change after append; old history versions continue to describe exactly what was learned at that time.

## 11. Concurrency and stale-frame safety

Identity Guard uses its own generation/epoch token in addition to the existing model microscope selection epoch.

Any asynchronous XS/S/M guard result is ignored if, before UI commit:
- guard generation changed;
- tracked face changed;
- page changed;
- delete/re-enroll/append action changed context;
- camera/profile changed.

This prevents an old frame from reopening a deleted identity or changing CLEAR back to EXISTING after the user began enrollment.

## 12. Privacy and storage policy

- All identity data remains on-device.
- Only aligned 112x112 enrollment thumbnails are persisted for replay.
- No raw full-resolution face frame is persisted by R5.
- History files live in app-private internal storage.
- Deleting an identity deletes its persisted R5 thumbnails and metadata.
- CSV export does not include face image bytes.

## 13. Migration behavior

Existing R4 installations upgrade in place.

For a legacy identity:
- active templates remain usable;
- stored R3.1 embeddings remain usable for matching/calibration;
- textual archive remains visible;
- if no R5 image history exists, the UI shows a legacy-history notice rather than empty fake S1-S5 frames;
- the first successful Append Learning creates V1 R5 history while preserving the old active centroid as pre-append comparison evidence;
- Delete & Re-enroll removes both legacy and R5 data.

## 14. Tests and CI contracts

R5 adds unit tests for:
- guard CLEAR/SUSPECTED/EXISTING transitions;
- three-model identity agreement;
- one-identity margin N/A behavior;
- temporal consensus and reset conditions;
- high-confidence existing identity blocks new enrollment;
- SUSPECTED blocks new enrollment;
- historical codec round-trip;
- five thumbnail file save/load/delete;
- legacy history fallback;
- append centroid fusion and bounded weight behavior;
- delete identity removes FaceStore templates, reference archive, textual archive, and history files;
- history versions are immutable;
- stale guard epoch results are rejected.

Add `tools/verify_r5_identity_guard.py` to assert wiring at source-contract level:
- versionCode 7 / versionName 0.5.0;
- Identity Guard card and states;
- no force-new path from SUSPECTED/EXISTING;
- history store and five-frame persistence;
- Keep/Append/Delete actions;
- delete support in FaceStore/EnrollmentArchiveStore;
- guard generation stale-result rejection;
- legacy fallback message.

Existing R3/R3.1/R3.2/R4 contracts remain green and are made forward-compatible only where they historically hard-code older version numbers.

Full CI must still verify:
- Android unit tests;
- XS/S/M diagnostic ONNX export and fidelity;
- APK build;
- presence of all three model assets;
- arm64-v8a-only native ABI.

## 15. Release acceptance criteria

R5 is complete only when all of the following hold on `main`:

1. A face matching an existing identity cannot be enrolled as a new identity.
2. A suspected duplicate also cannot be force-created as new until the guard becomes CLEAR.
3. EXISTING automatically restores the matched identity's historical learning microscope.
4. New R5 enrollments persist five aligned frames and reload them after app restart.
5. Historical matrix/PCA/quality/geometry can be reconstructed from persisted evidence.
6. Append Learning creates a new immutable learning version and updates the active template with conservative centroid fusion.
7. Delete & Re-enroll actually removes old templates, references, archive, thumbnails, and history metadata before fresh enrollment.
8. Legacy R4 identities continue to recognize and clearly indicate when historical five-frame images are unavailable.
9. Model switching and stale-frame protection from R3.2/R4 still work.
10. Main-branch CI is green and the delivered APK is produced from that main commit.

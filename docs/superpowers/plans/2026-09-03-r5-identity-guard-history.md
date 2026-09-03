# R5 Identity Guard + History Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent duplicate face enrollment and turn an existing identity match into a replayable, versioned five-frame learning microscope with append-learning and true delete/re-enroll lifecycle actions.

**Architecture:** Add a pure `IdentityGuardEngine` in front of enrollment, a versioned `EnrollmentHistoryStore` for immutable learning sessions, and focused lifecycle/fusion helpers. `FaceStore` remains the source of truth for the active XS/S/M templates; history reconstructs the existing R4 enrollment microscope from persisted evidence. `MainActivity` orchestrates these units but does not own their decision rules or serialization.

**Tech Stack:** Java 17, Android SDK 36, CameraX 1.6.2, ML Kit face detection 16.1.7, ONNX Runtime Android 1.29.0, JUnit 4.13.2, app-private files + SharedPreferences, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-03-r5-identity-guard-history-design.md`

## Global Constraints

- Release target is exactly `versionCode 7` / `versionName '0.5.0'`.
- New enrollment is allowed only when Identity Guard state is `CLEAR`.
- `SUSPECTED` and `EXISTING` have no force-create-new path.
- Empty identity library becomes `CLEAR` after the first valid aligned probe.
- Non-empty library requires five consecutive below-suspect valid frames for `CLEAR`.
- Automatic `EXISTING` requires valid five-point geometry, at least three consecutive confirming frames, and at least two of three model-local strong votes per confirming frame.
- XS/S/M embeddings are never cross-compared; aggregation happens only on identity names and scalar model-local evidence.
- Guard thresholds are `Tsuspect=clamp(max(Tid+0.05,0.55),0,0.99)` and `Texisting=clamp(max(Tid+0.10,0.62),0,0.99)`, made stricter by empirical calibration when present.
- With at least two identities, a model strong vote requires Top-1 >= `Texisting` and margin >= `0.08`; one-identity margin remains `N/A`.
- Only aligned 112x112 enrollment thumbnails are persisted; no raw camera frames or external-storage permission.
- Every successful R5 five-frame session is immutable `V1`, `V2`, ... history.
- Append fusion is `normalize(w_old*c_old + 5*c_newSession)`, with `w_old=min(existingEffectiveSamples,15)` and `effectiveSamplesAfter=min(existingEffectiveSamples+5,20)`.
- Delete & Re-enroll must remove active templates, legacy references/text, all history metadata/thumbs, and identity membership before fresh enrollment.
- Existing R4 identities must continue to recognize; missing historical thumbnails must be stated explicitly, never fabricated.
- Existing R3/R3.1/R3.2/R4 model-linkage and stale-frame protection must remain green.
- Final deliverable is an arm64-v8a APK produced by a green `main` CI run.

---

### Task 1: R5 CI contract and branch gate

**Files:**
- Create: `tools/verify_r5_identity_guard.py`
- Modify: `.github/workflows/android.yml`
- Modify: `tools/verify_r4_microscope.py`

**Interfaces:**
- Consumes: existing R3/R3.1/R3.2/R4 contract scripts.
- Produces: fast R5 source-contract failure before Android/model-export work and R5 branch CI triggering.

- [ ] **Step 1: Add the R5 branch and verifier step to Actions**

Add `feat/r5-identity-guard-history` to the push branch list and insert `python tools/verify_r5_identity_guard.py` immediately after the R4 contract.

- [ ] **Step 2: Write the failing R5 source contract**

The verifier must require these production types before they exist:

```python
for filename in (
    "IdentityGuardPolicy.java",
    "IdentityGuardEngine.java",
    "EnrollmentHistoryRecord.java",
    "EnrollmentHistoryCodec.java",
    "EnrollmentHistoryStore.java",
    "TemplateFusion.java",
    "IdentityGuardPanel.java",
):
    require((JAVA / filename).exists(), f"missing {filename}")
```

It must also require exact release version 7/0.5.0, history UI/action strings, no force-new path, complete deletion APIs, guard-generation stale-result checks, and the exact legacy-history message.

- [ ] **Step 3: Make the R4 verifier version-forward-compatible**

Replace the exact R4 version equality with a capability floor:

```python
version_code = re.search(r"versionCode\s+(\d+)\b", build)
require(version_code is not None and int(version_code.group(1)) >= 6,
        "versionCode must remain at least R4")
```

R5 verifier alone owns the exact `7 / 0.5.0` assertion.

- [ ] **Step 4: Push RED and inspect the first CI failure**

Expected: UI/R3/R3.1/R3.2/R4 contracts pass and the new R5 contract fails only because R5 production types/wiring do not yet exist.

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/android.yml tools/verify_r4_microscope.py tools/verify_r5_identity_guard.py
git commit -m "test: add R5 identity guard release contract"
```

---

### Task 2: Pure Identity Guard policy and temporal state machine

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/IdentityGuardPolicy.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/IdentityGuardEngine.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/IdentityGuardPolicyTest.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/IdentityGuardEngineTest.java`

**Interfaces:**
- Consumes: `FaceStore.Match`, `ModelVariant`, current `Tid` and optional empirical model thresholds.
- Produces:
  - `IdentityGuardPolicy.thresholds(float tid, Float empirical)` -> `Thresholds`
  - `IdentityGuardEngine.push(FrameInput)` -> immutable `Snapshot`
  - `IdentityGuardEngine.reset()` and generation-based `captureGeneration()/isCurrent(long)`.

- [ ] **Step 1: Write RED threshold tests**

Cover floor, offset, clamp, and stricter empirical threshold behavior:

```java
@Test public void thresholdsUseSafetyOffsetsAndClamp() {
    IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.45f, null);
    assertEquals(.55f, t.suspect, 1e-6f);
    assertEquals(.62f, t.existing, 1e-6f);
}
```

- [ ] **Step 2: Write RED state-machine tests**

Tests must include:
- empty library -> CLEAR on first valid frame;
- five clean frames -> CLEAR for non-empty library;
- one weak/suspect model -> SUSPECTED and `canCreateNew=false`;
- three consecutive frames with 2/3 strong same identity -> EXISTING;
- one-identity candidate can reach EXISTING while margin remains unavailable;
- fallback geometry never auto-promotes to EXISTING;
- competing candidate prevents EXISTING;
- tracking/reset changes generation and clears evidence.

Use a compact test evidence builder rather than invoking Android model code.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
gradle :app:testDebugUnitTest --tests '*IdentityGuard*' --stacktrace
```

Expected: compile failure because guard classes are absent.

- [ ] **Step 4: Implement `IdentityGuardPolicy`**

Centralize suspect/existing thresholds and margin policy. Do not duplicate these constants in `MainActivity`.

- [ ] **Step 5: Implement `IdentityGuardEngine`**

Use a five-frame deque. Each `FrameInput` contains:

```java
int librarySize;
int trackingId;
boolean validProbe;
boolean fullFivePointGeometry;
EnumMap<ModelVariant, ModelEvidence> evidence;
```

`ModelEvidence` contains top1/top2 names/scores and margin availability. `Snapshot` exposes state, candidate, per-model evidence, confirming frame count, clear frame count, reason, and `canCreateNew()`.

- [ ] **Step 6: Run guard tests GREEN**

Expected: all guard tests pass without Android camera/model execution.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/qujindai/facelivtlab/IdentityGuard*.java app/src/test/java/com/qujindai/facelivtlab/IdentityGuard*.java
git commit -m "feat: add temporal three-model identity guard"
```

---

### Task 3: Versioned history domain, codec, and deterministic template fusion

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentHistoryRecord.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentHistoryCodec.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/TemplateFusion.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometry.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/EnrollmentHistoryCodecTest.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/TemplateFusionTest.java`

**Interfaces:**
- Produces immutable serializable learning versions independent of Android Bitmap storage.
- `EnrollmentHistoryRecord.toEnrollmentSession()` reconstructs the R4 vector microscope.
- `TemplateFusion.fuse(float[] oldCentroid, int existingEffectiveSamples, float[] newSessionCentroid)` returns new unit centroid + bounded effective count.

- [ ] **Step 1: Write RED codec tests**

Construct a two-dimensional miniature record with five `FrameRecord`s and XS/S/M `ModelRecord`s. Assert exact round-trip of quality fields, geometry fields, embeddings, centroids, summaries, effective counts, version/timestamp/profile.

- [ ] **Step 2: Add an `AlignmentGeometry.restore(...)` factory**

The codec needs a stable reconstruction path. The factory accepts all persisted public fields and defensively copies point arrays.

- [ ] **Step 3: Implement immutable history domain**

`EnrollmentHistoryRecord` owns:
- identity/version/timestamp/profile/effectiveBefore/effectiveAfter;
- exactly five `FrameRecord`s;
- an `EnumMap<ModelVariant,ModelRecord>`.

`FrameRecord` stores `FaceQuality.Snapshot` and `AlignmentGeometry`; `ModelRecord` stores the five embeddings, centroid, sample-to-centroid and R4 summary scalars.

- [ ] **Step 4: Implement binary Base64 codec**

Use magic + explicit codec version + bounded counts/dimensions. Reject truncated/oversized/malformed input with `IllegalArgumentException`.

- [ ] **Step 5: Write RED fusion tests**

Cover normalization, `w_old` cap at 15, effective count cap at 20, and a first append from a legacy 5-sample template.

- [ ] **Step 6: Implement `TemplateFusion`**

```java
int oldWeight = Math.min(Math.max(1, existingEffectiveSamples), 15);
int newWeight = 5;
float[] fused = VectorMath.normalize(weightedSum(oldCentroid, oldWeight, newCentroid, newWeight));
int effective = Math.min(Math.max(1, existingEffectiveSamples) + 5, 20);
```

- [ ] **Step 7: Run codec/fusion tests GREEN and commit**

```bash
gradle :app:testDebugUnitTest --tests '*EnrollmentHistoryCodecTest' --tests '*TemplateFusionTest' --stacktrace
git add app/src/main/java/com/qujindai/facelivtlab/EnrollmentHistory*.java app/src/main/java/com/qujindai/facelivtlab/TemplateFusion.java app/src/main/java/com/qujindai/facelivtlab/AlignmentGeometry.java app/src/test/java/com/qujindai/facelivtlab/EnrollmentHistoryCodecTest.java app/src/test/java/com/qujindai/facelivtlab/TemplateFusionTest.java
git commit -m "feat: add versioned enrollment history records"
```

---

### Task 4: History persistence and complete identity lifecycle

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentHistoryStore.java`
- Create: `app/src/main/java/com/qujindai/facelivtlab/IdentityLifecycle.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/FaceStore.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentArchiveStore.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/EnrollmentHistoryStoreTest.java`
- Create: `app/src/test/java/com/qujindai/facelivtlab/IdentityLifecycleTest.java`

**Interfaces:**
- `EnrollmentHistoryStore` persists metadata and five thumbnail byte arrays under app-private root.
- Public Android constructor accepts `Context`; package-private test constructor accepts a `File root` and metadata preference adapter/file so JVM tests can use temp directories without Bitmap.
- `FaceStore.sampleCount(...)`, `deleteTemplate(...)`, `deleteIdentity(...)` expose active-template lifecycle.
- `EnrollmentArchiveStore.deleteArchive/deleteReference/deleteIdentityData` removes legacy data.
- `IdentityLifecycle.deleteIdentity(name)` coordinates all stores.

- [ ] **Step 1: Write RED history-store tests**

Use temporary directories and fake thumbnail bytes. Verify:
- V1/V2 numbering;
- latest/versions ordering;
- five bytes saved and reloaded;
- V1 bytes/metadata remain unchanged after V2;
- identity delete recursively removes files and metadata;
- missing history returns legacy/no-version rather than fabricated frames.

- [ ] **Step 2: Implement store path safety and persistence**

Use a hash-based safe identity directory plus original identity in metadata; never place raw identity strings directly in filesystem paths. Write metadata and thumbnails into a temporary version directory, then publish the version only after all five files succeed.

- [ ] **Step 3: Write RED active-store deletion/count tests**

Verify one model can be removed while name remains, and `deleteIdentity` removes membership only after all XS/S/M templates/counts are gone.

- [ ] **Step 4: Extend `FaceStore`**

Add synchronized `sampleCount`, `deleteTemplate`, and `deleteIdentity`; also remove legacy S vector/count keys during full identity delete.

- [ ] **Step 5: Extend `EnrollmentArchiveStore`**

Add deletion for text archive and all three reference keys, plus a single `deleteIdentityData` coordinator method.

- [ ] **Step 6: Implement and test `IdentityLifecycle`**

A full delete must call FaceStore + legacy archive + history store. Tests must assert no residual active template, reference record, text archive, version metadata, or thumbnail bytes.

- [ ] **Step 7: Run lifecycle tests GREEN and commit**

```bash
gradle :app:testDebugUnitTest --tests '*EnrollmentHistoryStoreTest' --tests '*IdentityLifecycleTest' --stacktrace
git add app/src/main/java/com/qujindai/facelivtlab/EnrollmentHistoryStore.java app/src/main/java/com/qujindai/facelivtlab/IdentityLifecycle.java app/src/main/java/com/qujindai/facelivtlab/FaceStore.java app/src/main/java/com/qujindai/facelivtlab/EnrollmentArchiveStore.java app/src/test/java/com/qujindai/facelivtlab/EnrollmentHistoryStoreTest.java app/src/test/java/com/qujindai/facelivtlab/IdentityLifecycleTest.java
git commit -m "feat: persist learning versions and identity lifecycle"
```

---

### Task 5: Identity Guard and history replay UI component

**Files:**
- Create: `app/src/main/java/com/qujindai/facelivtlab/IdentityGuardPanel.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/styles.xml` only if a missing state/action style is required.

**Interfaces:**
- Consumes: `IdentityGuardEngine.Snapshot`, candidate identities, history version list.
- Produces callbacks for Continue Confirmation, manual existing-candidate selection, Keep Existing, Append Learning, Delete & Re-enroll, and history version selection.

- [ ] **Step 1: Add the panel above enrollment name/profile controls**

The panel must always show state and exact reason. It must not hide a blocked state behind a Toast.

- [ ] **Step 2: Implement state-specific controls**

`CLEAR`: history actions/candidate buttons hidden.

`SUSPECTED`: new enrollment remains outside the panel and disabled; show candidate buttons and Continue Confirmation.

`EXISTING`: show identity/version plus `保留现有`, `追加学习 ×5`, `删除并重新录入`; no create-new control.

- [ ] **Step 3: Add history strip labeling/version selector**

Reuse `sampleFace1..5`; add a nearby label for `历史学习 Vn · S1-S5` versus live `新录入采样`.

- [ ] **Step 4: Update the top product title to R5**

Use `FaceLiVT R5 · 身份防重学习显微镜`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/qujindai/facelivtlab/IdentityGuardPanel.java app/src/main/res/layout/activity_main.xml app/src/main/res/values/styles.xml
git commit -m "feat: add identity guard and history replay panel"
```

---

### Task 6: Main enrollment flow integration, replay, append, and delete/re-enroll

**Files:**
- Modify: `app/src/main/java/com/qujindai/facelivtlab/MainActivity.java`
- Modify: `app/src/main/java/com/qujindai/facelivtlab/EnrollmentSession.java` only for explicit history reconstruction helpers if necessary.
- Create: `app/src/test/java/com/qujindai/facelivtlab/HistoryReplayReconstructionTest.java`

**Interfaces:**
- Consumes all previous tasks.
- Produces complete enrollment-page behavior and R4 microscope reconstruction from history.

- [ ] **Step 1: Add enrollment workflow state**

Introduce an explicit intent enum such as:

```java
enum EnrollmentIntent { NONE, NEW, APPEND, REPLACE_AFTER_DELETE }
```

Track current existing identity, viewed history version, five accepted aligned Bitmaps, old centroids/effective counts for append, and guard generation/tracking context.

- [ ] **Step 2: Run Guard while enrollment is idle**

After alignment/quality on `Page.ENROLLMENT`, when no five-frame capture is active:
- reject invalid probe quality from Guard evidence;
- run `recognizerBank.embedAll(aligned)`;
- call `faceStore.topMatches` in each model space;
- push evidence into `IdentityGuardEngine`;
- reject UI commits if guard generation/model selection/page/tracking context is stale.

- [ ] **Step 3: Enforce Guard in `updateActionState()` and `beginEnrollment()`**

`btnEnroll` is enabled only when Guard snapshot `canCreateNew()` is true, camera/name are ready, and no existing-history context is active. `beginEnrollment()` re-checks Guard so a UI race cannot bypass the block.

- [ ] **Step 4: Enter EXISTING history context automatically**

On automatic EXISTING or manual SUSPECTED resolution:
- synchronize/read-only `editName`;
- load latest history record and five thumbnail bytes;
- if history exists, reconstruct `completedEnrollmentSession`, geometry list, sample thumbnails, matrix/PCA/formulas/model comparison;
- if legacy only, clear thumbnail strip and show exact legacy notice while retaining old archive/reference evidence.

- [ ] **Step 5: Add history version replay**

Changing V1/V2/... reloads only the viewed immutable version. It must never change `FaceStore` active templates.

- [ ] **Step 6: Persist five-frame evidence on successful NEW enrollment**

Keep a `Bitmap.copy` for each accepted sample. After all model summaries pass, compress only the five aligned 112x112 images to WebP/PNG byte arrays, create V1/next history record, save it, then replace active templates and legacy reference/text archives.

- [ ] **Step 7: Implement Append Learning**

Capture old XS/S/M active centroids and effective counts before collecting the new five frames. On pass:
- create next immutable version;
- `TemplateFusion.fuse` each model centroid;
- replace active template with fused centroid/effective count;
- refresh legacy reference/calibration;
- show Vn->Vn+1 and `cos(cold,cactive_new)` drift.

On fail: no persistent version and no active-template change.

- [ ] **Step 8: Implement Delete & Re-enroll with explicit confirmation**

Use an `AlertDialog` or equivalent explicit second action. On confirm call `IdentityLifecycle.deleteIdentity`, reset guard/fusion/history context, unlock/prefill the same name, and immediately enter a fresh `REPLACE_AFTER_DELETE` five-frame capture.

- [ ] **Step 9: Add replay reconstruction test**

Create a history record, reconstruct an `EnrollmentSession`, and assert the reconstructed N×N matrix/centroid statistics are derived from stored embeddings and selected model correctly.

- [ ] **Step 10: Run all Android unit tests GREEN and commit**

```bash
gradle :app:testDebugUnitTest --stacktrace
git add app/src/main/java/com/qujindai/facelivtlab/MainActivity.java app/src/main/java/com/qujindai/facelivtlab/EnrollmentSession.java app/src/test/java/com/qujindai/facelivtlab/HistoryReplayReconstructionTest.java
git commit -m "feat: integrate duplicate guard with learning history"
```

---

### Task 7: R5 release wiring, documentation, and clean branch CI

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Modify: `.github/workflows/android.yml`
- Modify: `tools/verify_r5_identity_guard.py` as needed after production wiring.

**Interfaces:**
- Produces release `0.5.0` and `FaceLiVT-Mobile-Lab-R5-debug.apk` artifact.

- [ ] **Step 1: Set release version**

```gradle
versionCode 7
versionName '0.5.0'
```

- [ ] **Step 2: Rename APK/artifact and add R5 contract to CI**

Use `FaceLiVT-Mobile-Lab-R5-debug.apk` and artifact `FaceLiVT-Mobile-Lab-R5-debug-apk`.

- [ ] **Step 3: Update README**

Document Guard states, thresholds as engineering defaults, history privacy/storage, legacy behavior, append fusion, and destructive deletion semantics.

- [ ] **Step 4: Run source contracts locally/CI**

Expected all: UI, R3, R3.1, R3.2, R4, R5 PASS.

- [ ] **Step 5: Run clean feature-branch full CI**

Require Android unit tests, pinned upstream XS/S/M diagnostic ONNX export/fidelity, assemble, three model assets, arm64-v8a-only, artifact upload.

- [ ] **Step 6: Commit release cleanup**

```bash
git add app/build.gradle README.md .github/workflows/android.yml tools/verify_r5_identity_guard.py
git commit -m "release: prepare R5 identity guard microscope"
```

---

### Task 8: PR review, merge, main verification, and APK handoff

**Files:**
- No product-code changes unless review/CI exposes a defect.

**Interfaces:**
- Produces the only user-facing final artifact: APK from green `main`.

- [ ] **Step 1: Compare `main...feat/r5-identity-guard-history`**

Reject accidental model binaries, temporary workflows/scripts, duplicate alternative R5 classes, or unrelated refactors.

- [ ] **Step 2: Create PR**

PR description must list Guard hard-block semantics, versioned five-frame replay, append fusion, full delete semantics, migration, and passing branch CI evidence.

- [ ] **Step 3: Squash merge only if head SHA is unchanged and PR mergeable**

- [ ] **Step 4: Wait for the resulting `main` Android APK workflow**

Only the exact merge commit is acceptable. Require every contract, unit tests, three model exports, APK/ABI checks, and artifact upload to pass.

- [ ] **Step 5: Download main artifact and independently verify**

Unzip locally, compute APK SHA-256/size, confirm `assets/facelivtv2_xs.onnx`, `facelivtv2_s.onnx`, `facelivtv2_m.onnx`, confirm `lib/arm64-v8a/` exists and no other native ABI exists.

- [ ] **Step 6: Handoff**

Return the direct sandbox APK plus the main GitHub Actions ZIP, exact main commit/run, APK hash/size, and a concise summary of the Guard/history behavior. Do not ask for feature-by-feature handset micro-tests.

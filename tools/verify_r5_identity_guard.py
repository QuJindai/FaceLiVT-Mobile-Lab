#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/qujindai/facelivtlab"
MAIN = JAVA / "MainActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
BUILD = ROOT / "app/build.gradle"
WORKFLOW = ROOT / ".github/workflows/android.yml"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R5 IDENTITY GUARD CONTRACT FAIL: " + message)


for filename in (
    "IdentityGuardPolicy.java",
    "IdentityGuardEngine.java",
    "EnrollmentIdentityLock.java",
    "EnrollmentHistoryRecord.java",
    "EnrollmentHistoryCodec.java",
    "EnrollmentHistoryStore.java",
    "TemplateFusion.java",
    "EnrollmentCommitPlan.java",
    "IdentityGuardPanel.java",
):
    require((JAVA / filename).exists(), f"missing {filename}")

layout = LAYOUT.read_text(encoding="utf-8")
for fragment in (
    "FaceLiVT R5 · 身份防重学习显微镜",
    "identityGuardPanel",
    "txtHistoryStrip",
    "sampleFace1",
    "sampleFace5",
):
    require(fragment in layout, f"layout missing {fragment}")

panel = (JAVA / "IdentityGuardPanel.java").read_text(encoding="utf-8")
for fragment in (
    "AttributeSet",
    "IdentityGuardPanel(Context context, AttributeSet attrs)",
    "保留现有",
    "追加学习 ×5",
    "删除并重新录入",
):
    require(fragment in panel, f"IdentityGuardPanel missing {fragment}")

main = MAIN.read_text(encoding="utf-8")
for fragment in (
    "IdentityGuardEngine",
    "IdentityGuardPanel",
    "EnrollmentHistoryStore",
    "IdentityLifecycle",
    "EnrollmentCommitPlan",
    "EnrollmentIdentityLock.forNewIdentity(",
    "EnrollmentIdentityLock.forAppend(",
    "enrollmentSession.isSameSubjectCandidate(",
    "handleFailedEnrollment(",
    "保留现有",
    "追加学习",
    "删除并重新录入",
    "旧版本没有保存五帧图像；追加学习或删除重录后可建立完整学习档案。",
    "guardGeneration",
    "isCurrent(",
    "tracked != null ? tracked : -1",
    "identityGuard.reset();",
    "historyStore.saveVersion(",
    "EnrollmentCommitPlan.build(",
    "EnrollmentIntent.APPEND",
    "EnrollmentIntent.REPLACE_AFTER_DELETE",
    "Bitmap.CompressFormat.WEBP",
    "historyStore.deleteVersion(name, version)",
):
    require(fragment in main, f"MainActivity missing {fragment}")

# During capture, duplicate re-check and same-subject continuity must happen before any sample mutates the session.
handle_start = main.find("private void handleEnrollment(")
handle_end = main.find("private void finalizeEnrollment(", handle_start)
require(handle_start >= 0 and handle_end > handle_start, "cannot isolate handleEnrollment")
handle = main[handle_start:handle_end]
lock_new = handle.find("EnrollmentIdentityLock.forNewIdentity(")
lock_append = handle.find("EnrollmentIdentityLock.forAppend(")
continuity = handle.find("enrollmentSession.isSameSubjectCandidate(")
first_add = handle.find("enrollmentSession.add(")
require(lock_new >= 0 and lock_append >= 0 and continuity >= 0 and first_add >= 0,
        "handleEnrollment must contain both identity-lock paths, continuity gate and sample add")
require(lock_new < first_add and lock_append < first_add and continuity < first_add,
        "in-capture identity locks and same-subject continuity must run before enrollmentSession.add")

# Failed append must return to existing history without overwriting the successful archive.
failed_start = main.find("private void handleFailedEnrollment(")
failed_end = main.find("private void", failed_start + 1)
require(failed_start >= 0 and failed_end > failed_start, "cannot isolate failed-enrollment handler")
failed = main[failed_start:failed_end]
require("failedIntent == EnrollmentIntent.APPEND" in failed,
        "failed enrollment handler must branch explicitly for APPEND")
require("enterExistingIdentity(" in failed,
        "failed APPEND must return to existing identity history")
append_branch_start = failed.find("failedIntent == EnrollmentIntent.APPEND")
append_branch_end = failed.find("} else", append_branch_start)
if append_branch_end < 0:
    append_branch_end = len(failed)
require("archiveStore.save(" not in failed[append_branch_start:append_branch_end],
        "failed APPEND must not overwrite the existing successful archive")

preflight_index = main.find("EnrollmentCommitPlan.build(")
history_publish_index = main.find("historyStore.saveVersion(")
require(preflight_index >= 0 and history_publish_index >= 0 and preflight_index < history_publish_index,
        "all three active templates must be preflighted before immutable history is published")

plan = (JAVA / "EnrollmentCommitPlan.java").read_text(encoding="utf-8")
for fragment in (
    "TemplateFusion.fuse(",
    "ModelVariant.values()",
    "historical active template missing",
    "effectiveSamplesBefore",
    "effectiveSamplesAfter",
):
    require(fragment in plan, f"EnrollmentCommitPlan missing {fragment}")

session = (JAVA / "EnrollmentSession.java").read_text(encoding="utf-8")
for fragment in (
    "MIN_CONTINUITY_COSINE",
    "MIN_CONTINUITY_MODEL_VOTES",
    "isSameSubjectCandidate(",
):
    require(fragment in session, f"EnrollmentSession missing {fragment}")

identity_lock = (JAVA / "EnrollmentIdentityLock.java").read_text(encoding="utf-8")
for fragment in (
    "forNewIdentity(",
    "forAppend(",
    "IdentityGuardPolicy.thresholds(",
):
    require(fragment in identity_lock, f"EnrollmentIdentityLock missing {fragment}")

require("noTrackingSequence" not in main,
        "no-tracking fallback must not synthesize a new identity every frame")
require('的 R5 学习版本。\\n" +' in main,
        "R5 enrollment status text must use escaped newlines, not literal Java source newlines")

# A blocked duplicate must not have a bypass action.
for forbidden in (
    "强制新建",
    "仍然新建",
    "forceCreateNew",
    "forceNewIdentity",
):
    require(forbidden not in main and forbidden not in layout,
            f"forbidden duplicate-enrollment bypass present: {forbidden}")

store = (JAVA / "FaceStore.java").read_text(encoding="utf-8")
for fragment in ("sampleCount(", "deleteTemplate(", "deleteIdentity("):
    require(fragment in store, f"FaceStore missing {fragment}")

archive = (JAVA / "EnrollmentArchiveStore.java").read_text(encoding="utf-8")
for fragment in ("deleteArchive(", "deleteReference(", "deleteIdentityData("):
    require(fragment in archive, f"EnrollmentArchiveStore missing {fragment}")

history = (JAVA / "EnrollmentHistoryStore.java").read_text(encoding="utf-8")
for fragment in (
    "saveVersion(", "latest(", "versions(", "loadVersion(",
    "loadFiveFrames", "deleteIdentity(", "deleteVersion(", "enrollment_history",
    '"s" + (i + 1) + ".webp"', "record.txt",
):
    require(fragment in history, f"EnrollmentHistoryStore missing {fragment}")

guard = (JAVA / "IdentityGuardEngine.java").read_text(encoding="utf-8")
for fragment in ("CLEAR", "SUSPECTED", "EXISTING", "captureGeneration", "isCurrent", "reset"):
    require(fragment in guard, f"IdentityGuardEngine missing {fragment}")

policy = (JAVA / "IdentityGuardPolicy.java").read_text(encoding="utf-8")
for fragment in ("0.05f", "0.55f", "0.10f", "0.62f", "0.08f"):
    require(fragment in policy, f"IdentityGuardPolicy missing threshold constant {fragment}")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+7\b", build) is not None, "versionCode must be 7")
require("versionName '0.5.0'" in build, "versionName must be 0.5.0")

workflow = WORKFLOW.read_text(encoding="utf-8")
require("Verify R5 identity guard contract" in workflow, "workflow must run R5 contract")
require("Verify R5 APK contents and ABI" in workflow, "workflow must verify R5 artifact")
require("FaceLiVT-Mobile-Lab-R5-debug.apk" in workflow, "workflow must package R5 APK")
require("FaceLiVT-Mobile-Lab-R5-debug-apk" in workflow, "workflow must upload R5 artifact")

print("R5 IDENTITY GUARD CONTRACT PASS: duplicate hard-block, XML-safe panel, temporal fallback, in-capture identity lock, same-subject continuity, atomic append preflight, history replay and delete lifecycle are wired")

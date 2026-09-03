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

print("R5 IDENTITY GUARD CONTRACT PASS: duplicate hard-block, XML-safe panel, temporal fallback, atomic append preflight, history replay, append/delete lifecycle and stale-result safety are wired")

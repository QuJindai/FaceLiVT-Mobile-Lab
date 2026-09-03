#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/qujindai/facelivtlab"
MAIN = JAVA / "MainActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
LOGGER = JAVA / "SessionLogger.java"
EXPORTER = ROOT / "tools/export_facelivtv2.py"
BUILD = ROOT / "app/build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R4 MICROSCOPE CONTRACT FAIL: " + message)


for filename in (
    "AlignmentGeometry.java",
    "DeepModelStats.java",
    "ModelTopology.java",
    "ModelComparisonStats.java",
    "BlockMicroscopeView.java",
    "ModelComparisonView.java",
    "MatrixDeltaView.java",
):
    require((JAVA / filename).exists(), f"missing {filename}")

layout = LAYOUT.read_text(encoding="utf-8")
for fragment in (
    "FaceLiVT R4 · 深层人脸显微镜",
    "txtGeometryMicroscope",
    "enrollmentModelMicroscope",
    "recognitionModelMicroscope",
    "modelComparisonView",
    "deltaXsS",
    "deltaMS",
    "5×5 来源于 5 张录入样本，不是模型结构",
    "PCA 坐标不可跨模型直接比较",
):
    require(fragment in layout, f"layout missing {fragment}")

main = MAIN.read_text(encoding="utf-8")
for fragment in (
    "FaceAligner.alignWithGeometry",
    "renderGeometryMicroscope(",
    "maybeRunDeepDiagnostic(",
    "renderDeepModelStats(",
    "renderEnrollmentComparison(",
    "ModelComparisonStats.from(",
    "ΔM_XS,S",
    "ΔM_M,S",
    "microscopeSelection.isCurrent(diagnosticSelection)",
    "5点 → sR+t → 112×112 →",
):
    require(fragment in main, f"MainActivity missing {fragment}")

topology_ui = (JAVA / "ModelTopology.java").read_text(encoding="utf-8") + (JAVA / "BlockMicroscopeView.java").read_text(encoding="utf-8")
for fragment in ("18 blocks", "[3,3,9,3]", "1284D→512D"):
    require(fragment in topology_ui, f"model topology UI missing {fragment}")

logger = LOGGER.read_text(encoding="utf-8")
for fragment in (
    "landmark_count",
    "fallback_crop",
    "eye_distance_px",
    "align_mean_residual_px",
    "align_max_residual_px",
    "stage1_rms",
    "stage4_rms",
    "prehead_rms",
):
    require(fragment in logger, f"SessionLogger missing {fragment}")

exporter = EXPORTER.read_text(encoding="utf-8")
for fragment in ("block_stats", "stage_stats", "prehead_stats", "relative_delta"):
    require(fragment in exporter, f"exporter missing {fragment}")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+6\b", build) is not None, "versionCode must be 6")
require("versionName '0.4.0'" in build, "versionName must be 0.4.0")

print("R4 MICROSCOPE CONTRACT PASS: geometry, N×N/model deltas, 18-block diagnostics and model-linked UI are wired")

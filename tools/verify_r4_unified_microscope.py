#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/qujindai/facelivtlab"
MAIN = JAVA / "MainActivity.java"
BUILD = ROOT / "app/build.gradle"
EXPORT = ROOT / "tools/export_facelivtv2.py"
OVERLAY = JAVA / "FaceOverlayView.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R4 UNIFIED MICROSCOPE CONTRACT FAIL: " + message)


for filename in (
    "ModelArchitectureSpec.java",
    "ModelDiagnostics.java",
    "CrossModelEnrollmentComparison.java",
    "FaceAlignmentDiagnostics.java",
    "ModelArchitectureView.java",
    "BlockDiagnosticsView.java",
    "CrossModelComparisonView.java",
    "AlignmentGeometryView.java",
):
    require((JAVA / filename).exists(), f"missing {filename}")

main = MAIN.read_text(encoding="utf-8")
for fragment in (
    "installR4Panels()",
    "FaceAligner.alignWithDiagnostics",
    "CrossModelEnrollmentComparison",
    "recognizerBank.diagnose(variant, aligned)",
    "ModelDiagnostics displayDiagnostics",
    "blockDiagnosticsView.setData",
    "recognitionArchitectureView.setVariant",
    "microscopeSelection.isCurrent(frameSelection)",
    "5 samples → 5×5 relation matrix",
    "18 blocks [3/3/9/3]",
    "1284D → 512D",
):
    require(fragment in main, f"MainActivity missing {fragment}")

arch = (JAVA / "ModelArchitectureSpec.java").read_text(encoding="utf-8")
for fragment in (
    "new int[]{3, 3, 9, 3}",
    "new int[]{32, 64, 128, 256}",
    "new int[]{48, 96, 192, 320}",
    "new int[]{56, 112, 224, 448}",
    "this.preheadDim = 1284",
    "this.embeddingDim = 512",
):
    require(fragment in arch, f"ModelArchitectureSpec missing {fragment}")

diag = (JAVA / "ModelDiagnostics.java").read_text(encoding="utf-8")
for fragment in ("BLOCK_COUNT = 18", "STAGE_COUNT = 4", "STAT_COUNT = 4", "PREHEAD_DIM = 1284"):
    require(fragment in diag, f"ModelDiagnostics missing {fragment}")

recognizer = (JAVA / "FaceRecognizer.java").read_text(encoding="utf-8")
for fragment in (
    "embedWithDiagnostics",
    "Collections.singleton(\"embedding\")",
    "block_stats",
    "stage_stats",
    "prehead",
):
    require(fragment in recognizer, f"FaceRecognizer missing {fragment}")

export = EXPORT.read_text(encoding="utf-8")
for fragment in (
    '"embedding": (1, 512)',
    '"block_stats": (18, 4)',
    '"stage_stats": (4, 4)',
    '"prehead": (1, 1284)',
    "DiagnosticWrapper",
    "feature_stats",
):
    require(fragment in export, f"exporter missing {fragment}")

overlay = OVERLAY.read_text(encoding="utf-8")
for label in ('"LE"', '"RE"', '"N"', '"ML"', '"MR"'):
    require(label in overlay, f"five-point overlay missing {label}")

alignment = (JAVA / "FaceAlignmentDiagnostics.java").read_text(encoding="utf-8")
for fragment in ("eyeDistancePx", "scale", "rotationDeg", "meanResidualPx", "maxResidualPx"):
    require(fragment in alignment, f"alignment diagnostics missing {fragment}")

cross = (JAVA / "CrossModelEnrollmentComparison.java").read_text(encoding="utf-8")
for fragment in ("meanPair", "minPair", "outlierIndex", "deltaXsVsS", "deltaMVsS"):
    require(fragment in cross, f"cross-model comparison missing {fragment}")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+6\b", build) is not None, "versionCode must be 6")
require("versionName '0.4.0'" in build, "versionName must be 0.4.0")
require("abiFilters 'arm64-v8a'" in build, "R4 handset artifact must remain arm64-v8a")

print("R4 UNIFIED MICROSCOPE CONTRACT PASS: geometry, model structure/internal diagnostics, cross-model enrollment relations, model linkage and version are wired")

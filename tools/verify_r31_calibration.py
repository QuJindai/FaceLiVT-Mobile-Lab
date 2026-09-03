#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/qujindai/facelivtlab"
MAIN = JAVA / "MainActivity.java"
BUILD = ROOT / "app/build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R3.1 CALIBRATION CONTRACT FAIL: " + message)


for filename in (
    "RecognitionDecision.java",
    "ThresholdCalibrator.java",
    "EnrollmentReferenceCodec.java",
    "R31CalibrationPanel.java",
    "ProbeEmbeddingView.java",
):
    require((JAVA / filename).exists(), f"missing {filename}")

quality = (JAVA / "FaceQuality.java").read_text(encoding="utf-8")
for fragment in ("passesProbeGate()", "passesEnrollmentGate()", "enrollmentGateReason()", "ENROLL_MIN_SHARPNESS"):
    require(fragment in quality, f"FaceQuality missing {fragment}")

enrollment = (JAVA / "EnrollmentSession.java").read_text(encoding="utf-8")
for fragment in ("MIN_COVERAGE", "embeddingCoverage", "poseCoverage", "isNovelCandidate"):
    require(fragment in enrollment, f"EnrollmentSession missing {fragment}")
require("maxCosine < NOVELTY_MAX_COSINE && maxPoseDelta >= NOVELTY_POSE_DELTA_DEG" in enrollment,
        "novelty must require embedding AND pose difference")

performance = (JAVA / "PerformanceWindow.java").read_text(encoding="utf-8")
for fragment in ("avgAlignMs()", "avgMatchMs()", "avgTotalMs()"):
    require(fragment in performance, f"PerformanceWindow missing {fragment}")

main = MAIN.read_text(encoding="utf-8")
for fragment in (
    "passesEnrollmentGate()",
    "isNovelCandidate(",
    "margin=N/A",
    "refreshCalibration(",
    "renderProbeProjection(",
    "30帧均值",
    "512D cosine",
    "Coverage",
):
    require(fragment in main, f"MainActivity missing {fragment}")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+4\b", build) is not None, "versionCode must be 4")
require("versionName '0.3.1'" in build, "versionName must be 0.3.1")

print("R3.1 CALIBRATION CONTRACT PASS: hard gates, coverage, N/A margin, empirical threshold, fixed-PCA probe and timing scopes are wired")

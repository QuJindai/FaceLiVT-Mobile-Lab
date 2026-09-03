#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
STORE = ROOT / "app/src/main/java/com/qujindai/facelivtlab/FaceStore.java"
LOGGER = ROOT / "app/src/main/java/com/qujindai/facelivtlab/SessionLogger.java"
BUILD = ROOT / "app/build.gradle"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R3 MICROSCOPE CONTRACT FAIL: " + message)


def id_name(value):
    return "" if not value else value.rsplit("/", 1)[-1]

root = ET.parse(LAYOUT).getroot()
nodes = {id_name(n.get(ANDROID + "id")): n for n in root.iter() if id_name(n.get(ANDROID + "id"))}

for required in (
    "pageEnrollment", "pageRecognition", "tabEnrollment", "tabRecognition",
    "txtEnrollmentLiveQuality", "txtEnrollmentArchive", "txtEnrollmentFormula",
    "similarityMatrix", "embeddingScatter", "txtRecognitionQuality", "txtPipeline",
    "topKChart", "trendChart", "txtRecognitionFormula", "faceOverlay",
    "sampleFace1", "sampleFace2", "sampleFace3", "sampleFace4", "sampleFace5",
):
    require(required in nodes, f"missing view {required}")

expected_tags = {
    "similarityMatrix": "com.qujindai.facelivtlab.SimilarityMatrixView",
    "embeddingScatter": "com.qujindai.facelivtlab.EmbeddingScatterView",
    "topKChart": "com.qujindai.facelivtlab.TopKBarView",
    "trendChart": "com.qujindai.facelivtlab.TrendChartView",
    "faceOverlay": "com.qujindai.facelivtlab.FaceOverlayView",
}
for view_id, tag in expected_tags.items():
    require(nodes[view_id].tag == tag, f"{view_id} must be {tag}")

main = MAIN.read_text(encoding="utf-8")
for fragment in (
    "enum Page", "showPage(Page.ENROLLMENT)", "showPage(Page.RECOGNITION)",
    "EnrollmentSession", "FaceQuality.evaluate", "renderEnrollmentMicroscope",
    "renderRecognitionMicroscope", "faceStore.topMatches", "RecognitionTrend",
    "Sstable", "margin =", "Qprobe",
):
    require(fragment in main, f"MainActivity missing {fragment}")

for filename in (
    "SimilarityMatrixView.java", "EmbeddingScatterView.java", "TopKBarView.java",
    "TrendChartView.java", "FaceOverlayView.java", "EnrollmentSession.java",
    "FaceQuality.java", "EmbeddingProjector.java", "RecognitionTrend.java",
):
    require((ROOT / "app/src/main/java/com/qujindai/facelivtlab" / filename).exists(), f"missing {filename}")

store = STORE.read_text(encoding="utf-8")
require("replaceTemplate(" in store, "FaceStore must support quality-weighted template replacement")
require("topMatches(" in store, "FaceStore must expose Top-K matching")

logger = LOGGER.read_text(encoding="utf-8")
for field in ("quality", "margin", "align_ms", "match_ms"):
    require(field in logger, f"CSV microscope missing {field}")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+3\b", build) is not None, "versionCode must be 3")
require("versionName '0.3.0'" in build, "versionName must be 0.3.0")

print("R3 MICROSCOPE CONTRACT PASS: two pages, quality/template microscope, Top-K/trend and formula chains are wired")

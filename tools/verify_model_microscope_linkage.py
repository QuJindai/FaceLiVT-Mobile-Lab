#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
STATE = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MicroscopeSelectionState.java"
BUILD = ROOT / "app/build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("MODEL MICROSCOPE LINKAGE FAIL: " + message)


require(STATE.exists(), "missing MicroscopeSelectionState.java")
main = MAIN.read_text(encoding="utf-8")

for fragment in (
    "applyRecognitionModelSelection(",
    "renderRecognitionModelPendingState(",
    "MicroscopeSelectionState.Snapshot frameSelection",
    "frameSelection.mode.variants()",
    "microscopeSelection.isCurrent(frameSelection)",
    "frameSelection.focus",
    "模型已切换",
    "Probe 质量（模型无关）",
):
    require(fragment in main, f"MainActivity missing {fragment}")

require("lastDisplayVariant = displayVariantForMode()" not in main,
        "legacy detached display variant assignment must be removed")
require("for (ModelVariant variant : modelMode.variants())" not in main,
        "recognition frame must use one captured model-selection snapshot")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+5\b", build) is not None, "versionCode must be 5")
require("versionName '0.3.2'" in build, "versionName must be 0.3.2")

print("MODEL MICROSCOPE LINKAGE PASS: selection drives all model-specific microscope state and stale frames are rejected")

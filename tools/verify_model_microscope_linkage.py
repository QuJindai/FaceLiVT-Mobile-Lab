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
version_code = re.search(r"versionCode\s+(\d+)\b", build)
version_name = re.search(r"versionName\s+'([^']+)'", build)
require(version_code is not None and int(version_code.group(1)) >= 5, "versionCode must remain at least R3.2")
require(version_name is not None and re.match(r"0\.(?:3|4)\.", version_name.group(1)) is not None,
        "versionName must remain R3.2-or-newer in current 0.x line")

print("MODEL MICROSCOPE LINKAGE PASS: selection drives all model-specific microscope state and stale frames are rejected")

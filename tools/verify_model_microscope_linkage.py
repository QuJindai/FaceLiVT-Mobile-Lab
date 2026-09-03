#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
STATE = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MicroscopeSelectionState.java"


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
):
    require(fragment in main, f"MainActivity missing {fragment}")

require("lastDisplayVariant = displayVariantForMode()" not in main,
        "legacy detached display variant assignment must be removed")
require("for (ModelVariant variant : modelMode.variants())" not in main,
        "recognition frame must use one captured model-selection snapshot")

print("MODEL MICROSCOPE LINKAGE PASS: selection drives all model-specific microscope state and stale frames are rejected")

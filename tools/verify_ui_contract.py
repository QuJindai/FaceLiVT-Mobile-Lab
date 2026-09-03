#!/usr/bin/env python3
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
STYLES = ROOT / "app/src/main/res/values/styles.xml"
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"

ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("UI CONTRACT FAIL: " + message)


def id_name(value: str | None) -> str:
    if not value:
        return ""
    return value.rsplit("/", 1)[-1]


def dp(value: str | None) -> float:
    if not value or not value.endswith("dp"):
        return -1
    return float(value[:-2])


layout_tree = ET.parse(LAYOUT)
root = layout_tree.getroot()
styles_text = STYLES.read_text(encoding="utf-8")
main_text = MAIN.read_text(encoding="utf-8")

require(id_name(root.get(ANDROID + "id")) == "root", "root view must have @+id/root for WindowInsets")

nodes = {id_name(node.get(ANDROID + "id")): node for node in root.iter() if id_name(node.get(ANDROID + "id"))}
for required_id in ("topOverlay", "bottomControls", "txtActionHint", "btnEnroll", "btnSwitch", "btnExport"):
    require(required_id in nodes, f"missing {required_id}")

for button_id in ("btnEnroll", "btnSwitch", "btnExport"):
    node = nodes[button_id]
    require(dp(node.get(ANDROID + "layout_height")) >= 52, f"{button_id} height must be at least 52dp")
    require(node.get("style") is not None, f"{button_id} must use an explicit readable button style")

for spinner_id in ("spinnerProfile", "spinnerModel"):
    require(nodes[spinner_id].get("style") is not None, f"{spinner_id} must use an explicit readable spinner style")

for style_name in (
    "Widget.FaceLiVTLab.Button.Primary",
    "Widget.FaceLiVTLab.Button.Secondary",
    "Widget.FaceLiVTLab.Spinner",
):
    require(f'name="{style_name}"' in styles_text, f"missing style {style_name}")

for code_fragment in (
    "WindowCompat.setDecorFitsSystemWindows(getWindow(), false)",
    "ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)",
    "WindowInsetsCompat.Type.systemBars()",
    "updateActionState()",
):
    require(code_fragment in main_text, f"MainActivity missing {code_fragment}")

require("btnExport.setEnabled(sessionLogger.size() > 0)" in main_text,
        "CSV button must expose a real enabled/disabled state")
require("btnEnroll.setEnabled(" in main_text,
        "enrollment button must expose a real enabled/disabled state")

print("UI CONTRACT PASS: safe area, readable controls, and explicit action states are present")

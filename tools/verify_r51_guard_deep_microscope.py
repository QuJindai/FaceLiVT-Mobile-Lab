#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
BUILD = ROOT / "app/build.gradle"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R5.1 GUARD DEEP MICROSCOPE FAIL: " + message)


main = MAIN.read_text(encoding="utf-8")
start = main.find("private void handleIdentityGuardProbe(")
helper_start = main.find("private float[] guardEmbeddingWithDeepDiagnostic(", start)
render_start = main.find("private void renderIdentityGuardPanel()", start)
end = helper_start if helper_start >= 0 else render_start
require(start >= 0 and end > start, "cannot isolate handleIdentityGuardProbe")
guard = main[start:end]

for fragment in (
    "ModelVariant guardFocus = inspectVariant;",
    "guardEmbeddingWithDeepDiagnostic(aligned, variant, guardFocus)",
    "private float[] guardEmbeddingWithDeepDiagnostic(",
    "RecognizerBank.TimedDiagnostic diagnostic = recognizerBank.diagnose(focus, aligned);",
    "latestDeepStats.put(focus, diagnostic.stats);",
    "renderDeepModelStats(focus, diagnostic.stats, true);",
):
    require(fragment in main, f"missing {fragment}")

require("guardEmbeddingWithDeepDiagnostic(aligned, variant, guardFocus)" in guard,
        "Guard path must source model evidence through focused diagnostic helper")
require("faceStore.topMatches(variant, embedding, 2)" in guard,
        "Guard matching must use the embedding returned by the diagnostic-aware helper")
require("recognizerBank.embed(variant, aligned)" not in guard,
        "Guard main loop must not bypass the diagnostic-aware helper with direct embed calls")

helper_start = main.find("private float[] guardEmbeddingWithDeepDiagnostic(")
helper_end = main.find("private void renderIdentityGuardPanel()", helper_start)
require(helper_start >= 0 and helper_end > helper_start, "cannot isolate Guard diagnostic helper")
helper = main[helper_start:helper_end]
require("variant != focus" in helper, "non-focused variants must stay on lightweight embed path")
require("now - lastDeepDiagnosticMs < 1000L" in helper,
        "focused Guard diagnostics must be throttled to protect handset thermals")
require("return diagnostic.embedding;" in helper,
        "focused diagnostic embedding must be reused for Guard matching instead of duplicated inference")

build = BUILD.read_text(encoding="utf-8")
require(re.search(r"versionCode\s+8\b", build) is not None, "versionCode must be 8")
require("versionName '0.5.1'" in build, "versionName must be 0.5.1")

print("R5.1 GUARD DEEP MICROSCOPE PASS: Guard live frames feed the focused enrollment 18-block microscope without duplicate focused inference")

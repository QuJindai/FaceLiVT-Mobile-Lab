#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("R5.2 GUARD DEEP STABILITY FAIL: " + message)


main = MAIN.read_text(encoding="utf-8")

start = main.find("private void handleIdentityGuardProbe(")
helper_start = main.find("private float[] guardEmbeddingWithDeepDiagnostic(", start)
require(start >= 0 and helper_start > start, "cannot isolate Guard probe handler")
guard = main[start:helper_start]

require("applyExistingGuardSnapshot(snapshot);" in guard,
        "EXISTING Guard frames must use an idempotent transition helper")
require("enterExistingIdentity(snapshot.candidateIdentity, false);" not in guard,
        "Guard loop must not reload the same existing identity on every frame")

transition_start = main.find("private void applyExistingGuardSnapshot(")
transition_end = main.find("private float[] guardEmbeddingWithDeepDiagnostic(", transition_start)
require(transition_start >= 0 and transition_end > transition_start,
        "missing existing-identity transition helper")
transition = main[transition_start:transition_end]

for fragment in (
    "String id = snapshot.candidateIdentity.trim();",
    "if (!id.equals(existingIdentityContext))",
    "enterExistingIdentity(id, false);",
    "历史已载入",
):
    require(fragment in transition, f"transition helper missing {fragment}")

# Intentional history navigation may still clear live deep stats because history and live frames
# are different evidence sources. The regression fix must stop repeated reload at the source,
# rather than preserving stale deep data across a real history change.
history_start = main.find("private void loadHistoryVersion(")
history_end = main.find("private void", history_start + 1)
require(history_start >= 0 and history_end > history_start, "cannot isolate loadHistoryVersion")
history = main[history_start:history_end]
require("latestDeepStats.clear();" in history,
        "real history-version changes should still invalidate live deep stats")
require("enrollmentModelMicroscope.clearStats(" in history,
        "real history-version changes should still clear the live Block microscope")

print("R5.2 GUARD DEEP STABILITY PASS: repeated EXISTING frames no longer reload history or clear live 18-block diagnostics")

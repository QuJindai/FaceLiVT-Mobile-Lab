#!/usr/bin/env python3
from pathlib import Path

# One-shot product patch; removed by the patch workflow after GREEN verification.
path = Path("app/src/main/java/com/qujindai/facelivtlab/MainActivity.java")
text = path.read_text(encoding="utf-8")

old_focus = """        long requestGeneration = identityGuard.captureGeneration();\n        boolean valid = quality != null && quality.passesProbeGate();\n"""
new_focus = """        long requestGeneration = identityGuard.captureGeneration();\n        ModelVariant guardFocus = inspectVariant;\n        boolean valid = quality != null && quality.passesProbeGate();\n"""
if old_focus not in text:
    raise SystemExit("guard focus insertion point not found")
text = text.replace(old_focus, new_focus, 1)

old_embed = """                RecognizerBank.TimedEmbedding embedding = recognizerBank.embed(variant, aligned);\n                List<FaceStore.Match> top = faceStore.topMatches(variant, embedding.embedding, 2);\n"""
new_embed = """                float[] embedding = guardEmbeddingWithDeepDiagnostic(aligned, variant, guardFocus);\n                List<FaceStore.Match> top = faceStore.topMatches(variant, embedding, 2);\n"""
if old_embed not in text:
    raise SystemExit("Guard direct embed block not found")
text = text.replace(old_embed, new_embed, 1)

marker = """    private void renderIdentityGuardPanel() {\n"""
helper = """    private float[] guardEmbeddingWithDeepDiagnostic(Bitmap aligned, ModelVariant variant, ModelVariant focus) throws Exception {\n        if (variant != focus) return recognizerBank.embed(variant, aligned).embedding;\n\n        DeepModelStats cached = latestDeepStats.get(focus);\n        long now = SystemClock.elapsedRealtime();\n        if (cached != null && now - lastDeepDiagnosticMs < 1000L) {\n            return recognizerBank.embed(variant, aligned).embedding;\n        }\n\n        lastDeepDiagnosticMs = now;\n        try {\n            RecognizerBank.TimedDiagnostic diagnostic = recognizerBank.diagnose(focus, aligned);\n            latestDeepStats.put(focus, diagnostic.stats);\n            runOnUiThread(() -> {\n                if (currentPage != Page.ENROLLMENT || inspectVariant != focus || enrollmentRemaining.get() > 0) return;\n                renderDeepModelStats(focus, diagnostic.stats, true);\n            });\n            return diagnostic.embedding;\n        } catch (Exception e) {\n            return recognizerBank.embed(variant, aligned).embedding;\n        }\n    }\n\n"""
if marker not in text:
    raise SystemExit("renderIdentityGuardPanel marker not found")
text = text.replace(marker, helper + marker, 1)

path.write_text(text, encoding="utf-8")

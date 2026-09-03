#!/usr/bin/env python3
from pathlib import Path

path = Path("app/src/main/java/com/qujindai/facelivtlab/MainActivity.java")
text = path.read_text(encoding="utf-8")

old = """            if (snapshot.state == IdentityGuardEngine.State.EXISTING && !snapshot.candidateIdentity.isEmpty()) {\n                enterExistingIdentity(snapshot.candidateIdentity, false);\n            } else {\n"""
new = """            if (snapshot.state == IdentityGuardEngine.State.EXISTING && !snapshot.candidateIdentity.isEmpty()) {\n                applyExistingGuardSnapshot(snapshot);\n            } else {\n"""
if old not in text:
    raise SystemExit("EXISTING Guard reload block not found")
text = text.replace(old, new, 1)

marker = """    private float[] guardEmbeddingWithDeepDiagnostic(Bitmap aligned, ModelVariant variant, ModelVariant focus) throws Exception {\n"""
helper = """    private void applyExistingGuardSnapshot(IdentityGuardEngine.Snapshot snapshot) {\n        if (snapshot == null || snapshot.candidateIdentity == null) return;\n        String id = snapshot.candidateIdentity.trim();\n        if (id.isEmpty()) return;\n        if (!id.equals(existingIdentityContext)) {\n            enterExistingIdentity(id, false);\n            return;\n        }\n        txtResult.setText(\"EXISTING · 已确认历史身份 · \" + id + \" · 历史已载入，实时深层显微镜继续\");\n        renderIdentityGuardPanel();\n        updateActionState();\n    }\n\n"""
if marker not in text:
    raise SystemExit("Guard deep helper marker not found")
text = text.replace(marker, helper + marker, 1)
path.write_text(text, encoding="utf-8")

#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")
start = '''        int version = historyStore.nextVersion(name);
        boolean append = enrollmentIntent == EnrollmentIntent.APPEND;
'''
end = '''        completedEnrollmentSession = enrollmentSession;
'''
si = text.find(start)
if si < 0:
    raise SystemExit("R5 ATOMIC PATCH FAIL: start anchor missing")
ei = text.find(end, si)
if ei < 0:
    raise SystemExit("R5 ATOMIC PATCH FAIL: end anchor missing")

replacement = '''        int version = historyStore.nextVersion(name);
        boolean append = enrollmentIntent == EnrollmentIntent.APPEND;
        EnrollmentCommitPlan.Plan commitPlan;
        EnrollmentHistoryRecord history;
        List<byte[]> thumbnailBytes;
        try {
            // All XS/S/M fusion/template validation MUST finish before immutable Vn is published.
            commitPlan = EnrollmentCommitPlan.build(enrollmentSession, append,
                    appendOldCentroids, appendOldEffectiveSamples);
            history = EnrollmentHistoryRecord.fromSession(name, version,
                    System.currentTimeMillis(), enrollmentProfileAtStart,
                    commitPlan.effectiveSamplesBefore, commitPlan.effectiveSamplesAfter,
                    enrollmentSession, enrollmentGeometries);
            thumbnailBytes = encodeEnrollmentThumbnails();
            historyStore.saveVersion(history, thumbnailBytes);
        } catch (RuntimeException e) {
            enrollmentName = null;
            EnrollmentIntent failedIntent = enrollmentIntent;
            enrollmentIntent = EnrollmentIntent.NONE;
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                txtResult.setText("R5 提交前验证/历史写入失败 · 活动模板未更新 · " + failedIntent + " · " + safeMessage(e));
                if (append && !existingIdentityContext.isEmpty()) {
                    enterExistingIdentity(existingIdentityContext, false);
                } else {
                    resetIdentityGuardContext("R5 提交失败，模板保持原状");
                }
                updateActionState();
            });
            return;
        }

        archive.append("\\n学习版本：V").append(version).append(" · 五张 112×112 对齐脸已写入 app 私有历史\\n");
        try {
            for (ModelVariant variant : ModelVariant.values()) {
                EnrollmentCommitPlan.ActiveTemplate active = commitPlan.templates.get(variant);
                if (active == null) throw new IllegalStateException("missing preflight template " + variant.storageKey);
                if (active.appended) {
                    archive.append(String.format(Locale.US,
                            "%s append: wOld=%d · wNew=%d · effective=%d · cos(cOld,cActiveNew)=%.4f\\n",
                            variant.storageKey, active.oldWeight, active.newWeight,
                            active.effectiveSamples, active.driftCosine));
                }
                faceStore.replaceTemplate(name, variant, active.centroid, active.effectiveSamples);
                saveActiveReference(name, variant, enrollmentSession.embeddings(variant), active.centroid, append);
            }
        } catch (RuntimeException e) {
            // The immutable version was published only after all computational validation passed.
            // If local persistence itself fails, remove the just-published version so history never claims success.
            historyStore.deleteVersion(name, version);
            enrollmentName = null;
            EnrollmentIntent failedIntent = enrollmentIntent;
            enrollmentIntent = EnrollmentIntent.NONE;
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                txtResult.setText("R5 活动模板写入失败 · 已撤销 V" + version + " · " + failedIntent + " · " + safeMessage(e));
                resetIdentityGuardContext("活动模板写入失败，已撤销历史版本");
                updateActionState();
            });
            return;
        }
        archive.append(append
                ? "\\n结论：PASS · 新版本已保存，活动模板完成保守融合；旧版本保持不可变"
                : "\\n结论：PASS · 新版本与三模型活动模板已入库");
        archiveStore.save(name, archive.toString());

'''
new_text = text[:si] + replacement + text[ei:]
if new_text.count("EnrollmentCommitPlan.build(") != 1:
    raise SystemExit("R5 ATOMIC PATCH FAIL: expected exactly one commit plan build")
if new_text.find("EnrollmentCommitPlan.build(") > new_text.find("historyStore.saveVersion("):
    raise SystemExit("R5 ATOMIC PATCH FAIL: preflight must precede history publish")
MAIN.write_text(new_text, encoding="utf-8")
print("R5 atomic preflight patch applied")

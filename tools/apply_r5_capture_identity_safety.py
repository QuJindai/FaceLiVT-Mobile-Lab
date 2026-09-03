#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"R5 CAPTURE SAFETY PATCH FAIL {label}: expected 1 anchor, got {count}")
    return text.replace(old, new, 1)


def between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    si = text.find(start)
    if si < 0:
        raise SystemExit(f"R5 CAPTURE SAFETY PATCH FAIL {label}: start missing")
    ei = text.find(end, si + len(start))
    if ei < 0:
        raise SystemExit(f"R5 CAPTURE SAFETY PATCH FAIL {label}: end missing")
    return text[:si] + replacement + text[ei:]


text = MAIN.read_text(encoding="utf-8")

# Pass the detector tracking context into active enrollment so a duplicate abort can seed the Guard correctly.
text = once(text,
'''                    handleEnrollment(aligned, quality, geometry, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
''',
'''                    handleEnrollment(aligned, trackingId, quality, geometry, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
''',
"handleEnrollment call")

handle = '''    private void handleEnrollment(Bitmap aligned, int trackingId,
                                  FaceQuality.Snapshot quality, AlignmentGeometry geometry,
                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                  DegradationProfile active, int faceW, int faceH,
                                  long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        String name = enrollmentName;
        EnrollmentIntent intent = enrollmentIntent;
        if (!quality.passesEnrollmentGate()) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 入库硬门 FAIL\\n" + quality.enrollmentGateReason());
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms\\n" + thermalLine(thermal));
            });
            return;
        }

        EnumMap<ModelVariant, RecognizerBank.TimedDiagnostic> all = new EnumMap<>(ModelVariant.class);
        EnumMap<ModelVariant, float[]> candidateEmbeddings = new EnumMap<>(ModelVariant.class);
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> identityEvidence = new EnumMap<>(ModelVariant.class);
        LinkedHashSet<String> candidateNames = new LinkedHashSet<>();
        for (ModelVariant variant : ModelVariant.values()) {
            RecognizerBank.TimedDiagnostic diagnostic = recognizerBank.diagnose(variant, aligned);
            all.put(variant, diagnostic);
            candidateEmbeddings.put(variant, diagnostic.embedding);
            List<FaceStore.Match> top = faceStore.topMatches(variant, diagnostic.embedding, 2);
            FaceStore.Match one = top.size() > 0 ? top.get(0) : null;
            FaceStore.Match two = top.size() > 1 ? top.get(1) : null;
            identityEvidence.put(variant, new IdentityGuardEngine.ModelEvidence(
                    one == null ? "" : one.name, one == null ? Float.NaN : one.similarity,
                    two == null ? "" : two.name, two == null ? Float.NaN : two.similarity,
                    two != null));
            if (one != null && one.name != null && !one.name.isEmpty()) candidateNames.add(one.name);
        }
        EnumMap<ModelVariant, Float> empiricalThresholds = guardEmpiricalThresholds();

        if (intent == EnrollmentIntent.APPEND) {
            EnrollmentIdentityLock.Result lock = EnrollmentIdentityLock.forAppend(
                    name, faceStore.identityCount(), threshold, empiricalThresholds, identityEvidence);
            if (!lock.allowed) {
                runOnUiThread(() -> {
                    txtResult.setText("本帧未计入 · 追加身份锁 FAIL\\n" + lock.reason);
                    txtPerf.setText("身份锁 WAIT · 请让 " + name + " 回到镜头\\n" + thermalLine(thermal));
                });
                return;
            }
        } else if (intent == EnrollmentIntent.NEW || intent == EnrollmentIntent.REPLACE_AFTER_DELETE) {
            EnrollmentIdentityLock.Result lock = EnrollmentIdentityLock.forNewIdentity(
                    threshold, empiricalThresholds, identityEvidence);
            if (!lock.allowed) {
                abortEnrollmentToGuard(lock, identityEvidence, empiricalThresholds,
                        candidateNames, trackingId, geometry);
                return;
            }
        }

        if (!enrollmentSession.isSameSubjectCandidate(candidateEmbeddings)) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 五帧同人连续性 FAIL\\n疑似换人；已收样本不变，请让同一人回到镜头");
                txtPerf.setText("Same-subject 2/3 模型门 WAIT\\n" + thermalLine(thermal));
            });
            return;
        }

        RecognizerBank.TimedDiagnostic sEmbedding = all.get(ModelVariant.S);
        if (sEmbedding == null || !enrollmentSession.isNovelCandidate(ModelVariant.S, sEmbedding.embedding, quality)) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 与已采样帧过于重复\\n请轻微左右转头/抬低头，让模板获得覆盖性");
                txtPerf.setText("身份锁 PASS · 同人连续性 PASS · 差异门 WAIT\\n" + thermalLine(thermal));
            });
            return;
        }

        StringBuilder timings = new StringBuilder();
        for (ModelVariant variant : ModelVariant.values()) {
            RecognizerBank.TimedDiagnostic te = all.get(variant);
            enrollmentSession.add(variant, te.embedding, quality);
            latestDeepStats.put(variant, te.stats);
            long total = detectMs + alignMs + te.inferMs;
            performance.get(variant).add(detectMs, alignMs, te.inferMs, 0L, total);
            if (timings.length() > 0) timings.append(" | ");
            timings.append(variant.storageKey).append(' ').append(te.inferMs).append("ms");
        }

        enrollmentGeometries.add(geometry);
        Bitmap thumb = aligned.copy(Bitmap.Config.ARGB_8888, false);
        enrollmentThumbnails.add(thumb);
        int acceptedCount = enrollmentSession.size(ModelVariant.S);
        int left = Math.max(0, ENROLLMENT_SAMPLES - acceptedCount);
        enrollmentRemaining.set(left);
        int index = acceptedCount - 1;
        runOnUiThread(() -> {
            if (index >= 0 && index < sampleFaces.length) sampleFaces[index].setImageBitmap(thumb);
            txtResult.setText(left > 0
                    ? ("已收同人合格差异帧 " + acceptedCount + "/5 · 还需 " + left + " 帧")
                    : "5 张同人合格差异帧完成 · 正在构建三模型学习版本");
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
            txtPerf.setText("身份锁 PASS · 同人连续性 PASS · detect " + detectMs + " / align " + alignMs + "ms | diag " + timings + "\\n" + thermalLine(thermal));
            renderDeepModelStats(inspectVariant, latestDeepStats.get(inspectVariant), true);
            updateActionState();
        });
        if (left == 0) finalizeEnrollment(name);
    }

    private void abortEnrollmentToGuard(EnrollmentIdentityLock.Result lock,
                                        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> identityEvidence,
                                        EnumMap<ModelVariant, Float> empiricalThresholds,
                                        LinkedHashSet<String> candidateNames,
                                        int trackingId, AlignmentGeometry geometry) {
        String abortedName = enrollmentName == null ? "" : enrollmentName;
        enrollmentSession.clear();
        enrollmentGeometries.clear();
        enrollmentThumbnails.clear();
        enrollmentName = null;
        enrollmentRemaining.set(0);
        enrollmentIntent = EnrollmentIntent.NONE;
        appendOldCentroids.clear();
        appendOldEffectiveSamples.clear();

        identityGuard.reset();
        boolean fullGeometry = geometry != null && !geometry.usedFallback && geometry.landmarkCount == 5;
        IdentityGuardEngine.Snapshot snapshot = identityGuard.push(new IdentityGuardEngine.FrameInput(
                faceStore.identityCount(), trackingId, true, fullGeometry,
                threshold, empiricalThresholds, identityEvidence));
        guardGeneration = snapshot.generation;
        guardTrackingId = trackingId;
        lastGuardCandidates = new ArrayList<>(candidateNames);

        runOnUiThread(() -> {
            if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
            editName.setEnabled(true);
            for (ImageView image : sampleFaces) image.setImageDrawable(null);
            similarityMatrix.setMatrix(null);
            embeddingScatter.setProjection(null, 0);
            if (modelComparisonView != null) modelComparisonView.setRows(null);
            if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", null);
            if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", null);
            if (txtHistoryStrip != null) txtHistoryStrip.setText("录入已中止 · Identity Guard 重新接管");
            txtResult.setText("R5 已中止 " + abortedName + " 的新身份录入\\n" + lock.reason);
            txtEnrollmentArchive.setText("本轮没有写入历史或活动模板。Identity Guard 已用当前人脸重新建立疑似重复证据。");
            renderIdentityGuardPanel();
            updateActionState();
        });
    }

'''
text = between(text, "    private void handleEnrollment(", "    private void finalizeEnrollment(", handle, "handleEnrollment")

# Failed append must not overwrite the successful archive. Route all failure policy through one explicit helper.
old_fail = '''        if (!passAll) {
            archive.append("\\n结论：FAIL · 本轮不建立历史版本，也不覆盖/融合已有模板");
            archiveStore.save(name, archive.toString());
            completedEnrollmentSession = enrollmentSession;
            enrollmentName = null;
            enrollmentIntent = EnrollmentIntent.NONE;
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                txtEnrollmentArchive.setText(archive.toString());
                txtResult.setText("录入未达门槛 · 历史与活动模板均未更新");
                renderEnrollmentMicroscope(inspectVariant);
                renderEnrollmentComparison();
                resetIdentityGuardContext("失败录入结束，重新进行身份防重");
                updateActionState();
            });
            return;
        }
'''
new_fail = '''        if (!passAll) {
            archive.append("\\n结论：FAIL · 本轮不建立历史版本，也不覆盖/融合已有模板");
            handleFailedEnrollment(name, archive.toString(), enrollmentIntent);
            return;
        }
'''
text = once(text, old_fail, new_fail, "failed enrollment branch")

helper_anchor = "    private void handleIdentityGuardProbe("
failed_helper = '''    private void handleFailedEnrollment(String name, String failedArchive, EnrollmentIntent failedIntent) {
        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        enrollmentRemaining.set(0);
        enrollmentIntent = EnrollmentIntent.NONE;
        appendOldCentroids.clear();
        appendOldEffectiveSamples.clear();

        if (failedIntent == EnrollmentIntent.APPEND) {
            // Critical lifecycle rule: a failed append is transient evidence only.
            // Never replace the existing successful human-readable archive/reference/history.
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                enterExistingIdentity(name, false);
                txtResult.setText("追加学习未达门槛 · 原活动模板、历史版本和成功档案均保持不变");
                updateActionState();
            });
        } else {
            archiveStore.save(name, failedArchive);
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                txtEnrollmentArchive.setText(failedArchive);
                txtResult.setText("录入未达门槛 · 历史与活动模板均未更新");
                renderEnrollmentMicroscope(inspectVariant);
                renderEnrollmentComparison();
                resetIdentityGuardContext("失败录入结束，重新进行身份防重");
                updateActionState();
            });
        }
    }

'''
text = once(text, helper_anchor, failed_helper + helper_anchor, "failed enrollment helper")

MAIN.write_text(text, encoding="utf-8")
print("R5 in-capture identity safety patch applied")

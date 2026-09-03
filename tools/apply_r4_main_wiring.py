#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")
original = text


def replace_once(old: str, new: str, label: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"R4 WIRING PATCH FAIL [{label}]: source anchor not found")
    text = text.replace(old, new, 1)


def regex_once(pattern: str, replacement: str, label: str) -> None:
    global text
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"R4 WIRING PATCH FAIL [{label}]: matched {count}")
    text = updated


replace_once(
'''    private R31CalibrationPanel calibrationPanel;
    private ProbeEmbeddingView probeEmbeddingView;
    private TextView txtProbeEmbeddingInfo;
''',
'''    private R31CalibrationPanel calibrationPanel;
    private ProbeEmbeddingView probeEmbeddingView;
    private TextView txtProbeEmbeddingInfo;

    private ModelArchitectureView enrollmentArchitectureView;
    private ModelArchitectureView recognitionArchitectureView;
    private CrossModelComparisonView crossModelComparisonView;
    private AlignmentGeometryView alignmentGeometryView;
    private BlockDiagnosticsView blockDiagnosticsView;
''',
"fields")

replace_once(
'''        setupUi();
        installR31Panels();
        compactCameraStage();
''',
'''        setupUi();
        installR31Panels();
        installR4Panels();
        compactCameraStage();
''',
"install panels")

replace_once(
'''        txtResult.setText("R3.1 已就绪 · 稳定性与覆盖性分离");
''',
'''        txtResult.setText("R4 已就绪 · 几何 + 向量 + 18 Block 统一显微镜");
''',
"ready copy")

replace_once(
'''        if (txtProbeEmbeddingInfo != null) {
            txtProbeEmbeddingInfo.setText("显微镜焦点 " + focus.storageKey + " · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。");
        }
    }
''',
'''        if (txtProbeEmbeddingInfo != null) {
            txtProbeEmbeddingInfo.setText("显微镜焦点 " + focus.storageKey + " · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。");
        }
        if (recognitionArchitectureView != null) {
            recognitionArchitectureView.setVariant(ModelArchitectureSpec.forVariant(focus));
        }
        if (blockDiagnosticsView != null) {
            blockDiagnosticsView.clearForVariant(ModelArchitectureSpec.forVariant(focus));
        }
    }
''',
"pending model views")

install_r4 = r'''
    private void installR4Panels() {
        View top = findViewById(R.id.topOverlay);
        if (top instanceof ViewGroup && ((ViewGroup) top).getChildCount() > 0) {
            View row = ((ViewGroup) top).getChildAt(0);
            if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0) {
                View title = ((ViewGroup) row).getChildAt(0);
                if (title instanceof TextView) ((TextView) title).setText("FaceLiVT R4 · 人脸显微镜");
            }
        }

        enrollmentArchitectureView = new ModelArchitectureView(this);
        recognitionArchitectureView = new ModelArchitectureView(this);
        crossModelComparisonView = new CrossModelComparisonView(this);
        alignmentGeometryView = new AlignmentGeometryView(this);
        blockDiagnosticsView = new BlockDiagnosticsView(this);

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = dp(7);

        if (pageEnrollment instanceof ScrollView) {
            View child = ((ScrollView) pageEnrollment).getChildAt(0);
            if (child instanceof LinearLayout) {
                LinearLayout content = (LinearLayout) child;
                View inspectRow = spinnerEnrollmentInspectModel.getParent() instanceof View
                        ? (View) spinnerEnrollmentInspectModel.getParent() : null;
                int inspectIndex = inspectRow == null ? -1 : content.indexOfChild(inspectRow);
                content.addView(enrollmentArchitectureView,
                        inspectIndex >= 0 ? inspectIndex + 1 : content.getChildCount(), panelLp);

                int scatterIndex = content.indexOfChild(embeddingScatter);
                LinearLayout.LayoutParams crossLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                crossLp.topMargin = dp(8);
                content.addView(crossModelComparisonView,
                        scatterIndex >= 0 ? scatterIndex + 1 : content.getChildCount(), crossLp);
            }
        }

        if (pageRecognition instanceof ScrollView) {
            View child = ((ScrollView) pageRecognition).getChildAt(0);
            if (child instanceof LinearLayout) {
                LinearLayout content = (LinearLayout) child;
                int thresholdIndex = content.indexOfChild(seekThreshold);
                content.addView(recognitionArchitectureView,
                        thresholdIndex >= 0 ? thresholdIndex + 1 : 0, panelLp);

                View qualityRow = imgAlignedProbe.getParent() instanceof View
                        ? (View) imgAlignedProbe.getParent() : null;
                int qualityIndex = qualityRow == null ? -1 : content.indexOfChild(qualityRow);
                LinearLayout.LayoutParams geometryLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                geometryLp.topMargin = dp(7);
                content.addView(alignmentGeometryView,
                        qualityIndex >= 0 ? qualityIndex + 1 : content.getChildCount(), geometryLp);

                int pipelineIndex = content.indexOfChild(txtPipeline);
                LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blockLp.topMargin = dp(7);
                content.addView(blockDiagnosticsView,
                        pipelineIndex >= 0 ? pipelineIndex + 1 : content.getChildCount(), blockLp);
            }
        }

        enrollmentArchitectureView.setVariant(ModelArchitectureSpec.forVariant(inspectVariant));
        ModelVariant focus = microscopeSelection.snapshot().focus;
        recognitionArchitectureView.setVariant(ModelArchitectureSpec.forVariant(focus));
        blockDiagnosticsView.clearForVariant(ModelArchitectureSpec.forVariant(focus));
        crossModelComparisonView.clear();
        alignmentGeometryView.clear();
    }

'''
replace_once(
'''    private void compactCameraStage() {
''',
install_r4 + '''    private void compactCameraStage() {
''',
"installR4Panels method")

replace_once(
'''        similarityMatrix.setMatrix(null);
        embeddingScatter.setProjection(null, 0);
''',
'''        similarityMatrix.setMatrix(null);
        embeddingScatter.setProjection(null, 0);
        if (crossModelComparisonView != null) crossModelComparisonView.clear();
''',
"clear enrollment comparison")

replace_once(
'''        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R3.1 质量档案。\\n先过像素硬门，再筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。\\n本轮档位：" + enrollmentProfileAtStart);
''',
'''        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R4 质量档案。\\n5 landmarks ≠ 5 samples ≠ 18 blocks ≠ 512 dims。\\n先过像素硬门，再筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。\\n本轮档位：" + enrollmentProfileAtStart);
''',
"enrollment copy")

replace_once(
'''        txtResult.setText("R3.1 录入 · 等待第 1 张合格差异帧");
''',
'''        txtResult.setText("R4 录入 · 等待第 1 张合格差异帧");
''',
"enrollment result copy")

replace_once(
'''            txtResult.setText("R3.1 显微镜 CSV 已导出 · " + sessionLogger.size() + " 条\\n" + file.getAbsolutePath());
''',
'''            txtResult.setText("R4 显微镜 CSV 已导出 · " + sessionLogger.size() + " 条\\n" + file.getAbsolutePath());
''',
"export copy")

replace_once(
'''                } else {
                    txtRecognitionQuality.setText("Probe 质量：未检测到人脸");
                    faceOverlay.clear();
                    txtPipeline.setText("frame → degrade → detect " + detectMs + "ms → 无人脸，链路在检测阶段停止");
                }
''',
'''                } else {
                    txtRecognitionQuality.setText("Probe 质量：未检测到人脸");
                    faceOverlay.clear();
                    if (alignmentGeometryView != null) alignmentGeometryView.clear();
                    txtPipeline.setText("frame → degrade → detect " + detectMs + "ms → 无人脸，链路在检测阶段停止");
                }
''',
"clear geometry on no face")

replace_once(
'''            long alignStart = SystemClock.elapsedRealtimeNanos();
            Bitmap aligned = FaceAligner.align(detectorBitmap, face);
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());
''',
'''            long alignStart = SystemClock.elapsedRealtimeNanos();
            FaceAligner.AlignmentResult alignment = FaceAligner.alignWithDiagnostics(detectorBitmap, face);
            Bitmap aligned = alignment.aligned;
            FaceAlignmentDiagnostics geometry = alignment.diagnostics;
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());
            runOnUiThread(() -> {
                if (alignmentGeometryView != null) alignmentGeometryView.setData(geometry);
            });
''',
"alignment diagnostics")

replace_once(
'''        archive.append("\\n模型模板质量：稳定性 ≠ 覆盖性\\n");
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
''',
'''        archive.append("\\n模型模板质量：稳定性 ≠ 覆盖性\\n");
        EnumMap<ModelVariant, EnrollmentSession.Summary> r4Summaries = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
            r4Summaries.put(variant, summary);
''',
"collect summaries")

replace_once(
'''        archiveStore.save(name, archive.toString());
        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        final boolean finalPassAll = passAll;
''',
'''        CrossModelEnrollmentComparison r4Comparison = CrossModelEnrollmentComparison.from(r4Summaries);
        archiveStore.save(name, archive.toString());
        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        final boolean finalPassAll = passAll;
''',
"build cross model comparison")

replace_once(
'''            txtEnrollmentArchive.setText(archive.toString());
            txtResult.setText(finalPassAll ? "R3.1 录入完成 · " + name + " · 模板已入库" : "录入未达门槛 · 模板未覆盖");
            renderEnrollmentMicroscope(inspectVariant);
''',
'''            txtEnrollmentArchive.setText(archive.toString());
            txtResult.setText(finalPassAll ? "R4 录入完成 · " + name + " · 模板已入库" : "录入未达门槛 · 模板未覆盖");
            if (crossModelComparisonView != null) crossModelComparisonView.setData(r4Comparison);
            renderEnrollmentMicroscope(inspectVariant);
''',
"publish cross model comparison")

replace_once(
'''    private void renderEnrollmentMicroscope(ModelVariant variant) {
        EnrollmentSession session = completedEnrollmentSession;
''',
'''    private void renderEnrollmentMicroscope(ModelVariant variant) {
        if (enrollmentArchitectureView != null && variant != null) {
            enrollmentArchitectureView.setVariant(ModelArchitectureSpec.forVariant(variant));
        }
        EnrollmentSession session = completedEnrollmentSession;
''',
"enrollment architecture linkage")

replace_once(
'''                "公式链 · %s\\n" +
                "HardGate_i = Qsharp≥.28 ∧ Qlight≥.28 ∧ Qcontrast≥.25 ∧ Qpose≥.55 ∧ Qlandmark≥.80 ∧ Qsize≥.45\\n" +
''',
'''                "公式链 · %s\\n" +
                "5 samples → 5×5 relation matrix；matrix 阶数跟样本数走，不是模型深度。\\n" +
                "HardGate_i = Qsharp≥.28 ∧ Qlight≥.28 ∧ Qcontrast≥.25 ∧ Qpose≥.55 ∧ Qlandmark≥.80 ∧ Qsize≥.45\\n" +
''',
"enrollment matrix explanation")

replace_once(
'''        float[] displayFusedEmbedding = null;
        long displayInfer = 0L, displayMatch = 0L, displayTotal = 0L;
''',
'''        float[] displayFusedEmbedding = null;
        ModelDiagnostics displayDiagnostics = null;
        long displayInfer = 0L, displayMatch = 0L, displayTotal = 0L;
''',
"diagnostic variable")

new_loop = '''        for (ModelVariant variant : frameSelection.mode.variants()) {
            float[] frameEmbedding;
            long inferMs;
            ModelDiagnostics diagnostics = null;
            if (variant == displayVariant) {
                RecognizerBank.TimedDiagnostics td = recognizerBank.diagnose(variant, aligned);
                frameEmbedding = td.embedding;
                inferMs = td.inferMs;
                diagnostics = td.diagnostics;
            } else {
                RecognizerBank.TimedEmbedding te = recognizerBank.embed(variant, aligned);
                frameEmbedding = te.embedding;
                inferMs = te.inferMs;
            }
            float[] fusedEmbedding = fusion.get(variant).push(trackingId, frameEmbedding);
            int fusedFrames = fusion.get(variant).size();
            long matchStart = SystemClock.elapsedRealtimeNanos();
            List<FaceStore.Match> top = faceStore.topMatches(variant, fusedEmbedding, 3);
            long matchMs = elapsedMs(matchStart);
            RecognitionDecision decision = RecognitionDecision.from(top, threshold, quality);
            float similarity = Float.isFinite(decision.top1Score) ? decision.top1Score : 0f;
            long totalMs = detectMs + alignMs + inferMs + matchMs;
            performance.get(variant).add(detectMs, alignMs, inferMs, matchMs, totalMs);
            sessionLogger.addMicroscope(timestamp, active.label, variant,
                    sourceW, sourceH, degraded.getWidth(), degraded.getHeight(),
                    detectorBitmap.getWidth(), detectorBitmap.getHeight(), faceW, faceH,
                    quality, decision.top1Name, decision.top2Name,
                    similarity, decision.margin, threshold, decision.accepted, fusedFrames,
                    detectMs, alignMs, inferMs, matchMs, totalMs, thermal);

            if (results.length() > 0) results.append('\\n');
            results.append(variant.storageKey).append("  ");
            if (decision.top1Name.isEmpty()) {
                results.append("无模板");
            } else {
                results.append(String.format(Locale.US, "%s %.3f · margin %s · %s [%df]",
                        decision.accepted ? decision.top1Name : "UNKNOWN",
                        decision.top1Score, decision.marginLabel(),
                        decision.accepted ? "ACCEPT" : "REJECT", fusedFrames));
            }

            if (variant == displayVariant) {
                displayTop = top;
                displayDecision = decision;
                displayFusedEmbedding = fusedEmbedding.clone();
                displayDiagnostics = diagnostics;
                displayInfer = inferMs;
                displayMatch = matchMs;
                displayTotal = totalMs;
                displayFusedFrames = fusedFrames;
            }
        }

        List<FaceStore.Match> finalTop = displayTop;'''
regex_once(
    r'''        for \(ModelVariant variant : frameSelection\.mode\.variants\(\)\) \{.*?\n        \}\n\n        List<FaceStore\.Match> finalTop = displayTop;''',
    new_loop,
    "focused diagnostic inference")

replace_once(
'''        float[] finalFusedEmbedding = displayFusedEmbedding;
        long finalInfer = displayInfer;
''',
'''        float[] finalFusedEmbedding = displayFusedEmbedding;
        ModelDiagnostics finalDiagnostics = displayDiagnostics;
        long finalInfer = displayInfer;
''',
"capture final diagnostics")

replace_once(
'''            PerformanceWindow window = performance.get(displayVariant);
            txtPerf.setText(String.format(Locale.US,
''',
'''            PerformanceWindow window = performance.get(displayVariant);
            if (recognitionArchitectureView != null) {
                recognitionArchitectureView.setVariant(ModelArchitectureSpec.forVariant(displayVariant));
            }
            if (blockDiagnosticsView != null) {
                blockDiagnosticsView.setData(ModelArchitectureSpec.forVariant(displayVariant), finalDiagnostics);
            }
            txtPerf.setText(String.format(Locale.US,
''',
"publish runtime diagnostics")

replace_once(
'''                "frame → degrade(%s) → detect %dms → 5pt align %dms → ProbeHardGate %s → embed %s %dms → fusion %d/5 → Top-K %dms → %s\\n本帧链路=%dms",
''',
'''                "frame → degrade(%s) → detect %dms → 5pt align %dms → ProbeHardGate %s → FaceLiVT %s: 18 blocks [3/3/9/3] → 1284D → 512D %dms → fusion %d/5 → Top-K %dms → %s\\n本帧链路=%dms",
''',
"pipeline structure")

if text == original:
    raise SystemExit("R4 WIRING PATCH FAIL: no changes")
MAIN.write_text(text, encoding="utf-8")
print("R4 wiring patch applied")

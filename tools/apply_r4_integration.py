#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
LOGGER = ROOT / "app/src/main/java/com/qujindai/facelivtlab/SessionLogger.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"R4 PATCH FAIL [{label}]: anchor not found")
    return text.replace(old, new, 1)


def patch_main() -> None:
    text = MAIN.read_text(encoding="utf-8")

    text = replace_once(text,
        "    private TextView txtRecognitionFormula;\n",
        "    private TextView txtRecognitionFormula;\n"
        "    private TextView txtEnrollmentGeometry;\n"
        "    private TextView txtGeometryMicroscope;\n",
        "main fields text")

    text = replace_once(text,
        "    private TrendChartView trendChart;\n    private SeekBar seekThreshold;\n",
        "    private TrendChartView trendChart;\n"
        "    private SeekBar seekThreshold;\n"
        "    private BlockMicroscopeView enrollmentModelMicroscope;\n"
        "    private BlockMicroscopeView recognitionModelMicroscope;\n"
        "    private ModelComparisonView modelComparisonView;\n"
        "    private MatrixDeltaView deltaXsS;\n"
        "    private MatrixDeltaView deltaMS;\n",
        "main fields views")

    text = replace_once(text,
        "    private final MicroscopeSelectionState microscopeSelection = new MicroscopeSelectionState();\n",
        "    private final MicroscopeSelectionState microscopeSelection = new MicroscopeSelectionState();\n"
        "    private final EnumMap<ModelVariant, DeepModelStats> latestDeepStats = new EnumMap<>(ModelVariant.class);\n"
        "    private final List<AlignmentGeometry> enrollmentGeometries = new ArrayList<>();\n",
        "main deep state")

    text = replace_once(text,
        "    private int lastProbeTrackingId = Integer.MIN_VALUE;\n",
        "    private int lastProbeTrackingId = Integer.MIN_VALUE;\n"
        "    private volatile long lastDeepDiagnosticMs = 0L;\n",
        "main diagnostic clock")

    text = replace_once(text,
        "        txtRecognitionFormula = findViewById(R.id.txtRecognitionFormula);\n",
        "        txtRecognitionFormula = findViewById(R.id.txtRecognitionFormula);\n"
        "        txtEnrollmentGeometry = findViewById(R.id.txtEnrollmentGeometry);\n"
        "        txtGeometryMicroscope = findViewById(R.id.txtGeometryMicroscope);\n",
        "bind geometry")

    text = replace_once(text,
        "        trendChart = findViewById(R.id.trendChart);\n        seekThreshold = findViewById(R.id.seekThreshold);\n",
        "        trendChart = findViewById(R.id.trendChart);\n"
        "        seekThreshold = findViewById(R.id.seekThreshold);\n"
        "        enrollmentModelMicroscope = findViewById(R.id.enrollmentModelMicroscope);\n"
        "        recognitionModelMicroscope = findViewById(R.id.recognitionModelMicroscope);\n"
        "        modelComparisonView = findViewById(R.id.modelComparisonView);\n"
        "        deltaXsS = findViewById(R.id.deltaXsS);\n"
        "        deltaMS = findViewById(R.id.deltaMS);\n",
        "bind R4 views")

    text = text.replace("R3.1 已就绪 · 稳定性与覆盖性分离", "R4 已就绪 · 几何 + 向量 + 18 Block 深层显微镜")

    text = replace_once(text,
        "        clearProbeProjection();\n        refreshCalibration(focus);\n        renderRecognitionModelPendingState(selection);\n",
        "        clearProbeProjection();\n"
        "        lastDeepDiagnosticMs = 0L;\n"
        "        if (recognitionModelMicroscope != null) recognitionModelMicroscope.clearStats(ModelTopology.forVariant(focus));\n"
        "        refreshCalibration(focus);\n"
        "        renderRecognitionModelPendingState(selection);\n",
        "model switch clears deep stats")

    text = replace_once(text,
        "        if (txtProbeEmbeddingInfo != null) {\n            txtProbeEmbeddingInfo.setText(\"显微镜焦点 \" + focus.storageKey + \" · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。\");\n        }\n",
        "        if (txtProbeEmbeddingInfo != null) {\n"
        "            txtProbeEmbeddingInfo.setText(\"显微镜焦点 \" + focus.storageKey + \" · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。\");\n"
        "        }\n"
        "        if (recognitionModelMicroscope != null) recognitionModelMicroscope.clearStats(ModelTopology.forVariant(focus));\n",
        "pending deep panel")

    text = replace_once(text,
        "        completedEnrollmentSession = null;\n        enrollmentName = name;\n",
        "        completedEnrollmentSession = null;\n"
        "        enrollmentGeometries.clear();\n"
        "        enrollmentName = name;\n",
        "reset geometry archive")

    text = replace_once(text,
        "        similarityMatrix.setMatrix(null);\n        embeddingScatter.setProjection(null, 0);\n",
        "        similarityMatrix.setMatrix(null);\n"
        "        embeddingScatter.setProjection(null, 0);\n"
        "        if (modelComparisonView != null) modelComparisonView.setRows(null);\n"
        "        if (deltaXsS != null) deltaXsS.setDelta(\"ΔM_XS,S\", null);\n"
        "        if (deltaMS != null) deltaMS.setDelta(\"ΔM_M,S\", null);\n"
        "        if (enrollmentModelMicroscope != null) enrollmentModelMicroscope.clearStats(ModelTopology.forVariant(inspectVariant));\n",
        "reset R4 enrollment views")

    text = text.replace("正在建立 \" + name + \" 的 R3.1 质量档案。", "正在建立 \" + name + \" 的 R4 质量档案。")
    text = text.replace("R3.1 录入 · 等待第 1 张合格差异帧", "R4 录入 · 等待第 1 张合格差异帧")
    text = text.replace("R3.1 显微镜 CSV 已导出", "R4 显微镜 CSV 已导出")

    old_align = """            long alignStart = SystemClock.elapsedRealtimeNanos();
            Bitmap aligned = FaceAligner.align(detectorBitmap, face);
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());

            if (currentPage == Page.ENROLLMENT) {
                runOnUiThread(() -> txtEnrollmentLiveQuality.setText(
                        \"实时质量 · \" + quality.compactLine() + \"\\n入库硬门：\" + quality.enrollmentGateReason()));
                if (enrollmentRemaining.get() > 0 && enrollmentName != null) {
                    handleEnrollment(aligned, quality, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
"""
    new_align = """            long alignStart = SystemClock.elapsedRealtimeNanos();
            FaceAligner.Result alignment = FaceAligner.alignWithGeometry(detectorBitmap, face);
            Bitmap aligned = alignment.aligned;
            AlignmentGeometry geometry = alignment.geometry;
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());

            if (currentPage == Page.ENROLLMENT) {
                runOnUiThread(() -> {
                    txtEnrollmentLiveQuality.setText(\"实时质量 · \" + quality.compactLine() + \"\\n入库硬门：\" + quality.enrollmentGateReason());
                    renderGeometryMicroscope(geometry, true);
                });
                if (enrollmentRemaining.get() > 0 && enrollmentName != null) {
                    handleEnrollment(aligned, quality, geometry, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
"""
    text = replace_once(text, old_align, new_align, "alignWithGeometry enrollment")

    text = replace_once(text,
        "                    txtRecognitionQuality.setText(quality.compactLine() + \"\\nProbe硬门：\" + quality.probeGateReason());\n                });\n                handleRecognition(aligned, trackingId, quality, degraded, detectorBitmap,\n",
        "                    txtRecognitionQuality.setText(quality.compactLine() + \"\\nProbe硬门：\" + quality.probeGateReason());\n"
        "                    renderGeometryMicroscope(geometry, false);\n"
        "                });\n"
        "                handleRecognition(aligned, trackingId, quality, geometry, degraded, detectorBitmap,\n",
        "alignWithGeometry recognition")

    text = replace_once(text,
        "    private void handleEnrollment(Bitmap aligned, FaceQuality.Snapshot quality,\n                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,\n",
        "    private void handleEnrollment(Bitmap aligned, FaceQuality.Snapshot quality, AlignmentGeometry geometry,\n"
        "                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,\n",
        "enrollment signature")

    old_embed_all = """        EnumMap<ModelVariant, RecognizerBank.TimedEmbedding> all = recognizerBank.embedAll(aligned);
        RecognizerBank.TimedEmbedding sEmbedding = all.get(ModelVariant.S);
        if (sEmbedding == null || !enrollmentSession.isNovelCandidate(ModelVariant.S, sEmbedding.embedding, quality)) {
"""
    new_embed_all = """        EnumMap<ModelVariant, RecognizerBank.TimedDiagnostic> all = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) all.put(variant, recognizerBank.diagnose(variant, aligned));
        RecognizerBank.TimedDiagnostic sEmbedding = all.get(ModelVariant.S);
        if (sEmbedding == null || !enrollmentSession.isNovelCandidate(ModelVariant.S, sEmbedding.embedding, quality)) {
"""
    text = replace_once(text, old_embed_all, new_embed_all, "enrollment diagnostics")

    text = replace_once(text,
        "        for (ModelVariant variant : ModelVariant.values()) {\n            RecognizerBank.TimedEmbedding te = all.get(variant);\n            enrollmentSession.add(variant, te.embedding, quality);\n",
        "        for (ModelVariant variant : ModelVariant.values()) {\n"
        "            RecognizerBank.TimedDiagnostic te = all.get(variant);\n"
        "            enrollmentSession.add(variant, te.embedding, quality);\n"
        "            latestDeepStats.put(variant, te.stats);\n",
        "enrollment cache deep stats")

    text = replace_once(text,
        "        int acceptedCount = enrollmentSession.size(ModelVariant.S);\n",
        "        enrollmentGeometries.add(geometry);\n"
        "        int acceptedCount = enrollmentSession.size(ModelVariant.S);\n",
        "enrollment geometry list")

    text = replace_once(text,
        "            txtPerf.setText(\"本帧 detect \" + detectMs + \" / align \" + alignMs + \"ms | \" + timings + \"\\n\" + thermalLine(thermal));\n            updateActionState();\n",
        "            txtPerf.setText(\"本帧 detect \" + detectMs + \" / align \" + alignMs + \"ms | diag \" + timings + \"\\n\" + thermalLine(thermal));\n"
        "            renderDeepModelStats(inspectVariant, latestDeepStats.get(inspectVariant), true);\n"
        "            updateActionState();\n",
        "enrollment deep render")

    text = replace_once(text,
        "            archive.append(String.format(Locale.US,\n                    \"S%d  Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 点 %.2f 尺寸 %.2f | Y/P/R %.1f/%.1f/%.1f° | Hard %s\\n\",\n                    i+1, q.composite, q.sharpness, q.brightness, q.contrast, q.pose, q.landmarks, q.size,\n                    q.yaw, q.pitch, q.roll, q.enrollmentGateReason()));\n",
        "            AlignmentGeometry g = i < enrollmentGeometries.size() ? enrollmentGeometries.get(i) : null;\n"
        "            archive.append(String.format(Locale.US,\n"
        "                    \"S%d  Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 点 %.2f 尺寸 %.2f | Y/P/R %.1f/%.1f/%.1f° | Align %s | Hard %s\\n\",\n"
        "                    i+1, q.composite, q.sharpness, q.brightness, q.contrast, q.pose, q.landmarks, q.size,\n"
        "                    q.yaw, q.pitch, q.roll, geometryArchive(g), q.enrollmentGateReason()));\n",
        "archive geometry")

    text = replace_once(text,
        "                    \"%s  Qavg %.3f · Sstable %.4f · D %.4f · Cover %.3f (Emb %.3f / Pose %.3f) · %s\\n\",\n                    variant.storageKey, summary.averageQuality, summary.stability, summary.dispersion,\n                    summary.coverage, summary.embeddingCoverage, summary.poseCoverage,\n",
        "                    \"%s  Qavg %.3f · Sstable %.4f · D %.4f · Cover %.3f (Emb %.3f / Pose %.3f) · pair[min %.3f / mean %.3f] · outlier %s · %s\\n\",\n"
        "                    variant.storageKey, summary.averageQuality, summary.stability, summary.dispersion,\n"
        "                    summary.coverage, summary.embeddingCoverage, summary.poseCoverage,\n"
        "                    summary.minPairCosine, summary.meanPairCosine,\n"
        "                    summary.outlierIndex < 0 ? \"N/A\" : \"S\" + (summary.outlierIndex + 1),\n",
        "archive pair stats")

    text = text.replace("三模型质量加权模板与 R3.1 参考样本已入库", "三模型质量加权模板与 R4 显微镜参考样本已入库")
    text = text.replace("R3.1 录入完成 · ", "R4 录入完成 · ")

    text = replace_once(text,
        "            renderEnrollmentMicroscope(inspectVariant);\n            refreshCalibration(displayVariantForMode());\n",
        "            renderEnrollmentMicroscope(inspectVariant);\n"
        "            renderEnrollmentComparison();\n"
        "            refreshCalibration(displayVariantForMode());\n",
        "final enrollment comparison")

    text = replace_once(text,
        "        embeddingScatter.setProjection(s.projection, s.sampleCount);\n",
        "        embeddingScatter.setProjection(s.projection, s.sampleCount);\n"
        "        renderDeepModelStats(variant, latestDeepStats.get(variant), true);\n",
        "enrollment model deep view")

    text = replace_once(text,
        "                \"Cemb=%.3f · Cpose=%.3f · Coverage=sqrt(Cemb×Cpose)=%.3f\\n\" +\n                \"Pass=(N=%d≥5) ∧ (Qavg %.3f≥.55) ∧ (Sstable %.4f≥.70) ∧ (Coverage %.3f≥%.2f) ∧ HardGate ⇒ %s\",\n",
        "                \"Cemb=%.3f · Cpose=%.3f · Coverage=sqrt(Cemb×Cpose)=%.3f\\n\" +\n"
        "                \"N×N矩阵: Mij=cos(fi,fj) · minPair %.3f · meanPair %.3f · outlier %s(均值 %.3f)\\n\" +\n"
        "                \"PCA 坐标不可跨模型直接比较；最终身份判定仍使用512D cosine\\n\" +\n"
        "                \"Pass=(N=%d≥5) ∧ (Qavg %.3f≥.55) ∧ (Sstable %.4f≥.70) ∧ (Coverage %.3f≥%.2f) ∧ HardGate ⇒ %s\",\n",
        "enrollment formula extra placeholders")

    text = replace_once(text,
        "                s.stability, s.dispersion, s.embeddingCoverage, s.poseCoverage, s.coverage,\n                s.sampleCount, s.averageQuality, s.stability, s.coverage, EnrollmentSession.MIN_COVERAGE,\n",
        "                s.stability, s.dispersion, s.embeddingCoverage, s.poseCoverage, s.coverage,\n"
        "                s.minPairCosine, s.meanPairCosine, s.outlierIndex < 0 ? \"N/A\" : \"S\" + (s.outlierIndex + 1), s.outlierMeanCosine,\n"
        "                s.sampleCount, s.averageQuality, s.stability, s.coverage, EnrollmentSession.MIN_COVERAGE,\n",
        "enrollment formula args")

    marker = "    private void handleRecognition(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality,\n"
    insert_methods = r'''    private void renderEnrollmentComparison() {
        EnrollmentSession session = completedEnrollmentSession;
        if (session == null) return;
        EnumMap<ModelVariant, EnrollmentSession.Summary> summaries = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) summaries.put(variant, session.summary(variant));
        ModelComparisonStats comparison = ModelComparisonStats.from(summaries);
        if (modelComparisonView != null) modelComparisonView.setRows(comparison.rows);
        if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", comparison.deltaXsMinusS);
        if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", comparison.deltaMMinusS);
    }

    private void renderDeepModelStats(ModelVariant variant, DeepModelStats stats, boolean enrollment) {
        ModelTopology topology = ModelTopology.forVariant(variant == null ? ModelVariant.S : variant);
        BlockMicroscopeView view = enrollment ? enrollmentModelMicroscope : recognitionModelMicroscope;
        if (view != null) view.setStats(topology, stats);
    }

    private void renderGeometryMicroscope(AlignmentGeometry geometry, boolean enrollment) {
        TextView target = enrollment ? txtEnrollmentGeometry : txtGeometryMicroscope;
        if (target == null) return;
        if (geometry == null) {
            target.setText("5点几何显微镜 · 等待人脸");
            return;
        }
        if (geometry.usedFallback) {
            target.setText("5点几何显微镜（模型无关）\nLandmark " + geometry.landmarkCount + "/5 · 5pt unavailable → fallback crop\n5点 → sR+t → 112×112 → FaceLiVTv2：本帧无有效5点残差");
            return;
        }
        target.setText(String.format(Locale.US,
                "5点几何显微镜（模型无关） · LE/RE/N/ML/MR = 5/5\n眼间距 %.1fpx · Roll %.2f° · scale %.4f · translation %.1fpx\nE_align=mean||p'i-pHat_i||=%.2fpx · Emax=%.2fpx\n5点 → sR+t → 112×112 → FaceLiVTv2 → 512D",
                geometry.eyeDistancePx, geometry.rollDeg, geometry.scale, geometry.translationPx,
                geometry.meanResidualPx, geometry.maxResidualPx));
    }

    private static String geometryArchive(AlignmentGeometry g) {
        if (g == null) return "N/A";
        if (g.usedFallback) return g.landmarkCount + "/5 fallback";
        return String.format(Locale.US, "5/5 residual %.2f/%.2fpx", g.meanResidualPx, g.maxResidualPx);
    }

'''
    if insert_methods not in text:
        if marker not in text:
            raise SystemExit("R4 PATCH FAIL [insert R4 methods]: marker not found")
        text = text.replace(marker, insert_methods + marker, 1)

    text = replace_once(text,
        "    private void handleRecognition(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality,\n                                   Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,\n",
        "    private void handleRecognition(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality, AlignmentGeometry geometry,\n"
        "                                   Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,\n",
        "recognition signature")

    text = replace_once(text,
        "        MicroscopeSelectionState.Snapshot frameSelection = microscopeSelection.snapshot();\n        StringBuilder results = new StringBuilder();\n",
        "        MicroscopeSelectionState.Snapshot frameSelection = microscopeSelection.snapshot();\n"
        "        DeepModelStats frameDeepStats = maybeRunDeepDiagnostic(aligned, frameSelection);\n"
        "        StringBuilder results = new StringBuilder();\n",
        "recognition deep diagnostic")

    text = replace_once(text,
        "                    quality, decision.top1Name, decision.top2Name,\n                    similarity, decision.margin, threshold, decision.accepted, fusedFrames,\n",
        "                    quality, geometry, variant == displayVariant ? frameDeepStats : null,\n"
        "                    decision.top1Name, decision.top2Name,\n"
        "                    similarity, decision.margin, threshold, decision.accepted, fusedFrames,\n",
        "logger R4 args")

    text = replace_once(text,
        "        int finalFusedFrames = displayFusedFrames;\n        runOnUiThread(() -> {\n",
        "        int finalFusedFrames = displayFusedFrames;\n"
        "        DeepModelStats finalDeepStats = frameDeepStats;\n"
        "        runOnUiThread(() -> {\n",
        "recognition final deep")

    text = replace_once(text,
        "            renderRecognitionMicroscope(displayVariant, finalTop, quality, finalFusedFrames,\n                    detectMs, alignMs, finalInfer, finalMatch, finalDecision, finalFusedEmbedding);\n",
        "            renderRecognitionMicroscope(displayVariant, finalTop, quality, finalFusedFrames,\n"
        "                    detectMs, alignMs, finalInfer, finalMatch, finalDecision, finalFusedEmbedding);\n"
        "            renderDeepModelStats(displayVariant, finalDeepStats, false);\n",
        "recognition render deep")

    probe_marker = "    private void renderProbeProjection(ModelVariant variant, RecognitionDecision decision, float[] fusedEmbedding) {\n"
    diagnostic_method = r'''    private DeepModelStats maybeRunDeepDiagnostic(Bitmap aligned, MicroscopeSelectionState.Snapshot diagnosticSelection) {
        if (aligned == null || diagnosticSelection == null) return null;
        ModelVariant focus = diagnosticSelection.focus;
        DeepModelStats cached = latestDeepStats.get(focus);
        long now = SystemClock.elapsedRealtime();
        if (cached != null && now - lastDeepDiagnosticMs < 1000L) return cached;
        lastDeepDiagnosticMs = now;
        try {
            RecognizerBank.TimedDiagnostic diagnostic = recognizerBank.diagnose(focus, aligned);
            latestDeepStats.put(focus, diagnostic.stats);
            runOnUiThread(() -> {
                if (!microscopeSelection.isCurrent(diagnosticSelection)) return;
                renderDeepModelStats(focus, diagnostic.stats, false);
            });
            return diagnostic.stats;
        } catch (Exception e) {
            return cached;
        }
    }

'''
    if diagnostic_method not in text:
        if probe_marker not in text:
            raise SystemExit("R4 PATCH FAIL [diagnostic method]: marker not found")
        text = text.replace(probe_marker, diagnostic_method + probe_marker, 1)

    text = replace_once(text,
        "                    txtEnrollmentLiveQuality.setText(\"实时质量：未检测到人脸\");\n",
        "                    txtEnrollmentLiveQuality.setText(\"实时质量：未检测到人脸\");\n"
        "                    if (txtEnrollmentGeometry != null) txtEnrollmentGeometry.setText(\"5点几何显微镜：未检测到人脸\");\n",
        "no face enrollment geometry")

    text = replace_once(text,
        "                    txtRecognitionQuality.setText(\"Probe 质量：未检测到人脸\");\n                    faceOverlay.clear();\n",
        "                    txtRecognitionQuality.setText(\"Probe 质量：未检测到人脸\");\n"
        "                    if (txtGeometryMicroscope != null) txtGeometryMicroscope.setText(\"5点几何显微镜：未检测到人脸\");\n"
        "                    faceOverlay.clear();\n",
        "no face recognition geometry")

    MAIN.write_text(text, encoding="utf-8")


def patch_layout() -> None:
    text = LAYOUT.read_text(encoding="utf-8")
    text = text.replace("FaceLiVT R3 · 人脸显微镜", "FaceLiVT R4 · 深层人脸显微镜")
    text = text.replace("相机初始化中 · 录入和检测已拆分为独立工作流", "R4 · 几何 / 向量 / 18 Block 三层显微镜")

    enrollment_quality = '''                    <TextView
                        android:id="@+id/txtEnrollmentLiveQuality"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:background="@drawable/r3_panel"
                        android:text="实时质量：等待人脸"
                        android:textColor="#E8F5F2"
                        android:textSize="12sp" />
'''
    geometry_panel = enrollment_quality + '''
                    <TextView
                        android:id="@+id/txtEnrollmentGeometry"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:background="@drawable/r3_panel"
                        android:text="5点几何显微镜（模型无关）：等待 LE / RE / N / ML / MR"
                        android:textColor="#D9FFF7"
                        android:textIsSelectable="true"
                        android:textSize="11sp" />
'''
    text = replace_once(text, enrollment_quality, geometry_panel, "layout enrollment geometry")

    enrollment_formula = '''                    <TextView
                        android:id="@+id/txtEnrollmentFormula"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:background="@drawable/r3_panel"
                        android:text="公式链\\nQi = .25Qsharp + .15Qlight + .10Qcontrast + .20Qpose + .15Qlandmark + .15Qsize\\nαi=max(Qi,.05) → c=normalize(Σαifi/Σαi)\\nSstable=mean(cos(fi,c)) · D=mean(1-cos(fi,c))\\nPass=N≥5 ∧ Qavg≥.55 ∧ Sstable≥.70"
                        android:textColor="#D9FFF7"
                        android:textIsSelectable="true"
                        android:textSize="11sp" />
'''
    model_panel = enrollment_formula + '''

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="9dp"
                        android:text="模型结构显微镜 · 真实 18 Block 中间统计"
                        android:textColor="#FFFFFF"
                        android:textStyle="bold" />

                    <com.qujindai.facelivtlab.BlockMicroscopeView
                        android:id="@+id/enrollmentModelMicroscope"
                        android:layout_width="match_parent"
                        android:layout_height="270dp"
                        android:layout_marginTop="4dp" />
'''
    text = replace_once(text, enrollment_formula, model_panel, "layout enrollment block view")

    similarity = '''                    <com.qujindai.facelivtlab.SimilarityMatrixView
                        android:id="@+id/similarityMatrix"
                        android:layout_width="match_parent"
                        android:layout_height="180dp"
                        android:layout_marginTop="4dp" />
'''
    comparison = similarity + '''

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="5dp"
                        android:text="5×5 来源于 5 张录入样本，不是模型结构（样本数变化时自动为 N×N）"
                        android:textColor="#B8C9C7"
                        android:textSize="10sp" />

                    <com.qujindai.facelivtlab.ModelComparisonView
                        android:id="@+id/modelComparisonView"
                        android:layout_width="match_parent"
                        android:layout_height="150dp"
                        android:layout_marginTop="7dp" />

                    <com.qujindai.facelivtlab.MatrixDeltaView
                        android:id="@+id/deltaXsS"
                        android:layout_width="match_parent"
                        android:layout_height="190dp"
                        android:layout_marginTop="6dp" />

                    <com.qujindai.facelivtlab.MatrixDeltaView
                        android:id="@+id/deltaMS"
                        android:layout_width="match_parent"
                        android:layout_height="190dp"
                        android:layout_marginTop="6dp" />
'''
    text = replace_once(text, similarity, comparison, "layout comparison views")
    text = text.replace("512D → 2D embedding 投影", "512D → 2D embedding 投影 · PCA 坐标不可跨模型直接比较")

    recognition_quality = '''                    </LinearLayout>

                    <TextView
                        android:id="@+id/txtPipeline"
'''
    recognition_geometry = '''                    </LinearLayout>

                    <TextView
                        android:id="@+id/txtGeometryMicroscope"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:background="@drawable/r3_panel"
                        android:text="5点几何显微镜（模型无关）：等待 LE / RE / N / ML / MR"
                        android:textColor="#D9FFF7"
                        android:textIsSelectable="true"
                        android:textSize="11sp" />

                    <TextView
                        android:id="@+id/txtPipeline"
'''
    text = replace_once(text, recognition_quality, recognition_geometry, "layout recognition geometry")

    pipeline = '''                    <TextView
                        android:id="@+id/txtPipeline"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:background="@drawable/r3_panel"
                        android:text="链路：frame → degrade → detect → 5pt align → quality gate → embed → 5帧融合 → Top-K → decision"
                        android:textColor="#D9FFF7"
                        android:textIsSelectable="true"
                        android:textSize="11sp" />
'''
    recognition_model = pipeline + '''

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="9dp"
                        android:text="模型结构显微镜 · 当前焦点模型真实 18 Block"
                        android:textColor="#FFFFFF"
                        android:textStyle="bold" />

                    <com.qujindai.facelivtlab.BlockMicroscopeView
                        android:id="@+id/recognitionModelMicroscope"
                        android:layout_width="match_parent"
                        android:layout_height="270dp"
                        android:layout_marginTop="4dp" />
'''
    text = replace_once(text, pipeline, recognition_model, "layout recognition block view")
    LAYOUT.write_text(text, encoding="utf-8")


def patch_logger() -> None:
    text = LOGGER.read_text(encoding="utf-8")
    text = replace_once(text,
        '    private static final String HEADER = "timestamp_ms,profile,model,source,simulated,assist,face_px,quality,sharpness,brightness,contrast,pose,landmarks,size,yaw,pitch,roll,top1,top2,similarity,margin,threshold,quality_gate,accepted,fused_frames,detect_ms,align_ms,infer_ms,match_ms,total_ms,battery_c,thermal_status,thermal_label";\n',
        '    private static final String HEADER = "timestamp_ms,profile,model,source,simulated,assist,face_px,quality,sharpness,brightness,contrast,pose,landmarks,size,yaw,pitch,roll,landmark_count,fallback_crop,eye_distance_px,align_roll_deg,align_scale,align_translation_px,align_mean_residual_px,align_max_residual_px,stage1_rms,stage2_rms,stage3_rms,stage4_rms,prehead_rms,top1,top2,similarity,margin,threshold,quality_gate,accepted,fused_frames,detect_ms,align_ms,infer_ms,match_ms,total_ms,battery_c,thermal_status,thermal_label";\n',
        "logger header")

    text = replace_once(text,
        "                                           int faceW, int faceH, FaceQuality.Snapshot quality,\n                                           String top1, String top2, float similarity, float margin, float threshold,\n",
        "                                           int faceW, int faceH, FaceQuality.Snapshot quality,\n"
        "                                           AlignmentGeometry geometry, DeepModelStats deepStats,\n"
        "                                           String top1, String top2, float similarity, float margin, float threshold,\n",
        "logger signature")

    text = replace_once(text,
        "                f(q.yaw) + \",\" + f(q.pitch) + \",\" + f(q.roll) + \",\" +\n                SessionCsv.escape(top1) + \",\" + SessionCsv.escape(top2) + \",\" + f(similarity) + \",\" + f(margin) + \",\" +\n",
        "                f(q.yaw) + \",\" + f(q.pitch) + \",\" + f(q.roll) + \",\" +\n"
        "                geometryFields(geometry) + \",\" + deepFields(deepStats) + \",\" +\n"
        "                SessionCsv.escape(top1) + \",\" + SessionCsv.escape(top2) + \",\" + f(similarity) + \",\" + f(margin) + \",\" +\n",
        "logger geometry/deep fields")

    text = replace_once(text,
        "                faceW, faceH, null, top1, \"\", similarity, Float.NaN, 0f, accepted, fusedFrames,\n",
        "                faceW, faceH, null, null, null, top1, \"\", similarity, Float.NaN, 0f, accepted, fusedFrames,\n",
        "logger compatibility wrapper")

    helper_anchor = "    private static String f(float value) {\n"
    helpers = r'''    private static String geometryFields(AlignmentGeometry g) {
        if (g == null) return "0,false,,,,,,";
        return g.landmarkCount + "," + g.usedFallback + "," +
                f(g.eyeDistancePx) + "," + f(g.rollDeg) + "," + f(g.scale) + "," +
                f(g.translationPx) + "," + f(g.meanResidualPx) + "," + f(g.maxResidualPx);
    }

    private static String deepFields(DeepModelStats stats) {
        if (stats == null || stats.stages.length < 4) return ",,,,";
        return f(stats.stages[0].rms) + "," + f(stats.stages[1].rms) + "," +
                f(stats.stages[2].rms) + "," + f(stats.stages[3].rms) + "," + f(stats.preHeadRms);
    }

'''
    if helpers not in text:
        if helper_anchor not in text:
            raise SystemExit("R4 PATCH FAIL [logger helpers]: anchor not found")
        text = text.replace(helper_anchor, helpers + helper_anchor, 1)
    text = text.replace('"facelivt-r31-microscope-"', '"facelivt-r4-microscope-"')
    LOGGER.write_text(text, encoding="utf-8")


patch_main()
patch_layout()
patch_logger()
print("R4 integration patch applied")

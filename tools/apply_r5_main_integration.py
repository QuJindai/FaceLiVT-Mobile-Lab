#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/qujindai/facelivtlab/MainActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_main.xml"
BUILD = ROOT / "app/build.gradle"


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"R5 PATCH FAIL {label}: expected 1 anchor, got {count}")
    return text.replace(old, new, 1)


def between(text: str, start: str, end: str, replacement: str, label: str) -> str:
    si = text.find(start)
    if si < 0:
        raise SystemExit(f"R5 PATCH FAIL {label}: start anchor missing")
    ei = text.find(end, si + len(start))
    if ei < 0:
        raise SystemExit(f"R5 PATCH FAIL {label}: end anchor missing")
    return text[:si] + replacement + text[ei:]


main = MAIN.read_text(encoding="utf-8")
layout = LAYOUT.read_text(encoding="utf-8")
build = BUILD.read_text(encoding="utf-8")

# Imports.
main = once(main, "import android.graphics.Bitmap;\n", "import android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\n", "bitmap import")
main = once(main, "import java.io.File;\n", "import java.io.ByteArrayOutputStream;\nimport java.io.File;\n", "io import")
main = once(main, "import java.util.List;\n", "import java.util.LinkedHashSet;\nimport java.util.List;\n", "linked hash set import")

# Constants / enums / fields.
main = once(main,
    "    private static final int PROBE_TRAIL_SIZE = 20;\n\n    enum Page { ENROLLMENT, RECOGNITION }\n",
    "    private static final int PROBE_TRAIL_SIZE = 20;\n"
    "    private static final long GUARD_INTERVAL_MS = 350L;\n"
    "    private static final String LEGACY_HISTORY_NOTICE = \"旧版本没有保存五帧图像；追加学习或删除重录后可建立完整学习档案。\";\n\n"
    "    enum Page { ENROLLMENT, RECOGNITION }\n"
    "    enum EnrollmentIntent { NONE, NEW, APPEND, REPLACE_AFTER_DELETE }\n",
    "constants")

main = once(main,
    "    private TextView txtEnrollmentGeometry;\n    private TextView txtGeometryMicroscope;\n",
    "    private TextView txtEnrollmentGeometry;\n    private TextView txtGeometryMicroscope;\n    private TextView txtHistoryStrip;\n    private IdentityGuardPanel identityGuardPanel;\n",
    "ui fields")

main = once(main,
    "    private final List<AlignmentGeometry> enrollmentGeometries = new ArrayList<>();\n",
    "    private final List<AlignmentGeometry> enrollmentGeometries = new ArrayList<>();\n"
    "    private final List<Bitmap> enrollmentThumbnails = new ArrayList<>();\n"
    "    private final IdentityGuardEngine identityGuard = new IdentityGuardEngine();\n"
    "    private final EnumMap<ModelVariant, float[]> appendOldCentroids = new EnumMap<>(ModelVariant.class);\n"
    "    private final EnumMap<ModelVariant, Integer> appendOldEffectiveSamples = new EnumMap<>(ModelVariant.class);\n",
    "guard collections")

main = once(main,
    "    private EnrollmentArchiveStore archiveStore;\n    private ProcessCameraProvider cameraProvider;\n",
    "    private EnrollmentArchiveStore archiveStore;\n"
    "    private EnrollmentHistoryStore historyStore;\n"
    "    private IdentityLifecycle identityLifecycle;\n"
    "    private ProcessCameraProvider cameraProvider;\n",
    "store fields")

main = once(main,
    "    private volatile long lastDeepDiagnosticMs = 0L;\n",
    "    private volatile long lastDeepDiagnosticMs = 0L;\n"
    "    private volatile long guardGeneration = 0L;\n"
    "    private volatile long lastGuardProbeMs = 0L;\n"
    "    private volatile int guardTrackingId = Integer.MIN_VALUE;\n"
    "    private volatile EnrollmentIntent enrollmentIntent = EnrollmentIntent.NONE;\n"
    "    private String existingIdentityContext = \"\";\n"
    "    private int viewedHistoryVersion = -1;\n"
    "    private List<String> lastGuardCandidates = new ArrayList<>();\n",
    "state fields")

# Store construction + R5 ready label.
main = once(main,
    "        faceStore = new FaceStore(this);\n        archiveStore = new EnrollmentArchiveStore(this);\n        recognizerBank = new RecognizerBank(getApplicationContext());\n",
    "        faceStore = new FaceStore(this);\n"
    "        archiveStore = new EnrollmentArchiveStore(this);\n"
    "        historyStore = new EnrollmentHistoryStore(this);\n"
    "        identityLifecycle = new IdentityLifecycle(faceStore, archiveStore, historyStore);\n"
    "        recognizerBank = new RecognizerBank(getApplicationContext());\n",
    "store init")
main = once(main,
    "        txtResult.setText(\"R4 已就绪 · 几何 + 向量 + 18 Block 深层显微镜\");\n",
    "        guardGeneration = identityGuard.captureGeneration();\n"
    "        txtResult.setText(\"R5 已就绪 · Identity Guard + 历史五帧学习回放\");\n",
    "ready label")

# bindViews additions.
main = once(main,
    "        txtGeometryMicroscope = findViewById(R.id.txtGeometryMicroscope);\n",
    "        txtGeometryMicroscope = findViewById(R.id.txtGeometryMicroscope);\n"
    "        txtHistoryStrip = findViewById(R.id.txtHistoryStrip);\n"
    "        identityGuardPanel = findViewById(R.id.identityGuardPanel);\n",
    "bind guard ui")

listener = '''        identityGuardPanel.setListener(new IdentityGuardPanel.Listener() {
            @Override public void onContinueConfirmation() {
                identityGuard.reset();
                guardGeneration = identityGuard.captureGeneration();
                lastGuardCandidates = new ArrayList<>();
                renderIdentityGuardPanel();
                txtResult.setText("Identity Guard · 已清空灰区证据，继续采样确认");
                updateActionState();
            }
            @Override public void onSelectExistingCandidate(String identity) { enterExistingIdentity(identity, true); }
            @Override public void onKeepExisting() {
                txtResult.setText("保留现有 · " + existingIdentityContext + " · 未修改模板或历史档案");
            }
            @Override public void onAppendLearning() { beginAppendLearning(); }
            @Override public void onDeleteAndReenroll() { confirmDeleteAndReenroll(); }
            @Override public void onHistoryVersionSelected(int version) {
                if (!existingIdentityContext.isEmpty() && version != viewedHistoryVersion) loadHistoryVersion(existingIdentityContext, version);
            }
        });

'''
main = once(main,
    "        tabEnrollment.setOnClickListener(v -> showPage(Page.ENROLLMENT));\n",
    listener + "        tabEnrollment.setOnClickListener(v -> showPage(Page.ENROLLMENT));\n",
    "guard listener")

# Camera switch reset and name watcher.
main = once(main,
    "        btnSwitch.setOnClickListener(v -> {\n            cameraReady = false;\n",
    "        btnSwitch.setOnClickListener(v -> {\n"
    "            if (enrollmentRemaining.get() == 0) resetIdentityGuardContext(\"相机切换，防重证据已重置\");\n"
    "            cameraReady = false;\n",
    "camera guard reset")
main = once(main,
    "                if (enrollmentRemaining.get() == 0 && archiveStore != null) {\n",
    "                if (enrollmentRemaining.get() == 0 && archiveStore != null && existingIdentityContext.isEmpty()) {\n",
    "name watcher history guard")

# setProfile / showPage / updateActionState / beginEnrollment.
set_profile = '''    private void setProfile(int position, DegradationProfile[] profiles, Spinner peer) {
        if (position < 0 || position >= profiles.length) return;
        DegradationProfile next = profiles[position];
        boolean changed = profile != next;
        profile = next;
        if (peer.getSelectedItemPosition() != position) peer.setSelection(position);
        clearFusion();
        recognitionTrend.clear();
        if (changed && enrollmentRemaining.get() == 0) resetIdentityGuardContext("画质档位变化，防重证据已重置");
    }

'''
main = between(main, "    private void setProfile(", "    private void showPage(", set_profile, "setProfile")

show_page = '''    private void showPage(Page page) {
        Page previous = currentPage;
        currentPage = page;
        pageEnrollment.setVisibility(page == Page.ENROLLMENT ? View.VISIBLE : View.GONE);
        pageRecognition.setVisibility(page == Page.RECOGNITION ? View.VISIBLE : View.GONE);
        tabEnrollment.setBackgroundResource(page == Page.ENROLLMENT ? R.drawable.r3_tab_active : R.drawable.r3_tab_inactive);
        tabRecognition.setBackgroundResource(page == Page.RECOGNITION ? R.drawable.r3_tab_active : R.drawable.r3_tab_inactive);
        clearFusion();
        recognitionTrend.clear();
        if (previous != page && enrollmentRemaining.get() == 0) resetIdentityGuardContext("页面切换，防重证据已重置");
        if (page == Page.RECOGNITION) refreshCalibration(displayVariantForMode());
        updateActionState();
    }

'''
main = between(main, "    private void showPage(", "    private void updateActionState(", show_page, "showPage")

update_action = '''    private void updateActionState() {
        if (btnEnroll == null || btnSwitch == null || btnExport == null || editName == null || txtActionHint == null) return;
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        String enteredName = editName.getText().toString().trim();
        boolean hasName = !enteredName.isEmpty();
        boolean enrolling = enrollmentName != null && enrollmentRemaining.get() > 0;
        int csvRows = sessionLogger.size();
        IdentityGuardEngine.Snapshot guard = identityGuard.snapshot();
        boolean nameCollision = hasName && faceStore != null && faceStore.identityNames().contains(enteredName);
        boolean guardAllowsNew = existingIdentityContext.isEmpty() && guard.canCreateNew() && !nameCollision;

        btnEnroll.setEnabled(cameraReady && hasName && !enrolling && currentPage == Page.ENROLLMENT && guardAllowsNew);
        btnSwitch.setEnabled(cameraReady && !enrolling);
        btnExport.setEnabled(csvRows > 0);
        tabRecognition.setEnabled(!enrolling);
        spinnerProfile.setEnabled(!enrolling);

        if (enrolling) {
            btnEnroll.setText("合格样本 " + (ENROLLMENT_SAMPLES - enrollmentRemaining.get()) + "/" + ENROLLMENT_SAMPLES);
        } else if (!existingIdentityContext.isEmpty()) {
            btnEnroll.setText("已有身份 · 禁止新建");
        } else if (guard.state == IdentityGuardEngine.State.SUSPECTED) {
            btnEnroll.setText("疑似重复 · 禁止新建");
        } else {
            btnEnroll.setText("开始质量录入 ×5");
        }
        btnSwitch.setText(cameraReady ? "切相机" : "初始化");

        if (!hasPermission) {
            txtActionHint.setText("状态：需要相机权限");
        } else if (!cameraReady) {
            txtActionHint.setText("状态：相机初始化中");
        } else if (enrolling) {
            txtActionHint.setText("R5 " + enrollmentIntent + "：还需 " + enrollmentRemaining.get() + " 帧 · 硬门 + 差异门");
        } else if (currentPage == Page.ENROLLMENT) {
            if (!existingIdentityContext.isEmpty()) {
                txtActionHint.setText("EXISTING · " + existingIdentityContext + " · 可回放历史 / 追加学习 / 删除重录，禁止另存新人");
            } else if (nameCollision) {
                txtActionHint.setText("姓名/编号已存在，但当前人脸尚未被 Guard 确认为该身份；禁止覆盖旧身份");
            } else if (guard.state == IdentityGuardEngine.State.CLEAR) {
                txtActionHint.setText(hasName ? "CLEAR · 防重通过，可建立新身份学习档案" : "CLEAR · 输入新的姓名/编号后开始建档");
            } else {
                txtActionHint.setText(guard.reason);
            }
        } else {
            txtActionHint.setText(csvRows > 0 ? "检测显微镜：已记录 " + csvRows + " 条证据 · 可导出 CSV" : "检测显微镜：像素→512D→决策→经验标定");
        }
    }

'''
main = between(main, "    private void updateActionState(", "    private void updateThreshold(", update_action, "updateActionState")

begin_enroll = '''    private void beginEnrollment() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) { Toast.makeText(this, "先输入姓名或编号", Toast.LENGTH_SHORT).show(); return; }
        if (!cameraReady) { Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show(); return; }
        if (!existingIdentityContext.isEmpty()) {
            Toast.makeText(this, "该人脸已有关联身份，禁止另存为新人", Toast.LENGTH_SHORT).show();
            return;
        }
        IdentityGuardEngine.Snapshot guard = identityGuard.snapshot();
        if (!guard.canCreateNew()) {
            Toast.makeText(this, "Identity Guard 尚未 CLEAR，不能新建身份", Toast.LENGTH_SHORT).show();
            return;
        }
        if (faceStore.identityNames().contains(name)) {
            Toast.makeText(this, "该姓名/编号已经存在，不能覆盖旧身份", Toast.LENGTH_SHORT).show();
            return;
        }
        startEnrollmentCapture(name, EnrollmentIntent.NEW);
    }

    private void startEnrollmentCapture(String name, EnrollmentIntent intent) {
        enrollmentSession = new EnrollmentSession();
        completedEnrollmentSession = null;
        enrollmentGeometries.clear();
        enrollmentThumbnails.clear();
        enrollmentName = name;
        enrollmentIntent = intent == null ? EnrollmentIntent.NEW : intent;
        enrollmentProfileAtStart = profile.label;
        enrollmentRemaining.set(ENROLLMENT_SAMPLES);
        for (ImageView image : sampleFaces) image.setImageDrawable(null);
        similarityMatrix.setMatrix(null);
        embeddingScatter.setProjection(null, 0);
        if (modelComparisonView != null) modelComparisonView.setRows(null);
        if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", null);
        if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", null);
        if (enrollmentModelMicroscope != null) enrollmentModelMicroscope.clearStats(ModelTopology.forVariant(inspectVariant));
        if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.GONE);
        if (txtHistoryStrip != null) txtHistoryStrip.setText("新录入采样 · " + enrollmentIntent + " · S1-S5");
        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R5 学习版本。\n硬门通过后筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。\n本轮档位：" + enrollmentProfileAtStart);
        txtEnrollmentFormula.setText("等待合格且有差异的样本……");
        clearFusion();
        identityGuard.reset();
        guardGeneration = identityGuard.captureGeneration();
        txtResult.setText("R5 " + enrollmentIntent + " · 等待第 1 张合格差异帧");
        updateActionState();
    }

'''
main = between(main, "    private void beginEnrollment(", "    private void exportSession(", begin_enroll, "beginEnrollment")

# Idle enrollment branch -> Guard.
old_idle = '''                } else {
                    runOnUiThread(() -> {
                        txtResult.setText("录入显微镜 · 已找到人脸，等待开始建档");
                        txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                        txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms\\n" + thermalLine(thermal));
                    });
                }
'''
new_idle = '''                } else {
                    handleIdentityGuardProbe(aligned, trackingId, quality, geometry, sourceW, sourceH,
                            degraded, detectorBitmap, active, faceW, faceH, detectMs, alignMs, thermal);
                }
'''
main = once(main, old_idle, new_idle, "idle enrollment guard")

# Enrollment capture: persist aligned thumbnails in-memory.
handle_enrollment = '''    private void handleEnrollment(Bitmap aligned, FaceQuality.Snapshot quality, AlignmentGeometry geometry,
                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                  DegradationProfile active, int faceW, int faceH,
                                  long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        String name = enrollmentName;
        if (!quality.passesEnrollmentGate()) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 入库硬门 FAIL\\n" + quality.enrollmentGateReason());
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms\\n" + thermalLine(thermal));
            });
            return;
        }

        EnumMap<ModelVariant, RecognizerBank.TimedDiagnostic> all = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) all.put(variant, recognizerBank.diagnose(variant, aligned));
        RecognizerBank.TimedDiagnostic sEmbedding = all.get(ModelVariant.S);
        if (sEmbedding == null || !enrollmentSession.isNovelCandidate(ModelVariant.S, sEmbedding.embedding, quality)) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 与已采样帧过于重复\\n请轻微左右转头/抬低头，让模板获得覆盖性");
                txtPerf.setText("硬门 PASS · 差异门 WAIT\\n" + thermalLine(thermal));
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
                    ? ("已收合格差异帧 " + acceptedCount + "/5 · 还需 " + left + " 帧")
                    : "5 张合格差异帧完成 · 正在构建三模型学习版本");
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
            txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms | diag " + timings + "\\n" + thermalLine(thermal));
            renderDeepModelStats(inspectVariant, latestDeepStats.get(inspectVariant), true);
            updateActionState();
        });
        if (left == 0) finalizeEnrollment(name);
    }

'''
main = between(main, "    private void handleEnrollment(", "    private void finalizeEnrollment(", handle_enrollment, "handleEnrollment")

finalize = '''    private void finalizeEnrollment(String name) {
        boolean passAll = true;
        StringBuilder archive = new StringBuilder();
        archive.append("身份：").append(name).append('\\n');
        archive.append("R5 学习类型：").append(enrollmentIntent).append(" · 录入档位：").append(enrollmentProfileAtStart)
                .append(" · 合格差异样本 ").append(ENROLLMENT_SAMPLES).append(" 帧\\n");
        EnrollmentSession.Summary qualitySource = enrollmentSession.summary(ModelVariant.S);
        for (int i = 0; i < qualitySource.qualities.size(); i++) {
            FaceQuality.Snapshot q = qualitySource.qualities.get(i);
            AlignmentGeometry g = i < enrollmentGeometries.size() ? enrollmentGeometries.get(i) : null;
            archive.append(String.format(Locale.US,
                    "S%d  Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 点 %.2f 尺寸 %.2f | Y/P/R %.1f/%.1f/%.1f° | Align %s | Hard %s\\n",
                    i+1, q.composite, q.sharpness, q.brightness, q.contrast, q.pose, q.landmarks, q.size,
                    q.yaw, q.pitch, q.roll, geometryArchive(g), q.enrollmentGateReason()));
        }
        archive.append("\\n模型模板质量：稳定性 ≠ 覆盖性\\n");
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
            passAll &= summary.passesEnrollment();
            archive.append(String.format(Locale.US,
                    "%s  Qavg %.3f · Sstable %.4f · D %.4f · Cover %.3f (Emb %.3f / Pose %.3f) · pair[min %.3f / mean %.3f] · outlier %s · %s\\n",
                    variant.storageKey, summary.averageQuality, summary.stability, summary.dispersion,
                    summary.coverage, summary.embeddingCoverage, summary.poseCoverage,
                    summary.minPairCosine, summary.meanPairCosine,
                    summary.outlierIndex < 0 ? "N/A" : "S" + (summary.outlierIndex + 1),
                    summary.passesEnrollment() ? "PASS" : "FAIL"));
        }

        if (!passAll) {
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

        int version = historyStore.nextVersion(name);
        boolean append = enrollmentIntent == EnrollmentIntent.APPEND;
        int effectiveBefore = append ? appendOldEffectiveSamples.getOrDefault(ModelVariant.S,
                Math.max(1, faceStore.sampleCount(name, ModelVariant.S))) : 0;
        int effectiveAfter = append ? Math.min(Math.max(1, effectiveBefore) + 5, 20) : 5;
        EnrollmentHistoryRecord history = EnrollmentHistoryRecord.fromSession(name, version,
                System.currentTimeMillis(), enrollmentProfileAtStart, effectiveBefore, effectiveAfter,
                enrollmentSession, enrollmentGeometries);
        List<byte[]> thumbnailBytes;
        try {
            thumbnailBytes = encodeEnrollmentThumbnails();
            historyStore.saveVersion(history, thumbnailBytes);
        } catch (RuntimeException e) {
            enrollmentName = null;
            EnrollmentIntent failedIntent = enrollmentIntent;
            enrollmentIntent = EnrollmentIntent.NONE;
            runOnUiThread(() -> {
                if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
                txtResult.setText("R5 历史五帧写入失败 · 活动模板未更新 · " + failedIntent + " · " + safeMessage(e));
                resetIdentityGuardContext("历史写入失败，模板保持原状");
                updateActionState();
            });
            return;
        }

        archive.append("\\n学习版本：V").append(version).append(" · 五张 112×112 对齐脸已写入 app 私有历史\\n");
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
            float[] activeCentroid;
            int activeEffective;
            if (append) {
                float[] oldCentroid = appendOldCentroids.get(variant);
                int oldEffective = appendOldEffectiveSamples.getOrDefault(variant, Math.max(1, faceStore.sampleCount(name, variant)));
                if (oldCentroid == null) {
                    txtResult.setText("追加学习失败：旧模板缺失 " + variant.storageKey);
                    return;
                }
                TemplateFusion.Result fused = TemplateFusion.fuse(oldCentroid, oldEffective, summary.centroid);
                activeCentroid = fused.centroid;
                activeEffective = fused.effectiveSamples;
                archive.append(String.format(Locale.US,
                        "%s append: wOld=%d · wNew=%d · effective=%d · cos(cOld,cActiveNew)=%.4f\\n",
                        variant.storageKey, fused.oldWeight, fused.newWeight, fused.effectiveSamples,
                        VectorMath.cosine(oldCentroid, activeCentroid)));
            } else {
                activeCentroid = summary.centroid;
                activeEffective = 5;
            }
            faceStore.replaceTemplate(name, variant, activeCentroid, activeEffective);
            saveActiveReference(name, variant, enrollmentSession.embeddings(variant), activeCentroid, append);
        }
        archive.append(append
                ? "\\n结论：PASS · 新版本已保存，活动模板完成保守融合；旧版本保持不可变"
                : "\\n结论：PASS · 新版本与三模型活动模板已入库");
        archiveStore.save(name, archive.toString());

        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        enrollmentIntent = EnrollmentIntent.NONE;
        existingIdentityContext = name;
        viewedHistoryVersion = version;
        identityGuard.reset();
        guardGeneration = identityGuard.captureGeneration();
        appendOldCentroids.clear();
        appendOldEffectiveSamples.clear();
        runOnUiThread(() -> {
            if (identityGuardPanel != null) identityGuardPanel.setVisibility(View.VISIBLE);
            editName.setText(name);
            editName.setEnabled(false);
            loadHistoryVersion(name, version);
            txtResult.setText("R5 学习完成 · " + name + " · V" + version + " · 已进入历史回放");
            refreshCalibration(displayVariantForMode());
            renderIdentityGuardPanel();
            updateActionState();
        });
    }

'''
main = between(main, "    private void finalizeEnrollment(", "    private void renderEnrollmentMicroscope(", finalize, "finalizeEnrollment")

# Insert R5 helpers before renderEnrollmentMicroscope.
helpers = r'''    private void handleIdentityGuardProbe(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality,
                                          AlignmentGeometry geometry, int sourceW, int sourceH,
                                          Bitmap degraded, Bitmap detectorBitmap, DegradationProfile active,
                                          int faceW, int faceH, long detectMs, long alignMs,
                                          ThermalProbe.Snapshot thermal) throws Exception {
        long now = SystemClock.elapsedRealtime();
        if (now - lastGuardProbeMs < GUARD_INTERVAL_MS) return;
        lastGuardProbeMs = now;
        if (trackingId != guardTrackingId) {
            guardTrackingId = trackingId;
            identityGuard.reset();
            guardGeneration = identityGuard.captureGeneration();
            runOnUiThread(() -> clearExistingIdentityContextUi("检测到新的跟踪人脸，重新执行身份防重"));
        }
        long requestGeneration = identityGuard.captureGeneration();
        boolean valid = quality != null && quality.passesProbeGate();
        boolean fullGeometry = geometry != null && !geometry.usedFallback && geometry.landmarkCount == 5;
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> evidence = new EnumMap<>(ModelVariant.class);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        if (valid) {
            for (ModelVariant variant : ModelVariant.values()) {
                RecognizerBank.TimedEmbedding embedding = recognizerBank.embed(variant, aligned);
                List<FaceStore.Match> top = faceStore.topMatches(variant, embedding.embedding, 2);
                FaceStore.Match one = top.size() > 0 ? top.get(0) : null;
                FaceStore.Match two = top.size() > 1 ? top.get(1) : null;
                IdentityGuardEngine.ModelEvidence modelEvidence = new IdentityGuardEngine.ModelEvidence(
                        one == null ? "" : one.name, one == null ? Float.NaN : one.similarity,
                        two == null ? "" : two.name, two == null ? Float.NaN : two.similarity,
                        two != null);
                evidence.put(variant, modelEvidence);
                if (one != null) candidates.add(one.name);
            }
        }
        if (!identityGuard.isCurrent(requestGeneration) || currentPage != Page.ENROLLMENT || enrollmentRemaining.get() > 0) return;
        IdentityGuardEngine.FrameInput input = new IdentityGuardEngine.FrameInput(faceStore.identityCount(), trackingId,
                valid, fullGeometry, threshold, guardEmpiricalThresholds(), evidence);
        IdentityGuardEngine.Snapshot snapshot = identityGuard.push(input);
        guardGeneration = snapshot.generation;
        lastGuardCandidates = new ArrayList<>(candidates);
        runOnUiThread(() -> {
            if (!identityGuard.isCurrent(snapshot.generation) || currentPage != Page.ENROLLMENT || enrollmentRemaining.get() > 0) return;
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
            txtPerf.setText("Guard 本帧 detect " + detectMs + " / align " + alignMs + "ms · XS/S/M 身份检索\n" + thermalLine(thermal));
            renderIdentityGuardPanel();
            if (snapshot.state == IdentityGuardEngine.State.EXISTING && !snapshot.candidateIdentity.isEmpty()) {
                enterExistingIdentity(snapshot.candidateIdentity, false);
            } else {
                txtResult.setText("Identity Guard · " + snapshot.state + (snapshot.candidateIdentity.isEmpty() ? "" : " · " + snapshot.candidateIdentity));
                updateActionState();
            }
        });
    }

    private void renderIdentityGuardPanel() {
        if (identityGuardPanel == null) return;
        List<Integer> versions = existingIdentityContext.isEmpty() || historyStore == null
                ? new ArrayList<>() : historyStore.versions(existingIdentityContext);
        identityGuardPanel.render(identityGuard.snapshot(), lastGuardCandidates, versions,
                viewedHistoryVersion, existingIdentityContext);
    }

    private void resetIdentityGuardContext(String reason) {
        identityGuard.reset();
        guardGeneration = identityGuard.captureGeneration();
        guardTrackingId = Integer.MIN_VALUE;
        lastGuardProbeMs = 0L;
        lastGuardCandidates = new ArrayList<>();
        if (enrollmentRemaining.get() == 0) clearExistingIdentityContextUi(reason);
        renderIdentityGuardPanel();
    }

    private void clearExistingIdentityContextUi(String reason) {
        existingIdentityContext = "";
        viewedHistoryVersion = -1;
        if (editName != null) editName.setEnabled(true);
        if (enrollmentRemaining.get() == 0) {
            completedEnrollmentSession = null;
            enrollmentGeometries.clear();
            for (ImageView image : sampleFaces) if (image != null) image.setImageDrawable(null);
            if (similarityMatrix != null) similarityMatrix.setMatrix(null);
            if (embeddingScatter != null) embeddingScatter.setProjection(null, 0);
            if (modelComparisonView != null) modelComparisonView.setRows(null);
            if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", null);
            if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", null);
            if (txtHistoryStrip != null) txtHistoryStrip.setText("采样带 · 每帧都是模板建设证据");
        }
        if (reason != null && !reason.isEmpty() && txtResult != null) txtResult.setText(reason);
    }

    private void enterExistingIdentity(String identity, boolean manual) {
        if (identity == null || identity.trim().isEmpty()) return;
        String id = identity.trim();
        existingIdentityContext = id;
        enrollmentIntent = EnrollmentIntent.NONE;
        enrollmentName = null;
        enrollmentRemaining.set(0);
        editName.setText(id);
        editName.setEnabled(false);
        List<Integer> versions = historyStore.versions(id);
        if (!versions.isEmpty()) {
            loadHistoryVersion(id, versions.get(versions.size() - 1));
        } else {
            viewedHistoryVersion = -1;
            completedEnrollmentSession = null;
            enrollmentGeometries.clear();
            for (ImageView image : sampleFaces) image.setImageDrawable(null);
            similarityMatrix.setMatrix(null);
            embeddingScatter.setProjection(null, 0);
            if (modelComparisonView != null) modelComparisonView.setRows(null);
            if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", null);
            if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", null);
            if (enrollmentModelMicroscope != null) enrollmentModelMicroscope.clearStats(ModelTopology.forVariant(inspectVariant));
            txtHistoryStrip.setText("历史学习 · 旧 R4 身份 · 无可回放五帧");
            String archive = archiveStore.load(id);
            txtEnrollmentArchive.setText(LEGACY_HISTORY_NOTICE + (archive.isEmpty() ? "" : "\n\n" + archive));
            txtEnrollmentFormula.setText("旧模板仍可识别；R5 不伪造不存在的历史图片/质量。追加学习后将建立 V1 完整学习版本。");
        }
        txtResult.setText((manual ? "人工指定已有身份 · " : "EXISTING · 已确认历史身份 · ") + id + " · 禁止另存为新人");
        renderIdentityGuardPanel();
        updateActionState();
    }

    private void loadHistoryVersion(String identity, int version) {
        EnrollmentHistoryRecord record = historyStore.loadVersion(identity, version);
        List<byte[]> bytes = historyStore.loadFiveFrames(identity, version);
        if (record == null || bytes.size() != ENROLLMENT_SAMPLES) {
            txtEnrollmentArchive.setText("历史版本读取失败 · " + identity + " V" + version);
            return;
        }
        viewedHistoryVersion = version;
        completedEnrollmentSession = record.toEnrollmentSession();
        enrollmentGeometries.clear();
        for (EnrollmentHistoryRecord.FrameRecord frame : record.frames) enrollmentGeometries.add(frame.geometry);
        latestDeepStats.clear();
        for (int i = 0; i < sampleFaces.length; i++) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes.get(i), 0, bytes.get(i).length);
            sampleFaces[i].setImageBitmap(bitmap);
        }
        txtHistoryStrip.setText("历史学习 V" + version + " · S1-S5 · " + record.profile + " · t=" + record.timestampMs);
        StringBuilder replay = new StringBuilder();
        replay.append("历史回放 · ").append(identity).append(" · V").append(version)
                .append(" · effective ").append(record.effectiveSamplesBefore).append("→").append(record.effectiveSamplesAfter).append('\n');
        for (int i = 0; i < record.frames.size(); i++) {
            FaceQuality.Snapshot q = record.frames.get(i).quality;
            AlignmentGeometry g = record.frames.get(i).geometry;
            replay.append(String.format(Locale.US, "S%d Q %.2f · Y/P/R %.1f/%.1f/%.1f · Align %s\n",
                    i + 1, q.composite, q.yaw, q.pitch, q.roll, geometryArchive(g)));
        }
        String latestText = archiveStore.load(identity);
        if (!latestText.isEmpty()) replay.append("\n当前文字档案：\n").append(latestText);
        txtEnrollmentArchive.setText(replay.toString());
        renderEnrollmentMicroscope(inspectVariant);
        renderEnrollmentComparison();
        if (enrollmentModelMicroscope != null) enrollmentModelMicroscope.clearStats(ModelTopology.forVariant(inspectVariant));
        renderIdentityGuardPanel();
    }

    private void beginAppendLearning() {
        if (existingIdentityContext.isEmpty() || enrollmentRemaining.get() > 0) return;
        String id = existingIdentityContext;
        appendOldCentroids.clear();
        appendOldEffectiveSamples.clear();
        for (ModelVariant variant : ModelVariant.values()) {
            float[] centroid = faceStore.template(id, variant);
            if (centroid == null) {
                Toast.makeText(this, "旧模板不完整，无法追加：" + variant.storageKey, Toast.LENGTH_SHORT).show();
                return;
            }
            appendOldCentroids.put(variant, centroid.clone());
            appendOldEffectiveSamples.put(variant, Math.max(1, faceStore.sampleCount(id, variant)));
        }
        startEnrollmentCapture(id, EnrollmentIntent.APPEND);
    }

    private void confirmDeleteAndReenroll() {
        if (existingIdentityContext.isEmpty() || enrollmentRemaining.get() > 0) return;
        final String id = existingIdentityContext;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("删除旧身份并重新录入？")
                .setMessage("将真实删除 " + id + " 的 XS/S/M 活动模板、旧参考/文字档案、全部 R5 历史版本和五帧图片。删除后立即重新采集 5 帧。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除并重新录入", (dialog, which) -> {
                    identityLifecycle.deleteIdentity(id);
                    existingIdentityContext = "";
                    viewedHistoryVersion = -1;
                    appendOldCentroids.clear();
                    appendOldEffectiveSamples.clear();
                    editName.setEnabled(true);
                    editName.setText(id);
                    identityGuard.reset();
                    guardGeneration = identityGuard.captureGeneration();
                    startEnrollmentCapture(id, EnrollmentIntent.REPLACE_AFTER_DELETE);
                })
                .show();
    }

    private List<byte[]> encodeEnrollmentThumbnails() {
        if (enrollmentThumbnails.size() != ENROLLMENT_SAMPLES) throw new IllegalStateException("five aligned thumbnails required");
        List<byte[]> out = new ArrayList<>();
        for (Bitmap bitmap : enrollmentThumbnails) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (bitmap == null || !bitmap.compress(Bitmap.CompressFormat.WEBP, 92, bytes)) {
                throw new IllegalStateException("cannot encode aligned history thumbnail");
            }
            out.add(bytes.toByteArray());
        }
        return out;
    }

    private void saveActiveReference(String identity, ModelVariant variant, List<float[]> newEmbeddings,
                                     float[] activeCentroid, boolean append) {
        List<float[]> references = new ArrayList<>();
        if (append) {
            EnrollmentReferenceCodec.Record previous = archiveStore.loadReference(identity, variant);
            if (previous != null) for (float[] embedding : previous.embeddings) references.add(embedding.clone());
        }
        if (newEmbeddings != null) for (float[] embedding : newEmbeddings) references.add(embedding.clone());
        while (references.size() > 20) references.remove(0);
        float[] scores = new float[references.size()];
        for (int i = 0; i < references.size(); i++) scores[i] = VectorMath.cosine(references.get(i), activeCentroid);
        archiveStore.saveReference(identity, variant, new EnrollmentReferenceCodec.Record(references, scores));
    }

    private EnumMap<ModelVariant, Float> guardEmpiricalThresholds() {
        EnumMap<ModelVariant, Float> out = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            ThresholdCalibrator.Result result = computeCalibration(variant);
            if (result != null && result.available && Float.isFinite(result.suggestedThreshold)) {
                out.put(variant, result.suggestedThreshold);
            }
        }
        return out;
    }

'''
main = once(main, "    private void renderEnrollmentMicroscope(ModelVariant variant) {\n",
            helpers + "    private void renderEnrollmentMicroscope(ModelVariant variant) {\n", "insert R5 helpers")

# Calibration refactor so Guard can obtain per-model empirical thresholds.
calibration = '''    private ThresholdCalibrator.Result computeCalibration(ModelVariant variant) {
        if (faceStore == null || archiveStore == null || variant == null) return null;
        Set<String> names = faceStore.identityNames();
        List<String> calibratedNames = new ArrayList<>();
        List<Float> genuine = new ArrayList<>();
        for (String name : names) {
            float[] template = faceStore.template(name, variant);
            EnrollmentReferenceCodec.Record record = archiveStore.loadReference(name, variant);
            if (template == null || record == null || record.embeddings.isEmpty() || record.genuineScores.length == 0) continue;
            calibratedNames.add(name);
            for (float score : record.genuineScores) if (Float.isFinite(score)) genuine.add(score);
        }
        List<Float> impostor = new ArrayList<>();
        for (int i=0;i<calibratedNames.size();i++) {
            float[] a = faceStore.template(calibratedNames.get(i), variant);
            for (int j=i+1;j<calibratedNames.size();j++) {
                float[] b = faceStore.template(calibratedNames.get(j), variant);
                if (a != null && b != null && a.length == b.length) impostor.add(VectorMath.cosine(a,b));
            }
        }
        return ThresholdCalibrator.calibrate(calibratedNames.size(), toArray(genuine), toArray(impostor));
    }

    private void refreshCalibration(ModelVariant variant) {
        if (calibrationPanel == null || variant == null) return;
        lastCalibration = computeCalibration(variant);
        if (lastCalibration != null) calibrationPanel.setResult(variant, lastCalibration);
    }

'''
main = between(main, "    private void refreshCalibration(", "    private static float[] toArray(", calibration, "calibration")

# Layout title, R5 card, history strip id.
layout = once(layout, 'android:text="FaceLiVT R4 · 深层人脸显微镜"', 'android:text="FaceLiVT R5 · 身份防重学习显微镜"', "layout title")
layout = once(layout, 'android:text="R4 · 几何 / 向量 / 18 Block 三层显微镜"', 'android:text="R5 · Guard / 历史五帧 / 几何 / 向量 / 18 Block"', "layout hint")
layout = once(layout, 'android:text="先观察每一帧质量，再构建三套模型各自的质量加权模板。"', 'android:text="先做录入前身份防重；确认新身份后再建立三模型质量加权模板。"', "layout intro")
control_anchor = '''                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="52dp"
                        android:layout_marginTop="8dp"
                        android:orientation="horizontal">
'''
panel_xml = '''                    <com.qujindai.facelivtlab.IdentityGuardPanel
                        android:id="@+id/identityGuardPanel"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp" />

'''
layout = once(layout, control_anchor, panel_xml + control_anchor, "guard panel layout")
history_label = '''                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="10dp"
                        android:text="采样带 · 每帧都是模板建设证据"
                        android:textColor="#FFFFFF"
                        android:textStyle="bold" />
'''
history_label_new = '''                    <TextView
                        android:id="@+id/txtHistoryStrip"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="10dp"
                        android:text="采样带 · 每帧都是模板建设证据"
                        android:textColor="#FFFFFF"
                        android:textStyle="bold" />
'''
layout = once(layout, history_label, history_label_new, "history strip label")

# Release version now, so compile and R5 source contract can validate wiring once workflow artifact is renamed.
build = once(build, "        versionCode 6\n        versionName '0.4.0'\n",
             "        versionCode 7\n        versionName '0.5.0'\n", "release version")

MAIN.write_text(main, encoding="utf-8")
LAYOUT.write_text(layout, encoding="utf-8")
BUILD.write_text(build, encoding="utf-8")
print("R5 main integration patch applied")

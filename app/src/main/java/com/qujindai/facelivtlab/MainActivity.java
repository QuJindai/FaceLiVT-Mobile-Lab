package com.qujindai.facelivtlab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 10;
    private static final long FRAME_INTERVAL_MS = 180;
    private static final int ENROLLMENT_SAMPLES = 5;

    enum Page { ENROLLMENT, RECOGNITION }

    private PreviewView previewView;
    private ImageView imgDegraded;
    private ImageView imgProbeFrame;
    private ImageView imgAlignedProbe;
    private final ImageView[] sampleFaces = new ImageView[ENROLLMENT_SAMPLES];
    private FaceOverlayView faceOverlay;
    private TextView txtResult;
    private TextView txtMetrics;
    private TextView txtPerf;
    private TextView txtThreshold;
    private TextView txtActionHint;
    private TextView txtEnrollmentLiveQuality;
    private TextView txtEnrollmentArchive;
    private TextView txtEnrollmentFormula;
    private TextView txtRecognitionQuality;
    private TextView txtPipeline;
    private TextView txtRecognitionFormula;
    private Spinner spinnerProfile;
    private Spinner spinnerRecognitionProfile;
    private Spinner spinnerModel;
    private Spinner spinnerEnrollmentInspectModel;
    private EditText editName;
    private Button btnEnroll;
    private Button btnSwitch;
    private Button btnExport;
    private Button tabEnrollment;
    private Button tabRecognition;
    private View pageEnrollment;
    private View pageRecognition;
    private SimilarityMatrixView similarityMatrix;
    private EmbeddingScatterView embeddingScatter;
    private TopKBarView topKChart;
    private TrendChartView trendChart;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicInteger enrollmentRemaining = new AtomicInteger(0);
    private final EnumMap<ModelVariant, TemporalEmbeddingBuffer> fusion = new EnumMap<>(ModelVariant.class);
    private final EnumMap<ModelVariant, PerformanceWindow> performance = new EnumMap<>(ModelVariant.class);
    private final SessionLogger sessionLogger = new SessionLogger();
    private final RecognitionTrend recognitionTrend = new RecognitionTrend(30);

    private volatile String enrollmentName;
    private volatile DegradationProfile profile = DegradationProfile.P480;
    private volatile ModelMode modelMode = ModelMode.S;
    private volatile ModelVariant inspectVariant = ModelVariant.S;
    private volatile float threshold = 0.45f;
    private volatile int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private volatile boolean cameraReady = false;
    private volatile Page currentPage = Page.ENROLLMENT;
    private long lastFrameMs = 0;
    private long noTrackingSequence = 0;
    private String enrollmentProfileAtStart = "";

    private EnrollmentSession enrollmentSession = new EnrollmentSession();
    private EnrollmentSession completedEnrollmentSession;
    private FaceDetector detector;
    private RecognizerBank recognizerBank;
    private FaceStore faceStore;
    private EnrollmentArchiveStore archiveStore;
    private ProcessCameraProvider cameraProvider;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        for (ModelVariant variant : ModelVariant.values()) {
            fusion.put(variant, new TemporalEmbeddingBuffer(5));
            performance.put(variant, new PerformanceWindow(30));
        }
        bindViews();
        faceStore = new FaceStore(this);
        archiveStore = new EnrollmentArchiveStore(this);
        recognizerBank = new RecognizerBank(getApplicationContext());
        setupUi();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.04f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);
        txtResult.setText("R3 已就绪 · 默认进入录入显微镜");
        showPage(Page.ENROLLMENT);
        updateActionState();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void applySystemBarInsets() {
        View topOverlay = findViewById(R.id.topOverlay);
        View bottomControls = findViewById(R.id.bottomControls);
        int topLeft = topOverlay.getPaddingLeft();
        int topTop = topOverlay.getPaddingTop();
        int topRight = topOverlay.getPaddingRight();
        int topBottom = topOverlay.getPaddingBottom();
        int bottomLeft = bottomControls.getPaddingLeft();
        int bottomTop = bottomControls.getPaddingTop();
        int bottomRight = bottomControls.getPaddingRight();
        int bottomBottom = bottomControls.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            topOverlay.setPadding(topLeft + bars.left, topTop + bars.top,
                    topRight + bars.right, topBottom);
            bottomControls.setPadding(bottomLeft + bars.left, bottomTop,
                    bottomRight + bars.right, bottomBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(findViewById(R.id.root));
    }

    private void bindViews() {
        previewView = findViewById(R.id.previewView);
        imgDegraded = findViewById(R.id.imgDegraded);
        imgProbeFrame = findViewById(R.id.imgProbeFrame);
        imgAlignedProbe = findViewById(R.id.imgAlignedProbe);
        faceOverlay = findViewById(R.id.faceOverlay);
        sampleFaces[0] = findViewById(R.id.sampleFace1);
        sampleFaces[1] = findViewById(R.id.sampleFace2);
        sampleFaces[2] = findViewById(R.id.sampleFace3);
        sampleFaces[3] = findViewById(R.id.sampleFace4);
        sampleFaces[4] = findViewById(R.id.sampleFace5);
        txtResult = findViewById(R.id.txtResult);
        txtMetrics = findViewById(R.id.txtMetrics);
        txtPerf = findViewById(R.id.txtPerf);
        txtThreshold = findViewById(R.id.txtThreshold);
        txtActionHint = findViewById(R.id.txtActionHint);
        txtEnrollmentLiveQuality = findViewById(R.id.txtEnrollmentLiveQuality);
        txtEnrollmentArchive = findViewById(R.id.txtEnrollmentArchive);
        txtEnrollmentFormula = findViewById(R.id.txtEnrollmentFormula);
        txtRecognitionQuality = findViewById(R.id.txtRecognitionQuality);
        txtPipeline = findViewById(R.id.txtPipeline);
        txtRecognitionFormula = findViewById(R.id.txtRecognitionFormula);
        spinnerProfile = findViewById(R.id.spinnerProfile);
        spinnerRecognitionProfile = findViewById(R.id.spinnerRecognitionProfile);
        spinnerModel = findViewById(R.id.spinnerModel);
        spinnerEnrollmentInspectModel = findViewById(R.id.spinnerEnrollmentInspectModel);
        editName = findViewById(R.id.editName);
        btnEnroll = findViewById(R.id.btnEnroll);
        btnSwitch = findViewById(R.id.btnSwitch);
        btnExport = findViewById(R.id.btnExport);
        tabEnrollment = findViewById(R.id.tabEnrollment);
        tabRecognition = findViewById(R.id.tabRecognition);
        pageEnrollment = findViewById(R.id.pageEnrollment);
        pageRecognition = findViewById(R.id.pageRecognition);
        similarityMatrix = findViewById(R.id.similarityMatrix);
        embeddingScatter = findViewById(R.id.embeddingScatter);
        topKChart = findViewById(R.id.topKChart);
        trendChart = findViewById(R.id.trendChart);

        tabEnrollment.setOnClickListener(v -> showPage(Page.ENROLLMENT));
        tabRecognition.setOnClickListener(v -> showPage(Page.RECOGNITION));
        btnEnroll.setOnClickListener(v -> beginEnrollment());
        btnSwitch.setOnClickListener(v -> {
            cameraReady = false;
            updateActionState();
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            clearFusion();
            bindCameraUseCases();
        });
        btnExport.setOnClickListener(v -> exportSession());

        editName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateActionState();
                if (enrollmentRemaining.get() == 0 && archiveStore != null) {
                    String archive = archiveStore.load(s.toString().trim());
                    if (!archive.isEmpty()) txtEnrollmentArchive.setText(archive);
                }
            }
        });
    }

    private void setupUi() {
        DegradationProfile[] profiles = DegradationProfile.values();
        ArrayAdapter<DegradationProfile> profileAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, profiles);
        profileAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerProfile.setAdapter(profileAdapter);
        spinnerRecognitionProfile.setAdapter(profileAdapter);
        spinnerProfile.setSelection(3);
        spinnerRecognitionProfile.setSelection(3);
        spinnerProfile.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> setProfile(position, profiles, spinnerRecognitionProfile)));
        spinnerRecognitionProfile.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> setProfile(position, profiles, spinnerProfile)));

        ModelMode[] modes = ModelMode.values();
        ArrayAdapter<ModelMode> modeAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, modes);
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerModel.setAdapter(modeAdapter);
        spinnerModel.setSelection(0);
        spinnerModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            modelMode = modes[position];
            clearFusion();
            recognitionTrend.clear();
            renderRecognitionMicroscope(null, new ArrayList<>(), null, 0, 0, 0, 0, 0, false);
        }));

        ModelVariant[] variants = ModelVariant.values();
        ArrayAdapter<ModelVariant> inspectAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, variants);
        inspectAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerEnrollmentInspectModel.setAdapter(inspectAdapter);
        spinnerEnrollmentInspectModel.setSelection(1);
        spinnerEnrollmentInspectModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            inspectVariant = variants[position];
            renderEnrollmentMicroscope(inspectVariant);
        }));

        SeekBar seek = findViewById(R.id.seekThreshold);
        updateThreshold(seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateThreshold(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setProfile(int position, DegradationProfile[] profiles, Spinner peer) {
        if (position < 0 || position >= profiles.length) return;
        profile = profiles[position];
        if (peer.getSelectedItemPosition() != position) peer.setSelection(position);
        clearFusion();
        recognitionTrend.clear();
    }

    private void showPage(Page page) {
        currentPage = page;
        pageEnrollment.setVisibility(page == Page.ENROLLMENT ? View.VISIBLE : View.GONE);
        pageRecognition.setVisibility(page == Page.RECOGNITION ? View.VISIBLE : View.GONE);
        tabEnrollment.setBackgroundResource(page == Page.ENROLLMENT ? R.drawable.r3_tab_active : R.drawable.r3_tab_inactive);
        tabRecognition.setBackgroundResource(page == Page.RECOGNITION ? R.drawable.r3_tab_active : R.drawable.r3_tab_inactive);
        clearFusion();
        recognitionTrend.clear();
        updateActionState();
    }

    private void updateActionState() {
        if (btnEnroll == null || btnSwitch == null || btnExport == null || editName == null || txtActionHint == null) return;
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasName = !editName.getText().toString().trim().isEmpty();
        boolean enrolling = enrollmentName != null && enrollmentRemaining.get() > 0;
        int csvRows = sessionLogger.size();

        btnEnroll.setEnabled(cameraReady && hasName && !enrolling && currentPage == Page.ENROLLMENT);
        btnSwitch.setEnabled(cameraReady);
        btnExport.setEnabled(sessionLogger.size() > 0);
        tabRecognition.setEnabled(!enrolling);

        if (enrolling) {
            btnEnroll.setText("采集中 " + (ENROLLMENT_SAMPLES - enrollmentRemaining.get()) + "/" + ENROLLMENT_SAMPLES);
        } else {
            btnEnroll.setText("开始质量录入 ×5");
        }
        btnSwitch.setText(cameraReady ? "切相机" : "初始化");

        if (!hasPermission) {
            txtActionHint.setText("状态：需要相机权限");
        } else if (!cameraReady) {
            txtActionHint.setText("状态：相机初始化中");
        } else if (enrolling) {
            txtActionHint.setText("录入显微镜：" + enrollmentName + " · 还需 " + enrollmentRemaining.get() + " 帧，完成后才构建模板");
        } else if (currentPage == Page.ENROLLMENT) {
            txtActionHint.setText(hasName ? "录入显微镜：可开始 5 帧质量建档" : "录入显微镜：输入姓名/编号后开始建档");
        } else {
            txtActionHint.setText(csvRows > 0 ? "检测显微镜：已记录 " + csvRows + " 条证据，可导出 CSV" : "检测显微镜：实时展示像素→决策完整链");
        }
    }

    private void updateThreshold(int progress) {
        threshold = 0.20f + progress * 0.006f;
        txtThreshold.setText(String.format(Locale.US, "身份阈值 %.2f · 质量门 Q≥%.2f", threshold, FaceQuality.QUALITY_GATE));
        if (topKChart != null) topKChart.setResults(new ArrayList<>(), threshold);
    }

    private void beginEnrollment() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) { Toast.makeText(this, "先输入姓名或编号", Toast.LENGTH_SHORT).show(); return; }
        if (!cameraReady) { Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show(); return; }
        enrollmentSession = new EnrollmentSession();
        completedEnrollmentSession = null;
        enrollmentName = name;
        enrollmentProfileAtStart = profile.label;
        enrollmentRemaining.set(ENROLLMENT_SAMPLES);
        for (ImageView image : sampleFaces) image.setImageDrawable(null);
        similarityMatrix.setMatrix(null);
        embeddingScatter.setProjection(null, 0);
        txtEnrollmentArchive.setText("正在建立 " + name + " 的质量档案……每帧记录图像质量、姿态、关键点和 XS/S/M embedding。\n本轮摄像头档位：" + enrollmentProfileAtStart);
        txtEnrollmentFormula.setText("公式链等待 5 帧数值代入……");
        clearFusion();
        txtResult.setText("质量录入 " + name + " · 还需 5 帧");
        updateActionState();
    }

    private void exportSession() {
        if (sessionLogger.size() == 0) { Toast.makeText(this, "暂无检测记录可导出", Toast.LENGTH_SHORT).show(); return; }
        try {
            File file = sessionLogger.exportCsv(this);
            txtResult.setText("显微镜 CSV 已导出 · " + sessionLogger.size() + " 条\n" + file.getAbsolutePath());
        } catch (Exception e) {
            txtResult.setText("CSV 导出失败: " + e.getClass().getSimpleName());
        }
        updateActionState();
    }

    private void startCamera() {
        cameraReady = false;
        updateActionState();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                cameraReady = false;
                txtResult.setText("相机启动失败");
                updateActionState();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) { cameraReady = false; updateActionState(); return; }
        cameraProvider.unbindAll();
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyze);
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        try {
            cameraProvider.bindToLifecycle(this, selector, preview, analysis);
            cameraReady = true;
            updateActionState();
        } catch (Exception e) {
            cameraReady = false;
            txtResult.setText("相机绑定失败: " + e.getClass().getSimpleName());
            updateActionState();
        }
    }

    private void analyze(@NonNull ImageProxy imageProxy) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFrameMs < FRAME_INTERVAL_MS || !busy.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }
        lastFrameMs = now;
        final Bitmap raw;
        final int sourceW;
        final int sourceH;
        try {
            Bitmap bitmap = imageProxy.toBitmap();
            raw = rotate(bitmap, imageProxy.getImageInfo().getRotationDegrees());
            sourceW = raw.getWidth();
            sourceH = raw.getHeight();
        } catch (Exception e) {
            imageProxy.close();
            busy.set(false);
            return;
        }
        imageProxy.close();

        DegradationProfile active = profile;
        Bitmap degraded = FrameDegrader.apply(raw, active);
        int[] assisted = LowResPolicy.assistedSize(degraded.getWidth(), degraded.getHeight());
        Bitmap detectorBitmap = assisted[0] == degraded.getWidth() && assisted[1] == degraded.getHeight()
                ? degraded : Bitmap.createScaledBitmap(degraded, assisted[0], assisted[1], true);
        runOnUiThread(() -> {
            imgDegraded.setImageBitmap(degraded);
            if (currentPage == Page.RECOGNITION) imgProbeFrame.setImageBitmap(detectorBitmap);
        });

        long detectStart = SystemClock.elapsedRealtimeNanos();
        detector.process(InputImage.fromBitmap(detectorBitmap, 0))
                .addOnSuccessListener(cameraExecutor, faces -> {
                    long detectMs = elapsedMs(detectStart);
                    handleFaces(faces, degraded, detectorBitmap, sourceW, sourceH, active, detectMs);
                })
                .addOnFailureListener(cameraExecutor, e -> runOnUiThread(() -> txtResult.setText("检测失败")))
                .addOnCompleteListener(cameraExecutor, task -> busy.set(false));
    }

    private void handleFaces(List<Face> faces, Bitmap degraded, Bitmap detectorBitmap,
                             int sourceW, int sourceH, DegradationProfile active, long detectMs) {
        ThermalProbe.Snapshot thermal = ThermalProbe.read(this);
        if (faces.isEmpty()) {
            runOnUiThread(() -> {
                txtResult.setText("未检测到人脸");
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, 0, 0, detectMs));
                txtPerf.setText(thermalLine(thermal));
                if (currentPage == Page.ENROLLMENT) txtEnrollmentLiveQuality.setText("实时质量：未检测到人脸");
                else {
                    txtRecognitionQuality.setText("Probe 质量：未检测到人脸");
                    faceOverlay.clear();
                    txtPipeline.setText("frame → degrade → detect " + detectMs + "ms → 无人脸，链路在检测阶段停止");
                }
            });
            return;
        }

        Face face = faces.stream()
                .max(Comparator.comparingInt(f -> f.getBoundingBox().width() * f.getBoundingBox().height()))
                .orElse(faces.get(0));
        int faceW = Math.max(1, Math.round(face.getBoundingBox().width() * degraded.getWidth() / (float) detectorBitmap.getWidth()));
        int faceH = Math.max(1, Math.round(face.getBoundingBox().height() * degraded.getHeight() / (float) detectorBitmap.getHeight()));
        Integer tracked = face.getTrackingId();
        int trackingId = tracked != null ? tracked : -1 - (int)(++noTrackingSequence & 0x3fffffff);

        try {
            long alignStart = SystemClock.elapsedRealtimeNanos();
            Bitmap aligned = FaceAligner.align(detectorBitmap, face);
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());

            if (currentPage == Page.ENROLLMENT) {
                runOnUiThread(() -> txtEnrollmentLiveQuality.setText("实时质量 · " + quality.compactLine()));
                if (enrollmentRemaining.get() > 0 && enrollmentName != null) {
                    handleEnrollment(aligned, quality, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
                } else {
                    runOnUiThread(() -> {
                        txtResult.setText("录入显微镜 · 已找到人脸，等待开始建档");
                        txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                        txtPerf.setText("align " + alignMs + "ms | " + thermalLine(thermal));
                    });
                }
            } else {
                runOnUiThread(() -> {
                    imgProbeFrame.setImageBitmap(detectorBitmap);
                    imgAlignedProbe.setImageBitmap(aligned);
                    faceOverlay.setFace(face, detectorBitmap.getWidth(), detectorBitmap.getHeight());
                    txtRecognitionQuality.setText(quality.compactLine() + "\n质量门：" + (quality.passesProbeGate() ? "PASS" : "FAIL") + " (Q≥0.35)");
                });
                handleRecognition(aligned, trackingId, quality, degraded, detectorBitmap,
                        sourceW, sourceH, active, faceW, faceH, detectMs, alignMs, thermal);
            }
        } catch (Exception e) {
            runOnUiThread(() -> txtResult.setText("显微镜链路失败: " + e.getClass().getSimpleName() + " · " + safeMessage(e)));
        }
    }

    private void handleEnrollment(Bitmap aligned, FaceQuality.Snapshot quality,
                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                  DegradationProfile active, int faceW, int faceH,
                                  long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        String name = enrollmentName;
        EnumMap<ModelVariant, RecognizerBank.TimedEmbedding> all = recognizerBank.embedAll(aligned);
        StringBuilder timings = new StringBuilder();
        for (ModelVariant variant : ModelVariant.values()) {
            RecognizerBank.TimedEmbedding te = all.get(variant);
            enrollmentSession.add(variant, te.embedding, quality);
            performance.get(variant).add(detectMs, te.inferMs, detectMs + alignMs + te.inferMs);
            if (timings.length() > 0) timings.append(" | ");
            timings.append(variant.storageKey).append(' ').append(te.inferMs).append("ms");
        }

        int left = enrollmentRemaining.decrementAndGet();
        int index = ENROLLMENT_SAMPLES - left - 1;
        Bitmap thumb = aligned.copy(Bitmap.Config.ARGB_8888, false);
        runOnUiThread(() -> {
            if (index >= 0 && index < sampleFaces.length) sampleFaces[index].setImageBitmap(thumb);
            txtResult.setText(left > 0 ? "质量录入 " + name + " · 还需 " + left + " 帧" : "5 帧采集完成 · 正在构建质量模板");
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
            txtPerf.setText("align " + alignMs + "ms | " + timings + "\n" + thermalLine(thermal));
            updateActionState();
        });

        if (left == 0) finalizeEnrollment(name);
    }

    private void finalizeEnrollment(String name) {
        boolean passAll = true;
        StringBuilder archive = new StringBuilder();
        archive.append("身份：").append(name).append('\n');
        archive.append("录入档位：").append(enrollmentProfileAtStart).append(" · 样本 ").append(ENROLLMENT_SAMPLES).append(" 帧\n");
        EnrollmentSession.Summary qualitySource = enrollmentSession.summary(ModelVariant.S);
        for (int i = 0; i < qualitySource.qualities.size(); i++) {
            FaceQuality.Snapshot q = qualitySource.qualities.get(i);
            archive.append(String.format(Locale.US,
                    "S%d  Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 点 %.2f 尺寸 %.2f | Y/P/R %.1f/%.1f/%.1f°\n",
                    i+1, q.composite, q.sharpness, q.brightness, q.contrast, q.pose, q.landmarks, q.size,
                    q.yaw, q.pitch, q.roll));
        }
        archive.append("\n模型模板质量：\n");
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
            passAll &= summary.passesEnrollment();
            archive.append(String.format(Locale.US,
                    "%s  Qavg %.3f · Sstable %.4f · D %.4f · %s\n",
                    variant.storageKey, summary.averageQuality, summary.stability, summary.dispersion,
                    summary.passesEnrollment() ? "PASS" : "FAIL"));
        }
        archive.append(passAll ? "\n结论：PASS · 三模型质量加权模板已入库" : "\n结论：FAIL · 本轮档案保留，但不覆盖已有模板；建议重录");

        if (passAll) {
            for (ModelVariant variant : ModelVariant.values()) {
                EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
                faceStore.replaceTemplate(name, variant, summary.centroid, summary.sampleCount);
            }
        }
        archiveStore.save(name, archive.toString());
        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        final boolean finalPassAll = passAll;
        runOnUiThread(() -> {
            txtEnrollmentArchive.setText(archive.toString());
            txtResult.setText(finalPassAll ? "录入完成 · " + name + " · 模板已入库" : "录入质量未达门槛 · 已保留档案，模板未覆盖");
            renderEnrollmentMicroscope(inspectVariant);
            updateActionState();
        });
    }

    private void renderEnrollmentMicroscope(ModelVariant variant) {
        EnrollmentSession session = completedEnrollmentSession;
        if (session == null || session.size(variant) == 0) return;
        EnrollmentSession.Summary s = session.summary(variant);
        similarityMatrix.setMatrix(s.similarityMatrix);
        embeddingScatter.setProjection(s.projection, s.sampleCount);
        StringBuilder cosines = new StringBuilder();
        for (int i=0;i<s.sampleToCentroid.length;i++) {
            if (i>0) cosines.append(", ");
            cosines.append(String.format(Locale.US,"S%d=%.3f",i+1,s.sampleToCentroid[i]));
        }
        txtEnrollmentFormula.setText(String.format(Locale.US,
                "公式链 · %s\n" +
                "Qi=.25Qsharp+.15Qlight+.10Qcontrast+.20Qpose+.15Qlandmark+.15Qsize\n" +
                "当前 Qavg=%.3f · αi=max(Qi,.05)\n" +
                "c=normalize(Σαifi/Σαi) → %dD 模板中心\n" +
                "cos(fi,c): %s\n" +
                "Sstable=mean(cos(fi,c))=%.4f\n" +
                "D=mean(1-cos(fi,c))=%.4f\n" +
                "Pass=(N=%d≥5) ∧ (Qavg %.3f≥.55) ∧ (Sstable %.4f≥.70) ⇒ %s",
                variant.storageKey, s.averageQuality, s.centroid.length, cosines,
                s.stability, s.dispersion, s.sampleCount, s.averageQuality, s.stability,
                s.passesEnrollment() ? "PASS" : "FAIL"));
    }

    private void handleRecognition(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality,
                                   Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                   DegradationProfile active, int faceW, int faceH,
                                   long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        StringBuilder results = new StringBuilder();
        StringBuilder perfText = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        ModelVariant displayVariant = displayVariantForMode();
        List<FaceStore.Match> displayTop = new ArrayList<>();
        long displayInfer = 0L, displayMatch = 0L;
        int displayFused = 0;
        boolean displayAccepted = false;

        for (ModelVariant variant : modelMode.variants()) {
            RecognizerBank.TimedEmbedding te = recognizerBank.embed(variant, aligned);
            float[] fused = fusion.get(variant).push(trackingId, te.embedding);
            int fusedFrames = fusion.get(variant).size();
            long matchStart = SystemClock.elapsedRealtimeNanos();
            List<FaceStore.Match> top = faceStore.topMatches(variant, fused, 3);
            long matchMs = elapsedMs(matchStart);
            FaceStore.Match top1 = top.isEmpty() ? null : top.get(0);
            FaceStore.Match top2 = top.size() > 1 ? top.get(1) : null;
            float similarity = top1 == null ? 0f : top1.similarity;
            float second = top2 == null ? 0f : top2.similarity;
            float margin = top1 == null ? 0f : similarity - second;
            boolean accepted = top1 != null && similarity >= threshold && quality.composite >= FaceQuality.QUALITY_GATE;
            long totalMs = detectMs + alignMs + te.inferMs + matchMs;
            performance.get(variant).add(detectMs, te.inferMs, totalMs);
            sessionLogger.addMicroscope(timestamp, active.label, variant,
                    sourceW, sourceH, degraded.getWidth(), degraded.getHeight(),
                    detectorBitmap.getWidth(), detectorBitmap.getHeight(), faceW, faceH,
                    quality, top1 == null ? "" : top1.name, top2 == null ? "" : top2.name,
                    similarity, margin, threshold, accepted, fusedFrames,
                    detectMs, alignMs, te.inferMs, matchMs, totalMs, thermal);

            if (results.length() > 0) results.append('\n');
            results.append(variant.storageKey).append("  ");
            if (top1 == null) results.append("无模板");
            else results.append(String.format(Locale.US, "%s %.3f · margin %.3f · %s [%df]",
                    accepted ? top1.name : "UNKNOWN", similarity, margin, accepted ? "ACCEPT" : "REJECT", fusedFrames));

            PerformanceWindow window = performance.get(variant);
            if (perfText.length() > 0) perfText.append(" | ");
            perfText.append(String.format(Locale.US, "%s infer %.1fms total %.1fms",
                    variant.storageKey, window.avgInferMs(), window.avgTotalMs()));

            if (variant == displayVariant) {
                displayTop = top;
                displayInfer = te.inferMs;
                displayMatch = matchMs;
                displayFused = fusedFrames;
                displayAccepted = accepted;
            }
        }

        List<FaceStore.Match> finalDisplayTop = displayTop;
        long finalDisplayInfer = displayInfer;
        long finalDisplayMatch = displayMatch;
        int finalDisplayFused = displayFused;
        boolean finalDisplayAccepted = displayAccepted;
        runOnUiThread(() -> {
            txtResult.setText(results.toString());
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs)
                    + " | Tid=" + String.format(Locale.US,"%.2f",threshold) + " | 库=" + faceStore.identityCount() + " | CSV=" + sessionLogger.size());
            txtPerf.setText(perfText + "\n" + thermalLine(thermal));
            renderRecognitionMicroscope(displayVariant, finalDisplayTop, quality, finalDisplayFused,
                    detectMs, alignMs, finalDisplayInfer, finalDisplayMatch, finalDisplayAccepted);
            updateActionState();
        });
    }

    private void renderRecognitionMicroscope(ModelVariant variant, List<FaceStore.Match> top,
                                             FaceQuality.Snapshot quality, int fusedFrames,
                                             long detectMs, long alignMs, long inferMs, long matchMs,
                                             boolean accepted) {
        if (quality == null) {
            topKChart.setResults(top, threshold);
            trendChart.setSeries(recognitionTrend.similarities(), recognitionTrend.qualities(), threshold);
            return;
        }
        float top1 = top.isEmpty() ? 0f : top.get(0).similarity;
        float top2 = top.size() > 1 ? top.get(1).similarity : 0f;
        float margin = top.isEmpty() ? 0f : top1 - top2;
        String top1Name = top.isEmpty() ? "无模板" : top.get(0).name;
        String top2Name = top.size() > 1 ? top.get(1).name : "—";
        recognitionTrend.add(top1, quality.composite);
        topKChart.setResults(top, threshold);
        trendChart.setSeries(recognitionTrend.similarities(), recognitionTrend.qualities(), threshold);
        long total = detectMs + alignMs + inferMs + matchMs;
        txtPipeline.setText(String.format(Locale.US,
                "frame → degrade(%s) → detect %dms → 5pt align %dms → quality Q=%.3f → embed %s %dms → temporal fusion %d/5 → Top-K %dms → %s\n总链路≈%dms",
                profile.label, detectMs, alignMs, quality.composite,
                variant == null ? "?" : variant.storageKey, inferMs, fusedFrames, matchMs,
                accepted ? "ACCEPT" : "REJECT", total));
        txtRecognitionFormula.setText(String.format(Locale.US,
                "公式链 · %s\n" +
                "sk=cos(fprobe,ck) → Top1 %s=%.4f · Top2 %s=%.4f\n" +
                "margin = sTop1 - sTop2 = %.4f\n" +
                "k*=argmax(sk) → %s\n" +
                "Accept=(%.4f≥Tid %.2f) ∧ (Qprobe %.3f≥%.2f)\n" +
                "身份门=%s · 质量门=%s ⇒ %s",
                variant == null ? "?" : variant.storageKey,
                top1Name, top1, top2Name, top2, margin, top1Name,
                top1, threshold, quality.composite, FaceQuality.QUALITY_GATE,
                top1 >= threshold ? "PASS" : "FAIL",
                quality.composite >= FaceQuality.QUALITY_GATE ? "PASS" : "FAIL",
                accepted ? "ACCEPT" : "REJECT"));
    }

    private ModelVariant displayVariantForMode() {
        for (ModelVariant variant : modelMode.variants()) if (variant == ModelVariant.S) return variant;
        ModelVariant[] variants = modelMode.variants();
        return variants.length == 0 ? ModelVariant.S : variants[0];
    }

    private void clearFusion() {
        for (TemporalEmbeddingBuffer buffer : fusion.values()) buffer.clear();
    }

    private static String metricLine(int sourceW, int sourceH, Bitmap degraded, Bitmap detectorBitmap,
                                     DegradationProfile active, int faceW, int faceH, long detectMs) {
        String assist = degraded.getWidth() == detectorBitmap.getWidth() && degraded.getHeight() == detectorBitmap.getHeight()
                ? "无" : detectorBitmap.getWidth() + "x" + detectorBitmap.getHeight();
        return String.format(Locale.US,
                "源 %dx%d → 模拟 %dx%d (%s) → 检测辅助 %s | 有效脸 %dx%d px | detect %dms",
                sourceW, sourceH, degraded.getWidth(), degraded.getHeight(), active.label,
                assist, faceW, faceH, detectMs);
    }

    private static String thermalLine(ThermalProbe.Snapshot thermal) {
        String battery = Float.isNaN(thermal.batteryC) ? "N/A" : String.format(Locale.US, "%.1f°C", thermal.batteryC);
        return "电池 " + battery + " | Thermal " + thermal.thermalLabel;
    }

    private static long elapsedMs(long startNanos) {
        return (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) return "";
        return message.length() > 100 ? message.substring(0, 100) : message;
    }

    private static Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == REQ_CAMERA) {
            cameraReady = false;
            txtResult.setText("需要相机权限才能测试");
            updateActionState();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close();
        try { if (recognizerBank != null) recognizerBank.close(); } catch (Exception ignored) {}
        cameraExecutor.shutdownNow();
    }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void onSelected(int position); }
        private final Callback callback;
        SimpleItemSelectedListener(Callback callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { callback.onSelected(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}

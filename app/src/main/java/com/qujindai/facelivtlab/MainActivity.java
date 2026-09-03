package com.qujindai.facelivtlab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 10;
    private static final long FRAME_INTERVAL_MS = 180;
    private static final int ENROLLMENT_SAMPLES = 5;
    private static final int PROBE_TRAIL_SIZE = 20;

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
    private TextView txtEnrollmentGeometry;
    private TextView txtGeometryMicroscope;
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
    private SeekBar seekThreshold;
    private BlockMicroscopeView enrollmentModelMicroscope;
    private BlockMicroscopeView recognitionModelMicroscope;
    private ModelComparisonView modelComparisonView;
    private MatrixDeltaView deltaXsS;
    private MatrixDeltaView deltaMS;

    private R31CalibrationPanel calibrationPanel;
    private ProbeEmbeddingView probeEmbeddingView;
    private TextView txtProbeEmbeddingInfo;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicInteger enrollmentRemaining = new AtomicInteger(0);
    private final EnumMap<ModelVariant, TemporalEmbeddingBuffer> fusion = new EnumMap<>(ModelVariant.class);
    private final EnumMap<ModelVariant, PerformanceWindow> performance = new EnumMap<>(ModelVariant.class);
    private final SessionLogger sessionLogger = new SessionLogger();
    private final RecognitionTrend recognitionTrend = new RecognitionTrend(30);
    private final MicroscopeSelectionState microscopeSelection = new MicroscopeSelectionState();
    private final EnumMap<ModelVariant, DeepModelStats> latestDeepStats = new EnumMap<>(ModelVariant.class);
    private final List<AlignmentGeometry> enrollmentGeometries = new ArrayList<>();

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

    private List<FaceStore.Match> lastDisplayTop = new ArrayList<>();
    private ThresholdCalibrator.Result lastCalibration;
    private EmbeddingProjector.Model probeProjectionModel;
    private String probeProjectionKey = "";
    private final List<float[]> probeTrail = new ArrayList<>();
    private int lastProbeTrackingId = Integer.MIN_VALUE;
    private volatile long lastDeepDiagnosticMs = 0L;

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
        installR31Panels();
        compactCameraStage();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.04f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);
        txtResult.setText("R4 已就绪 · 几何 + 向量 + 18 Block 深层显微镜");
        showPage(Page.ENROLLMENT);
        refreshCalibration(ModelVariant.S);
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
        txtEnrollmentGeometry = findViewById(R.id.txtEnrollmentGeometry);
        txtGeometryMicroscope = findViewById(R.id.txtGeometryMicroscope);
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
        seekThreshold = findViewById(R.id.seekThreshold);
        enrollmentModelMicroscope = findViewById(R.id.enrollmentModelMicroscope);
        recognitionModelMicroscope = findViewById(R.id.recognitionModelMicroscope);
        modelComparisonView = findViewById(R.id.modelComparisonView);
        deltaXsS = findViewById(R.id.deltaXsS);
        deltaMS = findViewById(R.id.deltaMS);

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
        spinnerModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                applyRecognitionModelSelection(modes[position])));

        ModelVariant[] variants = ModelVariant.values();
        ArrayAdapter<ModelVariant> inspectAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, variants);
        inspectAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerEnrollmentInspectModel.setAdapter(inspectAdapter);
        spinnerEnrollmentInspectModel.setSelection(1);
        spinnerEnrollmentInspectModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            inspectVariant = variants[position];
            renderEnrollmentMicroscope(inspectVariant);
            if (modelMode == ModelMode.COMPARE) {
                MicroscopeSelectionState.Snapshot focused = microscopeSelection.selectFocus(inspectVariant);
                clearFusion();
                recognitionTrend.clear();
                clearProbeProjection();
                refreshCalibration(focused.focus);
                renderRecognitionModelPendingState(focused);
            }
        }));

        updateThreshold(seekThreshold.getProgress());
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateThreshold(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }


    private void applyRecognitionModelSelection(ModelMode selectedMode) {
        modelMode = selectedMode == null ? ModelMode.S : selectedMode;
        MicroscopeSelectionState.Snapshot selection = microscopeSelection.selectMode(modelMode);
        ModelVariant focus = selection.focus;

        if (modelMode != ModelMode.COMPARE) {
            inspectVariant = focus;
            int index = variantIndex(focus);
            if (spinnerEnrollmentInspectModel != null && spinnerEnrollmentInspectModel.getSelectedItemPosition() != index) {
                spinnerEnrollmentInspectModel.setSelection(index);
            }
            renderEnrollmentMicroscope(focus);
        }

        clearFusion();
        recognitionTrend.clear();
        lastDisplayTop = new ArrayList<>();
        clearProbeProjection();
        lastDeepDiagnosticMs = 0L;
        if (recognitionModelMicroscope != null) recognitionModelMicroscope.clearStats(ModelTopology.forVariant(focus));
        refreshCalibration(focus);
        renderRecognitionModelPendingState(selection);
        updateActionState();
    }

    private void renderRecognitionModelPendingState(MicroscopeSelectionState.Snapshot selection) {
        if (selection == null) return;
        ModelVariant focus = selection.focus;
        if (topKChart != null) topKChart.setResults(new ArrayList<>(), threshold);
        if (trendChart != null) trendChart.setSeries(new float[0], new float[0], threshold);

        String modeText = selection.mode == ModelMode.COMPARE
                ? "COMPARE · 显微镜焦点 " + focus.storageKey
                : focus.storageKey;
        if (txtResult != null) txtResult.setText("模型已切换 · " + modeText + " · 等待该模型新帧");
        if (txtRecognitionQuality != null) {
            txtRecognitionQuality.setText("Probe 质量（模型无关）· 等待 " + focus.storageKey + " 新帧");
        }
        if (txtPipeline != null) {
            txtPipeline.setText("模型切换 → " + modeText + " → 清空旧融合/趋势 → 等待新帧；旧模型在途帧不会再回写显微镜");
        }
        if (txtRecognitionFormula != null) {
            txtRecognitionFormula.setText("公式链 · " + focus.storageKey + "\n等待该模型新帧后刷新 Top-K / margin / gate / 512D cosine");
        }
        PerformanceWindow window = performance.get(focus);
        if (txtPerf != null && window != null) {
            txtPerf.setText(String.format(Locale.US,
                    "显微镜焦点 %s · 本帧等待中\n该模型30帧均值 detect %.1f / align %.1f / infer %.1f / match %.1f / total %.1f ms",
                    focus.storageKey, window.avgDetectMs(), window.avgAlignMs(), window.avgInferMs(),
                    window.avgMatchMs(), window.avgTotalMs()));
        }
        if (txtProbeEmbeddingInfo != null) {
            txtProbeEmbeddingInfo.setText("显微镜焦点 " + focus.storageKey + " · 等待该模型 Probe。2D 只观察，最终仍用 512D cosine。");
        }
        if (recognitionModelMicroscope != null) recognitionModelMicroscope.clearStats(ModelTopology.forVariant(focus));
    }

    private static int variantIndex(ModelVariant variant) {
        ModelVariant[] variants = ModelVariant.values();
        for (int i = 0; i < variants.length; i++) if (variants[i] == variant) return i;
        return 0;
    }

    private void installR31Panels() {
        if (!(pageRecognition instanceof ScrollView)) return;
        View child = ((ScrollView) pageRecognition).getChildAt(0);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout content = (LinearLayout) child;

        calibrationPanel = new R31CalibrationPanel(this);
        calibrationPanel.setApplyListener(v -> applySuggestedThreshold());
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = dp(7);
        int thresholdIndex = content.indexOfChild(seekThreshold);
        content.addView(calibrationPanel, Math.max(0, thresholdIndex + 1), panelLp);

        TextView title = new TextView(this);
        title.setText("实时 Probe embedding · 固定 PCA 坐标系");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

        txtProbeEmbeddingInfo = new TextView(this);
        txtProbeEmbeddingInfo.setText("R3.1 录入参考样本后，将当前 Probe 投影到同一固定坐标系。2D 只用于观察。 ");
        txtProbeEmbeddingInfo.setTextColor(Color.rgb(188, 211, 207));
        txtProbeEmbeddingInfo.setTextSize(10.5f);

        probeEmbeddingView = new ProbeEmbeddingView(this);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(8);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(2);
        LinearLayout.LayoutParams plotLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(185));
        plotLp.topMargin = dp(4);

        int topKIndex = content.indexOfChild(topKChart);
        int insert = topKIndex >= 0 ? topKIndex + 1 : content.getChildCount();
        content.addView(title, insert++, titleLp);
        content.addView(txtProbeEmbeddingInfo, insert++, infoLp);
        content.addView(probeEmbeddingView, insert, plotLp);
    }

    private void compactCameraStage() {
        if (previewView == null || previewView.getParent() == null) return;
        View parent = (View) previewView.getParent();
        ViewGroup.LayoutParams params = parent.getLayoutParams();
        if (params != null) {
            params.height = dp(205);
            parent.setLayoutParams(params);
            parent.requestLayout();
        }
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
        if (page == Page.RECOGNITION) refreshCalibration(displayVariantForMode());
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
        btnExport.setEnabled(csvRows > 0);
        tabRecognition.setEnabled(!enrolling);

        if (enrolling) {
            btnEnroll.setText("合格样本 " + (ENROLLMENT_SAMPLES - enrollmentRemaining.get()) + "/" + ENROLLMENT_SAMPLES);
        } else {
            btnEnroll.setText("开始质量录入 ×5");
        }
        btnSwitch.setText(cameraReady ? "切相机" : "初始化");

        if (!hasPermission) {
            txtActionHint.setText("状态：需要相机权限");
        } else if (!cameraReady) {
            txtActionHint.setText("状态：相机初始化中");
        } else if (enrolling) {
            txtActionHint.setText("录入显微镜：还需 " + enrollmentRemaining.get() + " 帧 · 只统计硬门通过且有差异的样本");
        } else if (currentPage == Page.ENROLLMENT) {
            txtActionHint.setText(hasName ? "录入显微镜：硬门控 + 稳定性 + 覆盖性" : "录入显微镜：输入姓名/编号后开始建档");
        } else {
            txtActionHint.setText(csvRows > 0 ? "检测显微镜：已记录 " + csvRows + " 条证据 · 可导出 CSV" : "检测显微镜：像素→512D→决策→经验标定");
        }
    }

    private void updateThreshold(int progress) {
        threshold = 0.20f + progress * 0.006f;
        txtThreshold.setText(String.format(Locale.US, "身份阈值 %.3f · Probe硬门 Q≥%.2f", threshold, FaceQuality.QUALITY_GATE));
        if (topKChart != null) topKChart.setResults(lastDisplayTop, threshold);
        if (trendChart != null) trendChart.setSeries(recognitionTrend.similarities(), recognitionTrend.qualities(), threshold);
    }

    private void applySuggestedThreshold() {
        ThresholdCalibrator.Result result = lastCalibration;
        if (result == null || !result.available || !Float.isFinite(result.suggestedThreshold)) return;
        int progress = Math.round((result.suggestedThreshold - 0.20f) / 0.006f);
        progress = Math.max(0, Math.min(100, progress));
        seekThreshold.setProgress(progress);
        Toast.makeText(this, String.format(Locale.US, "已采用经验阈值 %.3f", threshold), Toast.LENGTH_SHORT).show();
    }

    private void beginEnrollment() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) { Toast.makeText(this, "先输入姓名或编号", Toast.LENGTH_SHORT).show(); return; }
        if (!cameraReady) { Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show(); return; }
        enrollmentSession = new EnrollmentSession();
        completedEnrollmentSession = null;
        enrollmentGeometries.clear();
        enrollmentName = name;
        enrollmentProfileAtStart = profile.label;
        enrollmentRemaining.set(ENROLLMENT_SAMPLES);
        for (ImageView image : sampleFaces) image.setImageDrawable(null);
        similarityMatrix.setMatrix(null);
        embeddingScatter.setProjection(null, 0);
        if (modelComparisonView != null) modelComparisonView.setRows(null);
        if (deltaXsS != null) deltaXsS.setDelta("ΔM_XS,S", null);
        if (deltaMS != null) deltaMS.setDelta("ΔM_M,S", null);
        if (enrollmentModelMicroscope != null) enrollmentModelMicroscope.clearStats(ModelTopology.forVariant(inspectVariant));
        txtEnrollmentArchive.setText("正在建立 " + name + " 的 R4 质量档案。\n先过像素硬门，再筛掉近重复帧；5 张样本同时追求稳定性与覆盖性。\n本轮档位：" + enrollmentProfileAtStart);
        txtEnrollmentFormula.setText("等待合格且有差异的样本……");
        clearFusion();
        txtResult.setText("R4 录入 · 等待第 1 张合格差异帧");
        updateActionState();
    }

    private void exportSession() {
        if (sessionLogger.size() == 0) { Toast.makeText(this, "暂无检测记录可导出", Toast.LENGTH_SHORT).show(); return; }
        try {
            File file = sessionLogger.exportCsv(this);
            txtResult.setText("R4 显微镜 CSV 已导出 · " + sessionLogger.size() + " 条\n" + file.getAbsolutePath());
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
                if (currentPage == Page.ENROLLMENT) {
                    txtEnrollmentLiveQuality.setText("实时质量：未检测到人脸");
                    if (txtEnrollmentGeometry != null) txtEnrollmentGeometry.setText("5点几何显微镜：未检测到人脸");
                } else {
                    txtRecognitionQuality.setText("Probe 质量：未检测到人脸");
                    if (txtGeometryMicroscope != null) txtGeometryMicroscope.setText("5点几何显微镜：未检测到人脸");
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
            FaceAligner.Result alignment = FaceAligner.alignWithGeometry(detectorBitmap, face);
            Bitmap aligned = alignment.aligned;
            AlignmentGeometry geometry = alignment.geometry;
            long alignMs = elapsedMs(alignStart);
            FaceQuality.Snapshot quality = FaceQuality.evaluate(aligned, face, detectorBitmap.getWidth(), detectorBitmap.getHeight());

            if (currentPage == Page.ENROLLMENT) {
                runOnUiThread(() -> {
                    txtEnrollmentLiveQuality.setText("实时质量 · " + quality.compactLine() + "\n入库硬门：" + quality.enrollmentGateReason());
                    renderGeometryMicroscope(geometry, true);
                });
                if (enrollmentRemaining.get() > 0 && enrollmentName != null) {
                    handleEnrollment(aligned, quality, geometry, degraded, detectorBitmap, sourceW, sourceH,
                            active, faceW, faceH, detectMs, alignMs, thermal);
                } else {
                    runOnUiThread(() -> {
                        txtResult.setText("录入显微镜 · 已找到人脸，等待开始建档");
                        txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                        txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms\n" + thermalLine(thermal));
                    });
                }
            } else {
                runOnUiThread(() -> {
                    imgProbeFrame.setImageBitmap(detectorBitmap);
                    imgAlignedProbe.setImageBitmap(aligned);
                    faceOverlay.setFace(face, detectorBitmap.getWidth(), detectorBitmap.getHeight());
                    txtRecognitionQuality.setText(quality.compactLine() + "\nProbe硬门：" + quality.probeGateReason());
                    renderGeometryMicroscope(geometry, false);
                });
                handleRecognition(aligned, trackingId, quality, geometry, degraded, detectorBitmap,
                        sourceW, sourceH, active, faceW, faceH, detectMs, alignMs, thermal);
            }
        } catch (Exception e) {
            runOnUiThread(() -> txtResult.setText("显微镜链路失败: " + e.getClass().getSimpleName() + " · " + safeMessage(e)));
        }
    }

    private void handleEnrollment(Bitmap aligned, FaceQuality.Snapshot quality, AlignmentGeometry geometry,
                                  Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                  DegradationProfile active, int faceW, int faceH,
                                  long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        String name = enrollmentName;
        if (!quality.passesEnrollmentGate()) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 入库硬门 FAIL\n" + quality.enrollmentGateReason());
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
                txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms\n" + thermalLine(thermal));
            });
            return;
        }

        EnumMap<ModelVariant, RecognizerBank.TimedDiagnostic> all = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) all.put(variant, recognizerBank.diagnose(variant, aligned));
        RecognizerBank.TimedDiagnostic sEmbedding = all.get(ModelVariant.S);
        if (sEmbedding == null || !enrollmentSession.isNovelCandidate(ModelVariant.S, sEmbedding.embedding, quality)) {
            runOnUiThread(() -> {
                txtResult.setText("本帧未计入 · 与已采样帧过于重复\n请轻微左右转头/抬低头，让模板获得覆盖性");
                txtPerf.setText("硬门 PASS · 差异门 WAIT\n" + thermalLine(thermal));
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
        int acceptedCount = enrollmentSession.size(ModelVariant.S);
        int left = Math.max(0, ENROLLMENT_SAMPLES - acceptedCount);
        enrollmentRemaining.set(left);
        int index = acceptedCount - 1;
        Bitmap thumb = aligned.copy(Bitmap.Config.ARGB_8888, false);
        runOnUiThread(() -> {
            if (index >= 0 && index < sampleFaces.length) sampleFaces[index].setImageBitmap(thumb);
            txtResult.setText(left > 0
                    ? ("已收合格差异帧 " + acceptedCount + "/5 · 还需 " + left + " 帧")
                    : "5 张合格差异帧完成 · 正在构建三模型模板");
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs));
            txtPerf.setText("本帧 detect " + detectMs + " / align " + alignMs + "ms | diag " + timings + "\n" + thermalLine(thermal));
            renderDeepModelStats(inspectVariant, latestDeepStats.get(inspectVariant), true);
            updateActionState();
        });
        if (left == 0) finalizeEnrollment(name);
    }

    private void finalizeEnrollment(String name) {
        boolean passAll = true;
        StringBuilder archive = new StringBuilder();
        archive.append("身份：").append(name).append('\n');
        archive.append("录入档位：").append(enrollmentProfileAtStart).append(" · 合格差异样本 ").append(ENROLLMENT_SAMPLES).append(" 帧\n");
        EnrollmentSession.Summary qualitySource = enrollmentSession.summary(ModelVariant.S);
        for (int i = 0; i < qualitySource.qualities.size(); i++) {
            FaceQuality.Snapshot q = qualitySource.qualities.get(i);
            AlignmentGeometry g = i < enrollmentGeometries.size() ? enrollmentGeometries.get(i) : null;
            archive.append(String.format(Locale.US,
                    "S%d  Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 点 %.2f 尺寸 %.2f | Y/P/R %.1f/%.1f/%.1f° | Align %s | Hard %s\n",
                    i+1, q.composite, q.sharpness, q.brightness, q.contrast, q.pose, q.landmarks, q.size,
                    q.yaw, q.pitch, q.roll, geometryArchive(g), q.enrollmentGateReason()));
        }
        archive.append("\n模型模板质量：稳定性 ≠ 覆盖性\n");
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
            passAll &= summary.passesEnrollment();
            archive.append(String.format(Locale.US,
                    "%s  Qavg %.3f · Sstable %.4f · D %.4f · Cover %.3f (Emb %.3f / Pose %.3f) · pair[min %.3f / mean %.3f] · outlier %s · %s\n",
                    variant.storageKey, summary.averageQuality, summary.stability, summary.dispersion,
                    summary.coverage, summary.embeddingCoverage, summary.poseCoverage,
                    summary.minPairCosine, summary.meanPairCosine,
                    summary.outlierIndex < 0 ? "N/A" : "S" + (summary.outlierIndex + 1),
                    summary.passesEnrollment() ? "PASS" : "FAIL"));
        }
        archive.append(passAll
                ? "\n结论：PASS · 三模型质量加权模板与 R4 显微镜参考样本已入库"
                : "\n结论：FAIL · 档案保留但不覆盖已有模板；稳定性或覆盖性不足");

        if (passAll) {
            for (ModelVariant variant : ModelVariant.values()) {
                EnrollmentSession.Summary summary = enrollmentSession.summary(variant);
                faceStore.replaceTemplate(name, variant, summary.centroid, summary.sampleCount);
                archiveStore.saveReference(name, variant,
                        new EnrollmentReferenceCodec.Record(enrollmentSession.embeddings(variant), summary.sampleToCentroid));
            }
        }
        archiveStore.save(name, archive.toString());
        completedEnrollmentSession = enrollmentSession;
        enrollmentName = null;
        final boolean finalPassAll = passAll;
        runOnUiThread(() -> {
            txtEnrollmentArchive.setText(archive.toString());
            txtResult.setText(finalPassAll ? "R4 录入完成 · " + name + " · 模板已入库" : "录入未达门槛 · 模板未覆盖");
            renderEnrollmentMicroscope(inspectVariant);
            renderEnrollmentComparison();
            refreshCalibration(displayVariantForMode());
            updateActionState();
        });
    }

    private void renderEnrollmentMicroscope(ModelVariant variant) {
        EnrollmentSession session = completedEnrollmentSession;
        if (session == null || session.size(variant) == 0) return;
        EnrollmentSession.Summary s = session.summary(variant);
        similarityMatrix.setMatrix(s.similarityMatrix);
        embeddingScatter.setProjection(s.projection, s.sampleCount);
        renderDeepModelStats(variant, latestDeepStats.get(variant), true);
        StringBuilder cosines = new StringBuilder();
        for (int i=0;i<s.sampleToCentroid.length;i++) {
            if (i>0) cosines.append(", ");
            cosines.append(String.format(Locale.US,"S%d=%.3f",i+1,s.sampleToCentroid[i]));
        }
        txtEnrollmentFormula.setText(String.format(Locale.US,
                "公式链 · %s\n" +
                "HardGate_i = Qsharp≥.28 ∧ Qlight≥.28 ∧ Qcontrast≥.25 ∧ Qpose≥.55 ∧ Qlandmark≥.80 ∧ Qsize≥.45\n" +
                "Qi=.25Qsharp+.15Qlight+.10Qcontrast+.20Qpose+.15Qlandmark+.15Qsize\n" +
                "Qavg=%.3f · αi=max(Qi,.05)\n" +
                "c=normalize(Σαifi/Σαi) → %dD 模板中心\n" +
                "cos(fi,c): %s\n" +
                "Sstable=mean(cos(fi,c))=%.4f · D=%.4f\n" +
                "Cemb=%.3f · Cpose=%.3f · Coverage=sqrt(Cemb×Cpose)=%.3f\n" +
                "N×N矩阵: Mij=cos(fi,fj) · minPair %.3f · meanPair %.3f · outlier %s(均值 %.3f)\n" +
                "PCA 坐标不可跨模型直接比较；最终身份判定仍使用512D cosine\n" +
                "Pass=(N=%d≥5) ∧ (Qavg %.3f≥.55) ∧ (Sstable %.4f≥.70) ∧ (Coverage %.3f≥%.2f) ∧ HardGate ⇒ %s",
                variant.storageKey, s.averageQuality, s.centroid.length, cosines,
                s.stability, s.dispersion, s.embeddingCoverage, s.poseCoverage, s.coverage,
                s.minPairCosine, s.meanPairCosine, s.outlierIndex < 0 ? "N/A" : "S" + (s.outlierIndex + 1), s.outlierMeanCosine,
                s.sampleCount, s.averageQuality, s.stability, s.coverage, EnrollmentSession.MIN_COVERAGE,
                s.passesEnrollment() ? "PASS" : "FAIL"));
    }

    private void renderEnrollmentComparison() {
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

    private void handleRecognition(Bitmap aligned, int trackingId, FaceQuality.Snapshot quality, AlignmentGeometry geometry,
                                   Bitmap degraded, Bitmap detectorBitmap, int sourceW, int sourceH,
                                   DegradationProfile active, int faceW, int faceH,
                                   long detectMs, long alignMs, ThermalProbe.Snapshot thermal) throws Exception {
        if (trackingId != lastProbeTrackingId) {
            probeTrail.clear();
            lastProbeTrackingId = trackingId;
        }
        MicroscopeSelectionState.Snapshot frameSelection = microscopeSelection.snapshot();
        DeepModelStats frameDeepStats = maybeRunDeepDiagnostic(aligned, frameSelection);
        StringBuilder results = new StringBuilder();
        long timestamp = System.currentTimeMillis();
        ModelVariant displayVariant = frameSelection.focus;
        List<FaceStore.Match> displayTop = new ArrayList<>();
        RecognitionDecision displayDecision = null;
        float[] displayFusedEmbedding = null;
        long displayInfer = 0L, displayMatch = 0L, displayTotal = 0L;
        int displayFusedFrames = 0;

        for (ModelVariant variant : frameSelection.mode.variants()) {
            RecognizerBank.TimedEmbedding te = recognizerBank.embed(variant, aligned);
            float[] fusedEmbedding = fusion.get(variant).push(trackingId, te.embedding);
            int fusedFrames = fusion.get(variant).size();
            long matchStart = SystemClock.elapsedRealtimeNanos();
            List<FaceStore.Match> top = faceStore.topMatches(variant, fusedEmbedding, 3);
            long matchMs = elapsedMs(matchStart);
            RecognitionDecision decision = RecognitionDecision.from(top, threshold, quality);
            float similarity = Float.isFinite(decision.top1Score) ? decision.top1Score : 0f;
            long totalMs = detectMs + alignMs + te.inferMs + matchMs;
            performance.get(variant).add(detectMs, alignMs, te.inferMs, matchMs, totalMs);
            sessionLogger.addMicroscope(timestamp, active.label, variant,
                    sourceW, sourceH, degraded.getWidth(), degraded.getHeight(),
                    detectorBitmap.getWidth(), detectorBitmap.getHeight(), faceW, faceH,
                    quality, geometry, variant == displayVariant ? frameDeepStats : null,
                    decision.top1Name, decision.top2Name,
                    similarity, decision.margin, threshold, decision.accepted, fusedFrames,
                    detectMs, alignMs, te.inferMs, matchMs, totalMs, thermal);

            if (results.length() > 0) results.append('\n');
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
                displayInfer = te.inferMs;
                displayMatch = matchMs;
                displayTotal = totalMs;
                displayFusedFrames = fusedFrames;
            }
        }

        List<FaceStore.Match> finalTop = displayTop;
        RecognitionDecision finalDecision = displayDecision;
        float[] finalFusedEmbedding = displayFusedEmbedding;
        long finalInfer = displayInfer;
        long finalMatch = displayMatch;
        long finalTotal = displayTotal;
        int finalFusedFrames = displayFusedFrames;
        DeepModelStats finalDeepStats = frameDeepStats;
        runOnUiThread(() -> {
            if (!microscopeSelection.isCurrent(frameSelection)) return;
            lastDisplayTop = finalTop;
            txtResult.setText(results.toString());
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs)
                    + " | Tid=" + String.format(Locale.US,"%.3f",threshold) + " | 库=" + faceStore.identityCount() + " | CSV=" + sessionLogger.size());
            PerformanceWindow window = performance.get(displayVariant);
            txtPerf.setText(String.format(Locale.US,
                    "本帧 %s  detect %d / align %d / infer %d / match %d / total %d ms\n" +
                    "30帧均值  detect %.1f / align %.1f / infer %.1f / match %.1f / total %.1f ms\n%s",
                    displayVariant.storageKey, detectMs, alignMs, finalInfer, finalMatch, finalTotal,
                    window.avgDetectMs(), window.avgAlignMs(), window.avgInferMs(), window.avgMatchMs(), window.avgTotalMs(),
                    thermalLine(thermal)));
            renderRecognitionMicroscope(displayVariant, finalTop, quality, finalFusedFrames,
                    detectMs, alignMs, finalInfer, finalMatch, finalDecision, finalFusedEmbedding);
            renderDeepModelStats(displayVariant, finalDeepStats, false);
            updateActionState();
        });
    }

    private void renderRecognitionMicroscope(ModelVariant variant, List<FaceStore.Match> top,
                                             FaceQuality.Snapshot quality, int fusedFrames,
                                             long detectMs, long alignMs, long inferMs, long matchMs,
                                             RecognitionDecision decision, float[] fusedEmbedding) {
        if (quality == null) {
            topKChart.setResults(top, threshold);
            trendChart.setSeries(recognitionTrend.similarities(), recognitionTrend.qualities(), threshold);
            return;
        }
        float trendScore = decision != null && Float.isFinite(decision.top1Score) ? decision.top1Score : 0f;
        recognitionTrend.add(trendScore, quality.composite);
        topKChart.setResults(top, threshold);
        trendChart.setSeries(recognitionTrend.similarities(), recognitionTrend.qualities(), threshold);
        long total = detectMs + alignMs + inferMs + matchMs;
        boolean accepted = decision != null && decision.accepted;
        txtPipeline.setText(String.format(Locale.US,
                "frame → degrade(%s) → detect %dms → 5pt align %dms → ProbeHardGate %s → embed %s %dms → fusion %d/5 → Top-K %dms → %s\n本帧链路=%dms",
                profile.label, detectMs, alignMs, quality.passesProbeGate() ? "PASS" : "FAIL",
                variant == null ? "?" : variant.storageKey, inferMs, fusedFrames, matchMs,
                accepted ? "ACCEPT" : "REJECT", total));

        String top1Name = decision == null || decision.top1Name.isEmpty() ? "无模板" : decision.top1Name;
        String top1Score = decision == null || !Float.isFinite(decision.top1Score)
                ? "N/A" : String.format(Locale.US,"%.4f",decision.top1Score);
        String top2Line;
        if (decision != null && decision.marginAvailable) {
            top2Line = String.format(Locale.US, "Top2 %s=%.4f · margin = sTop1-sTop2 = %.4f",
                    decision.top2Name, decision.top2Score, decision.margin);
        } else {
            top2Line = "Top2=无 · margin=N/A（候选不足，不能据此评价 1:N 区分度）";
        }
        txtRecognitionFormula.setText(
                "公式链 · " + (variant == null ? "?" : variant.storageKey) + "\n" +
                "sk=cos(fprobe,ck) → Top1 " + top1Name + "=" + top1Score + "\n" +
                top2Line + "\n" +
                "k*=argmax(sk) → " + top1Name + "\n" +
                "Qprobe=" + String.format(Locale.US,"%.3f",quality.composite) +
                " · ProbeHardGate=" + quality.probeGateReason() + "\n" +
                "Accept=(sTop1≥Tid " + String.format(Locale.US,"%.3f",threshold) + ") ∧ ProbeHardGate\n" +
                "身份门=" + (decision != null && decision.identityPass ? "PASS" : "FAIL") +
                " · 质量门=" + (decision != null && decision.qualityPass ? "PASS" : "FAIL") +
                " ⇒ " + (accepted ? "ACCEPT" : "REJECT"));

        renderProbeProjection(variant, decision, fusedEmbedding);
    }

    private DeepModelStats maybeRunDeepDiagnostic(Bitmap aligned, MicroscopeSelectionState.Snapshot diagnosticSelection) {
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

    private void renderProbeProjection(ModelVariant variant, RecognitionDecision decision, float[] fusedEmbedding) {
        if (probeEmbeddingView == null || txtProbeEmbeddingInfo == null) return;
        if (variant == null || decision == null || decision.top1Name.isEmpty() || fusedEmbedding == null) {
            probeEmbeddingView.clearData();
            txtProbeEmbeddingInfo.setText("暂无可投影的 Top1 模板。最终身份判定始终使用原始 512D cosine。 ");
            return;
        }
        EnrollmentReferenceCodec.Record record = archiveStore.loadReference(decision.top1Name, variant);
        float[] centroid = faceStore.template(decision.top1Name, variant);
        if (record == null || record.embeddings.size() < 2 || centroid == null) {
            probeEmbeddingView.clearData();
            txtProbeEmbeddingInfo.setText("Top1 " + decision.top1Name + " 是旧模板：需用 R3.1 重新录入后才能显示固定 PCA Probe 轨迹；识别本身仍可继续。 ");
            return;
        }
        String key = variant.storageKey + "/" + decision.top1Name;
        if (!key.equals(probeProjectionKey) || probeProjectionModel == null) {
            List<float[]> fit = new ArrayList<>();
            for (float[] e : record.embeddings) fit.add(e.clone());
            fit.add(centroid.clone());
            probeProjectionModel = EmbeddingProjector.fit(fit);
            probeProjectionKey = key;
            probeTrail.clear();
        }
        float[] probe = probeProjectionModel.project(fusedEmbedding);
        probeTrail.add(probe.clone());
        while (probeTrail.size() > PROBE_TRAIL_SIZE) probeTrail.remove(0);
        float[] ev = probeProjectionModel.explainedVarianceRatio();
        probeEmbeddingView.setData(probeProjectionModel.trainingProjection(), record.embeddings.size(), probe, probeTrail, ev);
        txtProbeEmbeddingInfo.setText(String.format(Locale.US,
                "Top1 %s · PC1 %.1f%% + PC2 %.1f%% · 粉色=当前Probe，蓝线=轨迹。2D仅观察；最终判定仍使用512D cosine。",
                decision.top1Name, ev[0]*100f, ev[1]*100f));
    }

    private void refreshCalibration(ModelVariant variant) {
        if (calibrationPanel == null || faceStore == null || archiveStore == null || variant == null) return;
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
        lastCalibration = ThresholdCalibrator.calibrate(calibratedNames.size(), toArray(genuine), toArray(impostor));
        calibrationPanel.setResult(variant, lastCalibration);
    }

    private static float[] toArray(List<Float> values) {
        float[] out = new float[values.size()];
        for (int i=0;i<values.size();i++) out[i] = values.get(i);
        return out;
    }

    private ModelVariant displayVariantForMode() {
        return microscopeSelection.snapshot().focus;
    }

    private void clearFusion() {
        for (TemporalEmbeddingBuffer buffer : fusion.values()) buffer.clear();
        lastProbeTrackingId = Integer.MIN_VALUE;
        probeTrail.clear();
    }

    private void clearProbeProjection() {
        probeProjectionModel = null;
        probeProjectionKey = "";
        probeTrail.clear();
        lastProbeTrackingId = Integer.MIN_VALUE;
        if (probeEmbeddingView != null) probeEmbeddingView.clearData();
        if (txtProbeEmbeddingInfo != null) txtProbeEmbeddingInfo.setText("等待实时 Probe 与 R3.1 参考模板进入同一固定 PCA 坐标系。 ");
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

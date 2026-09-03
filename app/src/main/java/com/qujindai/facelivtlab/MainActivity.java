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

    private PreviewView previewView;
    private ImageView imgDegraded;
    private TextView txtResult;
    private TextView txtMetrics;
    private TextView txtPerf;
    private TextView txtThreshold;
    private TextView txtActionHint;
    private Spinner spinnerProfile;
    private Spinner spinnerModel;
    private EditText editName;
    private Button btnEnroll;
    private Button btnSwitch;
    private Button btnExport;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicInteger enrollmentRemaining = new AtomicInteger(0);
    private final EnumMap<ModelVariant, TemporalEmbeddingBuffer> fusion = new EnumMap<>(ModelVariant.class);
    private final EnumMap<ModelVariant, PerformanceWindow> performance = new EnumMap<>(ModelVariant.class);
    private final SessionLogger sessionLogger = new SessionLogger();

    private volatile String enrollmentName;
    private volatile DegradationProfile profile = DegradationProfile.P480;
    private volatile ModelMode modelMode = ModelMode.S;
    private volatile float threshold = 0.45f;
    private volatile int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private volatile boolean cameraReady = false;
    private long lastFrameMs = 0;
    private long noTrackingSequence = 0;

    private FaceDetector detector;
    private RecognizerBank recognizerBank;
    private FaceStore faceStore;
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
        setupUi();
        faceStore = new FaceStore(this);
        recognizerBank = new RecognizerBank(getApplicationContext());

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.04f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);
        txtResult.setText("R2.1 已就绪 · 默认 FaceLiVTv2-S");
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
            topOverlay.setPadding(
                    topLeft + bars.left,
                    topTop + bars.top,
                    topRight + bars.right,
                    topBottom);
            bottomControls.setPadding(
                    bottomLeft + bars.left,
                    bottomTop,
                    bottomRight + bars.right,
                    bottomBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(findViewById(R.id.root));
    }

    private void bindViews() {
        previewView = findViewById(R.id.previewView);
        imgDegraded = findViewById(R.id.imgDegraded);
        txtResult = findViewById(R.id.txtResult);
        txtMetrics = findViewById(R.id.txtMetrics);
        txtPerf = findViewById(R.id.txtPerf);
        txtThreshold = findViewById(R.id.txtThreshold);
        txtActionHint = findViewById(R.id.txtActionHint);
        spinnerProfile = findViewById(R.id.spinnerProfile);
        spinnerModel = findViewById(R.id.spinnerModel);
        editName = findViewById(R.id.editName);
        btnEnroll = findViewById(R.id.btnEnroll);
        btnSwitch = findViewById(R.id.btnSwitch);
        btnExport = findViewById(R.id.btnExport);

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
            @Override public void afterTextChanged(Editable s) { updateActionState(); }
        });
    }

    private void setupUi() {
        DegradationProfile[] profiles = DegradationProfile.values();
        ArrayAdapter<DegradationProfile> profileAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, profiles);
        profileAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerProfile.setAdapter(profileAdapter);
        spinnerProfile.setSelection(3);
        spinnerProfile.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            profile = profiles[position];
            clearFusion();
        }));

        ModelMode[] modes = ModelMode.values();
        ArrayAdapter<ModelMode> modeAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, modes);
        modeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerModel.setAdapter(modeAdapter);
        spinnerModel.setSelection(0);
        spinnerModel.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            modelMode = modes[position];
            clearFusion();
        }));

        SeekBar seek = findViewById(R.id.seekThreshold);
        updateThreshold(seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateThreshold(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateActionState() {
        if (btnEnroll == null || btnSwitch == null || btnExport == null || editName == null || txtActionHint == null) return;

        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean hasName = !editName.getText().toString().trim().isEmpty();
        boolean enrolling = enrollmentName != null && enrollmentRemaining.get() > 0;
        int csvRows = sessionLogger.size();

        btnEnroll.setEnabled(cameraReady && hasName && !enrolling);
        btnSwitch.setEnabled(cameraReady);
        btnExport.setEnabled(sessionLogger.size() > 0);

        if (enrolling) {
            int left = Math.max(0, enrollmentRemaining.get());
            btnEnroll.setText("录入中 " + (5 - left) + "/5");
        } else {
            btnEnroll.setText("三模型录入×5");
        }
        btnSwitch.setText(cameraReady ? "切换相机" : "相机初始化");

        if (!hasPermission) {
            txtActionHint.setText("状态：需要相机权限");
        } else if (!cameraReady) {
            txtActionHint.setText("状态：相机初始化中，按钮暂不可用");
        } else if (enrolling) {
            txtActionHint.setText("状态：正在录入 " + enrollmentName + " · 还需 " + enrollmentRemaining.get() + " 帧");
        } else if (!hasName) {
            txtActionHint.setText(csvRows > 0
                    ? ("状态：相机就绪 · CSV 已记录 " + csvRows + " 条 · 输入姓名/编号可继续录入")
                    : "状态：相机就绪 · 输入姓名/编号后可录入");
        } else if (csvRows == 0) {
            txtActionHint.setText("状态：可录入 · 产生识别结果后可导出 CSV");
        } else {
            txtActionHint.setText("状态：相机就绪 · CSV 已记录 " + csvRows + " 条");
        }
    }

    private void updateThreshold(int progress) {
        threshold = 0.20f + progress * 0.006f;
        txtThreshold.setText(String.format(Locale.US, "识别阈值 %.2f", threshold));
    }

    private void beginEnrollment() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "先输入姓名或编号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cameraReady) {
            Toast.makeText(this, "相机尚未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        enrollmentName = name;
        enrollmentRemaining.set(5);
        clearFusion();
        txtResult.setText("三模型同步录入 " + name + " · 还需 5 帧");
        updateActionState();
    }

    private void exportSession() {
        if (sessionLogger.size() == 0) {
            Toast.makeText(this, "暂无识别记录可导出", Toast.LENGTH_SHORT).show();
            updateActionState();
            return;
        }
        try {
            File file = sessionLogger.exportCsv(this);
            txtResult.setText("CSV 已导出 · " + sessionLogger.size() + " 条\n" + file.getAbsolutePath());
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
        if (cameraProvider == null) {
            cameraReady = false;
            updateActionState();
            return;
        }
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
        runOnUiThread(() -> imgDegraded.setImageBitmap(degraded));

        long detectStart = SystemClock.elapsedRealtimeNanos();
        detector.process(InputImage.fromBitmap(detectorBitmap, 0))
                .addOnSuccessListener(cameraExecutor, faces -> {
                    long detectMs = (SystemClock.elapsedRealtimeNanos() - detectStart) / 1_000_000L;
                    handleFaces(faces, degraded, detectorBitmap, sourceW, sourceH, active, detectMs);
                })
                .addOnFailureListener(cameraExecutor, e -> runOnUiThread(() -> txtResult.setText("检测失败")))
                .addOnCompleteListener(cameraExecutor, task -> busy.set(false));
    }

    private void handleFaces(List<Face> faces, Bitmap degraded, Bitmap detectorBitmap,
                             int sourceW, int sourceH, DegradationProfile active, long detectMs) {
        if (faces.isEmpty()) {
            ThermalProbe.Snapshot thermal = ThermalProbe.read(this);
            runOnUiThread(() -> {
                txtResult.setText("未检测到人脸");
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, 0, 0, detectMs));
                txtPerf.setText(thermalLine(thermal));
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
        ThermalProbe.Snapshot thermal = ThermalProbe.read(this);

        try {
            Bitmap aligned = FaceAligner.align(detectorBitmap, face);
            if (enrollmentRemaining.get() > 0 && enrollmentName != null) {
                handleEnrollment(aligned, trackingId, degraded, detectorBitmap, sourceW, sourceH,
                        active, faceW, faceH, detectMs, thermal);
                return;
            }
            handleRecognition(aligned, trackingId, degraded, detectorBitmap, sourceW, sourceH,
                    active, faceW, faceH, detectMs, thermal);
        } catch (Exception e) {
            runOnUiThread(() -> txtResult.setText("识别失败: " + e.getClass().getSimpleName() + " · " + safeMessage(e)));
        }
    }

    private void handleEnrollment(Bitmap aligned, int trackingId, Bitmap degraded, Bitmap detectorBitmap,
                                  int sourceW, int sourceH, DegradationProfile active, int faceW, int faceH,
                                  long detectMs, ThermalProbe.Snapshot thermal) throws Exception {
        String name = enrollmentName;
        StringBuilder timing = new StringBuilder();
        for (ModelVariant variant : ModelVariant.values()) {
            RecognizerBank.TimedEmbedding te = recognizerBank.embed(variant, aligned);
            faceStore.addSample(name, variant, te.embedding);
            performance.get(variant).add(detectMs, te.inferMs, detectMs + te.inferMs);
            if (timing.length() > 0) timing.append(" · ");
            timing.append(variant.storageKey).append(' ').append(te.inferMs).append("ms");
        }
        int left = enrollmentRemaining.decrementAndGet();
        if (left == 0) enrollmentName = null;
        int totalIds = faceStore.identityCount();
        runOnUiThread(() -> {
            txtResult.setText(left > 0
                    ? ("三模型同步录入 " + name + " · 还需 " + left + " 帧")
                    : ("三模型录入完成 · " + name));
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs)
                    + " | 库=" + totalIds);
            txtPerf.setText(timing + "\n" + thermalLine(thermal));
            updateActionState();
        });
    }

    private void handleRecognition(Bitmap aligned, int trackingId, Bitmap degraded, Bitmap detectorBitmap,
                                   int sourceW, int sourceH, DegradationProfile active, int faceW, int faceH,
                                   long detectMs, ThermalProbe.Snapshot thermal) throws Exception {
        StringBuilder results = new StringBuilder();
        StringBuilder perfText = new StringBuilder();
        long timestamp = System.currentTimeMillis();

        for (ModelVariant variant : modelMode.variants()) {
            RecognizerBank.TimedEmbedding te = recognizerBank.embed(variant, aligned);
            float[] fused = fusion.get(variant).push(trackingId, te.embedding);
            int fusedFrames = fusion.get(variant).size();
            FaceStore.Match match = faceStore.bestMatch(variant, fused);
            float similarity = match == null ? 0f : match.similarity;
            boolean accepted = match != null && similarity >= threshold;
            String top1 = match == null ? "" : match.name;
            long totalMs = detectMs + te.inferMs;
            performance.get(variant).add(detectMs, te.inferMs, totalMs);
            sessionLogger.add(timestamp, active.label, variant,
                    sourceW, sourceH, degraded.getWidth(), degraded.getHeight(),
                    detectorBitmap.getWidth(), detectorBitmap.getHeight(), faceW, faceH,
                    top1, similarity, accepted, fusedFrames, detectMs, te.inferMs, totalMs, thermal);

            if (results.length() > 0) results.append('\n');
            if (match == null) {
                results.append(variant.storageKey).append("  无模板");
            } else if (accepted) {
                results.append(String.format(Locale.US, "%s  %s  %.3f  [%df]", variant.storageKey, match.name, similarity, fusedFrames));
            } else {
                results.append(String.format(Locale.US, "%s  UNKNOWN %.3f (Top1 %s) [%df]", variant.storageKey, similarity, match.name, fusedFrames));
            }

            PerformanceWindow window = performance.get(variant);
            double avgTotal = window.avgTotalMs();
            double fps = avgTotal > 0 ? 1000.0 / avgTotal : 0.0;
            if (perfText.length() > 0) perfText.append(" | ");
            perfText.append(String.format(Locale.US, "%s infer %.1fms total %.1fms %.1ffps",
                    variant.storageKey, window.avgInferMs(), avgTotal, fps));
        }

        int totalIds = faceStore.identityCount();
        runOnUiThread(() -> {
            txtResult.setText(results.toString());
            txtMetrics.setText(metricLine(sourceW, sourceH, degraded, detectorBitmap, active, faceW, faceH, detectMs)
                    + " | 阈值=" + String.format(Locale.US, "%.2f", threshold) + " | 库=" + totalIds + " | CSV=" + sessionLogger.size());
            txtPerf.setText(perfText + "\n" + thermalLine(thermal));
            updateActionState();
        });
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

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) return "";
        return message.length() > 80 ? message.substring(0, 80) : message;
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

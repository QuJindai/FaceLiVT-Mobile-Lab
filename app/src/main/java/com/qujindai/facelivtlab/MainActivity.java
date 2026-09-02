package com.qujindai.facelivtlab;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.SystemClock;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 10;
    private static final long FRAME_INTERVAL_MS = 220;

    private PreviewView previewView;
    private ImageView imgDegraded;
    private TextView txtResult;
    private TextView txtMetrics;
    private TextView txtThreshold;
    private Spinner spinnerProfile;
    private EditText editName;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicInteger enrollmentRemaining = new AtomicInteger(0);
    private volatile String enrollmentName;
    private volatile DegradationProfile profile = DegradationProfile.P480;
    private volatile float threshold = 0.45f;
    private volatile int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private long lastFrameMs = 0;

    private FaceDetector detector;
    private FaceRecognizer recognizer;
    private FaceStore faceStore;
    private ProcessCameraProvider cameraProvider;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupUi();
        faceStore = new FaceStore(this);

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.06f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);

        cameraExecutor.execute(() -> {
            try {
                recognizer = new FaceRecognizer(getApplicationContext());
                runOnUiThread(() -> txtResult.setText("FaceLiVTv2-S 已就绪"));
            } catch (Exception e) {
                runOnUiThread(() -> txtResult.setText("模型加载失败: " + e.getClass().getSimpleName()));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void bindViews() {
        previewView = findViewById(R.id.previewView);
        imgDegraded = findViewById(R.id.imgDegraded);
        txtResult = findViewById(R.id.txtResult);
        txtMetrics = findViewById(R.id.txtMetrics);
        txtThreshold = findViewById(R.id.txtThreshold);
        spinnerProfile = findViewById(R.id.spinnerProfile);
        editName = findViewById(R.id.editName);
        Button btnEnroll = findViewById(R.id.btnEnroll);
        Button btnSwitch = findViewById(R.id.btnSwitch);

        btnEnroll.setOnClickListener(v -> beginEnrollment());
        btnSwitch.setOnClickListener(v -> {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            bindCameraUseCases();
        });
    }

    private void setupUi() {
        DegradationProfile[] profiles = DegradationProfile.values();
        ArrayAdapter<DegradationProfile> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, profiles);
        spinnerProfile.setAdapter(adapter);
        spinnerProfile.setSelection(3);
        spinnerProfile.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> profile = profiles[position]));

        SeekBar seek = findViewById(R.id.seekThreshold);
        updateThreshold(seek.getProgress());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateThreshold(progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
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
        enrollmentName = name;
        enrollmentRemaining.set(5);
        txtResult.setText("开始录入 " + name + "，保持脸在画面中");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                txtResult.setText("相机启动失败");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
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
        } catch (Exception e) {
            txtResult.setText("相机绑定失败: " + e.getClass().getSimpleName());
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
        runOnUiThread(() -> imgDegraded.setImageBitmap(degraded));

        long detectStart = SystemClock.elapsedRealtimeNanos();
        detector.process(InputImage.fromBitmap(degraded, 0))
                .addOnSuccessListener(cameraExecutor, faces -> {
                    long detectMs = (SystemClock.elapsedRealtimeNanos() - detectStart) / 1_000_000L;
                    handleFaces(faces, degraded, sourceW, sourceH, active, detectMs);
                })
                .addOnFailureListener(cameraExecutor, e -> runOnUiThread(() -> txtResult.setText("检测失败")))
                .addOnCompleteListener(cameraExecutor, task -> busy.set(false));
    }

    private void handleFaces(List<Face> faces, Bitmap degraded, int sourceW, int sourceH,
                             DegradationProfile active, long detectMs) {
        if (faces.isEmpty()) {
            runOnUiThread(() -> {
                txtResult.setText("未检测到人脸");
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, active, 0, 0, detectMs, 0));
            });
            return;
        }
        Face face = faces.stream().max(Comparator.comparingInt(f -> f.getBoundingBox().width() * f.getBoundingBox().height())).orElse(faces.get(0));
        int faceW = face.getBoundingBox().width();
        int faceH = face.getBoundingBox().height();

        FaceRecognizer localRecognizer = recognizer;
        if (localRecognizer == null) {
            runOnUiThread(() -> txtResult.setText("模型仍在初始化"));
            return;
        }

        try {
            Bitmap aligned = FaceAligner.align(degraded, face);
            long inferStart = SystemClock.elapsedRealtimeNanos();
            float[] embedding = localRecognizer.embed(aligned);
            long inferMs = (SystemClock.elapsedRealtimeNanos() - inferStart) / 1_000_000L;

            int remain = enrollmentRemaining.get();
            if (remain > 0 && enrollmentName != null) {
                String name = enrollmentName;
                faceStore.addSample(name, embedding);
                int left = enrollmentRemaining.decrementAndGet();
                if (left == 0) enrollmentName = null;
                int totalIds = faceStore.identityCount();
                runOnUiThread(() -> {
                    txtResult.setText(left > 0 ? ("录入 " + name + "：还需 " + left + " 帧") : ("录入完成：" + name));
                    txtMetrics.setText(metricLine(sourceW, sourceH, degraded, active, faceW, faceH, detectMs, inferMs)
                            + " | 库=" + totalIds);
                });
                return;
            }

            FaceStore.Match match = faceStore.bestMatch(embedding);
            final String resultText;
            final float sim;
            if (match == null) {
                resultText = "人脸库为空：先录入";
                sim = 0f;
            } else {
                sim = match.similarity;
                resultText = sim >= threshold
                        ? String.format(Locale.US, "%s  %.3f", match.name, sim)
                        : String.format(Locale.US, "UNKNOWN  %.3f (Top1 %s)", sim, match.name);
            }
            int totalIds = faceStore.identityCount();
            runOnUiThread(() -> {
                txtResult.setText(resultText);
                txtMetrics.setText(metricLine(sourceW, sourceH, degraded, active, faceW, faceH, detectMs, inferMs)
                        + " | 阈值=" + String.format(Locale.US, "%.2f", threshold) + " | 库=" + totalIds);
            });
        } catch (Exception e) {
            runOnUiThread(() -> txtResult.setText("识别失败: " + e.getClass().getSimpleName()));
        }
    }

    private static String metricLine(int sourceW, int sourceH, Bitmap degraded, DegradationProfile active,
                                     int faceW, int faceH, long detectMs, long inferMs) {
        return String.format(Locale.US,
                "源 %dx%d → 模拟 %dx%d (%s) | 脸 %dx%d px | detect %d ms | embed %d ms",
                sourceW, sourceH, degraded.getWidth(), degraded.getHeight(), active.label,
                faceW, faceH, detectMs, inferMs);
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
            txtResult.setText("需要相机权限才能测试");
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (detector != null) detector.close();
        try { if (recognizer != null) recognizer.close(); } catch (Exception ignored) {}
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

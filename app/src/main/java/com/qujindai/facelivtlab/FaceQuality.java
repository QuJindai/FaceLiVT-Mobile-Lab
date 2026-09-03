package com.qujindai.facelivtlab;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Human-readable quality microscope for an aligned face sample. */
public final class FaceQuality {
    public static final float QUALITY_GATE = 0.35f;

    public static final float PROBE_MIN_SHARPNESS = 0.15f;
    public static final float PROBE_MIN_LIGHT = 0.15f;
    public static final float PROBE_MIN_CONTRAST = 0.15f;
    public static final float PROBE_MIN_POSE = 0.35f;
    public static final float PROBE_MIN_LANDMARKS = 0.60f;
    public static final float PROBE_MIN_SIZE = 0.20f;

    public static final float ENROLL_MIN_COMPOSITE = 0.55f;
    public static final float ENROLL_MIN_SHARPNESS = 0.28f;
    public static final float ENROLL_MIN_LIGHT = 0.28f;
    public static final float ENROLL_MIN_CONTRAST = 0.25f;
    public static final float ENROLL_MIN_POSE = 0.55f;
    public static final float ENROLL_MIN_LANDMARKS = 0.80f;
    public static final float ENROLL_MIN_SIZE = 0.45f;

    public static final class Snapshot {
        public final float sharpness;
        public final float brightness;
        public final float contrast;
        public final float pose;
        public final float landmarks;
        public final float size;
        public final float composite;
        public final float yaw;
        public final float pitch;
        public final float roll;
        public final float faceAreaRatio;

        Snapshot(float sharpness, float brightness, float contrast, float pose,
                 float landmarks, float size, float composite,
                 float yaw, float pitch, float roll, float faceAreaRatio) {
            this.sharpness = sharpness;
            this.brightness = brightness;
            this.contrast = contrast;
            this.pose = pose;
            this.landmarks = landmarks;
            this.size = size;
            this.composite = composite;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.faceAreaRatio = faceAreaRatio;
        }

        public String compactLine() {
            return String.format(Locale.US,
                    "Q %.2f | 清晰 %.2f 光照 %.2f 对比 %.2f 姿态 %.2f 关键点 %.2f 尺寸 %.2f | yaw %.1f° pitch %.1f° roll %.1f°",
                    composite, sharpness, brightness, contrast, pose, landmarks, size,
                    yaw, pitch, roll);
        }

        /** Recognition remains deliberately tolerant of cheap-camera images, but catastrophic inputs cannot pass on Q alone. */
        public boolean passesProbeGate() {
            return composite >= QUALITY_GATE &&
                    sharpness >= PROBE_MIN_SHARPNESS &&
                    brightness >= PROBE_MIN_LIGHT &&
                    contrast >= PROBE_MIN_CONTRAST &&
                    pose >= PROBE_MIN_POSE &&
                    landmarks >= PROBE_MIN_LANDMARKS &&
                    size >= PROBE_MIN_SIZE;
        }

        public String probeGateReason() {
            return gateReason(false);
        }

        /** Enrollment is intentionally stricter than recognition so good geometry cannot rescue poor source pixels. */
        public boolean passesEnrollmentGate() {
            return composite >= ENROLL_MIN_COMPOSITE &&
                    sharpness >= ENROLL_MIN_SHARPNESS &&
                    brightness >= ENROLL_MIN_LIGHT &&
                    contrast >= ENROLL_MIN_CONTRAST &&
                    pose >= ENROLL_MIN_POSE &&
                    landmarks >= ENROLL_MIN_LANDMARKS &&
                    size >= ENROLL_MIN_SIZE;
        }

        public String enrollmentGateReason() {
            return gateReason(true);
        }

        private String gateReason(boolean enrollment) {
            List<String> failures = new ArrayList<>();
            if (enrollment) {
                addFailure(failures, composite, ENROLL_MIN_COMPOSITE, "综合Q");
                addFailure(failures, sharpness, ENROLL_MIN_SHARPNESS, "清晰");
                addFailure(failures, brightness, ENROLL_MIN_LIGHT, "光照");
                addFailure(failures, contrast, ENROLL_MIN_CONTRAST, "对比");
                addFailure(failures, pose, ENROLL_MIN_POSE, "姿态");
                addFailure(failures, landmarks, ENROLL_MIN_LANDMARKS, "关键点");
                addFailure(failures, size, ENROLL_MIN_SIZE, "尺寸");
            } else {
                addFailure(failures, composite, QUALITY_GATE, "综合Q");
                addFailure(failures, sharpness, PROBE_MIN_SHARPNESS, "清晰");
                addFailure(failures, brightness, PROBE_MIN_LIGHT, "光照");
                addFailure(failures, contrast, PROBE_MIN_CONTRAST, "对比");
                addFailure(failures, pose, PROBE_MIN_POSE, "姿态");
                addFailure(failures, landmarks, PROBE_MIN_LANDMARKS, "关键点");
                addFailure(failures, size, PROBE_MIN_SIZE, "尺寸");
            }
            return failures.isEmpty() ? "PASS" : String.join(" / ", failures);
        }

        private static void addFailure(List<String> failures, float actual, float threshold, String label) {
            if (actual < threshold) {
                failures.add(String.format(Locale.US, "%s %.2f<%.2f", label, actual, threshold));
            }
        }
    }

    private FaceQuality() {}

    public static Snapshot compose(float sharpness, float brightness, float contrast,
                                   float pose, float landmarks, float size,
                                   float yaw, float pitch, float roll) {
        return compose(sharpness, brightness, contrast, pose, landmarks, size,
                yaw, pitch, roll, 0f);
    }

    public static Snapshot compose(float sharpness, float brightness, float contrast,
                                   float pose, float landmarks, float size,
                                   float yaw, float pitch, float roll, float faceAreaRatio) {
        float qSharp = clamp01(sharpness);
        float qLight = clamp01(brightness);
        float qContrast = clamp01(contrast);
        float qPose = clamp01(pose);
        float qLandmark = clamp01(landmarks);
        float qSize = clamp01(size);
        float composite = clamp01(
                0.25f * qSharp +
                0.15f * qLight +
                0.10f * qContrast +
                0.20f * qPose +
                0.15f * qLandmark +
                0.15f * qSize);
        return new Snapshot(qSharp, qLight, qContrast, qPose, qLandmark, qSize,
                composite, yaw, pitch, roll, Math.max(0f, faceAreaRatio));
    }

    public static float poseScore(float yaw, float pitch, float roll) {
        float y = Math.min(1f, Math.abs(yaw) / 45f);
        float p = Math.min(1f, Math.abs(pitch) / 30f);
        float r = Math.min(1f, Math.abs(roll) / 30f);
        return clamp01(1f - (0.50f * y + 0.30f * p + 0.20f * r));
    }

    /** Evaluate the pixels the recognition model actually receives plus detector geometry. */
    public static Snapshot evaluate(Bitmap aligned, Face face, int frameWidth, int frameHeight) {
        int w = aligned.getWidth();
        int h = aligned.getHeight();
        int[] pixels = new int[Math.max(1, w * h)];
        aligned.getPixels(pixels, 0, w, 0, 0, w, h);

        double sum = 0.0;
        double sumSq = 0.0;
        for (int pixel : pixels) {
            double y = luminance(pixel);
            sum += y;
            sumSq += y * y;
        }
        double mean = sum / pixels.length;
        double variance = Math.max(0.0, sumSq / pixels.length - mean * mean);
        double std = Math.sqrt(variance);

        double gradient = 0.0;
        int gradientCount = 0;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int idx = y * w + x;
                double here = luminance(pixels[idx]);
                if (x + 2 < w) {
                    gradient += Math.abs(here - luminance(pixels[idx + 2]));
                    gradientCount++;
                }
                if (y + 2 < h) {
                    gradient += Math.abs(here - luminance(pixels[idx + 2 * w]));
                    gradientCount++;
                }
            }
        }
        double avgGradient = gradientCount == 0 ? 0.0 : gradient / gradientCount;

        float qSharp = clamp01((float) (avgGradient / 28.0));
        float qLight = clamp01(1f - Math.abs((float) mean - 142f) / 142f);
        float qContrast = clamp01((float) (std / 58.0));

        float yaw = face.getHeadEulerAngleY();
        float pitch = face.getHeadEulerAngleX();
        float roll = face.getHeadEulerAngleZ();
        float qPose = poseScore(yaw, pitch, roll);

        int landmarkCount = 0;
        if (face.getLandmark(FaceLandmark.LEFT_EYE) != null) landmarkCount++;
        if (face.getLandmark(FaceLandmark.RIGHT_EYE) != null) landmarkCount++;
        if (face.getLandmark(FaceLandmark.NOSE_BASE) != null) landmarkCount++;
        if (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null) landmarkCount++;
        if (face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null) landmarkCount++;
        float qLandmarks = landmarkCount / 5f;

        float frameArea = Math.max(1f, (float) frameWidth * frameHeight);
        float faceArea = Math.max(0f, (float) face.getBoundingBox().width() * face.getBoundingBox().height());
        float faceAreaRatio = faceArea / frameArea;
        float qSize = clamp01(faceAreaRatio / 0.08f);

        return compose(qSharp, qLight, qContrast, qPose, qLandmarks, qSize,
                yaw, pitch, roll, faceAreaRatio);
    }

    private static double luminance(int pixel) {
        return 0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}

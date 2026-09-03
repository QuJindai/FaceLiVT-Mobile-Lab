package com.qujindai.facelivtlab;

/** Model-independent geometry evidence for the five-point similarity transform. */
public final class AlignmentGeometry {
    public final int landmarkCount;
    public final float[] sourcePoints;
    public final float[] targetPoints;
    public final float[] transformedPoints;
    public final float eyeDistancePx;
    public final float rollDeg;
    public final float scale;
    public final float translationPx;
    public final float meanResidualPx;
    public final float maxResidualPx;
    public final boolean usedFallback;

    private AlignmentGeometry(int landmarkCount, float[] sourcePoints, float[] targetPoints,
                              float[] transformedPoints, float eyeDistancePx, float rollDeg,
                              float scale, float translationPx, float meanResidualPx,
                              float maxResidualPx, boolean usedFallback) {
        this.landmarkCount = landmarkCount;
        this.sourcePoints = sourcePoints == null ? new float[0] : sourcePoints.clone();
        this.targetPoints = targetPoints == null ? new float[0] : targetPoints.clone();
        this.transformedPoints = transformedPoints == null ? new float[0] : transformedPoints.clone();
        this.eyeDistancePx = eyeDistancePx;
        this.rollDeg = rollDeg;
        this.scale = scale;
        this.translationPx = translationPx;
        this.meanResidualPx = meanResidualPx;
        this.maxResidualPx = maxResidualPx;
        this.usedFallback = usedFallback;
    }

    public static AlignmentGeometry fromTransform(float[] src, float[] dst, float[] affine) {
        if (src == null || dst == null || affine == null || src.length != 10 || dst.length != 10 || affine.length != 6) {
            throw new IllegalArgumentException("five source/target points and 6 affine coefficients are required");
        }
        float[] transformed = new float[10];
        double sum = 0.0;
        float max = 0f;
        for (int i = 0; i < 10; i += 2) {
            float x = src[i], y = src[i + 1];
            float tx = affine[0] * x + affine[1] * y + affine[2];
            float ty = affine[3] * x + affine[4] * y + affine[5];
            transformed[i] = tx;
            transformed[i + 1] = ty;
            float dx = tx - dst[i];
            float dy = ty - dst[i + 1];
            float residual = (float)Math.sqrt(dx * dx + dy * dy);
            sum += residual;
            max = Math.max(max, residual);
        }
        float eyeDx = src[2] - src[0];
        float eyeDy = src[3] - src[1];
        float eyeDistance = (float)Math.sqrt(eyeDx * eyeDx + eyeDy * eyeDy);
        float roll = (float)Math.toDegrees(Math.atan2(eyeDy, eyeDx));
        float scale = (float)Math.sqrt(affine[0] * affine[0] + affine[3] * affine[3]);
        float translation = (float)Math.sqrt(affine[2] * affine[2] + affine[5] * affine[5]);
        return new AlignmentGeometry(5, src, dst, transformed, eyeDistance, roll, scale,
                translation, (float)(sum / 5.0), max, false);
    }

    public static AlignmentGeometry fallback(int landmarkCount) {
        int count = Math.max(0, Math.min(5, landmarkCount));
        return new AlignmentGeometry(count, new float[0], new float[0], new float[0],
                Float.NaN, Float.NaN, Float.NaN, Float.NaN,
                Float.NaN, Float.NaN, true);
    }
}

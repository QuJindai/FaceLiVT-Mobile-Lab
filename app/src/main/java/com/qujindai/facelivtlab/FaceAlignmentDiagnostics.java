package com.qujindai.facelivtlab;

/** Immutable geometric diagnostics for the five-point similarity alignment. */
public final class FaceAlignmentDiagnostics {
    public final int landmarkCount;
    public final boolean usedFallback;
    public final float eyeDistancePx;
    public final float scale;
    public final float rotationDeg;
    public final float meanResidualPx;
    public final float maxResidualPx;

    private FaceAlignmentDiagnostics(int landmarkCount, boolean usedFallback, float eyeDistancePx,
                                     float scale, float rotationDeg,
                                     float meanResidualPx, float maxResidualPx) {
        this.landmarkCount = landmarkCount;
        this.usedFallback = usedFallback;
        this.eyeDistancePx = eyeDistancePx;
        this.scale = scale;
        this.rotationDeg = rotationDeg;
        this.meanResidualPx = meanResidualPx;
        this.maxResidualPx = maxResidualPx;
    }

    public static FaceAlignmentDiagnostics fromSimilarity(float[] sourcePoints, float[] canonicalPoints,
                                                          float[] affine, int landmarkCount,
                                                          boolean usedFallback) {
        if (sourcePoints == null || canonicalPoints == null || affine == null ||
                sourcePoints.length != canonicalPoints.length || sourcePoints.length < 4 ||
                (sourcePoints.length & 1) != 0 || affine.length != 6) {
            throw new IllegalArgumentException("matching 2D points and 6-value affine are required");
        }
        float a = affine[0];
        float c = affine[3];
        float scale = (float) Math.sqrt(a * a + c * c);
        float rotation = (float) Math.toDegrees(Math.atan2(c, a));
        float dx = sourcePoints[2] - sourcePoints[0];
        float dy = sourcePoints[3] - sourcePoints[1];
        float eyeDistance = (float) Math.sqrt(dx * dx + dy * dy);

        double residualSum = 0d;
        float residualMax = 0f;
        int count = sourcePoints.length / 2;
        for (int i = 0; i < sourcePoints.length; i += 2) {
            float x = sourcePoints[i];
            float y = sourcePoints[i + 1];
            float tx = affine[0] * x + affine[1] * y + affine[2];
            float ty = affine[3] * x + affine[4] * y + affine[5];
            float rx = tx - canonicalPoints[i];
            float ry = ty - canonicalPoints[i + 1];
            float residual = (float) Math.sqrt(rx * rx + ry * ry);
            residualSum += residual;
            residualMax = Math.max(residualMax, residual);
        }
        return new FaceAlignmentDiagnostics(
                Math.max(0, Math.min(5, landmarkCount)), usedFallback, eyeDistance,
                scale, rotation, (float) (residualSum / count), residualMax);
    }

    public static FaceAlignmentDiagnostics fallback(int landmarkCount) {
        return new FaceAlignmentDiagnostics(
                Math.max(0, Math.min(5, landmarkCount)), true,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    public String methodLabel() {
        return usedFallback ? "fallback crop" : "5pt similarity";
    }
}

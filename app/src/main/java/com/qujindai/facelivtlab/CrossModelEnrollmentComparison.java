package com.qujindai.facelivtlab;

import java.util.EnumMap;

/** Same-sample-pair comparison across independent FaceLiVTv2 embedding spaces. */
public final class CrossModelEnrollmentComparison {
    public static final class VariantMetrics {
        public final ModelVariant variant;
        public final int sampleCount;
        public final float meanPair;
        public final float minPair;
        public final int outlierIndex;
        public final float stability;
        public final float coverage;

        VariantMetrics(ModelVariant variant, int sampleCount, float meanPair, float minPair,
                       int outlierIndex, float stability, float coverage) {
            this.variant = variant;
            this.sampleCount = sampleCount;
            this.meanPair = meanPair;
            this.minPair = minPair;
            this.outlierIndex = outlierIndex;
            this.stability = stability;
            this.coverage = coverage;
        }
    }

    public final EnumMap<ModelVariant, VariantMetrics> byVariant;
    public final float[][] deltaXsVsS;
    public final float[][] deltaMVsS;

    private CrossModelEnrollmentComparison(EnumMap<ModelVariant, VariantMetrics> byVariant,
                                           float[][] deltaXsVsS, float[][] deltaMVsS) {
        this.byVariant = new EnumMap<>(byVariant);
        this.deltaXsVsS = copy(deltaXsVsS);
        this.deltaMVsS = copy(deltaMVsS);
    }

    public static CrossModelEnrollmentComparison from(
            EnumMap<ModelVariant, EnrollmentSession.Summary> summaries) {
        if (summaries == null) throw new IllegalArgumentException("summaries are required");
        EnumMap<ModelVariant, VariantMetrics> metrics = new EnumMap<>(ModelVariant.class);
        int expectedSize = -1;
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = summaries.get(variant);
            if (summary == null) continue;
            float[][] matrix = summary.similarityMatrix;
            validateSquare(matrix, variant);
            if (matrix.length > 0) {
                if (expectedSize < 0) expectedSize = matrix.length;
                else if (matrix.length != expectedSize) {
                    throw new IllegalArgumentException("matrix size mismatch across variants");
                }
            }
            metrics.put(variant, metricsFor(variant, summary));
        }
        float[][] xs = matrixOf(summaries, ModelVariant.XS);
        float[][] s = matrixOf(summaries, ModelVariant.S);
        float[][] m = matrixOf(summaries, ModelVariant.M);
        return new CrossModelEnrollmentComparison(metrics, delta(xs, s), delta(m, s));
    }

    private static VariantMetrics metricsFor(ModelVariant variant, EnrollmentSession.Summary summary) {
        float[][] matrix = summary.similarityMatrix;
        int n = matrix.length;
        if (n < 2) {
            return new VariantMetrics(variant, n, Float.NaN, Float.NaN, -1,
                    summary.stability, summary.coverage);
        }
        double pairSum = 0d;
        int pairCount = 0;
        float minPair = Float.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float value = matrix[i][j];
                pairSum += value;
                pairCount++;
                minPair = Math.min(minPair, value);
            }
        }
        int outlier = -1;
        double lowestMean = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double sum = 0d;
            int count = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                sum += matrix[i][j];
                count++;
            }
            double mean = count == 0 ? Double.POSITIVE_INFINITY : sum / count;
            if (mean < lowestMean) {
                lowestMean = mean;
                outlier = i;
            }
        }
        return new VariantMetrics(variant, n, (float)(pairSum / pairCount), minPair, outlier,
                summary.stability, summary.coverage);
    }

    private static float[][] matrixOf(EnumMap<ModelVariant, EnrollmentSession.Summary> summaries,
                                      ModelVariant variant) {
        EnrollmentSession.Summary summary = summaries.get(variant);
        return summary == null ? null : summary.similarityMatrix;
    }

    private static float[][] delta(float[][] a, float[][] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return new float[0][0];
        if (a.length != b.length) throw new IllegalArgumentException("matrix size mismatch for delta");
        int n = a.length;
        float[][] out = new float[n][n];
        for (int i = 0; i < n; i++) {
            if (a[i].length != n || b[i].length != n) {
                throw new IllegalArgumentException("matrix size mismatch for delta");
            }
            for (int j = 0; j < n; j++) out[i][j] = a[i][j] - b[i][j];
        }
        return out;
    }

    private static void validateSquare(float[][] matrix, ModelVariant variant) {
        if (matrix == null) throw new IllegalArgumentException("matrix size missing for " + variant);
        int n = matrix.length;
        for (float[] row : matrix) {
            if (row == null || row.length != n) {
                throw new IllegalArgumentException("matrix size must be square for " + variant);
            }
        }
    }

    private static float[][] copy(float[][] source) {
        if (source == null) return new float[0][0];
        float[][] out = new float[source.length][];
        for (int i = 0; i < source.length; i++) out[i] = source[i].clone();
        return out;
    }
}

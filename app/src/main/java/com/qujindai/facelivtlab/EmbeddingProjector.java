package com.qujindai.facelivtlab;

import java.util.ArrayList;
import java.util.List;

/** Deterministic dual-PCA projection for tiny face-reference sets, with fixed axes for live probes. */
public final class EmbeddingProjector {
    private EmbeddingProjector() {}

    public static final class Model {
        private final float[] mean;
        private final float[] axis1;
        private final float[] axis2;
        private final float scale1;
        private final float scale2;
        private final float[][] trainingProjection;
        private final float[] explainedVarianceRatio;

        Model(float[] mean, float[] axis1, float[] axis2,
              float scale1, float scale2, float[][] trainingProjection,
              float[] explainedVarianceRatio) {
            this.mean = mean;
            this.axis1 = axis1;
            this.axis2 = axis2;
            this.scale1 = scale1;
            this.scale2 = scale2;
            this.trainingProjection = trainingProjection;
            this.explainedVarianceRatio = explainedVarianceRatio;
        }

        public float[] project(float[] vector) {
            if (vector == null || vector.length != mean.length) {
                throw new IllegalArgumentException("embedding dimension must match fitted projection");
            }
            float x = dotCentered(vector, mean, axis1) / scale1;
            float y = dotCentered(vector, mean, axis2) / scale2;
            return new float[]{finite(x), finite(y)};
        }

        public float[][] trainingProjection() {
            float[][] out = new float[trainingProjection.length][2];
            for (int i = 0; i < out.length; i++) {
                out[i][0] = trainingProjection[i][0];
                out[i][1] = trainingProjection[i][1];
            }
            return out;
        }

        public float[] explainedVarianceRatio() {
            return explainedVarianceRatio.clone();
        }
    }

    public static float[][] project(List<float[]> vectors) {
        return fit(vectors).trainingProjection();
    }

    public static Model fit(List<float[]> vectors) {
        int n = vectors == null ? 0 : vectors.size();
        if (n == 0) {
            return new Model(new float[0], new float[0], new float[0], 1f, 1f,
                    new float[0][2], new float[]{0f, 0f});
        }
        int d = vectors.get(0).length;
        if (d == 0) {
            return new Model(new float[0], new float[0], new float[0], 1f, 1f,
                    new float[n][2], new float[]{0f, 0f});
        }

        double[][] centered = new double[n][d];
        float[] mean = new float[d];
        for (float[] vector : vectors) {
            if (vector == null || vector.length != d) {
                throw new IllegalArgumentException("all embeddings must have equal dimensions");
            }
            for (int j = 0; j < d; j++) mean[j] += vector[j] / (float) n;
        }
        for (int i = 0; i < n; i++) {
            float[] v = vectors.get(i);
            for (int j = 0; j < d; j++) centered[i][j] = v[j] - mean[j];
        }

        double[][] gram = new double[n][n];
        double trace = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double dot = 0.0;
                for (int k = 0; k < d; k++) dot += centered[i][k] * centered[j][k];
                gram[i][j] = gram[j][i] = dot;
            }
            trace += gram[i][i];
        }

        double[] first = powerEigenvector(gram, null, 0);
        double lambda1 = Math.max(0.0, rayleigh(gram, first));
        double[] second = powerEigenvector(gram, first, 1);
        double lambda2 = Math.max(0.0, rayleigh(gram, second));

        float[] axis1 = originalAxis(centered, first, lambda1, d);
        float[] axis2 = originalAxis(centered, second, lambda2, d);
        orthogonalizeFloat(axis2, axis1);
        normalizeFloat(axis2);

        float[][] raw = new float[n][2];
        float maxX = 0f, maxY = 0f;
        for (int i = 0; i < n; i++) {
            float[] vector = vectors.get(i);
            raw[i][0] = dotCentered(vector, mean, axis1);
            raw[i][1] = dotCentered(vector, mean, axis2);
            maxX = Math.max(maxX, Math.abs(raw[i][0]));
            maxY = Math.max(maxY, Math.abs(raw[i][1]));
        }
        float scale1 = maxX > 1e-8f ? maxX : 1f;
        float scale2 = maxY > 1e-8f ? maxY : 1f;
        float[][] projection = new float[n][2];
        for (int i = 0; i < n; i++) {
            projection[i][0] = finite(raw[i][0] / scale1);
            projection[i][1] = finite(raw[i][1] / scale2);
        }
        float ev1 = trace > 1e-12 ? clamp01((float) (lambda1 / trace)) : 0f;
        float ev2 = trace > 1e-12 ? clamp01((float) (lambda2 / trace)) : 0f;
        if (ev1 + ev2 > 1f) ev2 = Math.max(0f, 1f - ev1);
        return new Model(mean, axis1, axis2, scale1, scale2, projection, new float[]{ev1, ev2});
    }

    private static float[] originalAxis(double[][] centered, double[] sampleEigenvector,
                                        double lambda, int d) {
        float[] axis = new float[d];
        if (lambda <= 1e-12) return axis;
        double denom = Math.sqrt(lambda);
        for (int j = 0; j < d; j++) {
            double sum = 0.0;
            for (int i = 0; i < centered.length; i++) sum += sampleEigenvector[i] * centered[i][j];
            axis[j] = (float) (sum / denom);
        }
        normalizeFloat(axis);
        return axis;
    }

    private static float dotCentered(float[] vector, float[] mean, float[] axis) {
        double sum = 0.0;
        for (int i = 0; i < vector.length; i++) sum += (vector[i] - mean[i]) * axis[i];
        return (float) sum;
    }

    private static double[] powerEigenvector(double[][] matrix, double[] orthogonalTo, int phase) {
        int n = matrix.length;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = phase == 0 ? Math.sin((i + 1) * 1.731) : Math.cos((i + 1) * 2.117);
        }
        orthogonalize(v, orthogonalTo);
        normalize(v);
        for (int iteration = 0; iteration < 100; iteration++) {
            double[] next = multiply(matrix, v);
            orthogonalize(next, orthogonalTo);
            if (norm(next) < 1e-12) {
                for (int i = 0; i < n; i++) next[i] = (i == (phase % Math.max(1, n))) ? 1.0 : 0.0;
                orthogonalize(next, orthogonalTo);
            }
            normalize(next);
            v = next;
        }
        return v;
    }

    private static double[] multiply(double[][] matrix, double[] v) {
        double[] out = new double[v.length];
        for (int i = 0; i < matrix.length; i++) {
            double sum = 0.0;
            for (int j = 0; j < v.length; j++) sum += matrix[i][j] * v[j];
            out[i] = sum;
        }
        return out;
    }

    private static void orthogonalize(double[] v, double[] against) {
        if (against == null) return;
        double dot = 0.0;
        for (int i = 0; i < v.length; i++) dot += v[i] * against[i];
        for (int i = 0; i < v.length; i++) v[i] -= dot * against[i];
    }

    private static void orthogonalizeFloat(float[] v, float[] against) {
        if (against == null || against.length != v.length) return;
        double dot = 0.0;
        for (int i = 0; i < v.length; i++) dot += v[i] * against[i];
        for (int i = 0; i < v.length; i++) v[i] -= (float) (dot * against[i]);
    }

    private static double rayleigh(double[][] matrix, double[] v) {
        double[] mv = multiply(matrix, v);
        double value = 0.0;
        for (int i = 0; i < v.length; i++) value += v[i] * mv[i];
        return value;
    }

    private static void normalize(double[] v) {
        double norm = norm(v);
        if (norm < 1e-12) return;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
    }

    private static void normalizeFloat(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm < 1e-12) return;
        for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
    }

    private static double norm(double[] v) {
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    private static float finite(float value) { return Float.isFinite(value) ? value : 0f; }
    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
}

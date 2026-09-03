package com.qujindai.facelivtlab;

import java.util.List;

/** Tiny deterministic PCA projection optimized for <= 6 face vectors, not for model inference. */
public final class EmbeddingProjector {
    private EmbeddingProjector() {}

    public static float[][] project(List<float[]> vectors) {
        int n = vectors == null ? 0 : vectors.size();
        if (n == 0) return new float[0][2];
        int d = vectors.get(0).length;
        if (d == 0) return new float[n][2];

        double[][] centered = new double[n][d];
        double[] mean = new double[d];
        for (float[] vector : vectors) {
            if (vector.length != d) throw new IllegalArgumentException("all embeddings must have equal dimensions");
            for (int j = 0; j < d; j++) mean[j] += vector[j] / (double) n;
        }
        for (int i = 0; i < n; i++) {
            float[] v = vectors.get(i);
            for (int j = 0; j < d; j++) centered[i][j] = v[j] - mean[j];
        }

        double[][] gram = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double dot = 0.0;
                for (int k = 0; k < d; k++) dot += centered[i][k] * centered[j][k];
                gram[i][j] = gram[j][i] = dot;
            }
        }

        double[] first = powerEigenvector(gram, null, 0);
        double lambda1 = rayleigh(gram, first);
        double[] second = powerEigenvector(gram, first, 1);
        double lambda2 = rayleigh(gram, second);

        float[][] out = new float[n][2];
        double scale1 = Math.sqrt(Math.max(0.0, lambda1));
        double scale2 = Math.sqrt(Math.max(0.0, lambda2));
        float maxX = 0f, maxY = 0f;
        for (int i = 0; i < n; i++) {
            out[i][0] = finite((float) (first[i] * scale1));
            out[i][1] = finite((float) (second[i] * scale2));
            maxX = Math.max(maxX, Math.abs(out[i][0]));
            maxY = Math.max(maxY, Math.abs(out[i][1]));
        }
        if (maxX > 1e-8f) for (float[] p : out) p[0] /= maxX;
        if (maxY > 1e-8f) for (float[] p : out) p[1] /= maxY;
        return out;
    }

    private static double[] powerEigenvector(double[][] matrix, double[] orthogonalTo, int phase) {
        int n = matrix.length;
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            v[i] = phase == 0 ? Math.sin((i + 1) * 1.731) : Math.cos((i + 1) * 2.117);
        }
        orthogonalize(v, orthogonalTo);
        normalize(v);

        for (int iteration = 0; iteration < 80; iteration++) {
            double[] next = multiply(matrix, v);
            orthogonalize(next, orthogonalTo);
            if (norm(next) < 1e-12) {
                // Deterministic fallback axis in a degenerate subspace.
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

    private static double norm(double[] v) {
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    private static float finite(float value) { return Float.isFinite(value) ? value : 0f; }
}

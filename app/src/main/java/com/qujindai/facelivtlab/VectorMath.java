package com.qujindai.facelivtlab;

public final class VectorMath {
    private VectorMath() {}

    public static float[] normalize(float[] input) {
        double sum = 0.0;
        for (float v : input) sum += (double) v * v;
        double norm = Math.sqrt(sum);
        float[] out = new float[input.length];
        if (norm < 1e-12) return out;
        for (int i = 0; i < input.length; i++) out[i] = (float) (input[i] / norm);
        return out;
    }

    public static float cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0.0, aa = 0.0, bb = 0.0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            aa += (double) a[i] * a[i];
            bb += (double) b[i] * b[i];
        }
        if (aa < 1e-12 || bb < 1e-12) return 0f;
        return (float) (dot / Math.sqrt(aa * bb));
    }
}

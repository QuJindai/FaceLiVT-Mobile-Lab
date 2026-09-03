package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class FaceRecognizer implements AutoCloseable {
    private static final int SIZE = 112;
    private static final int EMBEDDING = 512;
    private static final Set<String> EMBEDDING_ONLY = Set.of("embedding");
    private static final Set<String> DIAGNOSTIC_OUTPUTS = Set.of(
            "embedding", "block_stats", "stage_stats", "prehead_stats");

    public static final class DiagnosticResult {
        public final float[] embedding;
        public final DeepModelStats stats;

        DiagnosticResult(float[] embedding, DeepModelStats stats) {
            this.embedding = embedding;
            this.stats = stats;
        }
    }

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final ModelVariant variant;

    public FaceRecognizer(Context context) throws Exception {
        this(context, ModelVariant.S);
    }

    public FaceRecognizer(Context context, ModelVariant variant) throws Exception {
        this(context, variant, variant.assetName);
    }

    public FaceRecognizer(Context context, String assetName) throws Exception {
        this(context, ModelVariant.S, assetName);
    }

    private FaceRecognizer(Context context, ModelVariant variant, String assetName) throws Exception {
        this.variant = variant == null ? ModelVariant.S : variant;
        env = OrtEnvironment.getEnvironment();
        byte[] model = readAsset(context, assetName);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setIntraOpNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        session = env.createSession(model, options);
        inputName = session.getInputNames().iterator().next();
    }

    public float[] embed(Bitmap aligned) throws Exception {
        float[] chw = pack(aligned);
        long[] shape = {1, 3, SIZE, SIZE};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor), EMBEDDING_ONLY)) {
            return parseEmbedding(value(result, "embedding"));
        }
    }

    public DiagnosticResult diagnose(Bitmap aligned) throws Exception {
        float[] chw = pack(aligned);
        long[] shape = {1, 3, SIZE, SIZE};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor), DIAGNOSTIC_OUTPUTS)) {
            float[] embedding = parseEmbedding(value(result, "embedding"));
            float[][] blocks = as2d(value(result, "block_stats"), 18, 5, "block_stats");
            float[][] stages = as2d(value(result, "stage_stats"), 4, 4, "stage_stats");
            float[] prehead = as1d(value(result, "prehead_stats"), 4, "prehead_stats");
            return new DiagnosticResult(embedding, new DeepModelStats(variant, blocks, stages, prehead));
        }
    }

    private static OnnxValue value(OrtSession.Result result, String name) {
        return result.get(name).orElseThrow(() -> new IllegalStateException("Missing ONNX output: " + name));
    }

    private static float[] parseEmbedding(OnnxValue value) throws Exception {
        Object rawValue = value.getValue();
        float[] raw;
        if (rawValue instanceof float[][]) {
            raw = ((float[][]) rawValue)[0];
        } else if (rawValue instanceof float[]) {
            raw = (float[]) rawValue;
        } else {
            throw new IllegalStateException("Unexpected embedding output type: " + rawValue.getClass());
        }
        if (raw.length != EMBEDDING) {
            throw new IllegalStateException("Unexpected embedding size: " + raw.length);
        }
        return VectorMath.normalize(raw);
    }

    private static float[][] as2d(OnnxValue value, int rows, int cols, String name) throws Exception {
        Object raw = value.getValue();
        if (!(raw instanceof float[][])) throw new IllegalStateException(name + " must be float[][]");
        float[][] data = (float[][]) raw;
        if (data.length != rows) throw new IllegalStateException(name + " rows=" + data.length);
        float[][] copy = new float[rows][cols];
        for (int r = 0; r < rows; r++) {
            if (data[r].length != cols) throw new IllegalStateException(name + " cols=" + data[r].length);
            System.arraycopy(data[r], 0, copy[r], 0, cols);
        }
        return copy;
    }

    private static float[] as1d(OnnxValue value, int size, String name) throws Exception {
        Object raw = value.getValue();
        if (!(raw instanceof float[])) throw new IllegalStateException(name + " must be float[]");
        float[] data = (float[]) raw;
        if (data.length != size) throw new IllegalStateException(name + " size=" + data.length);
        return data.clone();
    }

    private static float[] pack(Bitmap aligned) {
        Bitmap input = aligned.getWidth() == SIZE && aligned.getHeight() == SIZE
                ? aligned : Bitmap.createScaledBitmap(aligned, SIZE, SIZE, true);
        int[] pixels = new int[SIZE * SIZE];
        input.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        float[] chw = new float[3 * SIZE * SIZE];
        int plane = SIZE * SIZE;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            float r = ((p >> 16) & 0xff) / 127.5f - 1f;
            float g = ((p >> 8) & 0xff) / 127.5f - 1f;
            float b = (p & 0xff) / 127.5f - 1f;
            chw[i] = r;
            chw[plane + i] = g;
            chw[2 * plane + i] = b;
        }
        return chw;
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Override public void close() throws Exception {
        session.close();
    }
}

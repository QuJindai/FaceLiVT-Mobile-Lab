package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class FaceRecognizer implements AutoCloseable {
    private static final int SIZE = 112;
    private static final int EMBEDDING = 512;
    private static final Set<String> DIAGNOSTIC_OUTPUTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("embedding", "block_stats", "stage_stats", "prehead")));

    public static final class DiagnosticEmbedding {
        public final float[] embedding;
        public final ModelDiagnostics diagnostics;

        DiagnosticEmbedding(float[] embedding, ModelDiagnostics diagnostics) {
            this.embedding = embedding;
            this.diagnostics = diagnostics;
        }
    }

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public FaceRecognizer(Context context) throws Exception {
        this(context, ModelVariant.S);
    }

    public FaceRecognizer(Context context, ModelVariant variant) throws Exception {
        this(context, variant.assetName);
    }

    public FaceRecognizer(Context context, String assetName) throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] model = readAsset(context, assetName);
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setIntraOpNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        session = env.createSession(model, options);
        inputName = session.getInputNames().iterator().next();
    }

    public float[] embed(Bitmap aligned) throws Exception {
        float[] chw = preprocess(aligned);
        long[] shape = {1, 3, SIZE, SIZE};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(
                     Collections.singletonMap(inputName, tensor), Collections.singleton("embedding"))) {
            return VectorMath.normalize(readVector(requiredValue(result, "embedding"), EMBEDDING, "embedding"));
        }
    }

    public DiagnosticEmbedding embedWithDiagnostics(Bitmap aligned) throws Exception {
        float[] chw = preprocess(aligned);
        long[] shape = {1, 3, SIZE, SIZE};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(
                     Collections.singletonMap(inputName, tensor), DIAGNOSTIC_OUTPUTS)) {
            float[] embedding = VectorMath.normalize(
                    readVector(requiredValue(result, "embedding"), EMBEDDING, "embedding"));
            float[][] blocks = readMatrix(requiredValue(result, "block_stats"),
                    ModelDiagnostics.BLOCK_COUNT, ModelDiagnostics.STAT_COUNT, "block_stats");
            float[][] stages = readMatrix(requiredValue(result, "stage_stats"),
                    ModelDiagnostics.STAGE_COUNT, ModelDiagnostics.STAT_COUNT, "stage_stats");
            float[] prehead = readVector(requiredValue(result, "prehead"),
                    ModelDiagnostics.PREHEAD_DIM, "prehead");
            return new DiagnosticEmbedding(embedding, ModelDiagnostics.of(blocks, stages, prehead));
        }
    }

    private static float[] preprocess(Bitmap aligned) {
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

    private static Object requiredValue(OrtSession.Result result, String name) throws Exception {
        Optional<OnnxValue> value = result.get(name);
        if (!value.isPresent()) throw new IllegalStateException("Missing ONNX output: " + name);
        return value.get().getValue();
    }

    private static float[] readVector(Object value, int expected, String label) {
        float[] raw;
        if (value instanceof float[][]) {
            float[][] values = (float[][]) value;
            if (values.length != 1) throw new IllegalStateException(label + " batch must be 1");
            raw = values[0];
        } else if (value instanceof float[]) {
            raw = (float[]) value;
        } else {
            throw new IllegalStateException("Unexpected " + label + " type: " + value.getClass());
        }
        if (raw.length != expected) {
            throw new IllegalStateException("Unexpected " + label + " size: " + raw.length + " != " + expected);
        }
        return raw.clone();
    }

    private static float[][] readMatrix(Object value, int rows, int cols, String label) {
        if (!(value instanceof float[][])) {
            throw new IllegalStateException("Unexpected " + label + " type: " + value.getClass());
        }
        float[][] raw = (float[][]) value;
        if (raw.length != rows) {
            throw new IllegalStateException("Unexpected " + label + " rows: " + raw.length + " != " + rows);
        }
        float[][] out = new float[rows][];
        for (int r = 0; r < rows; r++) {
            if (raw[r] == null || raw[r].length != cols) {
                throw new IllegalStateException("Unexpected " + label + " columns at row " + r);
            }
            out[r] = raw[r].clone();
        }
        return out;
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

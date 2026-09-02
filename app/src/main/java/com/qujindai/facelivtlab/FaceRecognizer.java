package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class FaceRecognizer implements AutoCloseable {
    private static final int SIZE = 112;
    private static final int EMBEDDING = 512;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public FaceRecognizer(Context context) throws Exception {
        env = OrtEnvironment.getEnvironment();
        byte[] model = readAsset(context, "facelivtv2_s.onnx");
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setIntraOpNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        session = env.createSession(model, options);
        inputName = session.getInputNames().iterator().next();
    }

    public float[] embed(Bitmap aligned) throws Exception {
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

        long[] shape = {1, 3, SIZE, SIZE};
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            Object value = result.get(0).getValue();
            float[] raw;
            if (value instanceof float[][]) {
                raw = ((float[][]) value)[0];
            } else if (value instanceof float[]) {
                raw = (float[]) value;
            } else {
                throw new IllegalStateException("Unexpected ONNX output type: " + value.getClass());
            }
            if (raw.length != EMBEDDING) {
                throw new IllegalStateException("Unexpected embedding size: " + raw.length);
            }
            return VectorMath.normalize(raw);
        }
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

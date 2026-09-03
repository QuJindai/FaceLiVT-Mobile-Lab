package com.qujindai.facelivtlab;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import java.util.EnumMap;
import java.util.Map;

public final class RecognizerBank implements AutoCloseable {
    public static final class TimedEmbedding {
        public final ModelVariant variant;
        public final float[] embedding;
        public final long inferMs;

        TimedEmbedding(ModelVariant variant, float[] embedding, long inferMs) {
            this.variant = variant;
            this.embedding = embedding;
            this.inferMs = inferMs;
        }
    }

    public static final class TimedDiagnostics {
        public final ModelVariant variant;
        public final float[] embedding;
        public final ModelDiagnostics diagnostics;
        public final long inferMs;

        TimedDiagnostics(ModelVariant variant, float[] embedding,
                         ModelDiagnostics diagnostics, long inferMs) {
            this.variant = variant;
            this.embedding = embedding;
            this.diagnostics = diagnostics;
            this.inferMs = inferMs;
        }
    }

    private final Context context;
    private final EnumMap<ModelVariant, FaceRecognizer> recognizers = new EnumMap<>(ModelVariant.class);

    public RecognizerBank(Context context) {
        this.context = context.getApplicationContext();
    }

    private synchronized FaceRecognizer get(ModelVariant variant) throws Exception {
        FaceRecognizer recognizer = recognizers.get(variant);
        if (recognizer == null) {
            recognizer = new FaceRecognizer(context, variant);
            recognizers.put(variant, recognizer);
        }
        return recognizer;
    }

    public TimedEmbedding embed(ModelVariant variant, Bitmap aligned) throws Exception {
        long start = SystemClock.elapsedRealtimeNanos();
        float[] embedding = get(variant).embed(aligned);
        long ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        return new TimedEmbedding(variant, embedding, ms);
    }

    public TimedDiagnostics diagnose(ModelVariant variant, Bitmap aligned) throws Exception {
        long start = SystemClock.elapsedRealtimeNanos();
        FaceRecognizer.DiagnosticEmbedding result = get(variant).embedWithDiagnostics(aligned);
        long ms = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
        return new TimedDiagnostics(variant, result.embedding, result.diagnostics, ms);
    }

    public EnumMap<ModelVariant, TimedEmbedding> embedAll(Bitmap aligned) throws Exception {
        EnumMap<ModelVariant, TimedEmbedding> out = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            out.put(variant, embed(variant, aligned));
        }
        return out;
    }

    @Override public synchronized void close() throws Exception {
        Exception first = null;
        for (Map.Entry<ModelVariant, FaceRecognizer> entry : recognizers.entrySet()) {
            try { entry.getValue().close(); }
            catch (Exception e) { if (first == null) first = e; }
        }
        recognizers.clear();
        if (first != null) throw first;
    }
}

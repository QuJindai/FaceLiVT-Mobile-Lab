package com.qujindai.facelivtlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

public final class FaceStore {
    public static final class Match {
        public final String name;
        public final float similarity;
        Match(String name, float similarity) { this.name = name; this.similarity = similarity; }
    }

    private final SharedPreferences prefs;

    public FaceStore(Context context) {
        prefs = context.getSharedPreferences("face_store", Context.MODE_PRIVATE);
    }

    public synchronized void addSample(String name, float[] embedding) {
        addSample(name, ModelVariant.S, embedding);
    }

    public synchronized void addSample(String name, ModelVariant variant, float[] embedding) {
        migrateLegacySIfNeeded(name, variant);
        String vectorKey = TemplateKey.vector(name, variant);
        String countKey = TemplateKey.count(name, variant);
        int count = prefs.getInt(countKey, 0);
        float[] current = decode(prefs.getString(vectorKey, null));
        float[] merged = new float[embedding.length];
        if (current != null && current.length == embedding.length && count > 0) {
            for (int i = 0; i < embedding.length; i++) merged[i] = current[i] + embedding[i];
        } else {
            System.arraycopy(embedding, 0, merged, 0, embedding.length);
            count = 0;
        }
        Set<String> names = new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
        names.add(name);
        prefs.edit()
                .putStringSet("names", names)
                .putString(vectorKey, encode(merged))
                .putInt(countKey, count + 1)
                .apply();
    }

    public synchronized Match bestMatch(float[] embedding) {
        return bestMatch(ModelVariant.S, embedding);
    }

    public synchronized Match bestMatch(ModelVariant variant, float[] embedding) {
        Set<String> names = prefs.getStringSet("names", new HashSet<>());
        String bestName = null;
        float best = -1f;
        for (String name : names) {
            migrateLegacySIfNeeded(name, variant);
            float[] ref = decode(prefs.getString(TemplateKey.vector(name, variant), null));
            if (ref == null || ref.length != embedding.length) continue;
            float score = VectorMath.cosine(embedding, ref);
            if (score > best) { best = score; bestName = name; }
        }
        return bestName == null ? null : new Match(bestName, best);
    }

    public synchronized boolean hasTemplate(String name, ModelVariant variant) {
        migrateLegacySIfNeeded(name, variant);
        return prefs.contains(TemplateKey.vector(name, variant));
    }

    public int identityCount() {
        return prefs.getStringSet("names", new HashSet<>()).size();
    }

    private void migrateLegacySIfNeeded(String name, ModelVariant variant) {
        if (variant != ModelVariant.S) return;
        String newVector = TemplateKey.vector(name, ModelVariant.S);
        if (prefs.contains(newVector)) return;
        String legacyVector = prefs.getString(TemplateKey.legacyVector(name), null);
        if (legacyVector == null) return;
        int count = prefs.getInt(TemplateKey.legacyCount(name), 1);
        prefs.edit()
                .putString(newVector, legacyVector)
                .putInt(TemplateKey.count(name, ModelVariant.S), Math.max(1, count))
                .apply();
    }

    private static String encode(float[] vector) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) buf.putFloat(v);
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP);
    }

    private static float[] decode(String encoded) {
        if (encoded == null) return null;
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        if ((bytes.length & 3) != 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[bytes.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = buf.getFloat();
        return out;
    }
}

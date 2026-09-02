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
        String key = safeKey(name);
        int count = prefs.getInt(key + "_count", 0);
        float[] current = decode(prefs.getString(key + "_vec", null));
        float[] merged = new float[embedding.length];
        if (current != null && current.length == embedding.length && count > 0) {
            // Persist the raw sum of unit embeddings. Do not normalize between samples,
            // otherwise later samples receive a biased weight.
            for (int i = 0; i < embedding.length; i++) merged[i] = current[i] + embedding[i];
        } else {
            System.arraycopy(embedding, 0, merged, 0, embedding.length);
            count = 0;
        }
        Set<String> names = new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
        names.add(name);
        prefs.edit()
                .putStringSet("names", names)
                .putString(key + "_vec", encode(merged))
                .putInt(key + "_count", count + 1)
                .apply();
    }

    public synchronized Match bestMatch(float[] embedding) {
        Set<String> names = prefs.getStringSet("names", new HashSet<>());
        String bestName = null;
        float best = -1f;
        for (String name : names) {
            float[] ref = decode(prefs.getString(safeKey(name) + "_vec", null));
            if (ref == null || ref.length != embedding.length) continue;
            float score = VectorMath.cosine(embedding, ref);
            if (score > best) { best = score; bestName = name; }
        }
        return bestName == null ? null : new Match(bestName, best);
    }

    public int identityCount() {
        return prefs.getStringSet("names", new HashSet<>()).size();
    }

    private static String safeKey(String name) {
        return "id_" + Integer.toHexString(name.hashCode());
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

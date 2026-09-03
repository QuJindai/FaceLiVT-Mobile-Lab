package com.qujindai.facelivtlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FaceStore {
    public static final class Match {
        public final String name;
        public final float similarity;
        Match(String name, float similarity) { this.name = name; this.similarity = similarity; }
    }

    private final SharedPreferences prefs;

    public FaceStore(Context context) {
        this(context.getSharedPreferences("face_store", Context.MODE_PRIVATE));
    }

    /** Package-private injection keeps destructive lifecycle semantics unit-testable without Robolectric. */
    FaceStore(SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("preferences required");
        this.prefs = prefs;
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
        saveTemplateSum(name, variant, merged, count + 1);
    }

    /** Replace the model-specific identity template with a normalized centroid/effective evidence count. */
    public synchronized void replaceTemplate(String name, ModelVariant variant, float[] centroid, int sampleCount) {
        if (name == null || name.trim().isEmpty() || centroid == null || centroid.length == 0) {
            throw new IllegalArgumentException("name and centroid are required");
        }
        int count = Math.max(1, sampleCount);
        float[] normalized = VectorMath.normalize(centroid);
        float[] storedSum = new float[normalized.length];
        for (int i = 0; i < normalized.length; i++) storedSum[i] = normalized[i] * count;
        saveTemplateSum(name.trim(), variant, storedSum, count);
    }

    private void saveTemplateSum(String name, ModelVariant variant, float[] vectorSum, int count) {
        Set<String> names = new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
        names.add(name);
        prefs.edit()
                .putStringSet("names", names)
                .putString(TemplateKey.vector(name, variant), encode(vectorSum))
                .putInt(TemplateKey.count(name, variant), Math.max(1, count))
                .apply();
    }

    public synchronized Match bestMatch(float[] embedding) {
        return bestMatch(ModelVariant.S, embedding);
    }

    public synchronized Match bestMatch(ModelVariant variant, float[] embedding) {
        List<Match> matches = topMatches(variant, embedding, 1);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** Ordered, model-space-safe Top-K results for the recognition microscope. */
    public synchronized List<Match> topMatches(ModelVariant variant, float[] embedding, int limit) {
        int requested = Math.max(0, limit);
        List<Match> out = new ArrayList<>();
        if (requested == 0 || embedding == null || embedding.length == 0) return out;
        Set<String> names = prefs.getStringSet("names", new HashSet<>());
        for (String name : names) {
            migrateLegacySIfNeeded(name, variant);
            float[] ref = decode(prefs.getString(TemplateKey.vector(name, variant), null));
            if (ref == null || ref.length != embedding.length) continue;
            out.add(new Match(name, VectorMath.cosine(embedding, ref)));
        }
        out.sort(Comparator.comparingDouble((Match match) -> match.similarity).reversed());
        if (out.size() > requested) return new ArrayList<>(out.subList(0, requested));
        return out;
    }

    public synchronized float[] template(String name, ModelVariant variant) {
        migrateLegacySIfNeeded(name, variant);
        float[] ref = decode(prefs.getString(TemplateKey.vector(name, variant), null));
        return ref == null ? null : VectorMath.normalize(ref);
    }

    public synchronized int sampleCount(String name, ModelVariant variant) {
        if (name == null || name.trim().isEmpty() || variant == null) return 0;
        name = name.trim();
        migrateLegacySIfNeeded(name, variant);
        return prefs.contains(TemplateKey.vector(name, variant))
                ? Math.max(1, prefs.getInt(TemplateKey.count(name, variant), 1)) : 0;
    }

    public synchronized Set<String> identityNames() {
        return new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
    }

    public synchronized boolean hasTemplate(String name, ModelVariant variant) {
        if (name == null || variant == null) return false;
        migrateLegacySIfNeeded(name.trim(), variant);
        return prefs.contains(TemplateKey.vector(name.trim(), variant));
    }

    public synchronized void deleteTemplate(String name, ModelVariant variant) {
        if (name == null || name.trim().isEmpty() || variant == null) return;
        String id = name.trim();
        SharedPreferences.Editor editor = prefs.edit()
                .remove(TemplateKey.vector(id, variant))
                .remove(TemplateKey.count(id, variant));
        if (variant == ModelVariant.S) {
            editor.remove(TemplateKey.legacyVector(id)).remove(TemplateKey.legacyCount(id));
        }
        editor.apply();
        if (!hasAnyStoredTemplate(id)) removeName(id);
    }

    public synchronized void deleteIdentity(String name) {
        if (name == null || name.trim().isEmpty()) return;
        String id = name.trim();
        SharedPreferences.Editor editor = prefs.edit();
        for (ModelVariant variant : ModelVariant.values()) {
            editor.remove(TemplateKey.vector(id, variant));
            editor.remove(TemplateKey.count(id, variant));
        }
        editor.remove(TemplateKey.legacyVector(id));
        editor.remove(TemplateKey.legacyCount(id));
        Set<String> names = new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
        names.remove(id);
        editor.putStringSet("names", names).apply();
    }

    public synchronized int identityCount() {
        return prefs.getStringSet("names", new HashSet<>()).size();
    }

    private boolean hasAnyStoredTemplate(String name) {
        for (ModelVariant variant : ModelVariant.values()) {
            if (prefs.contains(TemplateKey.vector(name, variant))) return true;
        }
        return prefs.contains(TemplateKey.legacyVector(name));
    }

    private void removeName(String name) {
        Set<String> names = new HashSet<>(prefs.getStringSet("names", new HashSet<>()));
        if (!names.remove(name)) return;
        prefs.edit().putStringSet("names", names).apply();
    }

    private void migrateLegacySIfNeeded(String name, ModelVariant variant) {
        if (variant != ModelVariant.S || name == null || name.isEmpty()) return;
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

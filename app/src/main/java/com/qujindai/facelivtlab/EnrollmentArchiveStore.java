package com.qujindai.facelivtlab;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the human-readable dossier and R3.1 reference samples independently of identity templates. */
public final class EnrollmentArchiveStore {
    private final SharedPreferences prefs;

    public EnrollmentArchiveStore(Context context) {
        prefs = context.getSharedPreferences("enrollment_microscope_archive", Context.MODE_PRIVATE);
    }

    public void save(String identity, String archive) {
        if (identity == null || identity.trim().isEmpty()) return;
        prefs.edit().putString("archive/" + identity.trim(), archive == null ? "" : archive).apply();
    }

    public String load(String identity) {
        if (identity == null || identity.trim().isEmpty()) return "";
        return prefs.getString("archive/" + identity.trim(), "");
    }

    public void saveReference(String identity, ModelVariant variant, EnrollmentReferenceCodec.Record record) {
        if (identity == null || identity.trim().isEmpty() || variant == null || record == null) return;
        prefs.edit().putString(referenceKey(identity, variant), EnrollmentReferenceCodec.encode(record)).apply();
    }

    public EnrollmentReferenceCodec.Record loadReference(String identity, ModelVariant variant) {
        if (identity == null || identity.trim().isEmpty() || variant == null) return null;
        String encoded = prefs.getString(referenceKey(identity, variant), null);
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            return EnrollmentReferenceCodec.decode(encoded);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String referenceKey(String identity, ModelVariant variant) {
        return "reference/" + variant.storageKey + "/" + identity.trim();
    }
}

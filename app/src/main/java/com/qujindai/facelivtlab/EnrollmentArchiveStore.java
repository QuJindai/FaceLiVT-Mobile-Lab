package com.qujindai.facelivtlab;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the human-readable quality dossier independently of the embedding template store. */
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
}

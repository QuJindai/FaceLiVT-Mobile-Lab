package com.qujindai.facelivtlab;

/** Centralized, deliberately conservative duplicate-enrollment thresholds. */
public final class IdentityGuardPolicy {
    static final float SUSPECT_OFFSET = 0.05f;
    static final float SUSPECT_FLOOR = 0.55f;
    static final float EXISTING_OFFSET = 0.10f;
    static final float EXISTING_FLOOR = 0.62f;
    static final float MIN_MARGIN = 0.08f;
    static final float MAX_THRESHOLD = 0.99f;

    private IdentityGuardPolicy() {}

    public static final class Thresholds {
        public final float suspect;
        public final float existing;

        Thresholds(float suspect, float existing) {
            this.suspect = suspect;
            this.existing = existing;
        }
    }

    public static Thresholds thresholds(float tid, Float empiricalThreshold) {
        float base = clamp01(tid);
        float suspect = Math.max(base + 0.05f, 0.55f);
        float existing = Math.max(base + 0.10f, 0.62f);
        if (empiricalThreshold != null && Float.isFinite(empiricalThreshold)) {
            float empirical = clamp01(empiricalThreshold);
            suspect = Math.max(suspect, empirical + 0.05f);
            existing = Math.max(existing, empirical + 0.10f);
        }
        return new Thresholds(Math.min(MAX_THRESHOLD, suspect), Math.min(MAX_THRESHOLD, existing));
    }

    public static boolean isStrongVote(int librarySize, float top1Score,
                                       boolean marginAvailable, float margin,
                                       Thresholds thresholds) {
        if (thresholds == null || !Float.isFinite(top1Score) || top1Score < thresholds.existing) {
            return false;
        }
        if (librarySize <= 1) return true;
        return marginAvailable && Float.isFinite(margin) && margin >= 0.08f;
    }

    public static boolean isSuspect(float top1Score, Thresholds thresholds) {
        return thresholds != null && Float.isFinite(top1Score) && top1Score >= thresholds.suspect;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0f;
        return Math.max(0f, Math.min(MAX_THRESHOLD, value));
    }
}

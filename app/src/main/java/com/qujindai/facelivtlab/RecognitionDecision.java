package com.qujindai.facelivtlab;

import java.util.List;
import java.util.Locale;

/** Pure decision object so single-identity limitations are explicit instead of faking Top2=0. */
public final class RecognitionDecision {
    public final String top1Name;
    public final String top2Name;
    public final float top1Score;
    public final float top2Score;
    public final boolean marginAvailable;
    public final float margin;
    public final boolean identityPass;
    public final boolean qualityPass;
    public final boolean accepted;

    private RecognitionDecision(String top1Name, String top2Name,
                                float top1Score, float top2Score,
                                boolean marginAvailable, float margin,
                                boolean identityPass, boolean qualityPass, boolean accepted) {
        this.top1Name = top1Name;
        this.top2Name = top2Name;
        this.top1Score = top1Score;
        this.top2Score = top2Score;
        this.marginAvailable = marginAvailable;
        this.margin = margin;
        this.identityPass = identityPass;
        this.qualityPass = qualityPass;
        this.accepted = accepted;
    }

    public static RecognitionDecision from(List<FaceStore.Match> matches,
                                           float threshold,
                                           FaceQuality.Snapshot quality) {
        FaceStore.Match top1 = matches == null || matches.isEmpty() ? null : matches.get(0);
        FaceStore.Match top2 = matches != null && matches.size() > 1 ? matches.get(1) : null;
        float s1 = top1 == null ? Float.NaN : top1.similarity;
        float s2 = top2 == null ? Float.NaN : top2.similarity;
        boolean marginAvailable = top1 != null && top2 != null;
        float margin = marginAvailable ? s1 - s2 : Float.NaN;
        boolean identityPass = top1 != null && s1 >= threshold;
        boolean qualityPass = quality != null && quality.passesProbeGate();
        return new RecognitionDecision(
                top1 == null ? "" : top1.name,
                top2 == null ? "" : top2.name,
                s1, s2, marginAvailable, margin,
                identityPass, qualityPass, identityPass && qualityPass);
    }

    public String marginLabel() {
        return marginAvailable ? String.format(Locale.US, "%.4f", margin) : "N/A";
    }
}

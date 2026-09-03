package com.qujindai.facelivtlab;

/** Conservative active-template update used only after a successful five-frame append session. */
public final class TemplateFusion {
    private static final int NEW_WEIGHT = 5;
    private static final int OLD_WEIGHT_CAP = 15;
    private static final int EFFECTIVE_SAMPLE_CAP = 20;

    private TemplateFusion() {}

    public static final class Result {
        public final float[] centroid;
        public final int oldWeight;
        public final int newWeight;
        public final int effectiveSamples;

        Result(float[] centroid, int oldWeight, int newWeight, int effectiveSamples) {
            this.centroid = centroid.clone();
            this.oldWeight = oldWeight;
            this.newWeight = newWeight;
            this.effectiveSamples = effectiveSamples;
        }
    }

    public static Result fuse(float[] oldCentroid, int existingEffectiveSamples, float[] newSessionCentroid) {
        if (oldCentroid == null || newSessionCentroid == null || oldCentroid.length == 0 ||
                oldCentroid.length != newSessionCentroid.length) {
            throw new IllegalArgumentException("matching non-empty centroids required");
        }
        float[] oldUnit = VectorMath.normalize(oldCentroid);
        float[] newUnit = VectorMath.normalize(newSessionCentroid);
        int priorEvidence = Math.max(1, existingEffectiveSamples);
        int oldWeight = Math.min(priorEvidence, OLD_WEIGHT_CAP);
        float[] weighted = new float[oldUnit.length];
        for (int i = 0; i < weighted.length; i++) {
            weighted[i] = oldWeight * oldUnit[i] + NEW_WEIGHT * newUnit[i];
        }
        float[] fused = VectorMath.normalize(weighted);
        int effective = Math.min(priorEvidence + NEW_WEIGHT, EFFECTIVE_SAMPLE_CAP);
        return new Result(fused, oldWeight, NEW_WEIGHT, effective);
    }
}

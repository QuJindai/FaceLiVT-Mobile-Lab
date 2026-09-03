package com.qujindai.facelivtlab;

import java.util.EnumMap;

/**
 * Pure pre-commit plan for successful R5 five-frame learning.
 * All model-specific template/fusion validation happens here before history is published.
 */
public final class EnrollmentCommitPlan {
    private EnrollmentCommitPlan() {}

    public static final class ActiveTemplate {
        public final float[] centroid;
        public final int effectiveSamples;
        public final boolean appended;
        public final int oldWeight;
        public final int newWeight;
        public final float driftCosine;

        ActiveTemplate(float[] centroid, int effectiveSamples, boolean appended,
                       int oldWeight, int newWeight, float driftCosine) {
            this.centroid = centroid.clone();
            this.effectiveSamples = effectiveSamples;
            this.appended = appended;
            this.oldWeight = oldWeight;
            this.newWeight = newWeight;
            this.driftCosine = driftCosine;
        }
    }

    public static final class Plan {
        public final EnumMap<ModelVariant, ActiveTemplate> templates;
        public final int effectiveSamplesBefore;
        public final int effectiveSamplesAfter;

        Plan(EnumMap<ModelVariant, ActiveTemplate> templates,
             int effectiveSamplesBefore, int effectiveSamplesAfter) {
            this.templates = new EnumMap<>(ModelVariant.class);
            this.templates.putAll(templates);
            this.effectiveSamplesBefore = effectiveSamplesBefore;
            this.effectiveSamplesAfter = effectiveSamplesAfter;
        }
    }

    public static Plan build(EnrollmentSession session, boolean append,
                             EnumMap<ModelVariant, float[]> oldCentroids,
                             EnumMap<ModelVariant, Integer> oldEffectiveSamples) {
        if (session == null) throw new IllegalArgumentException("five-frame session required");
        EnumMap<ModelVariant, ActiveTemplate> templates = new EnumMap<>(ModelVariant.class);
        EnumMap<ModelVariant, float[]> priorCentroids = oldCentroids == null
                ? new EnumMap<>(ModelVariant.class) : oldCentroids;
        EnumMap<ModelVariant, Integer> priorCounts = oldEffectiveSamples == null
                ? new EnumMap<>(ModelVariant.class) : oldEffectiveSamples;

        int before = 0;
        int after = 5;
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary summary = session.summary(variant);
            if (summary.sampleCount != EnrollmentHistoryRecord.FRAME_COUNT || summary.centroid.length == 0) {
                throw new IllegalStateException("successful five-frame evidence missing for " + variant.storageKey);
            }
            if (!append) {
                templates.put(variant, new ActiveTemplate(
                        VectorMath.normalize(summary.centroid), 5, false, 0, 5, Float.NaN));
                continue;
            }

            float[] oldCentroid = priorCentroids.get(variant);
            Integer oldCount = priorCounts.get(variant);
            if (oldCentroid == null || oldCentroid.length != summary.centroid.length || oldCount == null || oldCount <= 0) {
                throw new IllegalStateException("historical active template missing for " + variant.storageKey);
            }
            TemplateFusion.Result fused = TemplateFusion.fuse(oldCentroid, oldCount, summary.centroid);
            float drift = VectorMath.cosine(oldCentroid, fused.centroid);
            if (!Float.isFinite(drift)) throw new IllegalStateException("invalid fusion drift for " + variant.storageKey);
            templates.put(variant, new ActiveTemplate(
                    fused.centroid, fused.effectiveSamples, true,
                    fused.oldWeight, fused.newWeight, drift));
            if (variant == ModelVariant.S) {
                before = oldCount;
                after = fused.effectiveSamples;
            }
        }
        return new Plan(templates, append ? before : 0, append ? after : 5);
    }
}

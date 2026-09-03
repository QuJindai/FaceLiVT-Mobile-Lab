package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnrollmentCommitPlanTest {
    private static EnrollmentSession fiveFrameSession() {
        EnrollmentSession session = new EnrollmentSession();
        for (int i = 0; i < 5; i++) {
            FaceQuality.Snapshot q = FaceQuality.compose(.72f, .72f, .65f, .82f, 1f, .90f,
                    i * 2f, i, 0f, .12f);
            for (ModelVariant variant : ModelVariant.values()) {
                float base = 1f + variant.ordinal() * .2f;
                session.add(variant, VectorMath.normalize(new float[]{base, .1f + i * .04f, .3f}), q);
            }
        }
        return session;
    }

    @Test public void newEnrollmentPrecomputesAllThreeActiveTemplatesBeforePersistence() {
        EnrollmentCommitPlan.Plan plan = EnrollmentCommitPlan.build(
                fiveFrameSession(), false, new EnumMap<>(ModelVariant.class), new EnumMap<>(ModelVariant.class));
        assertEquals(0, plan.effectiveSamplesBefore);
        assertEquals(5, plan.effectiveSamplesAfter);
        assertEquals(3, plan.templates.size());
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentCommitPlan.ActiveTemplate template = plan.templates.get(variant);
            assertEquals(5, template.effectiveSamples);
            assertFalse(template.appended);
            assertEquals(1f, norm(template.centroid), 1e-5f);
        }
    }

    @Test public void appendPrecomputesAllThreeFusionsAndCapsEvidence() {
        EnumMap<ModelVariant, float[]> old = new EnumMap<>(ModelVariant.class);
        EnumMap<ModelVariant, Integer> counts = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            old.put(variant, VectorMath.normalize(new float[]{1f, .05f * variant.ordinal(), .2f}));
            counts.put(variant, 19);
        }
        EnrollmentCommitPlan.Plan plan = EnrollmentCommitPlan.build(fiveFrameSession(), true, old, counts);
        assertEquals(19, plan.effectiveSamplesBefore);
        assertEquals(20, plan.effectiveSamplesAfter);
        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentCommitPlan.ActiveTemplate template = plan.templates.get(variant);
            assertTrue(template.appended);
            assertEquals(15, template.oldWeight);
            assertEquals(5, template.newWeight);
            assertEquals(20, template.effectiveSamples);
            assertTrue(Float.isFinite(template.driftCosine));
        }
    }

    @Test public void appendMissingAnyHistoricalModelFailsDuringPreflight() {
        EnumMap<ModelVariant, float[]> old = new EnumMap<>(ModelVariant.class);
        EnumMap<ModelVariant, Integer> counts = new EnumMap<>(ModelVariant.class);
        old.put(ModelVariant.XS, new float[]{1f, 0f, 0f});
        old.put(ModelVariant.S, new float[]{1f, 0f, 0f});
        counts.put(ModelVariant.XS, 5);
        counts.put(ModelVariant.S, 5);
        boolean threw = false;
        try {
            EnrollmentCommitPlan.build(fiveFrameSession(), true, old, counts);
        } catch (IllegalStateException expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    private static float norm(float[] vector) {
        double sum = 0d;
        for (float value : vector) sum += value * value;
        return (float)Math.sqrt(sum);
    }
}

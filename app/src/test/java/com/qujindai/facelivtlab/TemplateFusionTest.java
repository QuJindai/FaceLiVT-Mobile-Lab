package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TemplateFusionTest {
    @Test public void firstAppendUsesHistoricalEvidenceAndFiveNewSamples() {
        TemplateFusion.Result r = TemplateFusion.fuse(new float[]{1f, 0f}, 5,
                new float[]{0f, 1f});
        assertEquals(5, r.oldWeight);
        assertEquals(5, r.newWeight);
        assertEquals(10, r.effectiveSamples);
        assertEquals(1f, norm(r.centroid), 1e-6f);
        assertEquals(r.centroid[0], r.centroid[1], 1e-6f);
    }

    @Test public void oldWeightCapsAtFifteenAndEffectiveSamplesAtTwenty() {
        TemplateFusion.Result r = TemplateFusion.fuse(new float[]{1f, 0f}, 19,
                new float[]{0f, 1f});
        assertEquals(15, r.oldWeight);
        assertEquals(5, r.newWeight);
        assertEquals(20, r.effectiveSamples);
        assertTrue(r.centroid[0] > r.centroid[1]);
    }

    @Test public void inputsAreNormalizedBeforeFusion() {
        TemplateFusion.Result a = TemplateFusion.fuse(new float[]{10f, 0f}, 5,
                new float[]{0f, 20f});
        TemplateFusion.Result b = TemplateFusion.fuse(new float[]{1f, 0f}, 5,
                new float[]{0f, 1f});
        assertEquals(a.centroid[0], b.centroid[0], 1e-6f);
        assertEquals(a.centroid[1], b.centroid[1], 1e-6f);
    }

    private static float norm(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        return (float)Math.sqrt(sum);
    }
}

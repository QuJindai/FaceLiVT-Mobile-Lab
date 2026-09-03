package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlignmentGeometryTest {
    @Test public void exactFivePointSimilarityHasNearZeroResidual() {
        float[] src = {10,20, 30,20, 20,30, 12,40, 28,40};
        float[] dst = new float[src.length];
        for (int i = 0; i < src.length; i += 2) {
            float x = src[i], y = src[i + 1];
            dst[i] = 1.2f * x - 0.3f * y + 5f;
            dst[i + 1] = 0.3f * x + 1.2f * y - 7f;
        }
        float[] affine = SimilarityTransform.fit(src, dst);
        AlignmentGeometry g = AlignmentGeometry.fromTransform(src, dst, affine);

        assertEquals(5, g.landmarkCount);
        assertFalse(g.usedFallback);
        assertEquals(20f, g.eyeDistancePx, 1e-4f);
        assertEquals(0f, g.rollDeg, 1e-4f);
        assertEquals((float)Math.sqrt(1.2f * 1.2f + 0.3f * 0.3f), g.scale, 1e-4f);
        assertEquals(0f, g.meanResidualPx, 1e-3f);
        assertEquals(0f, g.maxResidualPx, 1e-3f);
    }

    @Test public void perturbedLandmarkExposesMaxResidual() {
        float[] src = {10,20, 30,20, 20,30, 12,40, 28,40};
        float[] dst = src.clone();
        dst[8] += 6f;
        dst[9] -= 4f;
        float[] affine = SimilarityTransform.fit(src, dst);
        AlignmentGeometry g = AlignmentGeometry.fromTransform(src, dst, affine);

        assertTrue(g.meanResidualPx > 0f);
        assertTrue(g.maxResidualPx > g.meanResidualPx);
    }

    @Test public void fallbackIsExplicitAndResidualIsUnavailable() {
        AlignmentGeometry g = AlignmentGeometry.fallback(3);
        assertTrue(g.usedFallback);
        assertEquals(3, g.landmarkCount);
        assertTrue(Float.isNaN(g.meanResidualPx));
        assertTrue(Float.isNaN(g.maxResidualPx));
    }
}

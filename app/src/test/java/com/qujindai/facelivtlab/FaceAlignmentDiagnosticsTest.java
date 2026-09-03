package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class FaceAlignmentDiagnosticsTest {
    private static final float[] CANONICAL = {
            38.2946f, 51.6963f,
            73.5318f, 51.5014f,
            56.0252f, 71.7366f,
            41.5493f, 92.3655f,
            70.7299f, 92.2041f
    };

    @Test public void perfectCanonicalPointsHaveNearZeroResidual() {
        float[] affine = SimilarityTransform.fit(CANONICAL, CANONICAL);
        FaceAlignmentDiagnostics d = FaceAlignmentDiagnostics.fromSimilarity(
                CANONICAL, CANONICAL, affine, 5, false);

        assertEquals(5, d.landmarkCount);
        assertFalse(d.usedFallback);
        assertEquals(1f, d.scale, 1e-4f);
        assertEquals(0f, d.rotationDeg, 1e-4f);
        assertTrue(d.eyeDistancePx > 35f);
        assertTrue(d.meanResidualPx < 1e-4f);
        assertTrue(d.maxResidualPx < 1e-4f);
    }

    @Test public void perturbedLandmarkProducesPositiveResidual() {
        float[] src = CANONICAL.clone();
        src[8] += 4f;
        float[] affine = SimilarityTransform.fit(src, CANONICAL);
        FaceAlignmentDiagnostics d = FaceAlignmentDiagnostics.fromSimilarity(
                src, CANONICAL, affine, 5, false);

        assertTrue(d.meanResidualPx > 0.1f);
        assertTrue(d.maxResidualPx >= d.meanResidualPx);
        assertTrue(Float.isFinite(d.scale));
        assertTrue(Float.isFinite(d.rotationDeg));
    }

    @Test public void fallbackDoesNotInventTransformMetrics() {
        FaceAlignmentDiagnostics d = FaceAlignmentDiagnostics.fallback(3);
        assertEquals(3, d.landmarkCount);
        assertTrue(d.usedFallback);
        assertTrue(Float.isNaN(d.scale));
        assertTrue(Float.isNaN(d.rotationDeg));
        assertTrue(Float.isNaN(d.meanResidualPx));
        assertTrue(Float.isNaN(d.maxResidualPx));
    }
}

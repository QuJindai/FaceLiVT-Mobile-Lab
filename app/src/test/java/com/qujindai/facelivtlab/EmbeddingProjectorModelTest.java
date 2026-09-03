package com.qujindai.facelivtlab;

import org.junit.Test;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EmbeddingProjectorModelTest {
    @Test public void fittedProjectionKeepsAxesFixedForMovingProbe() {
        EmbeddingProjector.Model model = EmbeddingProjector.fit(Arrays.asList(
                new float[]{1f,0f,0f,0f},
                new float[]{.98f,.18f,0f,0f},
                new float[]{.98f,-.18f,0f,0f},
                new float[]{.97f,0f,.20f,0f},
                new float[]{.97f,0f,-.20f,0f}
        ));

        float[][] refsBefore = model.trainingProjection();
        float[] p1 = model.project(new float[]{.99f,.05f,.02f,0f});
        float[] p2 = model.project(new float[]{.99f,-.05f,-.02f,0f});
        float[][] refsAfter = model.trainingProjection();

        assertArrayEquals(refsBefore[0], refsAfter[0], 1e-6f);
        assertEquals(2, p1.length);
        assertEquals(2, p2.length);
        assertTrue(Float.isFinite(p1[0]) && Float.isFinite(p1[1]));
        assertTrue(Float.isFinite(p2[0]) && Float.isFinite(p2[1]));
        assertFalse("probe should move while reference axes stay fixed",
                Math.abs(p1[0]-p2[0]) < 1e-6f && Math.abs(p1[1]-p2[1]) < 1e-6f);

        float[] ev = model.explainedVarianceRatio();
        assertEquals(2, ev.length);
        assertTrue(ev[0] >= 0f && ev[0] <= 1f);
        assertTrue(ev[1] >= 0f && ev[1] <= 1f);
        assertTrue(ev[0] + ev[1] <= 1.0001f);
    }
}

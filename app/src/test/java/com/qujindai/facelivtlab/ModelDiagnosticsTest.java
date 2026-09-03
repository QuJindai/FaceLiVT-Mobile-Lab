package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModelDiagnosticsTest {
    @Test public void acceptsExpectedDiagnosticShapesAndSummarizesPrehead() {
        float[][] blocks = new float[18][4];
        float[][] stages = new float[4][4];
        float[] prehead = new float[1284];
        prehead[0] = 2f;
        prehead[1] = -2f;

        ModelDiagnostics d = ModelDiagnostics.of(blocks, stages, prehead);

        assertEquals(18, d.blockStats.length);
        assertEquals(4, d.stageStats.length);
        assertEquals(1284, d.prehead.length);
        assertTrue(d.preheadMeanAbs > 0f);
        assertTrue(d.preheadRms > 0f);
        assertTrue(d.preheadSparsity > 0.99f);
    }

    @Test public void defensiveCopiesProtectRuntimeState() {
        float[][] blocks = new float[18][4];
        float[][] stages = new float[4][4];
        float[] prehead = new float[1284];
        ModelDiagnostics d = ModelDiagnostics.of(blocks, stages, prehead);
        blocks[0][0] = 99f;
        stages[0][0] = 99f;
        prehead[0] = 99f;
        assertEquals(0f, d.blockStats[0][0], 0f);
        assertEquals(0f, d.stageStats[0][0], 0f);
        assertEquals(0f, d.prehead[0], 0f);
    }

    @Test public void rejectsWrongBlockShape() {
        assertInvalid(new float[17][4], new float[4][4], new float[1284]);
    }

    @Test public void rejectsWrongStageShape() {
        assertInvalid(new float[18][4], new float[4][3], new float[1284]);
    }

    @Test public void rejectsWrongPreheadShape() {
        assertInvalid(new float[18][4], new float[4][4], new float[100]);
    }

    private static void assertInvalid(float[][] blocks, float[][] stages, float[] prehead) {
        try {
            ModelDiagnostics.of(blocks, stages, prehead);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}

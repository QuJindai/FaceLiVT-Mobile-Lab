package com.qujindai.facelivtlab;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class EmbeddingProjectorTest {
    @Test public void projectionIsFiniteBoundedAndHasTwoAxes() {
        float[][] out = EmbeddingProjector.project(Arrays.asList(
                new float[]{1f,0f,0f,0f},
                new float[]{.9f,.1f,0f,0f},
                new float[]{0f,1f,0f,0f},
                new float[]{0f,.9f,.1f,0f}
        ));
        assertEquals(4, out.length);
        for (float[] p : out) {
            assertEquals(2, p.length);
            assertTrue(Float.isFinite(p[0]));
            assertTrue(Float.isFinite(p[1]));
            assertTrue(Math.abs(p[0]) <= 1.0001f);
            assertTrue(Math.abs(p[1]) <= 1.0001f);
        }
    }
}
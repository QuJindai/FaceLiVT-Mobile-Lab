package com.qujindai.facelivtlab;

import org.junit.Test;
import static org.junit.Assert.*;

public class VectorMathTest {
    @Test public void normalizeHasUnitLength() {
        float[] v = VectorMath.normalize(new float[]{3f, 4f});
        assertEquals(1f, VectorMath.cosine(v, v), 1e-6f);
        assertEquals(0.6f, v[0], 1e-6f);
        assertEquals(0.8f, v[1], 1e-6f);
    }

    @Test public void cosineSeparatesOppositeVectors() {
        assertEquals(-1f, VectorMath.cosine(new float[]{1, 0}, new float[]{-1, 0}), 1e-6f);
    }
}

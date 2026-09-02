package com.qujindai.facelivtlab;

import org.junit.Test;
import static org.junit.Assert.*;

public class SimilarityTransformTest {
    @Test public void recoversKnownSimilarityTransform() {
        float[] src = {0,0, 1,0, 0,1, 2,1, 1,2};
        float[] dst = new float[src.length];
        for (int i = 0; i < src.length; i += 2) {
            float x = src[i], y = src[i + 1];
            dst[i] = 2f * x - 0.5f * y + 7f;
            dst[i + 1] = 0.5f * x + 2f * y - 3f;
        }
        float[] m = SimilarityTransform.fit(src, dst);
        assertEquals(2f, m[0], 1e-5f);
        assertEquals(-0.5f, m[1], 1e-5f);
        assertEquals(7f, m[2], 1e-5f);
        assertEquals(0.5f, m[3], 1e-5f);
        assertEquals(2f, m[4], 1e-5f);
        assertEquals(-3f, m[5], 1e-5f);
    }
}

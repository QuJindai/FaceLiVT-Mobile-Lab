package com.qujindai.facelivtlab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TemporalEmbeddingBufferTest {
    @Test public void sameTrackingIdFusesAndNormalizes() {
        TemporalEmbeddingBuffer buffer = new TemporalEmbeddingBuffer(5);
        buffer.push(7, new float[]{1f, 0f});
        float[] fused = buffer.push(7, new float[]{0f, 1f});
        float expected = (float)(1.0 / Math.sqrt(2.0));
        assertEquals(2, buffer.size());
        assertEquals(expected, fused[0], 1e-5f);
        assertEquals(expected, fused[1], 1e-5f);
    }

    @Test public void trackingIdChangeResetsBuffer() {
        TemporalEmbeddingBuffer buffer = new TemporalEmbeddingBuffer(5);
        buffer.push(7, new float[]{1f, 0f});
        float[] fused = buffer.push(8, new float[]{0f, 1f});
        assertEquals(1, buffer.size());
        assertEquals(0f, fused[0], 1e-6f);
        assertEquals(1f, fused[1], 1e-6f);
    }
}

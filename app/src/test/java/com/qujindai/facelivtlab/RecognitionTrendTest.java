package com.qujindai.facelivtlab;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecognitionTrendTest {
    @Test public void boundedTrendDropsOldestSamples() {
        RecognitionTrend trend = new RecognitionTrend(3);
        trend.add(.1f,.5f);
        trend.add(.2f,.6f);
        trend.add(.3f,.7f);
        trend.add(.4f,.8f);
        assertEquals(3, trend.size());
        assertArrayEquals(new float[]{.2f,.3f,.4f}, trend.similarities(), 1e-6f);
        assertArrayEquals(new float[]{.6f,.7f,.8f}, trend.qualities(), 1e-6f);
    }
}
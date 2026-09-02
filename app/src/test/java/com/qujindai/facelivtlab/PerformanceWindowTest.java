package com.qujindai.facelivtlab;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PerformanceWindowTest {
    @Test public void evictsOldestSampleAtCapacity() {
        PerformanceWindow window = new PerformanceWindow(2);
        window.add(10, 20, 30);
        window.add(20, 30, 50);
        window.add(30, 40, 70);
        assertEquals(2, window.size());
        assertEquals(25.0, window.avgDetectMs(), 1e-6);
        assertEquals(35.0, window.avgInferMs(), 1e-6);
        assertEquals(60.0, window.avgTotalMs(), 1e-6);
    }

    @Test public void emptyWindowReportsZero() {
        PerformanceWindow window = new PerformanceWindow(3);
        assertEquals(0.0, window.avgDetectMs(), 0.0);
        assertEquals(0.0, window.avgInferMs(), 0.0);
        assertEquals(0.0, window.avgTotalMs(), 0.0);
    }
}

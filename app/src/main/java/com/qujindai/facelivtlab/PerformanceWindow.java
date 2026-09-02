package com.qujindai.facelivtlab;

import java.util.ArrayDeque;
import java.util.Deque;

public final class PerformanceWindow {
    private static final class Sample {
        final long detectMs;
        final long inferMs;
        final long totalMs;
        Sample(long detectMs, long inferMs, long totalMs) {
            this.detectMs = detectMs;
            this.inferMs = inferMs;
            this.totalMs = totalMs;
        }
    }

    private final int capacity;
    private final Deque<Sample> samples = new ArrayDeque<>();

    public PerformanceWindow(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public synchronized void add(long detectMs, long inferMs, long totalMs) {
        samples.addLast(new Sample(detectMs, inferMs, totalMs));
        while (samples.size() > capacity) samples.removeFirst();
    }

    public synchronized int size() { return samples.size(); }

    public synchronized double avgDetectMs() { return average(0); }
    public synchronized double avgInferMs() { return average(1); }
    public synchronized double avgTotalMs() { return average(2); }

    private double average(int field) {
        if (samples.isEmpty()) return 0.0;
        long sum = 0L;
        for (Sample s : samples) {
            sum += field == 0 ? s.detectMs : field == 1 ? s.inferMs : s.totalMs;
        }
        return (double) sum / samples.size();
    }
}

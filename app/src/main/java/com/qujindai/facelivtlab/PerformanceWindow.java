package com.qujindai.facelivtlab;

import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling stage timing window. Current-frame values stay at the call site; this class only reports the moving mean. */
public final class PerformanceWindow {
    private static final class Sample {
        final long detectMs;
        final long alignMs;
        final long inferMs;
        final long matchMs;
        final long totalMs;

        Sample(long detectMs, long alignMs, long inferMs, long matchMs, long totalMs) {
            this.detectMs = detectMs;
            this.alignMs = alignMs;
            this.inferMs = inferMs;
            this.matchMs = matchMs;
            this.totalMs = totalMs;
        }
    }

    private final int capacity;
    private final Deque<Sample> samples = new ArrayDeque<>();

    public PerformanceWindow(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    /** R2/R3 compatibility path. */
    public synchronized void add(long detectMs, long inferMs, long totalMs) {
        add(detectMs, 0L, inferMs, 0L, totalMs);
    }

    public synchronized void add(long detectMs, long alignMs, long inferMs, long matchMs, long totalMs) {
        samples.addLast(new Sample(detectMs, alignMs, inferMs, matchMs, totalMs));
        while (samples.size() > capacity) samples.removeFirst();
    }

    public synchronized int size() { return samples.size(); }
    public synchronized double avgDetectMs() { return average(0); }
    public synchronized double avgAlignMs() { return average(1); }
    public synchronized double avgInferMs() { return average(2); }
    public synchronized double avgMatchMs() { return average(3); }
    public synchronized double avgTotalMs() { return average(4); }

    private double average(int field) {
        if (samples.isEmpty()) return 0.0;
        long sum = 0L;
        for (Sample s : samples) {
            if (field == 0) sum += s.detectMs;
            else if (field == 1) sum += s.alignMs;
            else if (field == 2) sum += s.inferMs;
            else if (field == 3) sum += s.matchMs;
            else sum += s.totalMs;
        }
        return (double) sum / samples.size();
    }
}

package com.qujindai.facelivtlab;

import java.util.ArrayDeque;
import java.util.Deque;

public final class TemporalEmbeddingBuffer {
    private final int capacity;
    private final Deque<float[]> samples = new ArrayDeque<>();
    private Integer trackingId;

    public TemporalEmbeddingBuffer(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public synchronized float[] push(int trackingId, float[] unitEmbedding) {
        if (unitEmbedding == null || unitEmbedding.length == 0) {
            throw new IllegalArgumentException("embedding required");
        }
        if (this.trackingId == null || this.trackingId != trackingId) {
            samples.clear();
            this.trackingId = trackingId;
        }
        samples.addLast(unitEmbedding.clone());
        while (samples.size() > capacity) samples.removeFirst();

        float[] sum = new float[unitEmbedding.length];
        for (float[] sample : samples) {
            if (sample.length != sum.length) throw new IllegalArgumentException("embedding size changed");
            for (int i = 0; i < sum.length; i++) sum[i] += sample[i];
        }
        return VectorMath.normalize(sum);
    }

    public synchronized int size() {
        return samples.size();
    }

    public synchronized void clear() {
        samples.clear();
        trackingId = null;
    }
}

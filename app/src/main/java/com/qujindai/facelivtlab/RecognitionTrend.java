package com.qujindai.facelivtlab;

import java.util.ArrayDeque;

/** Rolling microscope history for recognition similarity and probe quality. */
public final class RecognitionTrend {
    private final int capacity;
    private final ArrayDeque<Float> similarities = new ArrayDeque<>();
    private final ArrayDeque<Float> qualities = new ArrayDeque<>();

    public RecognitionTrend(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.capacity = capacity;
    }

    public synchronized void add(float similarity, float quality) {
        if (similarities.size() == capacity) {
            similarities.removeFirst();
            qualities.removeFirst();
        }
        similarities.addLast(similarity);
        qualities.addLast(quality);
    }

    public synchronized int size() { return similarities.size(); }

    public synchronized void clear() {
        similarities.clear();
        qualities.clear();
    }

    public synchronized float[] similarities() { return toArray(similarities); }
    public synchronized float[] qualities() { return toArray(qualities); }

    private static float[] toArray(ArrayDeque<Float> values) {
        float[] out = new float[values.size()];
        int i = 0;
        for (Float value : values) out[i++] = value == null ? 0f : value;
        return out;
    }
}

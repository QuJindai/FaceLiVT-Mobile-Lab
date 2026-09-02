package com.qujindai.facelivtlab;

public final class LowResPolicy {
    private static final int MIN_SHORT_SIDE = 360;

    private LowResPolicy() {}

    public static int[] assistedSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("positive dimensions required");
        int shortSide = Math.min(width, height);
        if (shortSide >= MIN_SHORT_SIDE) return new int[]{width, height};
        double scale = (double) MIN_SHORT_SIDE / shortSide;
        return new int[]{(int)Math.round(width * scale), (int)Math.round(height * scale)};
    }
}

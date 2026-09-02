package com.qujindai.facelivtlab;

public enum DegradationProfile {
    NATIVE("原始分析帧", Integer.MAX_VALUE, 100),
    P1080("1080p / JPEG 95", 1080, 95),
    P720("720p / JPEG 85", 720, 85),
    P480("480p / JPEG 75", 480, 75),
    P360("360p / JPEG 65", 360, 65),
    P240("240p / JPEG 55", 240, 55),
    P180("180p / JPEG 45", 180, 45),
    P144("144p / JPEG 35", 144, 35);

    public final String label;
    public final int targetShortSide;
    public final int jpegQuality;

    DegradationProfile(String label, int targetShortSide, int jpegQuality) {
        this.label = label;
        this.targetShortSide = targetShortSide;
        this.jpegQuality = jpegQuality;
    }

    @Override public String toString() { return label; }
}

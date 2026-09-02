package com.qujindai.facelivtlab;

public enum ModelVariant {
    XS("FaceLiVTv2-XS", "facelivtv2_xs.onnx", "XS"),
    S("FaceLiVTv2-S", "facelivtv2_s.onnx", "S"),
    M("FaceLiVTv2-M", "facelivtv2_m.onnx", "M");

    public final String label;
    public final String assetName;
    public final String storageKey;

    ModelVariant(String label, String assetName, String storageKey) {
        this.label = label;
        this.assetName = assetName;
        this.storageKey = storageKey;
    }

    @Override public String toString() { return label; }
}

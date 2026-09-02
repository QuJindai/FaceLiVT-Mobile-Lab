package com.qujindai.facelivtlab;

public enum ModelMode {
    S("S（默认）", new ModelVariant[]{ModelVariant.S}),
    XS("XS（更轻）", new ModelVariant[]{ModelVariant.XS}),
    M("M（更准）", new ModelVariant[]{ModelVariant.M}),
    COMPARE("XS / S / M 对比", new ModelVariant[]{ModelVariant.XS, ModelVariant.S, ModelVariant.M});

    private final String label;
    private final ModelVariant[] variants;

    ModelMode(String label, ModelVariant[] variants) {
        this.label = label;
        this.variants = variants;
    }

    public ModelVariant[] variants() {
        return variants.clone();
    }

    @Override public String toString() { return label; }
}

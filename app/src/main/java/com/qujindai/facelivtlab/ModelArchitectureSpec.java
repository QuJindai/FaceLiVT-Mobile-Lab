package com.qujindai.facelivtlab;

/** Immutable public metadata for one FaceLiVTv2 backbone variant. */
public final class ModelArchitectureSpec {
    public final ModelVariant variant;
    public final String label;
    public final int[] depths;
    public final int[] widths;
    public final String[] mixerTypes;
    public final int blockCount;
    public final float approxParamsM;
    public final int preheadDim;
    public final int embeddingDim;

    private ModelArchitectureSpec(ModelVariant variant, float approxParamsM, int[] widths) {
        this.variant = variant;
        this.label = variant.label;
        this.depths = new int[]{3, 3, 9, 3};
        this.widths = widths.clone();
        this.mixerTypes = new String[]{"RepMix", "RepMix", "MHLA", "MHLA"};
        int total = 0;
        for (int depth : depths) total += depth;
        this.blockCount = total;
        this.approxParamsM = approxParamsM;
        this.preheadDim = 1284;
        this.embeddingDim = 512;
    }

    public static ModelArchitectureSpec forVariant(ModelVariant variant) {
        if (variant == null) throw new IllegalArgumentException("variant is required");
        switch (variant) {
            case XS:
                return new ModelArchitectureSpec(variant, 2.90f, new int[]{32, 64, 128, 256});
            case S:
                return new ModelArchitectureSpec(variant, 4.62f, new int[]{48, 96, 192, 320});
            case M:
                return new ModelArchitectureSpec(variant, 7.04f, new int[]{56, 112, 224, 448});
            default:
                throw new IllegalArgumentException("unsupported variant: " + variant);
        }
    }

    public int stageStartBlock(int stage) {
        if (stage < 0 || stage >= depths.length) throw new IllegalArgumentException("stage out of range");
        int start = 0;
        for (int i = 0; i < stage; i++) start += depths[i];
        return start;
    }

    public int stageForBlock(int block) {
        if (block < 0 || block >= blockCount) throw new IllegalArgumentException("block out of range");
        int cursor = 0;
        for (int stage = 0; stage < depths.length; stage++) {
            cursor += depths[stage];
            if (block < cursor) return stage;
        }
        return depths.length - 1;
    }
}

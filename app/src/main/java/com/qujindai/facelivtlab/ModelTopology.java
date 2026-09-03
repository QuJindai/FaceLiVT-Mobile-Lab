package com.qujindai.facelivtlab;

/** One source of truth for the FaceLiVTv2 variants shown by the R4 model microscope. */
public final class ModelTopology {
    public final ModelVariant variant;
    public final float parameterCountM;
    public final int[] widths;
    public final int[] stageDepths;
    public final String[] stageTypes;
    public final int blockCount;
    public final int finalFeatureDim;
    public final int embeddingDim;

    private ModelTopology(ModelVariant variant, float parameterCountM, int[] widths) {
        this.variant = variant;
        this.parameterCountM = parameterCountM;
        this.widths = widths.clone();
        this.stageDepths = new int[]{3, 3, 9, 3};
        this.stageTypes = new String[]{"RepMix", "RepMix", "MHLA", "MHLA"};
        this.blockCount = 18;
        this.finalFeatureDim = 1284;
        this.embeddingDim = 512;
    }

    public static ModelTopology forVariant(ModelVariant variant) {
        if (variant == ModelVariant.XS) {
            return new ModelTopology(ModelVariant.XS, 2.90f, new int[]{32, 64, 128, 256});
        }
        if (variant == ModelVariant.M) {
            return new ModelTopology(ModelVariant.M, 7.00f, new int[]{56, 112, 224, 448});
        }
        return new ModelTopology(ModelVariant.S, 4.62f, new int[]{48, 96, 192, 320});
    }

    public String compactHeader() {
        return String.format(java.util.Locale.US,
                "FaceLiVTv2-%s · %.2fM · 18 blocks · [3,3,9,3] · width [%d,%d,%d,%d] · 1284D→512D",
                variant.storageKey, parameterCountM, widths[0], widths[1], widths[2], widths[3]);
    }
}

package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ModelTopologyTest {
    @Test public void variantsShareDepthButUseDifferentWidths() {
        ModelTopology xs = ModelTopology.forVariant(ModelVariant.XS);
        ModelTopology s = ModelTopology.forVariant(ModelVariant.S);
        ModelTopology m = ModelTopology.forVariant(ModelVariant.M);

        int[] depths = {3, 3, 9, 3};
        assertArrayEquals(depths, xs.stageDepths);
        assertArrayEquals(depths, s.stageDepths);
        assertArrayEquals(depths, m.stageDepths);
        assertArrayEquals(new int[]{32, 64, 128, 256}, xs.widths);
        assertArrayEquals(new int[]{48, 96, 192, 320}, s.widths);
        assertArrayEquals(new int[]{56, 112, 224, 448}, m.widths);
    }

    @Test public void allVariantsExposeEighteenBlocksAndSameOutputDimensions() {
        for (ModelVariant variant : ModelVariant.values()) {
            ModelTopology topology = ModelTopology.forVariant(variant);
            assertEquals(18, topology.blockCount);
            assertEquals(1284, topology.finalFeatureDim);
            assertEquals(512, topology.embeddingDim);
            assertArrayEquals(new String[]{"RepMix", "RepMix", "MHLA", "MHLA"}, topology.stageTypes);
        }
    }

    @Test public void parameterMetadataMatchesCurrentFaceLiVTv2Variants() {
        assertEquals(2.90f, ModelTopology.forVariant(ModelVariant.XS).parameterCountM, 0.02f);
        assertEquals(4.62f, ModelTopology.forVariant(ModelVariant.S).parameterCountM, 0.02f);
        assertEquals(7.00f, ModelTopology.forVariant(ModelVariant.M).parameterCountM, 0.08f);
    }
}

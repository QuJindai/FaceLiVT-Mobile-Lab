package com.qujindai.facelivtlab;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class ModelModeTest {
    @Test public void singleModesRunOnlySelectedModel() {
        assertArrayEquals(new ModelVariant[]{ModelVariant.S}, ModelMode.S.variants());
        assertArrayEquals(new ModelVariant[]{ModelVariant.XS}, ModelMode.XS.variants());
        assertArrayEquals(new ModelVariant[]{ModelVariant.M}, ModelMode.M.variants());
    }

    @Test public void compareRunsAllThreeInStableOrder() {
        assertArrayEquals(new ModelVariant[]{ModelVariant.XS, ModelVariant.S, ModelVariant.M}, ModelMode.COMPARE.variants());
    }
}

package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class ModelArchitectureSpecTest {
    @Test public void variantsShareDepthButDifferWidth() {
        ModelArchitectureSpec xs = ModelArchitectureSpec.forVariant(ModelVariant.XS);
        ModelArchitectureSpec s = ModelArchitectureSpec.forVariant(ModelVariant.S);
        ModelArchitectureSpec m = ModelArchitectureSpec.forVariant(ModelVariant.M);

        assertArrayEquals(new int[]{3, 3, 9, 3}, xs.depths);
        assertArrayEquals(new int[]{3, 3, 9, 3}, s.depths);
        assertArrayEquals(new int[]{3, 3, 9, 3}, m.depths);
        assertEquals(18, xs.blockCount);
        assertEquals(18, s.blockCount);
        assertEquals(18, m.blockCount);
        assertArrayEquals(new int[]{32, 64, 128, 256}, xs.widths);
        assertArrayEquals(new int[]{48, 96, 192, 320}, s.widths);
        assertArrayEquals(new int[]{56, 112, 224, 448}, m.widths);
        assertArrayEquals(new String[]{"RepMix", "RepMix", "MHLA", "MHLA"}, s.mixerTypes);
        assertEquals(1284, s.preheadDim);
        assertEquals(512, s.embeddingDim);
        assertEquals(2.90f, xs.approxParamsM, 1e-6f);
        assertEquals(4.62f, s.approxParamsM, 1e-6f);
        assertEquals(7.04f, m.approxParamsM, 1e-6f);
    }

    @Test public void returnedArraysAreDefensiveCopies() {
        ModelArchitectureSpec first = ModelArchitectureSpec.forVariant(ModelVariant.S);
        first.depths[0] = 99;
        first.widths[0] = 99;

        ModelArchitectureSpec second = ModelArchitectureSpec.forVariant(ModelVariant.S);
        assertArrayEquals(new int[]{3, 3, 9, 3}, second.depths);
        assertArrayEquals(new int[]{48, 96, 192, 320}, second.widths);
    }
}

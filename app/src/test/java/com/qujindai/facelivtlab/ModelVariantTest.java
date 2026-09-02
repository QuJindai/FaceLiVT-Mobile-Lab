package com.qujindai.facelivtlab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class ModelVariantTest {
    @Test public void mapsEachVariantToItsOwnOnnxAssetAndStorageKey() {
        assertEquals("facelivtv2_xs.onnx", ModelVariant.XS.assetName);
        assertEquals("facelivtv2_s.onnx", ModelVariant.S.assetName);
        assertEquals("facelivtv2_m.onnx", ModelVariant.M.assetName);
        assertEquals("XS", ModelVariant.XS.storageKey);
        assertEquals("S", ModelVariant.S.storageKey);
        assertEquals("M", ModelVariant.M.storageKey);
        assertNotEquals(ModelVariant.XS.assetName, ModelVariant.S.assetName);
        assertNotEquals(ModelVariant.S.assetName, ModelVariant.M.assetName);
    }
}

package com.qujindai.facelivtlab;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class LowResPolicyTest {
    @Test public void upscalesShortSideTo360WithoutChangingAspectRatio() {
        assertArrayEquals(new int[]{360, 480}, LowResPolicy.assistedSize(240, 320));
        assertArrayEquals(new int[]{640, 360}, LowResPolicy.assistedSize(320, 180));
    }

    @Test public void doesNotDownscaleFramesAlreadyAtOrAbove360() {
        assertArrayEquals(new int[]{720, 1280}, LowResPolicy.assistedSize(720, 1280));
        assertArrayEquals(new int[]{640, 360}, LowResPolicy.assistedSize(640, 360));
    }
}

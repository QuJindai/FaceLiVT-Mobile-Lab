package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class ThresholdCalibratorTest {
    @Test public void oneIdentityIsExplicitlyInsufficient() {
        ThresholdCalibrator.Result r = ThresholdCalibrator.calibrate(
                1,
                new float[]{.80f,.78f,.82f,.76f,.79f},
                new float[0]);

        assertFalse(r.available);
        assertTrue(r.message.contains("至少 2 个身份"));
    }

    @Test public void separatedDistributionsProduceUsefulThresholdAndLowEmpiricalError() {
        ThresholdCalibrator.Result r = ThresholdCalibrator.calibrate(
                3,
                new float[]{.82f,.79f,.76f,.84f,.74f,.81f,.77f,.80f,.75f,.83f},
                new float[]{.21f,.28f,.31f,.35f,.26f,.38f});

        assertTrue(r.available);
        assertTrue(r.suggestedThreshold > .38f);
        assertTrue(r.suggestedThreshold < .74f);
        assertEquals(0f, r.empiricalFar, 1e-6f);
        assertEquals(0f, r.empiricalFrr, 1e-6f);
        assertTrue(r.separation > 0f);
    }

    @Test public void overlappingDistributionsReportNonZeroRisk() {
        ThresholdCalibrator.Result r = ThresholdCalibrator.calibrate(
                3,
                new float[]{.58f,.60f,.62f,.64f,.66f,.68f},
                new float[]{.48f,.52f,.57f,.61f,.63f});

        assertTrue(r.available);
        assertTrue(r.empiricalFar > 0f || r.empiricalFrr > 0f);
    }
}

package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class FaceQualityHardGateTest {
    @Test public void enrollmentCannotBeRescuedByPoseLandmarksAndSize() {
        FaceQuality.Snapshot dimSoft = FaceQuality.compose(
                .20f, .22f, .29f, .81f, 1f, 1f,
                3f, 8f, 5f);

        assertTrue("composite stays above legacy average gate", dimSoft.composite > .55f);
        assertFalse("weak pixels must fail enrollment even when geometry is strong", dimSoft.passesEnrollmentGate());
        assertTrue(dimSoft.enrollmentGateReason().contains("清晰"));
        assertTrue(dimSoft.enrollmentGateReason().contains("光照"));
    }

    @Test public void probeGateIsMoreTolerantThanEnrollmentGate() {
        FaceQuality.Snapshot difficultButRecognizable = FaceQuality.compose(
                .20f, .22f, .29f, .81f, 1f, 1f,
                3f, 8f, 5f);

        assertTrue(difficultButRecognizable.passesProbeGate());
        assertFalse(difficultButRecognizable.passesEnrollmentGate());
    }

    @Test public void goodEnrollmentReportsPassWithoutFailureReasons() {
        FaceQuality.Snapshot good = FaceQuality.compose(
                .70f, .75f, .70f, .90f, 1f, .90f,
                4f, 4f, 2f);

        assertTrue(good.passesEnrollmentGate());
        assertEquals("PASS", good.enrollmentGateReason());
    }
}

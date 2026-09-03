package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class EnrollmentCoverageTest {
    private static FaceQuality.Snapshot q(float yaw, float pitch) {
        return FaceQuality.compose(.75f,.75f,.70f,.92f,1f,.90f,yaw,pitch,1f);
    }

    @Test public void nearDuplicateFiveFramesDoNotPassCoverage() {
        EnrollmentSession s = new EnrollmentSession();
        for (int i = 0; i < 5; i++) {
            s.add(ModelVariant.S, new float[]{1f, .001f * i, 0f}, q(1f + .1f*i, 2f + .1f*i));
        }
        EnrollmentSession.Summary summary = s.summary(ModelVariant.S);
        assertTrue(summary.stability > .99f);
        assertTrue(summary.coverage < EnrollmentSession.MIN_COVERAGE);
        assertFalse(summary.passesEnrollment());
    }

    @Test public void stableButVariedFiveFramesPassCoverage() {
        EnrollmentSession s = new EnrollmentSession();
        s.add(ModelVariant.S, new float[]{1f,0f,0f}, q(-7f, 0f));
        s.add(ModelVariant.S, new float[]{.98f,.18f,0f}, q(-3f, 3f));
        s.add(ModelVariant.S, new float[]{.97f,-.20f,.05f}, q(0f, -3f));
        s.add(ModelVariant.S, new float[]{.98f,.08f,.16f}, q(4f, 2f));
        s.add(ModelVariant.S, new float[]{.97f,-.08f,-.18f}, q(8f, -2f));

        EnrollmentSession.Summary summary = s.summary(ModelVariant.S);
        assertTrue(summary.stability > EnrollmentSession.MIN_STABILITY);
        assertTrue(summary.coverage >= EnrollmentSession.MIN_COVERAGE);
        assertTrue(summary.passesEnrollment());
    }

    @Test public void noveltyNeedsEmbeddingAndPoseDifference() {
        EnrollmentSession s = new EnrollmentSession();
        s.add(ModelVariant.S, new float[]{1f,0f,0f}, q(0f,0f));

        assertFalse(s.isNovelCandidate(ModelVariant.S, new float[]{1f,0f,0f}, q(.2f,.2f)));
        assertFalse("embedding noise alone must not count as coverage",
                s.isNovelCandidate(ModelVariant.S, new float[]{.96f,.28f,0f}, q(.2f,.2f)));
        assertFalse("pose alone must not count when identity vector is a duplicate",
                s.isNovelCandidate(ModelVariant.S, new float[]{1f,0f,0f}, q(5f,0f)));
        assertTrue(s.isNovelCandidate(ModelVariant.S, new float[]{.96f,.28f,0f}, q(5f,0f)));
    }
}

package com.qujindai.facelivtlab;

import org.junit.Test;
import static org.junit.Assert.*;

public class EnrollmentSessionTest {
    @Test public void summaryBuildsWeightedCentroidMatrixAndProjection() {
        EnrollmentSession session = new EnrollmentSession();
        FaceQuality.Snapshot q1 = FaceQuality.compose(1f,1f,1f,1f,1f,1f,0f,0f,0f);
        FaceQuality.Snapshot q2 = FaceQuality.compose(.7f,.8f,.8f,.9f,1f,.8f,10f,3f,2f);
        session.add(ModelVariant.S, new float[]{1f,0f,0f}, q1);
        session.add(ModelVariant.S, new float[]{.9f,.1f,0f}, q2);

        EnrollmentSession.Summary summary = session.summary(ModelVariant.S);
        assertEquals(2, summary.sampleCount);
        assertEquals(3, summary.centroid.length);
        assertEquals(1f, VectorMath.cosine(summary.centroid, summary.centroid), 1e-6f);
        assertEquals(2, summary.similarityMatrix.length);
        assertEquals(1f, summary.similarityMatrix[0][0], 1e-6f);
        assertEquals(summary.similarityMatrix[0][1], summary.similarityMatrix[1][0], 1e-6f);
        assertEquals(3, summary.projection.length); // 2 samples + centroid
        assertTrue(summary.stability > .95f);
        assertTrue(summary.dispersion < .05f);
    }

    @Test public void enrollmentGateNeedsFiveGoodStableAndCoveredSamples() {
        EnrollmentSession session = new EnrollmentSession();
        float[][] vectors = {
                {1f,0f,0f}, {.98f,.18f,0f}, {.97f,-.20f,.05f},
                {.98f,.08f,.16f}, {.97f,-.08f,-.18f}
        };
        float[][] poses = {{-7f,0f},{-3f,3f},{0f,-3f},{4f,2f},{8f,-2f}};
        for (int i = 0; i < 4; i++) {
            FaceQuality.Snapshot q = FaceQuality.compose(.9f,.9f,.9f,.95f,1f,.9f,poses[i][0],poses[i][1],1f);
            session.add(ModelVariant.S, vectors[i], q);
        }
        assertFalse(session.summary(ModelVariant.S).passesEnrollment());
        FaceQuality.Snapshot q5 = FaceQuality.compose(.9f,.9f,.9f,.95f,1f,.9f,poses[4][0],poses[4][1],1f);
        session.add(ModelVariant.S, vectors[4], q5);
        assertTrue(session.summary(ModelVariant.S).passesEnrollment());
    }
}

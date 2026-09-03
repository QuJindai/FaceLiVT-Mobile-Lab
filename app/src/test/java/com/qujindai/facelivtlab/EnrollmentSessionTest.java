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

    @Test public void enrollmentGateNeedsFiveGoodStableSamples() {
        EnrollmentSession session = new EnrollmentSession();
        FaceQuality.Snapshot q = FaceQuality.compose(.9f,.9f,.9f,.95f,1f,.9f,2f,2f,1f);
        for (int i = 0; i < 4; i++) session.add(ModelVariant.S, new float[]{1f,.01f*i,0f}, q);
        assertFalse(session.summary(ModelVariant.S).passesEnrollment());
        session.add(ModelVariant.S, new float[]{1f,.04f,0f}, q);
        assertTrue(session.summary(ModelVariant.S).passesEnrollment());
    }
}
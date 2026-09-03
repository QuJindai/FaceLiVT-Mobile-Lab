package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModelComparisonStatsTest {
    private static FaceQuality.Snapshot q(float yaw, float pitch) {
        return FaceQuality.compose(1f, 1f, 1f, 1f, 1f, 1f, yaw, pitch, 0f);
    }

    @Test public void fiveSamplesCreateFiveByFiveMatrixAndExposeOutlier() {
        EnrollmentSession session = new EnrollmentSession();
        session.add(ModelVariant.S, new float[]{0f, 1f, 0f}, q(-8f, -4f));
        session.add(ModelVariant.S, new float[]{1f, 0f, 0f}, q(-4f, -1f));
        session.add(ModelVariant.S, new float[]{0.99f, 0.10f, 0f}, q(0f, 1f));
        session.add(ModelVariant.S, new float[]{0.98f, -0.10f, 0f}, q(4f, 3f));
        session.add(ModelVariant.S, new float[]{1f, 0.05f, 0f}, q(8f, 5f));

        EnrollmentSession.Summary summary = session.summary(ModelVariant.S);
        assertEquals(5, summary.similarityMatrix.length);
        assertEquals(5, summary.similarityMatrix[0].length);
        assertEquals(0, summary.outlierIndex);
        assertTrue(summary.minPairCosine < summary.meanPairCosine);
        assertTrue(summary.outlierMeanCosine < summary.meanPairCosine);
    }

    @Test public void matrixShapeFollowsSampleCountRatherThanHardCodedFive() {
        EnrollmentSession session = new EnrollmentSession();
        session.add(ModelVariant.XS, new float[]{1f, 0f}, q(-5f, 0f));
        session.add(ModelVariant.XS, new float[]{0.9f, 0.1f}, q(0f, 3f));
        session.add(ModelVariant.XS, new float[]{0.8f, 0.2f}, q(5f, 6f));
        EnrollmentSession.Summary summary = session.summary(ModelVariant.XS);
        assertEquals(3, summary.similarityMatrix.length);
        assertEquals(3, summary.similarityMatrix[0].length);
    }

    @Test public void deltaMatrixIsElementwiseAndKeepsShape() {
        float[][] a = {{1f, .8f, .7f}, {.8f, 1f, .6f}, {.7f, .6f, 1f}};
        float[][] b = {{1f, .7f, .65f}, {.7f, 1f, .55f}, {.65f, .55f, 1f}};
        float[][] d = ModelComparisonStats.delta(a, b);
        assertEquals(3, d.length);
        assertEquals(0.10f, d[0][1], 1e-6f);
        assertEquals(0.05f, d[1][2], 1e-6f);
        assertEquals(0f, d[2][2], 1e-6f);
    }
}

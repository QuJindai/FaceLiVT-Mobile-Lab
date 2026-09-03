package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;

import static org.junit.Assert.*;

public class CrossModelEnrollmentComparisonTest {
    private static EnrollmentSession.Summary summary(float[][] matrix, float stability, float coverage) {
        int n = matrix.length;
        return new EnrollmentSession.Summary(
                n,
                new float[]{1f},
                0.8f,
                stability,
                1f - stability,
                coverage,
                0.5f,
                0.5f,
                true,
                matrix,
                new float[0][0],
                new float[n],
                new ArrayList<>());
    }

    @Test public void computesPairMetricsOutlierAndSignedDeltas() {
        float[][] xs = {
                {1f, 0.72f, 0.82f},
                {0.72f, 1f, 0.88f},
                {0.82f, 0.88f, 1f}
        };
        float[][] s = {
                {1f, 0.70f, 0.80f},
                {0.70f, 1f, 0.90f},
                {0.80f, 0.90f, 1f}
        };
        float[][] m = {
                {1f, 0.75f, 0.85f},
                {0.75f, 1f, 0.92f},
                {0.85f, 0.92f, 1f}
        };

        EnumMap<ModelVariant, EnrollmentSession.Summary> input = new EnumMap<>(ModelVariant.class);
        input.put(ModelVariant.XS, summary(xs, 0.91f, 0.42f));
        input.put(ModelVariant.S, summary(s, 0.92f, 0.45f));
        input.put(ModelVariant.M, summary(m, 0.94f, 0.48f));

        CrossModelEnrollmentComparison result = CrossModelEnrollmentComparison.from(input);
        CrossModelEnrollmentComparison.VariantMetrics sm = result.byVariant.get(ModelVariant.S);

        assertEquals(0.80f, sm.meanPair, 1e-6f);
        assertEquals(0.70f, sm.minPair, 1e-6f);
        assertEquals(0, sm.outlierIndex);
        assertEquals(0.92f, sm.stability, 1e-6f);
        assertEquals(0.45f, sm.coverage, 1e-6f);
        assertEquals(xs[0][1] - s[0][1], result.deltaXsVsS[0][1], 1e-6f);
        assertEquals(m[2][1] - s[2][1], result.deltaMVsS[2][1], 1e-6f);
    }

    @Test public void rejectsMismatchedMatrixSizes() {
        EnumMap<ModelVariant, EnrollmentSession.Summary> input = new EnumMap<>(ModelVariant.class);
        input.put(ModelVariant.XS, summary(new float[][]{{1f, .8f}, {.8f, 1f}}, .9f, .4f));
        input.put(ModelVariant.S, summary(new float[][]{{1f}}, .9f, .4f));
        input.put(ModelVariant.M, summary(new float[][]{{1f, .8f}, {.8f, 1f}}, .9f, .4f));
        try {
            CrossModelEnrollmentComparison.from(input);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("matrix size"));
        }
    }
}

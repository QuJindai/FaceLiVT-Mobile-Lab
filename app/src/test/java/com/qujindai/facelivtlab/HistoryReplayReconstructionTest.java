package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HistoryReplayReconstructionTest {
    @Test public void persistedLearningVersionRebuildsSelectedModelMatrixAndCentroidChain() {
        EnrollmentSession original = new EnrollmentSession();
        List<AlignmentGeometry> geometries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FaceQuality.Snapshot q = FaceQuality.compose(.72f + i * .01f, .70f, .66f,
                    .82f, 1f, .9f, i, -i * .5f, 0f, .12f + i * .01f);
            geometries.add(AlignmentGeometry.fallback(4));
            for (ModelVariant variant : ModelVariant.values()) {
                float base = 1f + variant.ordinal() * .2f;
                original.add(variant, VectorMath.normalize(new float[]{base, i * .08f + .1f, .3f}), q);
            }
        }

        EnrollmentHistoryRecord record = EnrollmentHistoryRecord.fromSession(
                "history", 3, 123L, "480p", 10, 15, original, geometries);
        EnrollmentHistoryRecord decoded = EnrollmentHistoryCodec.decode(EnrollmentHistoryCodec.encode(record));
        EnrollmentSession replay = decoded.toEnrollmentSession();

        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentSession.Summary before = original.summary(variant);
            EnrollmentSession.Summary after = replay.summary(variant);
            assertEquals(5, after.sampleCount);
            assertEquals(before.centroid.length, after.centroid.length);
            for (int i = 0; i < before.similarityMatrix.length; i++) {
                for (int j = 0; j < before.similarityMatrix[i].length; j++) {
                    assertEquals(before.similarityMatrix[i][j], after.similarityMatrix[i][j], 1e-6f);
                }
            }
            for (int i = 0; i < before.sampleToCentroid.length; i++) {
                assertEquals(before.sampleToCentroid[i], after.sampleToCentroid[i], 1e-6f);
            }
        }
    }
}

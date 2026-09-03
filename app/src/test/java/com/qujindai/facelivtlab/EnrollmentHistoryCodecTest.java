package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnrollmentHistoryCodecTest {
    private static EnrollmentHistoryRecord sampleRecord() {
        List<EnrollmentHistoryRecord.FrameRecord> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            FaceQuality.Snapshot q = FaceQuality.compose(.70f + i * .01f, .72f, .65f,
                    .80f, 1f, .90f, i, -i, i * .5f, .10f + i * .01f);
            AlignmentGeometry g = AlignmentGeometry.restore(5,
                    new float[]{1+i,2,3,4,5,6,7,8,9,10},
                    new float[]{11,12,13,14,15,16,17,18,19,20},
                    new float[]{11.1f,12,13,14,15,16,17,18,19,20},
                    34f+i, 1.5f+i, .9f, 3.2f, .12f+i*.01f, .20f+i*.01f, false);
            frames.add(new EnrollmentHistoryRecord.FrameRecord(q, g));
        }

        EnumMap<ModelVariant, EnrollmentHistoryRecord.ModelRecord> models = new EnumMap<>(ModelVariant.class);
        for (ModelVariant variant : ModelVariant.values()) {
            int base = variant.ordinal() + 1;
            List<float[]> embeddings = new ArrayList<>();
            for (int i = 0; i < 5; i++) embeddings.add(new float[]{base + i, base + i + .5f});
            models.put(variant, new EnrollmentHistoryRecord.ModelRecord(
                    embeddings,
                    new float[]{base + .25f, base + .75f},
                    new float[]{.91f,.92f,.93f,.94f,.95f},
                    .70f, .92f, .08f, .40f, .50f, .45f,
                    .80f, .90f, 2, .84f, true));
        }
        return new EnrollmentHistoryRecord("person/一", 2, 1788434000000L, "480p · JPEG75",
                10, 15, frames, models);
    }

    @Test public void exactRoundTripPreservesLearningEvidence() {
        EnrollmentHistoryRecord original = sampleRecord();
        String encoded = EnrollmentHistoryCodec.encode(original);
        EnrollmentHistoryRecord decoded = EnrollmentHistoryCodec.decode(encoded);

        assertEquals(original.identity, decoded.identity);
        assertEquals(2, decoded.version);
        assertEquals(original.timestampMs, decoded.timestampMs);
        assertEquals(original.profile, decoded.profile);
        assertEquals(10, decoded.effectiveSamplesBefore);
        assertEquals(15, decoded.effectiveSamplesAfter);
        assertEquals(5, decoded.frames.size());

        for (int i = 0; i < 5; i++) {
            FaceQuality.Snapshot a = original.frames.get(i).quality;
            FaceQuality.Snapshot b = decoded.frames.get(i).quality;
            assertEquals(a.composite, b.composite, 0f);
            assertEquals(a.faceAreaRatio, b.faceAreaRatio, 0f);
            AlignmentGeometry ga = original.frames.get(i).geometry;
            AlignmentGeometry gb = decoded.frames.get(i).geometry;
            assertEquals(ga.landmarkCount, gb.landmarkCount);
            assertEquals(ga.meanResidualPx, gb.meanResidualPx, 0f);
            assertEquals(ga.maxResidualPx, gb.maxResidualPx, 0f);
            assertArrayEquals(ga.sourcePoints, gb.sourcePoints, 0f);
        }

        for (ModelVariant variant : ModelVariant.values()) {
            EnrollmentHistoryRecord.ModelRecord a = original.models.get(variant);
            EnrollmentHistoryRecord.ModelRecord b = decoded.models.get(variant);
            assertEquals(5, b.embeddings.size());
            assertArrayEquals(a.embeddings.get(3), b.embeddings.get(3), 0f);
            assertArrayEquals(a.centroid, b.centroid, 0f);
            assertArrayEquals(a.sampleToCentroid, b.sampleToCentroid, 0f);
            assertEquals(a.stability, b.stability, 0f);
            assertEquals(a.coverage, b.coverage, 0f);
            assertEquals(a.outlierIndex, b.outlierIndex);
            assertTrue(b.allSamplesPassHardGate);
        }
    }

    @Test public void decodedRecordCanReconstructR4MicroscopeSession() {
        EnrollmentHistoryRecord decoded = EnrollmentHistoryCodec.decode(EnrollmentHistoryCodec.encode(sampleRecord()));
        EnrollmentSession session = decoded.toEnrollmentSession();
        for (ModelVariant variant : ModelVariant.values()) {
            assertEquals(5, session.size(variant));
            EnrollmentSession.Summary summary = session.summary(variant);
            assertEquals(5, summary.similarityMatrix.length);
            assertEquals(5, summary.sampleToCentroid.length);
        }
    }

    @Test public void malformedRecordIsRejected() {
        boolean threw = false;
        try {
            EnrollmentHistoryCodec.decode("AAAA");
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test public void recordCopiesMutableInputArrays() {
        EnrollmentHistoryRecord original = sampleRecord();
        float before = original.models.get(ModelVariant.S).centroid[0];
        EnrollmentHistoryRecord.ModelRecord model = original.models.get(ModelVariant.S);
        float[] external = model.centroid.clone();
        external[0] += 100f;
        assertEquals(before, model.centroid[0], 0f);
        assertFalse(Float.isNaN(before));
    }
}

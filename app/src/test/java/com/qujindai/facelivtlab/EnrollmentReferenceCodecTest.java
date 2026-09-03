package com.qujindai.facelivtlab;

import org.junit.Test;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EnrollmentReferenceCodecTest {
    @Test public void referenceEmbeddingsAndGenuineScoresRoundTrip() {
        EnrollmentReferenceCodec.Record in = new EnrollmentReferenceCodec.Record(
                Arrays.asList(
                        new float[]{1f, .2f, -.1f},
                        new float[]{.9f, .3f, .05f}),
                new float[]{.91f, .94f});

        String encoded = EnrollmentReferenceCodec.encode(in);
        EnrollmentReferenceCodec.Record out = EnrollmentReferenceCodec.decode(encoded);

        assertEquals(2, out.embeddings.size());
        assertArrayEquals(in.embeddings.get(0), out.embeddings.get(0), 1e-6f);
        assertArrayEquals(in.embeddings.get(1), out.embeddings.get(1), 1e-6f);
        assertArrayEquals(in.genuineScores, out.genuineScores, 1e-6f);
    }
}

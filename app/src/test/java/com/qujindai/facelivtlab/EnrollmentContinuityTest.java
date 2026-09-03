package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnrollmentContinuityTest {
    private static FaceQuality.Snapshot q() {
        return FaceQuality.compose(.72f, .72f, .65f, .82f, 1f, .90f, 0f, 0f, 0f, .12f);
    }

    private static EnumMap<ModelVariant, float[]> vectors(float[] xs, float[] s, float[] m) {
        EnumMap<ModelVariant, float[]> map = new EnumMap<>(ModelVariant.class);
        map.put(ModelVariant.XS, xs);
        map.put(ModelVariant.S, s);
        map.put(ModelVariant.M, m);
        return map;
    }

    @Test public void firstAcceptedFrameEstablishesSubjectWithoutContinuityRequirement() {
        EnrollmentSession session = new EnrollmentSession();
        assertTrue(session.isSameSubjectCandidate(vectors(
                new float[]{1,0,0}, new float[]{1,0,0}, new float[]{1,0,0})));
    }

    @Test public void twoOfThreeModelsCanMaintainSameSubjectContinuity() {
        EnrollmentSession session = new EnrollmentSession();
        FaceQuality.Snapshot quality = q();
        for (ModelVariant variant : ModelVariant.values()) session.add(variant, new float[]{1,0,0}, quality);

        assertTrue(session.isSameSubjectCandidate(vectors(
                new float[]{.90f,.10f,0},
                new float[]{.88f,.12f,0},
                new float[]{0,1,0})));
    }

    @Test public void apparentPersonSwapIsRejectedEvenThoughItWouldLookNovel() {
        EnrollmentSession session = new EnrollmentSession();
        FaceQuality.Snapshot quality = q();
        for (ModelVariant variant : ModelVariant.values()) session.add(variant, new float[]{1,0,0}, quality);

        EnumMap<ModelVariant, float[]> swapped = vectors(
                new float[]{0,1,0}, new float[]{0,1,0}, new float[]{.2f,.98f,0});
        assertFalse(session.isSameSubjectCandidate(swapped));
        assertTrue(session.isNovelCandidate(ModelVariant.S, swapped.get(ModelVariant.S),
                FaceQuality.compose(.72f,.72f,.65f,.82f,1f,.90f,8f,0f,0f,.12f)));
    }
}

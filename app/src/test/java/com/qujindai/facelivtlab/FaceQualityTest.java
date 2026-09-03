package com.qujindai.facelivtlab;

import org.junit.Test;
import static org.junit.Assert.*;

public class FaceQualityTest {
    @Test public void compositeScoreUsesDeclaredWeightsAndStaysBounded() {
        FaceQuality.Snapshot perfect = FaceQuality.compose(1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f);
        assertEquals(1f, perfect.composite, 1e-6f);

        FaceQuality.Snapshot weak = FaceQuality.compose(0f, 0f, 0f, 0f, 0f, 0f, 50f, 40f, 30f);
        assertEquals(0f, weak.composite, 1e-6f);
    }

    @Test public void poseQualityPenalizesLargeHeadRotation() {
        float front = FaceQuality.poseScore(0f, 0f, 0f);
        float turned = FaceQuality.poseScore(45f, 25f, 20f);
        assertTrue(front > turned);
        assertTrue(turned >= 0f && turned <= 1f);
    }
}
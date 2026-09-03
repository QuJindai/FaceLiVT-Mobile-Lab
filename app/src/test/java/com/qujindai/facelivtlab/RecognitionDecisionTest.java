package com.qujindai.facelivtlab;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class RecognitionDecisionTest {
    private static FaceQuality.Snapshot probe() {
        return FaceQuality.compose(.5f,.5f,.5f,.9f,1f,.9f,0f,0f,0f);
    }

    @Test public void oneIdentityHasNoMeaningfulMargin() {
        RecognitionDecision d = RecognitionDecision.from(
                Collections.singletonList(new FaceStore.Match("ghe", .76f)), .45f, probe());

        assertFalse(d.marginAvailable);
        assertTrue(Float.isNaN(d.margin));
        assertEquals("N/A", d.marginLabel());
        assertTrue(d.accepted);
    }

    @Test public void twoIdentitiesExposeTop1Top2Margin() {
        RecognitionDecision d = RecognitionDecision.from(Arrays.asList(
                new FaceStore.Match("ghe", .76f),
                new FaceStore.Match("other", .52f)), .45f, probe());

        assertTrue(d.marginAvailable);
        assertEquals(.24f, d.margin, 1e-6f);
        assertEquals("ghe", d.top1Name);
        assertEquals("other", d.top2Name);
    }
}

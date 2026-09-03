package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentityGuardEngineTest {
    private static IdentityGuardEngine.ModelEvidence evidence(String top1, float score,
                                                               String top2, float top2Score) {
        boolean margin = top2 != null && !top2.isEmpty();
        return new IdentityGuardEngine.ModelEvidence(top1, score,
                margin ? top2 : "", margin ? top2Score : Float.NaN, margin);
    }

    private static EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> all(
            IdentityGuardEngine.ModelEvidence xs,
            IdentityGuardEngine.ModelEvidence s,
            IdentityGuardEngine.ModelEvidence m) {
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> map = new EnumMap<>(ModelVariant.class);
        map.put(ModelVariant.XS, xs);
        map.put(ModelVariant.S, s);
        map.put(ModelVariant.M, m);
        return map;
    }

    private static IdentityGuardEngine.FrameInput frame(int librarySize, int trackingId,
                                                        boolean geometry,
                                                        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> map) {
        return new IdentityGuardEngine.FrameInput(librarySize, trackingId, true, geometry,
                .45f, new EnumMap<>(ModelVariant.class), map);
    }

    @Test public void emptyLibraryClearsImmediatelyOnFirstValidProbe() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = engine.push(frame(0, 7, true,
                all(evidence("", Float.NaN, "", Float.NaN),
                        evidence("", Float.NaN, "", Float.NaN),
                        evidence("", Float.NaN, "", Float.NaN))));
        assertEquals(IdentityGuardEngine.State.CLEAR, s.state);
        assertTrue(s.canCreateNew());
        assertEquals(1, s.cleanFrames);
    }

    @Test public void nonEmptyLibraryNeedsFiveCleanFramesBeforeCreate() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = null;
        for (int i = 0; i < 4; i++) {
            s = engine.push(frame(2, 9, true,
                    all(evidence("a", .40f, "b", .20f),
                            evidence("a", .41f, "b", .20f),
                            evidence("a", .42f, "b", .20f))));
            assertEquals(IdentityGuardEngine.State.SUSPECTED, s.state);
            assertFalse(s.canCreateNew());
        }
        s = engine.push(frame(2, 9, true,
                all(evidence("a", .40f, "b", .20f),
                        evidence("a", .41f, "b", .20f),
                        evidence("a", .42f, "b", .20f))));
        assertEquals(IdentityGuardEngine.State.CLEAR, s.state);
        assertTrue(s.canCreateNew());
        assertEquals(5, s.cleanFrames);
    }

    @Test public void anySuspectModelBlocksNewEnrollment() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = engine.push(frame(2, 3, true,
                all(evidence("old", .56f, "other", .30f),
                        evidence("old", .40f, "other", .20f),
                        evidence("old", .39f, "other", .20f))));
        assertEquals(IdentityGuardEngine.State.SUSPECTED, s.state);
        assertEquals("old", s.candidateIdentity);
        assertFalse(s.canCreateNew());
    }

    @Test public void threeConsecutiveTwoOfThreeStrongFramesBecomeExisting() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = null;
        for (int i = 0; i < 3; i++) {
            s = engine.push(frame(2, 11, true,
                    all(evidence("old", .80f, "other", .60f),
                            evidence("old", .79f, "other", .58f),
                            evidence("other", .59f, "old", .58f))));
        }
        assertEquals(IdentityGuardEngine.State.EXISTING, s.state);
        assertEquals("old", s.candidateIdentity);
        assertEquals(3, s.confirmingFrames);
        assertFalse(s.canCreateNew());
    }

    @Test public void oneIdentityRequiresAllThreeModelsAcrossThreeFrames() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = null;
        for (int i = 0; i < 3; i++) {
            s = engine.push(frame(1, 5, true,
                    all(evidence("old", .80f, "", Float.NaN),
                            evidence("old", .79f, "", Float.NaN),
                            evidence("old", .58f, "", Float.NaN))));
        }
        assertEquals(IdentityGuardEngine.State.SUSPECTED, s.state);

        engine.reset();
        for (int i = 0; i < 3; i++) {
            s = engine.push(frame(1, 5, true,
                    all(evidence("old", .80f, "", Float.NaN),
                            evidence("old", .79f, "", Float.NaN),
                            evidence("old", .78f, "", Float.NaN))));
        }
        assertEquals(IdentityGuardEngine.State.EXISTING, s.state);
        for (IdentityGuardEngine.ModelEvidence e : s.modelEvidence.values()) {
            assertFalse(e.marginAvailable);
        }
    }

    @Test public void fallbackGeometryCanWarnButCannotAutoPromoteExisting() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = null;
        for (int i = 0; i < 4; i++) {
            s = engine.push(frame(2, 15, false,
                    all(evidence("old", .83f, "other", .60f),
                            evidence("old", .82f, "other", .60f),
                            evidence("old", .81f, "other", .60f))));
        }
        assertEquals(IdentityGuardEngine.State.SUSPECTED, s.state);
        assertEquals("old", s.candidateIdentity);
    }

    @Test public void conflictingStrongCandidatesDoNotBecomeExisting() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        IdentityGuardEngine.Snapshot s = null;
        s = engine.push(frame(2, 21, true,
                all(evidence("a", .82f, "b", .60f), evidence("a", .81f, "b", .60f), evidence("b", .82f, "a", .60f))));
        s = engine.push(frame(2, 21, true,
                all(evidence("b", .82f, "a", .60f), evidence("b", .81f, "a", .60f), evidence("a", .82f, "b", .60f))));
        s = engine.push(frame(2, 21, true,
                all(evidence("a", .82f, "b", .60f), evidence("a", .81f, "b", .60f), evidence("b", .82f, "a", .60f))));
        assertEquals(IdentityGuardEngine.State.SUSPECTED, s.state);
    }

    @Test public void resetAndTrackingChangeInvalidateCapturedGeneration() {
        IdentityGuardEngine engine = new IdentityGuardEngine();
        long first = engine.captureGeneration();
        engine.push(frame(0, 1, true,
                all(evidence("", Float.NaN, "", Float.NaN), evidence("", Float.NaN, "", Float.NaN), evidence("", Float.NaN, "", Float.NaN))));
        assertFalse(engine.isCurrent(first));
        long second = engine.captureGeneration();
        engine.reset();
        assertFalse(engine.isCurrent(second));
        assertTrue(engine.isCurrent(engine.captureGeneration()));
    }
}

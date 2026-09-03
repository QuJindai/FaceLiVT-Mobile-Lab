package com.qujindai.facelivtlab;

import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnrollmentIdentityLockTest {
    private static IdentityGuardEngine.ModelEvidence e(String name, float score) {
        return new IdentityGuardEngine.ModelEvidence(name, score, "", Float.NaN, false);
    }

    private static EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> evidence(
            IdentityGuardEngine.ModelEvidence xs,
            IdentityGuardEngine.ModelEvidence s,
            IdentityGuardEngine.ModelEvidence m) {
        EnumMap<ModelVariant, IdentityGuardEngine.ModelEvidence> map = new EnumMap<>(ModelVariant.class);
        map.put(ModelVariant.XS, xs);
        map.put(ModelVariant.S, s);
        map.put(ModelVariant.M, m);
        return map;
    }

    @Test public void newEnrollmentBlocksImmediatelyWhenAnyModelSeesExistingIdentity() {
        EnrollmentIdentityLock.Result r = EnrollmentIdentityLock.forNewIdentity(
                .45f, new EnumMap<>(ModelVariant.class),
                evidence(e("old", .56f), e("old", .40f), e("other", .39f)));
        assertFalse(r.allowed);
        assertEquals("old", r.candidateIdentity);
    }

    @Test public void newEnrollmentAllowsFrameWhenAllModelsStayBelowSuspectThreshold() {
        EnrollmentIdentityLock.Result r = EnrollmentIdentityLock.forNewIdentity(
                .45f, new EnumMap<>(ModelVariant.class),
                evidence(e("a", .49f), e("b", .50f), e("c", .54f)));
        assertTrue(r.allowed);
    }

    @Test public void appendWithMultipleIdentitiesNeedsTwoModelsToConfirmTarget() {
        EnrollmentIdentityLock.Result pass = EnrollmentIdentityLock.forAppend(
                "target", 3, .45f, new EnumMap<>(ModelVariant.class),
                evidence(e("target", .70f), e("target", .68f), e("other", .72f)));
        assertTrue(pass.allowed);
        assertEquals(2, pass.targetVotes);

        EnrollmentIdentityLock.Result fail = EnrollmentIdentityLock.forAppend(
                "target", 3, .45f, new EnumMap<>(ModelVariant.class),
                evidence(e("target", .70f), e("other", .68f), e("other", .72f)));
        assertFalse(fail.allowed);
        assertEquals(1, fail.targetVotes);
    }

    @Test public void oneIdentityAppendNeedsAllThreeModelsToStayLocked() {
        EnrollmentIdentityLock.Result r = EnrollmentIdentityLock.forAppend(
                "target", 1, .45f, new EnumMap<>(ModelVariant.class),
                evidence(e("target", .72f), e("target", .71f), e("target", .54f)));
        assertFalse(r.allowed);
        assertEquals(2, r.targetVotes);
    }
}

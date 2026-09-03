package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentityGuardPolicyTest {
    @Test public void thresholdsUseSafetyOffsetsAndFloors() {
        IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.45f, null);
        assertEquals(.55f, t.suspect, 1e-6f);
        assertEquals(.62f, t.existing, 1e-6f);
    }

    @Test public void empiricalThresholdCanOnlyMakeGuardStricter() {
        IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.45f, .70f);
        assertEquals(.75f, t.suspect, 1e-6f);
        assertEquals(.80f, t.existing, 1e-6f);
    }

    @Test public void thresholdsClampBelowOne() {
        IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.98f, .99f);
        assertEquals(.99f, t.suspect, 1e-6f);
        assertEquals(.99f, t.existing, 1e-6f);
    }

    @Test public void multiIdentityStrongVoteNeedsRealMargin() {
        IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.45f, null);
        assertFalse(IdentityGuardPolicy.isStrongVote(2, .80f, false, Float.NaN, t));
        assertFalse(IdentityGuardPolicy.isStrongVote(2, .80f, true, .07f, t));
        assertTrue(IdentityGuardPolicy.isStrongVote(2, .80f, true, .08f, t));
    }

    @Test public void oneIdentityKeepsMarginUnavailableButScoreCanBeStrong() {
        IdentityGuardPolicy.Thresholds t = IdentityGuardPolicy.thresholds(.45f, null);
        assertTrue(IdentityGuardPolicy.isStrongVote(1, .80f, false, Float.NaN, t));
    }
}

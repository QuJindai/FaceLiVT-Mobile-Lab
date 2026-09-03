package com.qujindai.facelivtlab;

import org.junit.Test;

import static org.junit.Assert.*;

public class MicroscopeSelectionStateTest {
    @Test public void singleModelSelectionMovesMicroscopeFocus() {
        MicroscopeSelectionState state = new MicroscopeSelectionState();

        assertEquals(ModelVariant.S, state.selectMode(ModelMode.S).focus);
        assertEquals(ModelVariant.XS, state.selectMode(ModelMode.XS).focus);
        assertEquals(ModelVariant.M, state.selectMode(ModelMode.M).focus);
    }

    @Test public void compareKeepsTheLastExplicitModelAsMicroscopeFocus() {
        MicroscopeSelectionState state = new MicroscopeSelectionState();
        state.selectMode(ModelMode.XS);

        MicroscopeSelectionState.Snapshot compare = state.selectMode(ModelMode.COMPARE);

        assertEquals(ModelMode.COMPARE, compare.mode);
        assertEquals(ModelVariant.XS, compare.focus);
        assertTrue(state.isCurrent(compare));
    }

    @Test public void switchingModelInvalidatesAnInFlightOldFrame() {
        MicroscopeSelectionState state = new MicroscopeSelectionState();
        MicroscopeSelectionState.Snapshot oldFrame = state.selectMode(ModelMode.S);

        MicroscopeSelectionState.Snapshot current = state.selectMode(ModelMode.M);

        assertFalse(state.isCurrent(oldFrame));
        assertTrue(state.isCurrent(current));
        assertTrue(current.epoch > oldFrame.epoch);
    }

    @Test public void compareFocusCanFollowAnExplicitInspectionModel() {
        MicroscopeSelectionState state = new MicroscopeSelectionState();
        state.selectMode(ModelMode.COMPARE);

        MicroscopeSelectionState.Snapshot focused = state.selectFocus(ModelVariant.M);

        assertEquals(ModelVariant.M, focused.focus);
        assertTrue(state.isCurrent(focused));
    }
}

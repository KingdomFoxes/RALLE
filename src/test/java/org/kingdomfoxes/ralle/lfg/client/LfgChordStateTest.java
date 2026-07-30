package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgChordStateTest {
    @Test
    void executesOnlyOnMatchingDigitReleaseWhileModifierRemainsHeld() {
        var state = new LfgChordState<String>();
        state.begin();

        assertEquals("TCC", state.press(4, () -> "TCC").selection());
        var release = state.release(4, true);

        assertTrue(release.matched());
        assertEquals("TCC", release.execution());
        assertTrue(state.completed());
    }

    @Test
    void modifierFirstReleaseCancelsAndHeldRepeatDoesNotCreateAnotherSelection() {
        var state = new LfgChordState<String>();
        int[] selections = {0};
        state.begin();

        state.press(5, () -> {
            selections[0]++;
            return "TNA";
        });
        state.press(5, () -> {
            selections[0]++;
            return "replacement";
        });
        var release = state.release(5, false);

        assertEquals(1, selections[0]);
        assertTrue(release.matched());
        assertNull(release.execution());
    }

    @Test
    void simultaneousDifferentDigitsMakeTheWholeChordAmbiguous() {
        var state = new LfgChordState<String>();
        state.begin();

        state.press(2, () -> "NOTG");
        assertTrue(state.press(3, () -> "NOL").ambiguous());
        assertNull(state.release(2, true).execution());
    }

    @Test
    void wrongReleaseIsIgnoredAndCompletedChordNeedsANewModifierPress() {
        var state = new LfgChordState<String>();
        state.begin();
        state.press(2, () -> "Member");

        assertFalse(state.release(3, true).matched());
        assertEquals("Member", state.release(2, true).execution());
        assertFalse(state.press(4, () -> "Other").accepted());

        state.begin();
        assertEquals("Other", state.press(4, () -> "Other").selection());
    }
}

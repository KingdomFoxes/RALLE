package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgChordStateTest {
    @Test void mouseSubmissionDiscardsAnArmedKeyboardChordEvenAfterKickSelectionResumes() {
        var state = new LfgChordState<String>();
        state.begin();
        state.press(2, () -> "old target");
        state.finish(); // The shared graphical submission guard accepted a mouse action.
        assertNull(state.release(2, true).execution());
        assertFalse(state.press(3, () -> "another target").accepted());
        state.begin(); // A fresh modifier hold.
        assertTrue(state.press(3, () -> "another target").accepted());
        assertEquals("another target", state.release(3, true).execution());
    }
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
    void switchingOptionsWhileTheFirstKeyIsHeldSubmitsTheLatestOnRelease() {
        var state = new LfgChordState<String>();
        state.begin();

        state.press(2, () -> "NOTG");
        assertEquals("NOL", state.press(3, () -> "NOL").selection());
        assertFalse(state.completed());
        assertNull(state.release(2, true).execution());
        assertFalse(state.completed());
        assertEquals("NOL", state.release(3, true).execution());
        assertTrue(state.completed());
    }

    @Test void releasingTheLatestBeforeTheOlderKeyExecutesOnlyOnce() {
        var state = new LfgChordState<String>();
        state.begin();
        state.press(2, () -> "first UUID");
        state.press(3, () -> "second UUID");
        assertEquals("second UUID", state.release(3, true).execution());
        assertNull(state.release(2, true).execution());
        assertNull(state.release(3, true).execution());
    }

    @Test void switchingSeveralTimesAndBackNeverRequiresReopeningTheMenu() {
        var state = new LfgChordState<String>();
        state.begin();
        state.press(1, () -> "Dailies");
        state.press(2, () -> "NOTG");
        assertFalse(state.release(1, true).matched());
        state.press(1, () -> "Dailies again");
        assertFalse(state.release(2, true).matched());
        assertEquals("Dailies again", state.release(1, true).execution());
    }

    @Test void resumingKickSelectionDiscardsStaleReleasesAndWaitsForANewPress() {
        var state = new LfgChordState<String>();
        state.begin();
        state.press(2, () -> "before mouse click");
        state.finish();
        state.begin(); // A kick response/animation resumes selection in the existing menu.
        assertFalse(state.release(2, true).matched());
        state.press(3, () -> "fresh target");
        assertEquals("fresh target", state.release(3, true).execution());
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

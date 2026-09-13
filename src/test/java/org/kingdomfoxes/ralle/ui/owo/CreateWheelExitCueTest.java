package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreateWheelExitCueTest {
    @Test void successfulCreationPlaysOnceOnTheRealExit() {
        var cue = new CreateWheelExitCue();
        cue.creationAccepted();
        assertTrue(cue.wheelRemoved(false));
        assertFalse(cue.wheelRemoved(false));
    }

    @Test void popupReplacementDoesNotConsumeTheCueAndUnacceptedCreationIsSilent() {
        var cue = new CreateWheelExitCue();
        assertFalse(cue.wheelRemoved(false));
        cue.creationAccepted();
        assertFalse(cue.wheelRemoved(true));
        assertTrue(cue.wheelRemoved(false));
    }
}

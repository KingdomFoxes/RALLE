package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.kingdomfoxes.ralle.lfg.client.LfgModifierInput.Route.*;

class LfgModifierInputTest {
    @Test void tickRecoveryNeverFallsBackToChordsForEnabledWheels() {
        var input = new LfgModifierInput();
        assertEquals(CREATE_WHEEL, input.route(true, false, true, true));
        assertEquals(KICK_WHEEL, input.route(false, true, true, true));
        assertEquals(CREATE_CHORD, input.route(true, false, false, true));
        assertEquals(KICK_CHORD, input.route(false, true, true, false));
        assertEquals(NONE, input.route(true, true, true, true));
    }
    @Test void screenCloseAfterRawReleaseDoesNotBlockNextPress() {
        var input = new LfgModifierInput();
        for (int cycle = 0; cycle < 10; cycle++) {
            assertEquals(CREATE_WHEEL, input.route(true, false, true, true));
            input.observe(false, false); // raw release hook
            input.closed(false, false); // Screen.keyReleased closes afterward
            assertFalse(input.awaitingRelease());
            assertEquals(KICK_WHEEL, input.route(false, true, true, true));
            input.observe(false, false);
            input.closed(false, false);
        }
    }
    @Test void escapeOrScreenReplacementRequiresPhysicalReleaseAndRecoversMissedEvent() {
        var input = new LfgModifierInput();
        input.closed(true, false);
        assertEquals(NONE, input.route(true, false, true, true));
        input.observe(false, true);
        assertTrue(input.awaitingRelease());
        input.observe(false, false); // polling recovers even without a raw release event
        assertEquals(KICK_WHEEL, input.route(false, true, true, true));
    }
}

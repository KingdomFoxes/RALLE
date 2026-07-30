package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LfgDisbandConfirmationTest {
    @Test
    void sameLobbyAndRevisionConfirmsWithinFiveSeconds() {
        long[] now = {1_000};
        var lobbyId = UUID.randomUUID();
        var confirmation = new LfgDisbandConfirmation(() -> now[0]);

        assertEquals(LfgDisbandConfirmation.Result.ARMED,
                confirmation.request(lobbyId, 7, "Press [G] again to disband"));
        now[0] += 4_999;
        assertEquals(LfgDisbandConfirmation.Result.CONFIRMED,
                confirmation.request(lobbyId, 7, "Press [G] again to disband"));
        assertNull(confirmation.snapshot());
    }

    @Test
    void timeoutRevisionChangeAndAuthorityLossCancelTheConfirmation() {
        long[] now = {0};
        var lobbyId = UUID.randomUUID();
        var confirmation = new LfgDisbandConfirmation(() -> now[0]);

        confirmation.request(lobbyId, 1, "Click again to disband");
        now[0] = 5_000;
        assertNull(confirmation.snapshot());

        confirmation.request(lobbyId, 2, "Click again to disband");
        confirmation.validate(lobbyId, 3, true);
        assertNull(confirmation.snapshot());

        confirmation.request(lobbyId, 3, "Click again to disband");
        confirmation.validate(lobbyId, 3, false);
        assertNull(confirmation.snapshot());
    }
}

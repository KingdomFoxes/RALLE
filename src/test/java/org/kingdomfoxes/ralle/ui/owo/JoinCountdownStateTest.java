package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinCountdownStateTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void countsDownForExactlyThreeSeconds() {
        var now = new long[]{5_000_000_000L};
        var countdown = new JoinCountdownState(() -> now[0]);

        assertTrue(countdown.start(FIRST));
        assertEquals(3, countdown.secondsRemaining());
        assertEquals(1d, countdown.remainingFraction());
        assertFalse(countdown.elapsed());

        now[0] += 1_000_000_000L;
        assertEquals(2, countdown.secondsRemaining());
        assertEquals(2d / 3d, countdown.remainingFraction(), 0.0001);

        now[0] += 1_999_999_999L;
        assertEquals(1, countdown.secondsRemaining());
        assertFalse(countdown.elapsed());

        now[0]++;
        assertEquals(0, countdown.secondsRemaining());
        assertEquals(0d, countdown.remainingFraction());
        assertTrue(countdown.elapsed());
    }

    @Test
    void blocksAnotherLobbyUntilCancelled() {
        var countdown = new JoinCountdownState(() -> 0L);

        assertTrue(countdown.start(FIRST));
        assertFalse(countdown.start(SECOND));
        assertTrue(countdown.activeFor(FIRST));

        countdown.cancel();

        assertFalse(countdown.active());
        assertTrue(countdown.start(SECOND));
        assertTrue(countdown.activeFor(SECOND));
    }
}

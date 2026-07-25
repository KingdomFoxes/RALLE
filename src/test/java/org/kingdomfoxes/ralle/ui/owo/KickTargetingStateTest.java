package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KickTargetingStateTest {
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void requiresFullOneAndAHalfSecondHold() {
        var now = new long[]{0L};
        var state = new KickTargetingState(() -> now[0]);

        assertTrue(state.toggle());
        assertTrue(state.hover(MEMBER));
        assertTrue(state.press(MEMBER));
        now[0] = KickTargetingState.HOLD_NANOS - 1;
        assertNull(state.completedTarget());
        assertEquals(1d, state.progress(), 0.000_001);
        now[0] = KickTargetingState.HOLD_NANOS;
        assertEquals(MEMBER, state.completedTarget());
        assertTrue(state.submitted());
        assertNull(state.completedTarget());
    }

    @Test
    void earlyReleaseCancelsHoldButKeepsTargetingActive() {
        var now = new long[]{0L};
        var state = new KickTargetingState(() -> now[0]);
        state.toggle();
        state.hover(MEMBER);
        state.press(MEMBER);
        now[0] = KickTargetingState.HOLD_NANOS / 2;

        assertTrue(state.release(MEMBER));
        assertTrue(state.active());
        assertFalse(state.holding());
        assertEquals(MEMBER, state.hovered());
        assertEquals(0.5d, state.progress(), 0.000_001);
        now[0] += KickTargetingState.CANCEL_FADE_NANOS / 2;
        assertEquals(0.25d, state.progress(), 0.000_001);
        now[0] += KickTargetingState.CANCEL_FADE_NANOS / 2;
        assertEquals(0d, state.progress(), 0.000_001);
        assertNull(state.completedTarget());
    }

    @Test
    void leavingTargetCancelsAnActiveHold() {
        var state = new KickTargetingState(() -> 0L);
        state.toggle();
        state.hover(MEMBER);
        state.press(MEMBER);

        assertTrue(state.leave(MEMBER));
        assertNull(state.hovered());
        assertFalse(state.holding());
        assertTrue(state.active());
    }

    @Test
    void togglingOffClearsAllTransientState() {
        var state = new KickTargetingState(() -> 0L);
        state.toggle();
        state.hover(MEMBER);
        state.press(MEMBER);

        assertFalse(state.toggle());
        assertFalse(state.active());
        assertNull(state.visualTarget());
        assertFalse(state.holding());
    }
}

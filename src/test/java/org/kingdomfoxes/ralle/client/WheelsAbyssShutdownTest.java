package org.kingdomfoxes.ralle.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelsAbyssShutdownTest {
    @Test
    void timelineArmsCapturesHoldsFiveSecondsAndStopsExactlyOnce() {
        var clock = new AtomicLong(10_000L);
        var timeline = new WheelsAbyssShutdown.Timeline(clock::get, WheelsAbyssShutdown.HOLD_MILLIS);

        assertFalse(timeline.active());
        assertFalse(timeline.awaitingCapture());
        assertFalse(timeline.shouldStopNow());

        assertTrue(timeline.trigger());
        assertFalse(timeline.trigger());
        assertTrue(timeline.active());
        assertTrue(timeline.awaitingCapture());
        assertEquals(WheelsAbyssShutdown.Timeline.State.ARMED, timeline.state());

        clock.addAndGet(50L);
        timeline.captured();
        assertFalse(timeline.awaitingCapture());
        assertEquals(WheelsAbyssShutdown.Timeline.State.HOLDING, timeline.state());

        clock.addAndGet(WheelsAbyssShutdown.HOLD_MILLIS - 1L);
        assertFalse(timeline.shouldStopNow());
        assertEquals(WheelsAbyssShutdown.Timeline.State.HOLDING, timeline.state());

        clock.addAndGet(1L);
        assertTrue(timeline.shouldStopNow());
        assertEquals(WheelsAbyssShutdown.Timeline.State.CLOSING, timeline.state());
        assertTrue(timeline.active());

        // Subsequent checks must not trigger stop again while Minecraft winds down.
        clock.addAndGet(1_000L);
        assertFalse(timeline.shouldStopNow());
    }
}

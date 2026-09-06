package org.kingdomfoxes.ralle.war.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueAttributionDemoTest {
    @Test
    void togglesThreeFixedCountdownsWithoutRetainingExpiredRows() {
        var clock = new MutableClock(1_000L);
        var demo = new QueueAttributionDemo(clock::now);

        assertEquals(QueueAttributionDemo.ToggleResult.ENABLED, demo.toggle(true));
        var timers = demo.timers();
        assertEquals(3, timers.size());
        assertEquals(71_000L, timers.get(0).timerEndMillis());
        assertEquals(153_000L, timers.get(1).timerEndMillis());
        assertEquals(194_000L, timers.get(2).timerEndMillis());
        assertEquals(QueueAttributionDemo.Sender.REMOTE,
                demo.row(timers.get(0).key()).orElseThrow().sender());
        assertEquals(QueueAttributionDemo.Sender.SELF,
                demo.row(timers.get(1).key()).orElseThrow().sender());
        assertEquals(QueueAttributionDemo.Sender.UNKNOWN,
                demo.row(timers.get(2).key()).orElseThrow().sender());

        clock.now = 200_000L;
        assertTrue(demo.timers().isEmpty());
        assertTrue(demo.row(timers.get(0).key()).isEmpty());
    }

    @Test
    void secondUseHidesRowsAndUnavailableUseNeverStartsThem() {
        var demo = new QueueAttributionDemo(() -> 1_000L);
        assertEquals(QueueAttributionDemo.ToggleResult.UNAVAILABLE, demo.toggle(false));
        assertTrue(demo.timers().isEmpty());

        assertEquals(QueueAttributionDemo.ToggleResult.ENABLED, demo.toggle(true));
        assertEquals(QueueAttributionDemo.ToggleResult.DISABLED, demo.toggle(true));
        assertTrue(demo.timers().isEmpty());
    }

    private static final class MutableClock {
        private long now;
        MutableClock(long now) { this.now = now; }
        long now() { return now; }
    }
}

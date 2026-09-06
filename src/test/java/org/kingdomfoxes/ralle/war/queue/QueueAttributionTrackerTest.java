package org.kingdomfoxes.ralle.war.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueAttributionTrackerTest {
    private final MutableClock clock = new MutableClock(1_000L);
    private final QueueAttributionTracker tracker = new QueueAttributionTracker(clock::now);

    @Test
    void bindsAnnouncementsBeforeOrAfterTheTimerAppears() {
        tracker.observe(announcement("Detlas", "First"));
        tracker.reconcile(List.of(timer("Detlas", 30_000L), timer("Ragni", 40_000L)));
        tracker.observe(announcement("Ragni", "Second"));

        assertEquals("First", tracker.attributionFor("Detlas").orElseThrow());
        assertEquals("Second", tracker.attributionFor("Ragni").orElseThrow());
    }

    @Test
    void firstObservedSenderWinsAndDuplicatesDoNotExtendPendingExpiry() {
        tracker.observe(announcement("Detlas", "First"));
        clock.advance(9_000L);
        tracker.observe(announcement("Detlas", "First"));
        tracker.observe(announcement("Detlas", "Conflicting"));
        clock.advance(1_001L);
        tracker.reconcile(List.of(timer("Detlas", 30_000L)));

        assertTrue(tracker.attributionFor("Detlas").isEmpty());
    }

    @Test
    void conflictingActiveAnnouncementCannotOverwriteFirstObservedSender() {
        tracker.reconcile(List.of(timer("Detlas", 30_000L)));
        tracker.observe(announcement("Detlas", "First"));
        tracker.observe(announcement("Detlas", "Conflicting"));

        assertEquals("First", tracker.attributionFor("Detlas").orElseThrow());
    }

    @Test
    void continuousTimerReplacementAndEndTimeDriftPreserveAttribution() {
        tracker.reconcile(List.of(timer("Detlas", 30_000L)));
        tracker.observe(announcement("Detlas", "First"));
        tracker.reconcile(List.of(timer("Detlas", 32_000L)));

        assertEquals("First", tracker.attributionFor("Detlas").orElseThrow());
    }

    @Test
    void absenceExpiryAndCaptureEndTheGeneration() {
        tracker.reconcile(List.of(timer("Detlas", 5_000L)));
        tracker.observe(announcement("Detlas", "First"));
        tracker.reconcile(List.of());
        assertTrue(tracker.attributionFor("Detlas").isEmpty());

        tracker.reconcile(List.of(timer("Detlas", 5_000L)));
        tracker.observe(announcement("Detlas", "Second"));
        tracker.captured("Detlas");
        tracker.reconcile(List.of(timer("Detlas", 5_000L)));
        assertTrue(tracker.attributionFor("Detlas").isEmpty());

        tracker.reconcile(List.of());
        tracker.observe(announcement("Detlas", "Third"));
        tracker.reconcile(List.of(timer("Detlas", 20_000L)));
        assertEquals("Third", tracker.attributionFor("Detlas").orElseThrow());

        clock.advance(20_000L);
        tracker.reconcile(List.of(timer("Detlas", 20_000L)));
        assertTrue(tracker.attributionFor("Detlas").isEmpty());
    }

    @Test
    void clearDropsPendingAndActiveSessionData() {
        tracker.observe(announcement("Detlas", "First"));
        tracker.reconcile(List.of(timer("Ragni", 30_000L)));
        tracker.observe(announcement("Ragni", "Second"));
        tracker.clear();

        assertEquals(0, tracker.pendingSize());
        assertEquals(0, tracker.activeSize());
        assertTrue(tracker.attributionFor("Ragni").isEmpty());
    }

    @Test
    void boundsPendingAndActiveMetadataWithoutTouchingSnapshots() {
        var timers = new ArrayList<AttackTimerSnapshot>();
        for (int index = 0; index < 600; index++) {
            tracker.observe(announcement("Territory " + index, "Player" + index));
            timers.add(timer("Territory " + index, 30_000L));
        }
        assertEquals(QueueAttributionTracker.MAX_PENDING, tracker.pendingSize());

        tracker.reconcile(timers);
        assertEquals(QueueAttributionTracker.MAX_ACTIVE, tracker.activeSize());
    }

    private static QueueAnnouncement announcement(String territory, String player) {
        return new QueueAnnouncement(territory, player);
    }

    private static AttackTimerSnapshot timer(String territory, long end) {
        return new AttackTimerSnapshot(territory, end);
    }

    private static final class MutableClock {
        private long now;
        MutableClock(long now) { this.now = now; }
        long now() { return now; }
        void advance(long millis) { now += millis; }
    }
}

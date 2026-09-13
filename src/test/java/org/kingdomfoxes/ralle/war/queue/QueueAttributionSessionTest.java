package org.kingdomfoxes.ralle.war.queue;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueueAttributionSessionTest {
    private final QueueAttributionTracker tracker = new QueueAttributionTracker(() -> 1_000L);
    private final QueueAttributionSession session = new QueueAttributionSession(tracker);

    private void rememberSender() {
        session.update("account", "Fox");
        tracker.observe(new QueueAnnouncement("Detlas", "First"));
        tracker.reconcile(List.of(new AttackTimerSnapshot("Detlas", 30_000L)));
    }

    @Test
    void missingIdentityWhileSwitchingWorldsOrCharactersPreservesCache() {
        rememberSender();
        assertFalse(session.update("account", ""));
        assertFalse(session.update(null, null));
        assertTrue(session.update("account", "Fox"));
        tracker.reconcile(List.of());
        tracker.reconcile(List.of(new AttackTimerSnapshot("Detlas", 30_000L)));
        assertEquals("First", tracker.attributionFor("Detlas").orElseThrow());
    }

    @Test
    void actualGuildOrAccountChangeAndExplicitClearDiscardCache() {
        rememberSender();
        session.update("account", "Other");
        assertEquals(0, tracker.cachedSize());
        rememberSender();
        session.update("other-account", "");
        assertEquals(0, tracker.cachedSize());
        rememberSender();
        session.clear();
        assertEquals(0, tracker.cachedSize());
    }
}

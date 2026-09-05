package org.kingdomfoxes.ralle.requeue;

import java.util.Optional;

/** Pairs server raid announcements with Ready Up prompts, regardless of who queued. */
final class RaidReadyTracker {
    private static final int PAIR_TICKS = 40;
    private WynnRaid pendingRaid;
    private int announcedTick;

    Optional<WynnRaid> observe(String text, int tick) {
        if (pendingRaid != null && (tick < announcedTick || tick - announcedTick > PAIR_TICKS)) clear();
        var announcement = RaidStartMessage.parseAnnouncement(text);
        if (announcement.isPresent()) {
            pendingRaid = announcement.orElseThrow().raid();
            announcedTick = tick;
        }
        if (pendingRaid != null && RaidStartMessage.isReadyPrompt(text)) {
            var raid = pendingRaid;
            clear();
            return Optional.of(raid);
        }
        return Optional.empty();
    }

    void clear() {
        pendingRaid = null;
        announcedTick = 0;
    }
}

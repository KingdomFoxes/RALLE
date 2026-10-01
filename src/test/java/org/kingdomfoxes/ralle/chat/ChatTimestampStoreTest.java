package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatTimestampStoreTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-08-26T10:34:56Z");

    @Test
    void recordsTheFirstLocalReceiveTimeOnly() {
        var store = new ChatTimestampStore(Clock.fixed(NOW, ZONE));
        var message = message("Hello");

        store.record(message);
        store.record(message);

        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 34, 56), store.receiveTime(message));
        assertEquals(1, store.size());
    }

    @Test
    void equalLogicalValuesRemainDistinctMessageIdentities() {
        var store = new ChatTimestampStore(Clock.fixed(NOW, ZONE));
        var first = message("Same");
        var second = message("Same");

        store.record(first);
        store.record(second);

        assertEquals(2, store.size());
    }

    @Test
    void transfersReceiveTimeToADeletionMarker() {
        var store = new ChatTimestampStore(Clock.fixed(NOW, ZONE));
        var original = message("Original");
        var replacement = message("Deleted");
        store.record(original);

        store.transfer(original, replacement);

        assertEquals(store.receiveTime(original), store.receiveTime(replacement));
        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 34, 56), store.receiveTime(replacement));
    }

    @Test
    void clearingOneHistoryPreservesOtherTabs() {
        var store = new ChatTimestampStore(Clock.fixed(NOW, ZONE));
        var retained = message("Retained");
        var removed = message("Removed");
        store.record(retained);
        store.record(removed);

        store.forgetAll(List.of(removed));
        assertEquals(1, store.size());
        assertNull(store.receiveTime(removed));

        store.forgetAll(List.of(retained));
        assertEquals(0, store.size());
    }

    @Test
    void tabSwitchesAndRefreshesKeepInactiveHistoryTimesWithoutReadingClock() {
        var clock = new AdvancingClock();
        var store = new ChatTimestampStore(clock);
        var guild = message("Same");
        var party = message("Same");
        store.record(guild);
        var guildTime = store.receiveTime(guild);
        store.record(party);
        var partyTime = store.receiveTime(party);
        org.junit.jupiter.api.Assertions.assertNotEquals(guildTime, partyTime);
        // Wynntils swaps the actual history lists; it does not receive these messages again.
        for (var history : List.of(List.of(guild), List.of(party), List.of(guild))) {
            assertEquals(history.getFirst() == guild ? guildTime : partyTime,
                    store.receiveTime(history.getFirst()));
        }
        assertEquals(2, clock.reads);
    }

    @Test
    void enablingTabsTransfersOriginalTimeToEveryRecipientAndDoesNotStampUnknownHistory() {
        var clock = new AdvancingClock();
        var store = new ChatTimestampStore(clock);
        var original = message("Hello");
        store.record(original);
        var firstTab = message("Hello");
        var secondTab = message("Hello");
        store.replay(original, () -> {
            store.record(firstTab);
            store.record(secondTab);
            store.record(firstTab);
        });
        assertEquals(store.receiveTime(original), store.receiveTime(firstTab));
        assertEquals(store.receiveTime(original), store.receiveTime(secondTab));
        var unknownReplay = message("Hello");
        store.replay(message("Hello"), () -> store.record(unknownReplay));
        assertNull(store.receiveTime(unknownReplay));
        assertEquals(1, clock.reads);
    }

    @Test
    void nestedAndFailedReplaysRestoreCaptureForNewMessages() {
        var clock = new AdvancingClock();
        var store = new ChatTimestampStore(clock);
        var original = message("Original");
        store.record(original);
        var replay = message("Replay");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                store.replay(original, () -> {
                    store.replay(message("Unknown"), () -> {});
                    store.record(replay);
                    throw new IllegalStateException("failed replay");
                }));
        assertEquals(store.receiveTime(original), store.receiveTime(replay));
        var fresh = message("Original");
        store.record(fresh);
        org.junit.jupiter.api.Assertions.assertNotEquals(store.receiveTime(original), store.receiveTime(fresh));
        assertEquals(2, clock.reads);
    }

    private static final class AdvancingClock extends Clock {
        private int reads;
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return NOW.plusSeconds(reads++ * 60L); }
    }

    private GuiMessage message(String text) {
        return new GuiMessage(0, Component.literal(text), null, null);
    }
}

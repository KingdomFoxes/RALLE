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

        assertNull(store.receiveTime(original));
        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 34, 56), store.receiveTime(replacement));
    }

    @Test
    void prunesAndClearsWithLogicalHistory() {
        var store = new ChatTimestampStore(Clock.fixed(NOW, ZONE));
        var retained = message("Retained");
        var removed = message("Removed");
        store.record(retained);
        store.record(removed);

        store.retainAll(List.of(retained));
        assertEquals(1, store.size());
        assertNull(store.receiveTime(removed));

        store.clear();
        assertEquals(0, store.size());
    }

    private GuiMessage message(String text) {
        return new GuiMessage(0, Component.literal(text), null, null);
    }
}

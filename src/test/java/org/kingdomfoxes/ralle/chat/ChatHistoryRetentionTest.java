package org.kingdomfoxes.ralle.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatHistoryRetentionTest {
    @Test
    void everyConfiguredLimitEvictsOldestEntriesFirst() {
        for (var limit : List.of(300, 500, 1000, 1500)) {
            var newestFirst = messages(1600);

            ChatHistoryRetention.pruneOldest(newestFirst, limit);

            assertEquals(limit, newestFirst.size());
            assertEquals(0, newestFirst.getFirst());
            assertEquals(limit - 1, newestFirst.getLast());
        }
    }

    @Test
    void loweringAndDisablingUseTheNewCeilingImmediately() {
        var newestFirst = messages(1500);
        ChatHistoryRetention.pruneOldest(newestFirst, 300);
        assertEquals(300, newestFirst.size());
        assertEquals(299, newestFirst.getLast());

        ChatHistoryRetention.pruneOldest(newestFirst, ChatHistoryRetention.VANILLA_LIMIT);
        assertEquals(100, newestFirst.size());
        assertEquals(99, newestFirst.getLast());
    }

    @Test
    void effectiveLimitUsesVanillaWhenDisabledAndSafeDefaultForInvalidValues() {
        assertEquals(100, ChatHistoryRetention.effectiveLimit(false, "1500"));
        assertEquals(500, ChatHistoryRetention.effectiveLimit(true, "unsupported"));
        assertEquals(300, ChatHistoryRetention.effectiveLimit(true, "300"));
        assertEquals(500, ChatHistoryRetention.effectiveLimit(true, "500"));
        assertEquals(1000, ChatHistoryRetention.effectiveLimit(true, "1000"));
        assertEquals(1500, ChatHistoryRetention.effectiveLimit(true, "1500"));
    }

    private static ArrayList<Integer> messages(int count) {
        var messages = new ArrayList<Integer>(count);
        for (int index = 0; index < count; index++) messages.add(index);
        return messages;
    }
}

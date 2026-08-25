package org.kingdomfoxes.ralle.chat;

import java.util.List;

/** Session-only retention policy shared by vanilla and projected chat storage. */
public final class ChatHistoryRetention {
    public static final int VANILLA_LIMIT = 100;

    private ChatHistoryRetention() {}

    public static int effectiveLimit(boolean enabled, String configuredLimit) {
        if (!enabled) return VANILLA_LIMIT;
        return switch (configuredLimit) {
            case "300" -> 300;
            case "1000" -> 1000;
            case "1500" -> 1500;
            default -> 500;
        };
    }

    public static <T> void pruneOldest(List<T> newestFirst, int limit) {
        if (limit < 0) throw new IllegalArgumentException("History limit cannot be negative");
        while (newestFirst.size() > limit) {
            newestFirst.removeLast();
        }
    }
}

package org.kingdomfoxes.ralle.requeue;

import java.util.Optional;

/** Strict parser for Wynncraft's clickable raid-start chat prompt. */
public record RaidStartMessage(String speaker, WynnRaid raid) {
    private static final String START = " would like to start ";
    private static final String READY = "Click here to Ready Up!";

    public static Optional<RaidStartMessage> parse(String text) {
        var announcement = parseAnnouncement(text);
        return announcement.isPresent() && isReadyPrompt(text) ? announcement : Optional.empty();
    }

    public static Optional<RaidStartMessage> parseAnnouncement(String text) {
        if (text == null) return Optional.empty();
        String flattened = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").strip();
        int start = flattened.indexOf(START);
        if (start <= 0) return Optional.empty();
        int ready = flattened.indexOf(READY, start + START.length());
        String beforeReady = flattened.substring(start + START.length(), ready < 0 ? flattened.length() : ready).strip();
        if (!beforeReady.endsWith("!")) return Optional.empty();
        String raidName = beforeReady.substring(0, beforeReady.length() - 1).strip();
        String speaker = flattened.substring(0, start).strip();
        return WynnRaid.fromDisplayName(raidName).map(raid -> new RaidStartMessage(speaker, raid));
    }

    public static boolean isReadyPrompt(String text) {
        if (text == null) return false;
        return text.replace('\n', ' ').replace('\r', ' ').contains(READY);
    }
}

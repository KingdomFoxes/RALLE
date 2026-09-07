package org.kingdomfoxes.ralle.war.queue;

import org.kingdomfoxes.ralle.chat.identity.GuildChatMessage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Anchored parser for Wynntils' automatic guild defense announcement body. */
public final class QueueAnnouncementParser {
    private static final Pattern ANNOUNCEMENT = Pattern.compile(
            "^(.{1,128}) defense is (None|Very Low|Low|Medium|High|Very High)$"
    );
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t]+");

    public Optional<QueueAnnouncement> parse(
            GuildChatMessage message,
            Collection<String> canonicalTerritoryNames
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(canonicalTerritoryNames, "canonicalTerritoryNames");
        if (message.resolvedIgn().isEmpty()
                || !IGN.matcher(message.resolvedIgn().orElseThrow()).matches()
                || containsLineBreak(message.body())) return Optional.empty();

        var match = ANNOUNCEMENT.matcher(normalizeWhitespace(message.body()));
        if (!match.matches()) return Optional.empty();

        var names = new LinkedHashMap<String, String>();
        for (String name : canonicalTerritoryNames) {
            if (name == null || name.isBlank() || containsLineBreak(name)) continue;
            String normalized = normalizeWhitespace(name);
            String previous = names.putIfAbsent(normalized, name);
            if (previous != null && !previous.equals(name)) return Optional.empty();
        }
        String canonical = names.get(normalizeWhitespace(match.group(1)));
        return canonical == null ? Optional.empty()
                : Optional.of(new QueueAnnouncement(canonical, message.resolvedIgn().orElseThrow()));
    }

    static String canonicalTerritory(String candidate, Collection<String> canonicalTerritoryNames) {
        if (candidate == null || containsLineBreak(candidate)) return null;
        String normalizedCandidate = normalizeWhitespace(candidate);
        String result = null;
        for (String name : canonicalTerritoryNames) {
            if (name == null || containsLineBreak(name)) continue;
            if (!normalizeWhitespace(name).equals(normalizedCandidate)) continue;
            if (result != null && !result.equals(name)) return null;
            result = name;
        }
        return result;
    }

    private static String normalizeWhitespace(String value) {
        return WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }

    private static boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}

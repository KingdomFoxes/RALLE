package org.kingdomfoxes.ralle.chat.rank;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/** Immutable instance-local snapshot of IGN to Fox title mappings. */
public final class GuildRankSnapshot {
    public static final GuildRankSnapshot EMPTY = new GuildRankSnapshot(0L, Map.of());
    private final long fetchedAtMillis;
    private final Map<String, String> titlesByPlayer;
    private final Map<String, GuildRankGlyphs> glyphsByTitle;

    public GuildRankSnapshot(long fetchedAtMillis, Map<String, String> titlesByPlayer) {
        this.fetchedAtMillis = fetchedAtMillis;
        var normalized = new LinkedHashMap<String, String>();
        titlesByPlayer.forEach((player, title) -> {
            String normalizedPlayer = normalizePlayer(player);
            String normalizedTitle = normalizeTitle(title);
            if (normalizedPlayer != null && normalizedTitle != null) {
                normalized.put(normalizedPlayer, normalizedTitle);
            }
        });
        this.titlesByPlayer = Map.copyOf(normalized);

        var encoded = new LinkedHashMap<String, GuildRankGlyphs>();
        normalized.values().forEach(title ->
                encoded.computeIfAbsent(title, GuildRankTitleTransformer::encode));
        this.glyphsByTitle = Map.copyOf(encoded);
    }

    public long fetchedAtMillis() {
        return fetchedAtMillis;
    }

    public Map<String, String> titlesByPlayer() {
        return titlesByPlayer;
    }

    public Optional<String> titleFor(String player) {
        String normalized = normalizePlayer(player);
        return normalized == null ? Optional.empty() : Optional.ofNullable(titlesByPlayer.get(normalized));
    }

    public boolean empty() {
        return titlesByPlayer.isEmpty();
    }

    GuildRankGlyphs glyphsFor(String title) {
        return glyphsByTitle.get(title);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof GuildRankSnapshot snapshot
                && fetchedAtMillis == snapshot.fetchedAtMillis
                && titlesByPlayer.equals(snapshot.titlesByPlayer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fetchedAtMillis, titlesByPlayer);
    }

    static String normalizePlayer(String player) {
        if (player == null || player.isEmpty() || player.length() > 16) return null;
        for (int index = 0; index < player.length(); index++) {
            char character = player.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_') {
                return null;
            }
        }
        return player.toLowerCase(Locale.ROOT);
    }

    static String normalizeTitle(String title) {
        if (title == null) return null;
        String normalized = title.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.length() > 32 || !normalized.matches("[A-Z]+(?: [A-Z]+)*")) return null;
        return normalized;
    }
}

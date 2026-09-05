package org.kingdomfoxes.ralle.lfg.client;

import java.util.Locale;
import java.util.Map;

/**
 * Locally stored territory-map colors for the guilds currently admitted to the alliance LFG.
 *
 * <p>The Wynncraft API is authoritative for guild identity and alliance eligibility, but it does
 * not expose territory-map colors. Keep this small presentation lookup synchronized with the
 * Fox's {@code assets/territory/guild-colors.json} territory lookup. The LFG service currently
 * sends UUID-hashed colors, so known territory entries override that presentation fallback.</p>
 */
public final class GuildTerritoryColors {
    private static final int UNKNOWN_GUILD = 0xFF657087;
    private static final Map<String, Integer> BY_PREFIX = Map.ofEntries(
            Map.entry("fox", 0xFFFF8200),
            Map.entry("novu", 0xFFCE4F4F),
            Map.entry("miku", 0xFFC93C39),
            Map.entry("crrs", 0xFF5EFFEC),
            Map.entry("taq", 0xFF0098FF),
            Map.entry("imp", 0xFF990033),
            Map.entry("vets", 0xFFE33232)
    );

    private GuildTerritoryColors() {}

    /** Known territory colors take precedence over the service's UUID-derived fallback. */
    public static int forGuild(String prefix, String fallbackColor) {
        return prefix == null ? parse(fallbackColor)
                : BY_PREFIX.getOrDefault(prefix.strip().toLowerCase(Locale.ROOT), parse(fallbackColor));
    }

    public static int forPrefix(String prefix) {
        if (prefix == null) return UNKNOWN_GUILD;
        return BY_PREFIX.getOrDefault(prefix.toLowerCase(Locale.ROOT), UNKNOWN_GUILD);
    }

    public static int parse(String color) {
        if (color == null) return UNKNOWN_GUILD;
        var value = color.strip();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() != 6) return UNKNOWN_GUILD;
        try {
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return UNKNOWN_GUILD;
        }
    }
}

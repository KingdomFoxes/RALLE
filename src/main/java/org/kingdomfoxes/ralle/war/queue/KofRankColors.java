package org.kingdomfoxes.ralle.war.queue;

import java.util.Locale;

/** Cosmetic KoF title palette. Unrecognized or unavailable ranks retain the normal gray. */
public final class KofRankColors {
    public static final int FALLBACK = 0xAAAAAA;

    private KofRankColors() {}

    public static int forTitle(String title) {
        if (title == null) return FALLBACK;
        return switch (title.strip().toUpperCase(Locale.ROOT)) {
            case "PAGE", "SQUIRE", "SIR", "MADAM", "KNIGHT", "LORD", "LADY", "LIEGE" -> 0x5A84D4;
            case "PRIME MINISTER" -> 0x8E77CC;
            case "BARON", "BARONESS", "BARONX", "VISCOUNT", "VISCOUNTESS", "VISCOUNTX" -> 0x0EACB4;
            case "COUNT", "COUNTESS", "COUNTX", "MARQUIS", "MARCHIONESS", "MARQIX" -> 0x4DD6EC;
            case "VICEROY" -> 0xFF9A19;
            case "ARCHDUKE", "PRINCE", "KING" -> 0xFFCC00;
            default -> FALLBACK;
        };
    }
}

package org.kingdomfoxes.ralle.cosmetics;

/** Canonical tooltip wording shared by LFG surfaces. */
public final class CosmeticPresentation {
    private CosmeticPresentation() {}

    public static String tooltip(String guildTag, String ign, CosmeticIdentity identity) {
        String prefix = guildTag == null || guildTag.isBlank() ? "" : "[" + guildTag + "] ";
        String base = prefix + ign;
        if (identity == null || identity.selectedStyle() == null || identity.grants().isEmpty()) return base;
        String tier = identity.grants().contains("admin") ? "Admin"
                : identity.grants().contains("contributor") ? "Contributor" : "Supporter";
        return base + ", RALLE " + tier;
    }
}

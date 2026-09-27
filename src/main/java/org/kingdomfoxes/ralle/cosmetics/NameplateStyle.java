package org.kingdomfoxes.ralle.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fixed client rendering recipes. Fox alone decides which ID a UUID may select. */
public record NameplateStyle(String id, String role, String label, int shadow, int midtone,
                             int highlight, double defaultResolution) {
    public static final List<NameplateStyle> CATALOG = List.of(
            new NameplateStyle("supporter-gold", "supporter", "Ralle Gold", 0x8b5c20, 0xe5b94c, 0xfff0b5, 1),
            new NameplateStyle("contributor-green", "contributor", "Contributor Green", 0x176f9b, 0x62f500, 0xffffff, .5),
            new NameplateStyle("contributor-green-alt", "contributor", "Contributor Green Alt", 0x388a15, 0x62f500, 0xffffff, .5),
            new NameplateStyle("contributor-blue", "contributor", "Contributor Blue", 0x176f9b, 0x00b8f5, 0xffffff, .5),
            new NameplateStyle("admin-white", "admin", "Admin White", 0x000000, 0x000000, 0xffffff, .5),
            new NameplateStyle("admin-red", "admin", "Admin Red", 0x000000, 0x000000, 0xff0000, .5)
    );

    public static Optional<NameplateStyle> byId(String id) {
        return CATALOG.stream().filter(style -> style.id.equals(id)).findFirst();
    }

    /** Fox v1 grants are hierarchical: Contributor includes Supporter; Admin includes both. */
    public static boolean allowed(String styleId, Set<String> grants) {
        var style = byId(styleId);
        if (style.isEmpty() || grants == null) return false;
        return switch (style.get().role()) {
            case "supporter" -> grants.contains("supporter") || grants.contains("contributor") || grants.contains("admin");
            case "contributor" -> grants.contains("contributor") || grants.contains("admin");
            case "admin" -> grants.contains("admin");
            default -> false;
        };
    }
}

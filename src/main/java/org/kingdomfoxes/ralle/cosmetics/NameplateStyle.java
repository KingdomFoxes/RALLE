package org.kingdomfoxes.ralle.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fixed client rendering recipes. Fox alone decides which ID a UUID may select. */
public record NameplateStyle(String id, String role, String label, int shadow, int midtone,
                             int highlight, double defaultResolution) {
    public static final List<NameplateStyle> CATALOG = List.of(
            load("supporter-gold", "supporter", "Ralle Gold"),
            load("contributor-green", "contributor", "Contributor Green"),
            load("contributor-green-alt", "contributor", "Contributor Green Alt"),
            load("contributor-blue", "contributor", "Contributor Blue"),
            load("admin-white", "admin", "Admin White"),
            load("admin-red", "admin", "Admin Red")
    );

    /** Bundled recipes are visual data; preview identities and supporter flags grant no permissions. */
    private static NameplateStyle load(String id, String role, String label) {
        String path = "/assets/ralle/nameplate_presets/" + id + ".json";
        try (var stream = NameplateStyle.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing nameplate preset: " + id);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"ralle-supporter-look-v1".equals(json.get("schema").getAsString())
                    || !"liquid".equals(json.get("effect").getAsString())
                    || json.get("seed").getAsInt() != LiquidMaterial.SEED
                    || json.get("speed").getAsDouble() != LiquidMaterial.SPEED
                    || json.get("scale").getAsDouble() != LiquidMaterial.SCALE
                    || json.get("warp").getAsDouble() / 100 != LiquidMaterial.WARP
                    || json.get("shine").getAsDouble() / 100 != LiquidMaterial.SHINE)
                throw new IllegalArgumentException("Unsupported liquid recipe");
            double resolution = json.get("resolution").getAsDouble();
            LiquidMaterial.width(resolution);
            return new NameplateStyle(id, role, label, color(json, "dark"), color(json, "gold"),
                    color(json, "light"), resolution);
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot load bundled nameplate preset: " + id, failure);
        }
    }

    private static int color(com.google.gson.JsonObject json, String key) {
        String value = json.get(key).getAsString();
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid preset color");
        return Integer.parseInt(value.substring(1), 16);
    }

    public static Optional<NameplateStyle> byId(String id) {
        return CATALOG.stream().filter(style -> style.id.equals(id)).findFirst();
    }

    public static List<NameplateStyle> available(Set<String> grants) {
        return CATALOG.stream().filter(style -> allowed(style.id(), grants)).toList();
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

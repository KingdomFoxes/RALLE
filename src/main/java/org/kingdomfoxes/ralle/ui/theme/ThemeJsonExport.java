package org.kingdomfoxes.ralle.ui.theme;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/** Stable clipboard representation of a theme's original metadata and effective RGB values. */
public final class ThemeJsonExport {
    private ThemeJsonExport() {}

    public static String serialize(RalleThemeCatalog.Theme theme) {
        var object = new JsonObject();
        object.addProperty("name", theme.id().equals(RalleThemeCatalog.DEFAULT_ID) ? "RALLE Default" : theme.name());
        object.addProperty("author", theme.id().equals(RalleThemeCatalog.DEFAULT_ID) ? "RALLE" : theme.contributor());
        object.addProperty("background", hex(theme.background()));
        object.addProperty("outline", hex(theme.outline()));
        object.addProperty("accent", hex(theme.accent()));
        return new GsonBuilder().setPrettyPrinting().create().toJson(object);
    }

    public static boolean validHex(String value) { return value != null && value.matches("#[0-9A-Fa-f]{6}"); }
    public static int parseHex(String value) {
        if (!validHex(value)) throw new IllegalArgumentException("Color must use #RRGGBB");
        return Integer.parseInt(value.substring(1), 16);
    }
    public static String hex(int rgb) { return "#%06X".formatted(rgb & 0xFFFFFF); }
}

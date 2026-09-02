package org.kingdomfoxes.ralle.war.consumables;

/** Immutable highlight style. RGB remains available while rainbow animation is active. */
public record HighlightStyle(int rgb, boolean rainbow) {
    public HighlightStyle {
        if ((rgb & 0xFF000000) != 0) throw new IllegalArgumentException("RGB must contain exactly 24 bits");
    }

    public String hex() {
        return "#%06X".formatted(rgb);
    }

    public static HighlightStyle parse(String hex, boolean rainbow) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Color must use #RRGGBB");
        }
        return new HighlightStyle(Integer.parseInt(hex.substring(1), 16), rainbow);
    }
}

package org.kingdomfoxes.ralle.ui.theme;

import java.util.HashMap;
import java.util.Map;

/** Session-only editable theme colors. Packaged catalog records remain immutable. */
public final class ThemePreviewSession {
    public enum Role { BACKGROUND, OUTLINE, ACCENT }

    private final Map<String, RalleThemeCatalog.Theme> overrides = new HashMap<>();
    private long revision;

    public RalleThemeCatalog.Theme effectiveTheme(String id) {
        var catalog = RalleThemeCatalog.get(id);
        return overrides.getOrDefault(catalog.id(), catalog);
    }

    public boolean setColor(String id, Role role, int rgb) {
        if (!RalleThemeCatalog.contains(id)) throw new IllegalArgumentException("Unknown theme id: " + id);
        if (rgb < 0 || rgb > 0xFFFFFF) throw new IllegalArgumentException("Color must be an RGB value");
        var old = effectiveTheme(id);
        int previous = switch (role) {
            case BACKGROUND -> old.background();
            case OUTLINE -> old.outline();
            case ACCENT -> old.accent();
        };
        if (previous == rgb) return false;
        var updated = new RalleThemeCatalog.Theme(old.id(), old.name(), old.contributor(),
                role == Role.BACKGROUND ? rgb : old.background(),
                role == Role.OUTLINE ? rgb : old.outline(),
                role == Role.ACCENT ? rgb : old.accent());
        if (updated.equals(RalleThemeCatalog.get(old.id()))) overrides.remove(old.id());
        else overrides.put(old.id(), updated);
        revision++;
        return true;
    }

    public long revision() { return revision; }
}

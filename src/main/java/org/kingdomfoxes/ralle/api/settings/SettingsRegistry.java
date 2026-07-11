package org.kingdomfoxes.ralle.api.settings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SettingsRegistry {
    private final Map<String, SettingsCategory> categories = new LinkedHashMap<>();
    private boolean sealed;

    public void registerCategory(SettingsCategory category) {
        requireOpen();
        Objects.requireNonNull(category, "category");
        if (categories.putIfAbsent(category.id(), category) != null) {
            throw new IllegalArgumentException("Duplicate settings category id: " + category.id());
        }
    }

    public Collection<SettingsCategory> categories() {
        return List.copyOf(categories.values());
    }

    public void seal() {
        sealed = true;
    }

    private void requireOpen() {
        if (sealed) throw new IllegalStateException("Settings registration is sealed");
    }
}

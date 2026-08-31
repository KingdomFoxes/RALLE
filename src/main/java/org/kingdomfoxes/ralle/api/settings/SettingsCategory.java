package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.List;
import java.util.Objects;

public record SettingsCategory(
        String id,
        Component title,
        Component description,
        List<SettingsEntry> entries,
        List<SettingsSubcategory> subcategories
) {
    public SettingsCategory {
        id = IdentifierRules.requireValid(id, "settings category id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        entries = List.copyOf(entries);
        subcategories = List.copyOf(subcategories);
        if (entries.isEmpty() && subcategories.isEmpty()) {
            throw new IllegalArgumentException("A settings category needs a direct entry or subcategory");
        }
    }

    public SettingsCategory(String id, Component title, Component description, List<SettingsSubcategory> subcategories) {
        this(id, title, description, List.of(), subcategories);
    }
}

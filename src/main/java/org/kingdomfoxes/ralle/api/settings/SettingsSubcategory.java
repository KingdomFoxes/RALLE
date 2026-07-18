package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.List;
import java.util.Objects;

public record SettingsSubcategory(String id, Component title, Component description, List<SettingsEntry> entries) {
    public SettingsSubcategory {
        id = IdentifierRules.requireValid(id, "settings subcategory id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        entries = List.copyOf(entries);
    }
}

package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.List;
import java.util.Objects;

public record SettingsCategory(String id, Component title, Component description, List<Setting<?>> settings) {
    public SettingsCategory {
        id = IdentifierRules.requireValid(id, "settings category id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        settings = List.copyOf(settings);
    }
}

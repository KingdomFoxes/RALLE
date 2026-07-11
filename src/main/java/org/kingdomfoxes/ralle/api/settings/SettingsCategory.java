package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.Objects;

public record SettingsCategory(String id, Component title, Component description) {
    public SettingsCategory {
        id = IdentifierRules.requireValid(id, "settings category id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }
}

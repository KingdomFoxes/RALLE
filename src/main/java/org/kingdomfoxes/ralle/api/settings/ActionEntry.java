package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.Objects;

public record ActionEntry(String id, Component title, Component description) implements SettingsEntry {
    public ActionEntry {
        id = IdentifierRules.requireValid(id, "settings action id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
    }
}

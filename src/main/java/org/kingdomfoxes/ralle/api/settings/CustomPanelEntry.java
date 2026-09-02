package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.api.feature.IdentifierRules;

import java.util.Objects;

/** Metadata-only, full-width settings entry rendered by a presentation adapter. */
public record CustomPanelEntry(
        String id,
        Component title,
        Component description,
        String providerId
) implements SettingsEntry {
    public CustomPanelEntry {
        id = IdentifierRules.requireValid(id, "custom settings panel id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        providerId = IdentifierRules.requireValid(providerId, "custom settings panel provider id");
    }
}

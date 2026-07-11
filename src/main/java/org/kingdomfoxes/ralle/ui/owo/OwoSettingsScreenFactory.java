package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.screens.Screen;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;

import java.util.Objects;

public final class OwoSettingsScreenFactory implements SettingsScreenFactory {
    private final SettingsRegistry settings;

    public OwoSettingsScreenFactory(SettingsRegistry settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public Screen create(Screen parent) {
        return new RalleSettingsScreen(parent, settings);
    }
}

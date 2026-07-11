package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.client.gui.screens.Screen;

@FunctionalInterface
public interface SettingsScreenFactory {
    Screen create(Screen parent);
}

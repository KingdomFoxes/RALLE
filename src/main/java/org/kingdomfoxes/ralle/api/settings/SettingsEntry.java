package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;

public interface SettingsEntry {
    String id();

    Component title();

    Component description();
}

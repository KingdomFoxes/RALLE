package org.kingdomfoxes.ralle;

import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;

public record RalleContext(
        FeatureRegistry features,
        SettingsRegistry settings,
        SettingsScreenFactory settingsScreens,
        ChatLayoutService chatLayout,
        ChatBehaviorService chatBehavior
) {
}

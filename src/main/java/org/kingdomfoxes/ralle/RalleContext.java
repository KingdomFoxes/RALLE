package org.kingdomfoxes.ralle;

import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;

public record RalleContext(
        FeatureRegistry features,
        SettingsRegistry settings,
        SettingsScreenFactory settingsScreens
) {
}

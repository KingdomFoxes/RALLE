package org.kingdomfoxes.ralle.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RalleSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void textShadowChoicesKeepVanillaDefaultAndLegacyFullValue() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        var setting = registry.setting("text-shadow", ChoiceSetting.class);

        assertEquals("vanilla", setting.value());
        assertEquals(List.of("none", "vanilla", "full", "wrapped-full"), setting.choices());
    }

    @Test
    void chatScreenshotOptionsAreIndependentlyDisabledByDefault() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertEquals(false, registry.setting("chat-screenshot-enabled", BooleanSetting.class).value());
        assertEquals(false, registry.setting("chat-screenshot-smooth-expansion", BooleanSetting.class).value());
    }
}

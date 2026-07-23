package org.kingdomfoxes.ralle.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        assertEquals(false, registry.setting("chat-selection-sounds", BooleanSetting.class).value());
        assertEquals(false, registry.setting("chat-screenshot-smooth-expansion", BooleanSetting.class).value());
    }

    @Test
    void chatScrollbarToggleIsDisabledByDefaultAndRequiresChatCustomization() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertFalse(registry.setting("hide-chat-scrollbar", BooleanSetting.class).value());
        assertEquals(List.of("chat-enabled"), registry.dependencies("hide-chat-scrollbar"));
    }

    @Test
    void registersApprovedSubcategoryOrderAndUnboundLfgKey() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        var categories = registry.categories().stream().toList();
        assertEquals(List.of("about", "chat", "raid-lfg"), categories.stream().map(value -> value.id()).toList());
        assertEquals(List.of("interface"),
                categories.get(0).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("general", "appearance", "message-behavior", "screenshots"),
                categories.get(1).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("general", "notifications", "controls"),
                categories.get(2).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(KeybindSetting.UNBOUND, registry.setting("raid-lfg-keybind", KeybindSetting.class).value());
    }

    @Test
    void interfaceFontDefaultsPersistsAndFallsBackFromInvalidStorage() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var font = registry.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class);
        assertEquals("vanilla", font.value());
        assertEquals(List.of("vanilla", "karla"), font.choices());

        font.set("karla");
        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertEquals("karla", restored.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class).value());

        Files.writeString(path, "about.interface-font=unsupported\n");
        var invalid = new SettingsRegistry(path);
        RalleSettings.register(invalid);
        invalid.seal();
        assertEquals("vanilla", invalid.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class).value());
    }
}

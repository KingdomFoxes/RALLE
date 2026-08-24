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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void chatScrollbarToggleIsIndependentAndDisabledByDefault() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertFalse(registry.setting("hide-chat-scrollbar", BooleanSetting.class).value());
        assertEquals(List.of(), registry.dependencies("hide-chat-scrollbar"));
    }

    @Test
    void chatSystemIndicatorToggleIsIndependentAndDisabledByDefault() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertFalse(registry.setting("remove-chat-system-indicators", BooleanSetting.class).value());
        assertEquals(List.of(), registry.dependencies("remove-chat-system-indicators"));
    }

    @Test
    void chatFeaturesUseTheirOwnTogglesWithoutAMasterToggle() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertTrue(registry.entry("chat-enabled").isEmpty());
        assertEquals(List.of("message-direction-enabled"), registry.dependencies("message-direction"));
        assertEquals(List.of("horizontal-alignment-enabled"), registry.dependencies("horizontal-alignment"));
        assertEquals(List.of("text-shadow-enabled"), registry.dependencies("text-shadow"));
        assertEquals(List.of("chat-screenshot-enabled"),
                registry.dependencies("chat-screenshot-smooth-expansion"));
        assertEquals(List.of("chat-screenshot-enabled"), registry.dependencies("chat-selection-sounds"));
    }

    @Test
    void registersApprovedSubcategoryOrderAndUnboundLfgKey() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        var categories = registry.categories().stream().toList();
        assertEquals(List.of("about", "chat", "raid-lfg"), categories.stream().map(value -> value.id()).toList());
        assertEquals(List.of("interface"),
                categories.get(0).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("edit-huds", RalleSettings.INTERFACE_FONT_ID),
                categories.get(0).subcategories().getFirst().entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of(), registry.dependencies("edit-huds"));
        assertEquals(List.of("general", "appearance", "message-direction", "horizontal-alignment", "text-shadow",
                        "message-behavior", "screenshots"),
                categories.get(1).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("general", "notifications", "controls"),
                categories.get(2).subcategories().stream().map(value -> value.id()).toList());
        for (var id : List.of(
                "raid-lfg-keybind",
                "raid-lfg-join-keybind",
                "raid-lfg-close-keybind",
                "raid-lfg-leave-disband-keybind",
                "raid-lfg-party-filled-keybind",
                "raid-lfg-ping-keybind",
                "raid-lfg-lock-keybind",
                "raid-lfg-create-keybind",
                "raid-lfg-kick-keybind")) {
            assertEquals(KeybindSetting.UNBOUND, registry.setting(id, KeybindSetting.class).value());
            assertEquals(List.of("raid-lfg-enabled"), registry.dependencies(id));
        }
    }

    @Test
    void discoveryNotificationsDefaultOffAndDependOnRaidLfg() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertFalse(registry.setting("new-party-notifications", BooleanSetting.class).value());
        assertFalse(registry.setting("reopened-party-notifications", BooleanSetting.class).value());
        assertFalse(registry.setting("party-status-notifications", BooleanSetting.class).value());
        assertFalse(registry.setting("auto-pop-out-main-ui", BooleanSetting.class).value());
        assertFalse(registry.setting("notification-sounds", BooleanSetting.class).value());
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("new-party-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("reopened-party-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("party-status-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("auto-pop-out-main-ui"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("edit-notification-position"));
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

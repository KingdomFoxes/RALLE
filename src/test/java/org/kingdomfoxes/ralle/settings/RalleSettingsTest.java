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
    void cosmeticControlsIgnoreLegacyToggleAndPersistOnlyLocalAppearance() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        Files.writeString(path, "cosmetics.nameplate-cosmetics=false\ncosmetics.effect-treatment=text\n"
                + "cosmetics.white-username-outline=true\ncosmetics.outline-thickness=2\n");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var supporter = registry.categories().stream().filter(category -> category.id().equals("cosmetics"))
                .findFirst().orElseThrow();
        assertTrue(supporter.subcategories().isEmpty());
        assertEquals(List.of("nameplate-preview", RalleSettings.MATERIAL_RESOLUTION_ID,
                        RalleSettings.NAMEPLATE_COLOR_ID, RalleSettings.SHOW_OWN_NAMETAG_ID),
                supporter.entries().stream().map(entry -> entry.id()).toList());
        assertTrue(registry.entry("nameplate-cosmetics").isEmpty());
        assertFalse(registry.setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class).value());
        assertTrue(registry.available(RalleSettings.MATERIAL_RESOLUTION_ID));
        assertEquals("1", registry.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class).value());
        var appearance = org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance.from(registry,
                org.kingdomfoxes.ralle.cosmetics.NameplateStyle.CATALOG.getFirst());
        assertEquals(org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance.Treatment.PLATE, appearance.treatment());
        assertEquals(0, appearance.usernameOutlinePixels());
        for (var style : org.kingdomfoxes.ralle.cosmetics.NameplateStyle.CATALOG) {
            assertEquals(1d, org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance.from(registry, style).resolution());
        }
        registry.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class).set("0.5");
        registry.setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class).set(true);
        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertEquals("0.5", restored.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class).value());
        assertTrue(restored.setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class).value());
        assertTrue(restored.available(RalleSettings.NAMEPLATE_COLOR_ID));
        assertFalse(Files.readString(path).contains("selected_style_id"));
        assertFalse(Files.readString(path).contains("nameplate-cosmetics"));
    }

    @Test
    void pointAndLaughIsAnEmptyTopLevelWarTextFieldAndPersists() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var setting = registry.setting(RalleSettings.POINT_AND_LAUGH_ID,
                org.kingdomfoxes.ralle.api.settings.TextSetting.class);
        assertEquals("", setting.value());
        assertTrue(registry.categories().stream().filter(category -> category.id().equals("war"))
                .findFirst().orElseThrow().entries().contains(setting));
        setting.set("Better luck next time!");
        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        var saved = restored.setting(RalleSettings.POINT_AND_LAUGH_ID,
                org.kingdomfoxes.ralle.api.settings.TextSetting.class);
        assertEquals(setting.value(), saved.value());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> saved.set("a\nb"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> saved.set("x".repeat(255)));
        saved.set("");
        assertEquals("", saved.value());
    }

    @Test
    void freshInstallPersistsTheRestoredPresetAndLeavesOtherFeaturesOff() throws Exception {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);
        registry.seal();
        var enabled = List.of("chat-screenshot-enabled", "chat-selection-sounds", "raid-lfg-enabled",
                "new-party-notifications", "reopened-party-notifications", "party-status-notifications",
                "auto-pop-out-main-ui", "notification-sounds");
        registry.entries().stream()
                .forEach(entry -> {
                    if (entry instanceof BooleanSetting setting) {
                        assertEquals(enabled.contains(setting.id()), setting.value(), setting.id());
                    }
                });
        var saved = new java.util.Properties();
        try (var reader = Files.newBufferedReader(temporaryDirectory.resolve("ralle.properties"))) {
            saved.load(reader);
        }
        assertEquals("true", saved.getProperty("chat.chat-screenshot-enabled"));
        assertEquals("true", saved.getProperty("raid-lfg.raid-lfg-enabled"));
        assertEquals("key.keyboard.f1", saved.getProperty("raid-lfg.raid-lfg-keybind"));
    }

    @Test
    void restoredDefaultsPreserveExistingDisabledFeaturesAndCustomBindings() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        String existing = "chat.chat-screenshot-enabled=false\nchat.chat-selection-sounds=false\n"
                + "raid-lfg.raid-lfg-enabled=false\nraid-lfg.raid-lfg-keybind=unbound\n"
                + "raid-lfg.raid-lfg-join-keybind=key.keyboard.g\n";
        Files.writeString(path, existing);
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        assertFalse(registry.setting("chat-screenshot-enabled", BooleanSetting.class).value());
        assertFalse(registry.setting("chat-selection-sounds", BooleanSetting.class).value());
        assertFalse(registry.setting(RalleSettings.RAID_LFG_ENABLED_ID, BooleanSetting.class).value());
        assertEquals(KeybindSetting.UNBOUND, registry.setting("raid-lfg-keybind", KeybindSetting.class).value());
        assertEquals("key.keyboard.g", registry.setting("raid-lfg-join-keybind", KeybindSetting.class).value());
        assertEquals(existing, Files.readString(path));
    }

    @Test
    void warColorAndInspectionBindingPersistLocally() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var color = registry.setting(RalleSettings.QUEUE_SELF_COLOR_ID, org.kingdomfoxes.ralle.api.settings.ColorSetting.class);
        assertEquals(new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x5555FF, false), color.value());
        color.set(new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0xABCDEF, true));
        registry.setting(RalleSettings.HQ_DISTANCE_KEYBIND_ID, KeybindSetting.class).set("key.keyboard.g");
        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertEquals(new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0xABCDEF, true), restored.setting(RalleSettings.QUEUE_SELF_COLOR_ID,
                org.kingdomfoxes.ralle.api.settings.ColorSetting.class).value());
        assertEquals("key.keyboard.g", restored.setting(RalleSettings.HQ_DISTANCE_KEYBIND_ID, KeybindSetting.class).value());
        assertFalse(restored.setting(RalleSettings.HQ_DISTANCE_ENABLED_ID, BooleanSetting.class).value());
        assertFalse(restored.setting(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID, BooleanSetting.class).value());
    }

    @Test
    void existingSolidQueueColorsRemainSolid() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        Files.writeString(path, "war.queue-self-color=#ABCDEF\n");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var color = registry.setting(RalleSettings.QUEUE_SELF_COLOR_ID, org.kingdomfoxes.ralle.api.settings.ColorSetting.class);
        assertEquals(new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0xABCDEF, false), color.value());
        assertEquals("#ABCDEF", color.serialize());
    }

    @Test
    void textShadowChoicesKeepVanillaDefaultAndLegacyFullValue() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        var setting = registry.setting("text-shadow", ChoiceSetting.class);

        assertEquals("vanilla", setting.value());
        assertEquals(List.of("none", "vanilla", "full", "wrapped-full"), setting.choices());
    }

    @Test
    void chatScreenshotsAndSoundsDefaultOnWithOptionalSelectionTweaksOff() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertTrue(registry.setting("chat-screenshot-enabled", BooleanSetting.class).value());
        assertEquals(false, registry.setting("chat-screenshot-snap-to-text", BooleanSetting.class).value());
        assertTrue(registry.setting("chat-selection-sounds", BooleanSetting.class).value());
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
    void chatTimestampsDefaultOffAndPersistUnderTheChatCategory() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        var timestamps = registry.setting("chat-timestamps", BooleanSetting.class);
        assertFalse(timestamps.value());
        assertEquals(List.of(), registry.dependencies("chat-timestamps"));

        timestamps.set(true);
        assertTrue(Files.readString(path).contains("chat.chat-timestamps=true"));

        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertTrue(restored.setting("chat-timestamps", BooleanSetting.class).value());
    }

    @Test
    void chatTypeTabbingDefaultsOffPersistsUnderChatAndHasNoParentDependency() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        var setting = registry.setting("chat-type-tabbing", BooleanSetting.class);
        assertFalse(setting.value());
        assertEquals(List.of(), registry.dependencies("chat-type-tabbing"));

        setting.set(true);
        assertTrue(Files.readString(path).contains("chat.chat-type-tabbing=true"));
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
        assertEquals(List.of("chat-screenshot-enabled"),
                registry.dependencies("chat-screenshot-snap-to-text"));
        assertEquals(List.of("chat-screenshot-enabled"), registry.dependencies("chat-selection-sounds"));
    }

    @Test
    void persistentChatDefaultsAndLimitDependencyMatchTheSessionOnlyFeature() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertFalse(registry.setting("persistent-chat-enabled", BooleanSetting.class).value());
        var limit = registry.setting("persistent-chat-limit", ChoiceSetting.class);
        assertEquals("500", limit.value());
        assertEquals(List.of("300", "500", "1000", "1500"), limit.choices());
        assertEquals(List.of("persistent-chat-enabled"), registry.dependencies("persistent-chat-limit"));
    }

    @Test
    void persistentChatSettingsPersistAndInvalidLimitFallsBackToDefault() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        registry.setting("persistent-chat-enabled", BooleanSetting.class).set(true);
        registry.setting("persistent-chat-limit", ChoiceSetting.class).set("1500");

        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertTrue(restored.setting("persistent-chat-enabled", BooleanSetting.class).value());
        assertEquals("1500", restored.setting("persistent-chat-limit", ChoiceSetting.class).value());
        assertTrue(Files.readString(path).contains("chat.persistent-chat-enabled=true"));
        assertTrue(Files.readString(path).contains("chat.persistent-chat-limit=1500"));

        Files.writeString(path, "chat.persistent-chat-enabled=true\nchat.persistent-chat-limit=unsupported\n");
        var invalid = new SettingsRegistry(path);
        RalleSettings.register(invalid);
        invalid.seal();
        assertTrue(invalid.setting("persistent-chat-enabled", BooleanSetting.class).value());
        assertEquals("500", invalid.setting("persistent-chat-limit", ChoiceSetting.class).value());
    }

    @Test
    void registersApprovedSubcategoryOrderAndRestoredFunctionKeyDefaults() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        var categories = registry.categories().stream().toList();
        assertEquals(List.of("about", "chat", "raid-lfg", "war", "cosmetics"), categories.stream().map(value -> value.id()).toList());
        assertEquals(List.of(),
                categories.get(0).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("edit-huds", RalleSettings.INTERFACE_FONT_ID, RalleSettings.UI_THEME_ID),
                categories.get(0).entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of(), registry.dependencies("edit-huds"));
        assertEquals(List.of("appearance", "input", "guild-ranks", "message-direction", "horizontal-alignment", "text-shadow",
                        "chat-history", "screenshots"),
                categories.get(1).subcategories().stream().map(value -> value.id()).toList());
        assertTrue(registry.entry("compact-chat").isEmpty());
        assertTrue(registry.entry("stack-empty-lines").isEmpty());
        assertEquals(List.of("edit-chat-layout"), categories.get(1).entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of("hide-chat-scrollbar", "remove-chat-system-indicators", "chat-timestamps"),
                categories.get(1).subcategories().get(0).entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of("chat-type-tabbing"),
                categories.get(1).subcategories().get(1).entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of(RalleSettings.INTERNAL_GUILD_RANKS_ID, RalleSettings.GUILD_RANK_STYLE_ID),
                categories.get(1).subcategories().get(2).entries().stream().map(value -> value.id()).toList());
        assertFalse(registry.setting(RalleSettings.INTERNAL_GUILD_RANKS_ID, BooleanSetting.class).value());
        assertEquals(List.of(), registry.dependencies(RalleSettings.INTERNAL_GUILD_RANKS_ID));
        var rankStyle = registry.setting(RalleSettings.GUILD_RANK_STYLE_ID, ChoiceSetting.class);
        assertEquals("titles", rankStyle.value());
        assertEquals(List.of("titles", "stars", "stars-and-titles"), rankStyle.choices());
        assertEquals(List.of(), registry.dependencies(RalleSettings.GUILD_RANK_STYLE_ID));
        assertEquals(List.of("notifications", "controls"),
                categories.get(2).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of("raid-lfg-enabled", "edit-notification-position"),
                categories.get(2).entries().stream().map(value -> value.id()).toList());
        assertEquals(List.of("new-party-notifications", "reopened-party-notifications",
                        "party-status-notifications", "auto-pop-out-main-ui", "notification-sounds"),
                categories.get(2).subcategories().getFirst().entries().stream().map(value -> value.id()).toList());
        var keybinds = List.of(
                "raid-lfg-keybind",
                "raid-lfg-join-keybind",
                "raid-lfg-close-keybind",
                "raid-lfg-leave-disband-keybind",
                "raid-lfg-party-filled-keybind",
                "raid-lfg-ping-keybind",
                "raid-lfg-lock-keybind",
                "raid-lfg-create-keybind",
                "raid-lfg-kick-keybind");
        for (int index = 0; index < keybinds.size(); index++) {
            var id = keybinds.get(index);
            assertEquals("key.keyboard.f" + (index + 1), registry.setting(id, KeybindSetting.class).value());
            assertEquals(List.of("raid-lfg-enabled"), registry.dependencies(id));
        }
        for (var id : List.of("raid-lfg-create-selector-wheel", "raid-lfg-kick-selector-wheel")) {
            assertFalse(registry.setting(id, BooleanSetting.class).value());
            assertEquals(List.of("raid-lfg-enabled"), registry.dependencies(id));
        }
        assertEquals(KeybindSetting.UNBOUND,
                registry.setting("automatic-raid-requeue-keybind", KeybindSetting.class).value());
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("automatic-raid-requeue-keybind"));
        assertEquals(List.of("attack-timers", "territory-map", "consumables"),
                categories.get(3).subcategories().stream().map(value -> value.id()).toList());
        assertEquals(List.of(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID,
                        RalleSettings.QUEUE_KOF_RANK_COLORS_ID, RalleSettings.QUEUE_SELF_COLOR_ID),
                categories.get(3).subcategories().getFirst().entries().stream().map(value -> value.id()).toList());
        assertFalse(registry.setting(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID, BooleanSetting.class).value());
        assertEquals(List.of(RalleSettings.HQ_DISTANCE_ENABLED_ID, RalleSettings.HQ_DISTANCE_KEYBIND_ID),
                categories.get(3).subcategories().get(1).entries().stream().map(value -> value.id()).toList());
        assertFalse(registry.setting(RalleSettings.HQ_DISTANCE_ENABLED_ID, BooleanSetting.class).value());
        assertEquals(List.of(RalleSettings.CONSUMABLE_HIGHLIGHTS_ENABLED_ID,
                        RalleSettings.CONSUMABLE_HIGHLIGHT_RULES_ID),
                categories.get(3).subcategories().get(2).entries().stream().map(value -> value.id()).toList());
        assertFalse(registry.setting(RalleSettings.CONSUMABLE_HIGHLIGHTS_ENABLED_ID, BooleanSetting.class).value());
        assertEquals(List.of(RalleSettings.CONSUMABLE_HIGHLIGHTS_ENABLED_ID),
                registry.dependencies(RalleSettings.CONSUMABLE_HIGHLIGHT_RULES_ID));
    }

    @Test
    void consumableHighlightTogglePersistsUnderWarAndGatesTheEditor() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        assertFalse(registry.available(RalleSettings.CONSUMABLE_HIGHLIGHT_RULES_ID));
        registry.setting(RalleSettings.CONSUMABLE_HIGHLIGHTS_ENABLED_ID, BooleanSetting.class).set(true);

        assertTrue(Files.readString(path).contains("war.consumable-highlights-enabled=true"));
        assertTrue(registry.available(RalleSettings.CONSUMABLE_HIGHLIGHT_RULES_ID));
    }

    @Test
    void hqDistanceToggleDefaultsOffAndPersistsUnderWar() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        var setting = registry.setting(RalleSettings.HQ_DISTANCE_ENABLED_ID, BooleanSetting.class);
        assertFalse(setting.value());
        setting.set(true);
        assertTrue(Files.readString(path).contains("war.hq-distance-enabled=true"));
    }

    @Test
    void queueAttributionDefaultsOffAndPersistsUnderWar() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        var setting = registry.setting(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID, BooleanSetting.class);
        assertFalse(setting.value());
        setting.set(true);
        assertTrue(Files.readString(path).contains("war.queue-attribution-enabled=true"));
        var rankColors = registry.setting(RalleSettings.QUEUE_KOF_RANK_COLORS_ID, BooleanSetting.class);
        assertFalse(rankColors.value());
        assertEquals(List.of(RalleSettings.WAR_QUEUE_ATTRIBUTION_ENABLED_ID),
                registry.dependencies(RalleSettings.QUEUE_KOF_RANK_COLORS_ID));
        rankColors.set(true);
        assertTrue(Files.readString(path).contains("war.queue-kof-rank-colors=true"));
    }

    @Test
    void raidLfgOptionsDefaultOnAndDependOnRaidLfg() {
        var registry = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(registry);

        assertTrue(registry.setting("raid-lfg-enabled", BooleanSetting.class).value());
        assertTrue(registry.setting("new-party-notifications", BooleanSetting.class).value());
        assertTrue(registry.setting("reopened-party-notifications", BooleanSetting.class).value());
        assertTrue(registry.setting("party-status-notifications", BooleanSetting.class).value());
        assertTrue(registry.setting("auto-pop-out-main-ui", BooleanSetting.class).value());
        assertTrue(registry.setting("notification-sounds", BooleanSetting.class).value());
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("new-party-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("reopened-party-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("party-status-notifications"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("auto-pop-out-main-ui"));
        assertEquals(List.of("raid-lfg-enabled"), registry.dependencies("edit-notification-position"));
    }

    @Test
    void storedValuesContinueToOverrideDefaults() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        Files.writeString(path, """
                chat.chat-screenshot-enabled=true
                chat.chat-selection-sounds=true
                raid-lfg.raid-lfg-enabled=true
                raid-lfg.new-party-notifications=true
                raid-lfg.raid-lfg-keybind=key.keyboard.g
                """);

        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        assertTrue(registry.setting("chat-screenshot-enabled", BooleanSetting.class).value());
        assertTrue(registry.setting("chat-selection-sounds", BooleanSetting.class).value());
        assertTrue(registry.setting("raid-lfg-enabled", BooleanSetting.class).value());
        assertTrue(registry.setting("new-party-notifications", BooleanSetting.class).value());
        assertEquals("key.keyboard.g", registry.setting("raid-lfg-keybind", KeybindSetting.class).value());
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

    @Test
    void uiThemeUsesStableCatalogIdsAndFallsBackWhenSavedThemeIsRemoved() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();
        var theme = registry.setting(RalleSettings.UI_THEME_ID, ChoiceSetting.class);
        assertEquals("default", theme.value());
        assertEquals(25, theme.choices().size());
        theme.set("hot-chocolate");
        assertTrue(Files.readString(path).contains("about.ui-theme=hot-chocolate"));

        Files.writeString(path, "about.ui-theme=renamed-or-removed\n");
        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertEquals("default", restored.setting(RalleSettings.UI_THEME_ID, ChoiceSetting.class).value());
    }

    @Test
    void guildRankStyleDefaultsToUnchangedTitlesAndPersistsUnderChat() throws Exception {
        var path = temporaryDirectory.resolve("ralle.properties");
        var registry = new SettingsRegistry(path);
        RalleSettings.register(registry);
        registry.seal();

        var style = registry.setting(RalleSettings.GUILD_RANK_STYLE_ID, ChoiceSetting.class);
        assertEquals("titles", style.value());

        style.set("stars-and-titles");
        assertTrue(Files.readString(path).contains("chat.guild-rank-style=stars-and-titles"));

        var restored = new SettingsRegistry(path);
        RalleSettings.register(restored);
        restored.seal();
        assertEquals("stars-and-titles",
                restored.setting(RalleSettings.GUILD_RANK_STYLE_ID, ChoiceSetting.class).value());
    }
}

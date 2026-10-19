package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSearchTest {
    @TempDir Path directory;

    @Test void wheelStylesHideInSearchUntilBothWheelAndLfgAreEnabled() {
        var registry = new SettingsRegistry(directory.resolve("wheels.properties"));
        org.kingdomfoxes.ralle.settings.RalleSettings.register(registry);
        registry.seal();
        String style = org.kingdomfoxes.ralle.settings.RalleSettings.CREATE_WHEEL_STYLE_ID;
        assertTrue(SettingsSearch.find(registry, style).isEmpty());
        registry.setting("raid-lfg-create-selector-wheel", BooleanSetting.class).set(true);
        var group = SettingsSearch.find(registry, style).getFirst();
        assertEquals("wheels", group.category().id());
        assertEquals("lfg-create-selector", group.subcategory().id());
        assertEquals(List.of(style), group.entries().stream().map(SettingsEntry::id).toList());
        registry.setting("raid-lfg-enabled", BooleanSetting.class).set(false);
        assertTrue(SettingsSearch.find(registry, style).isEmpty());
    }

    @Test
    void searchesMetadataAndGroupsResultsInRegistryOrder() {
        var registry = new SettingsRegistry(directory.resolve("settings.properties"));
        registry.registerCategory(new SettingsCategory("chat", text("Chat"), text("Local messages"), List.of(
                toggle("layout", "Edit Chat Layout")
        ), List.of(
                new SettingsSubcategory("general", text("General"), text("Core"), List.of(toggle("chat-enabled", "Enable Chat"))),
                new SettingsSubcategory("screenshots", text("Screenshots"), text("Transparent images"), List.of(toggle("sounds", "Selection Sounds")))
        )));

        var byEntry = SettingsSearch.find(registry, "sounds");
        assertEquals(List.of("Chat › Screenshots"), byEntry.stream()
                .map(group -> group.category().title().getString() + " › " + group.subcategory().title().getString()).toList());
        assertEquals(List.of("sounds"), byEntry.getFirst().entries().stream().map(SettingsEntry::id).toList());
        assertEquals(3, SettingsSearch.find(registry, "local messages").size());
        assertEquals(1, SettingsSearch.find(registry, "transparent images").size());

        var direct = SettingsSearch.find(registry, "layout").getFirst();
        assertEquals("chat", direct.category().id());
        assertEquals(true, direct.categoryPage());
        assertEquals(List.of("layout"), direct.entries().stream().map(SettingsEntry::id).toList());
    }

    @Test
    void includesAvailableCustomPanelsAndHidesThemWhenTheirDependencyIsUnmet() {
        var registry = new SettingsRegistry(directory.resolve("settings.properties"));
        var enabled = toggle("consumable-highlights-enabled", "Highlight Consumables");
        var rules = new CustomPanelEntry(
                "consumable-highlight-rules",
                text("Highlight Rules"),
                text("Ordered local consumable rules"),
                "consumable-highlight-editor"
        );
        registry.registerCategory(new SettingsCategory("war", text("War"), text("War tools"), List.of(), List.of(
                new SettingsSubcategory("consumables", text("Consumables"), text("Potion, food, and scroll tools"),
                        List.of(enabled, rules))
        )));
        registry.requireEnabled(rules.id(), enabled.id());

        assertTrue(SettingsSearch.find(registry, "ordered local").isEmpty());

        enabled.set(true);
        assertEquals(List.of("consumable-highlight-rules"), SettingsSearch.find(registry, "ordered local")
                .getFirst().entries().stream().map(SettingsEntry::id).toList());
        assertEquals(List.of("consumable-highlights-enabled", "consumable-highlight-rules"),
                SettingsSearch.find(registry, "consumables").getFirst().entries().stream()
                        .map(SettingsEntry::id).toList());
    }

    private static BooleanSetting toggle(String id, String title) { return new BooleanSetting(id, text(title), text(title + " description")); }
    private static Component text(String value) { return Component.literal(value); }
}

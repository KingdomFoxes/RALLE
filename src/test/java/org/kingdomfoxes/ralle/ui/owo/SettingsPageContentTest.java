package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPageContentTest {
    @TempDir Path directory;

    @Test
    void samePageChildrenRevealWithoutLosingTheirSavedValues() {
        var registry = registry();
        var screenshots = subcategory(registry, "chat", "screenshots").entries();
        var child = registry.setting("chat-selection-sounds", BooleanSetting.class);
        child.set(true);

        assertEquals(List.of("chat-screenshot-enabled"), ids(SettingsPageContent.visibleEntries(registry, screenshots)));
        registry.setting("chat-screenshot-enabled", BooleanSetting.class).set(true);
        assertTrue(ids(SettingsPageContent.visibleEntries(registry, screenshots)).contains("chat-selection-sounds"));
        registry.setting("chat-screenshot-enabled", BooleanSetting.class).set(false);
        assertFalse(ids(SettingsPageContent.visibleEntries(registry, screenshots)).contains("chat-selection-sounds"));
        assertTrue(child.value());
    }

    @Test
    void crossPageRaidDependenciesProduceOneParentMessage() {
        var registry = registry();
        var notifications = subcategory(registry, "raid-lfg", "notifications").entries();

        assertEquals(List.of(), SettingsPageContent.visibleEntries(registry, notifications));
        assertEquals(List.of("ralle.settings.option.raid-lfg-enabled"),
                SettingsPageContent.unmetParentTitles(registry, notifications));
    }

    private SettingsRegistry registry() {
        var registry = new SettingsRegistry(directory.resolve("ralle.properties"));
        RalleSettings.register(registry);
        registry.seal();
        return registry;
    }

    private static org.kingdomfoxes.ralle.api.settings.SettingsSubcategory subcategory(
            SettingsRegistry registry, String categoryId, String subcategoryId
    ) {
        return registry.categories().stream().filter(category -> category.id().equals(categoryId)).findFirst().orElseThrow()
                .subcategories().stream().filter(subcategory -> subcategory.id().equals(subcategoryId)).findFirst().orElseThrow();
    }

    private static List<String> ids(List<org.kingdomfoxes.ralle.api.settings.SettingsEntry> entries) {
        return entries.stream().map(org.kingdomfoxes.ralle.api.settings.SettingsEntry::id).toList();
    }
}

package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsRegistryTest {
    @TempDir Path directory;

    @Test
    void preservesSubcategoryAndEntryRegistrationOrder() {
        var registry = registry();
        registry.registerCategory(category("chat",
                subcategory("general", toggle("enabled")),
                subcategory("appearance", toggle("shadow"), toggle("alignment"))));

        assertEquals(List.of("general", "appearance"), registry.categories().iterator().next()
                .subcategories().stream().map(SettingsSubcategory::id).toList());
        assertEquals(List.of("enabled", "shadow", "alignment"), registry.entries().stream().map(SettingsEntry::id).toList());
    }

    @Test
    void acceptsDirectEntriesAndRejectsCompletelyEmptyCategories() {
        var registry = registry();
        registry.registerCategory(new SettingsCategory("chat", text("chat"), text("chat description"),
                List.of(toggle("layout")), List.of()));

        assertEquals(List.of("layout"), registry.entries().stream().map(SettingsEntry::id).toList());
        assertThrows(IllegalArgumentException.class, () -> new SettingsCategory(
                "empty", text("empty"), text("empty description"), List.of(), List.of()
        ));
    }

    @Test
    void directEntriesParticipateInDuplicatesDependenciesAndPersistence() throws Exception {
        var path = directory.resolve("direct.properties");
        Files.writeString(path, "chat.enabled=true\n");
        var registry = new SettingsRegistry(path);
        registry.registerCategory(new SettingsCategory("chat", text("chat"), text("chat description"),
                List.of(toggle("enabled")), List.of(subcategory("appearance", toggle("shadow")))));
        registry.requireEnabled("shadow", "enabled");
        registry.seal();

        assertTrue(registry.setting("enabled", BooleanSetting.class).value());
        assertTrue(registry.available("shadow"));
        registry.setting("enabled", BooleanSetting.class).set(false);
        assertTrue(Files.readString(path).contains("chat.enabled=false"));
    }

    @Test
    void rejectsGloballyDuplicateEntryIds() {
        var registry = registry();
        registry.registerCategory(category("chat", subcategory("general", toggle("enabled"))));
        assertThrows(IllegalArgumentException.class, () -> registry.registerCategory(
                category("lfg", subcategory("general", toggle("enabled")))
        ));
    }

    @Test
    void rejectsDuplicatesAcrossDirectAndNestedEntries() {
        var registry = registry();
        assertThrows(IllegalArgumentException.class, () -> registry.registerCategory(new SettingsCategory(
                "chat", text("chat"), text("chat description"), List.of(toggle("enabled")),
                List.of(subcategory("appearance", toggle("enabled")))
        )));
    }

    @Test
    void validatesDependencyReferencesTypesAndCycles() {
        var unknown = registry();
        unknown.registerCategory(category("chat", subcategory("general", toggle("enabled"))));
        unknown.requireEnabled("enabled", "missing");
        assertThrows(IllegalArgumentException.class, unknown::seal);

        var cycle = registry();
        cycle.registerCategory(category("chat", subcategory("general", toggle("first"), toggle("second"))));
        cycle.requireEnabled("first", "second");
        cycle.requireEnabled("second", "first");
        assertThrows(IllegalArgumentException.class, cycle::seal);
    }

    @Test
    void reportsDirectAndTransitiveUnmetParents() {
        var registry = registry();
        registry.registerCategory(category("chat", subcategory("general",
                toggle("chat-enabled"), toggle("screenshots"), toggle("sounds"))));
        registry.requireEnabled("screenshots", "chat-enabled");
        registry.requireEnabled("sounds", "screenshots");
        registry.seal();

        assertFalse(registry.available("sounds"));
        assertEquals(List.of("screenshots", "chat-enabled"), registry.unmetDependencies("sounds").stream()
                .map(SettingsEntry::id).toList());
        registry.setting("screenshots", BooleanSetting.class).set(true);
        registry.setting("chat-enabled", BooleanSetting.class).set(true);
        assertTrue(registry.available("sounds"));
    }

    @Test
    void loadsAndSavesLegacyCategorySettingKeysAcrossSubcategories() throws Exception {
        var path = directory.resolve("ralle.properties");
        Files.writeString(path, "chat.compact-chat=true\n");
        var registry = new SettingsRegistry(path);
        registry.registerCategory(category("chat", subcategory("message-behavior", toggle("compact-chat"))));
        registry.seal();

        assertTrue(registry.setting("compact-chat", BooleanSetting.class).value());
        registry.setting("compact-chat", BooleanSetting.class).set(false);
        assertTrue(Files.readString(path).contains("chat.compact-chat=false"));
    }

    private SettingsRegistry registry() { return new SettingsRegistry(directory.resolve("settings.properties")); }
    private static BooleanSetting toggle(String id) { return new BooleanSetting(id, text(id), text(id + " description")); }
    private static SettingsSubcategory subcategory(String id, SettingsEntry... entries) {
        return new SettingsSubcategory(id, text(id), text(id + " description"), List.of(entries));
    }
    private static SettingsCategory category(String id, SettingsSubcategory... subcategories) {
        return new SettingsCategory(id, text(id), text(id + " description"), List.of(subcategories));
    }
    private static Component text(String value) { return Component.literal(value); }
}

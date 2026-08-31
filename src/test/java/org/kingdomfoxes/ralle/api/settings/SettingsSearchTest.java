package org.kingdomfoxes.ralle.api.settings;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsSearchTest {
    @TempDir Path directory;

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

    private static BooleanSetting toggle(String id, String title) { return new BooleanSetting(id, text(title), text(title + " description")); }
    private static Component text(String value) { return Component.literal(value); }
}

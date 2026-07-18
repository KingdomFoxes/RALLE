package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsNavigationStateTest {
    @TempDir Path directory;

    @Test
    void firstVisitUsesAboutAndLaterVisitsRestoreDocumentPosition() {
        var registry = registry();
        var path = directory.resolve("ralle-settings-ui.properties");
        var first = new SettingsNavigationState(path, registry);
        assertTrue(first.snapshot().about());

        first.showCategory("chat", "screenshots", .625);
        var restored = new SettingsNavigationState(path, registry).snapshot();
        assertFalse(restored.about());
        assertEquals("chat", restored.categoryId());
        assertEquals("screenshots", restored.subcategoryId());
        assertEquals(.625, restored.scrollProgress());
    }

    @Test
    void invalidPersistedNavigationFallsBackToAbout() throws Exception {
        var path = directory.resolve("ralle-settings-ui.properties");
        Files.writeString(path, "about=false\ncategory=removed\nsubcategory=missing\nscroll=2\n");
        assertTrue(new SettingsNavigationState(path, registry()).snapshot().about());
    }

    private SettingsRegistry registry() {
        var registry = new SettingsRegistry(directory.resolve("ralle.properties"));
        RalleSettings.register(registry);
        return registry;
    }
}

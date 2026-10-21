package org.kingdomfoxes.ralle.ui.theme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RalleThemeCatalogTest {
    @TempDir Path directory;

    @Test void catalogHasStableUniqueIdsAndExactSubmittedPaletteValues() {
        assertEquals(28, RalleThemeCatalog.themes().size());
        assertEquals(28, RalleThemeCatalog.ids().stream().distinct().count());
        assertEquals("default", RalleThemeCatalog.themes().getFirst().id());
        assertEquals(0x041330, RalleThemeCatalog.get("default").background());
        assertEquals(0xFFFFFF, RalleThemeCatalog.get("default").outline());
        assertEquals(0xF2B84B, RalleThemeCatalog.get("default").accent());
        var countx = RalleThemeCatalog.get("countx-sini");
        assertEquals("Nothesinistrtype", countx.contributor());
        assertEquals(0x6DDFFF, countx.background());
        assertEquals("_Hotchocolate", RalleThemeCatalog.get("hot-chocolate").contributor());
        var bob = RalleThemeCatalog.get("isothopicalbob");
        assertEquals("isothopicalbob", bob.name());
        assertEquals("maxkarson", bob.contributor());
        assertEquals(0xFFFFFF, bob.background());
        assertEquals(0xFF0054, bob.outline());
        assertEquals(0xFF0365, bob.accent());
        var emergency = RalleThemeCatalog.get("john-hell-emergency");
        assertEquals("John Hell Emergency", emergency.name());
        assertEquals("maxkarson", emergency.contributor());
        assertEquals(0xE7F90E, emergency.background());
        assertEquals(0xE61B02, emergency.outline());
        assertEquals(0xE61B02, emergency.accent());
        var hatred = RalleThemeCatalog.get("hatred");
        assertEquals("Hatred", hatred.name());
        assertEquals("maxkarson", hatred.contributor());
        assertEquals(0, hatred.background());
        assertEquals(0xFFFFFF, hatred.outline());
        assertEquals(0xFFFFFF, hatred.accent());
    }

    @Test void unknownIdsResolveSafelyToDefaultWithoutChangingStableIds() {
        assertEquals(RalleThemeCatalog.get("default"), RalleThemeCatalog.get("removed-theme"));
        assertFalse(RalleThemeCatalog.contains("removed-theme"));
    }

    @Test void ordinaryTextColorsAreOpaqueForMinecraftRenderers() {
        assertEquals(0xFFFFFFFF, RallePalette.surfaceText());
        assertEquals(0xFFFFFFFF, RallePalette.primaryText());
        assertEquals(0xFFA9B0BE, RallePalette.secondaryText());
    }

    @Test void onlyIsothopicalbobModifierMenusUseBlackTextWithoutChangingOtherPaletteRoles() {
        var settings = new SettingsRegistry(directory.resolve("settings.properties"));
        RalleSettings.register(settings);
        settings.seal();
        var choice = settings.setting(RalleSettings.UI_THEME_ID, ChoiceSetting.class);
        RallePalette.bind(settings);
        try {
            for (var theme : RalleThemeCatalog.themes()) {
                choice.set(theme.id());
                int expected = "isothopicalbob".equals(theme.id()) ? 0xFF000000 : 0xFFFFFFFF;
                assertEquals(expected, RallePalette.modifierText(), theme.id());
                assertEquals(0xFFFFFFFF, RallePalette.primaryText(), theme.id());
                assertEquals(0xFFFFFFFF, RallePalette.surfaceText(), theme.id());
                assertEquals(theme.background(), RallePalette.background());
                assertEquals(theme.outline(), RallePalette.outline());
                assertEquals(theme.accent(), RallePalette.accent());
                assertEquals("default".equals(theme.id()) ? 0xFFA9B0BE : 0xFFD8DEE6,
                        RallePalette.secondaryText());
            }
        } finally {
            choice.set(RalleThemeCatalog.DEFAULT_ID);
        }
    }
}

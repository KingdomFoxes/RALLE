package org.kingdomfoxes.ralle.ui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RalleThemeCatalogTest {
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
}

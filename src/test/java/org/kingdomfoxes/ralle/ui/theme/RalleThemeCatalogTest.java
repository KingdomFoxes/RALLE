package org.kingdomfoxes.ralle.ui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RalleThemeCatalogTest {
    @Test void catalogHasStableUniqueIdsAndExactSubmittedPaletteValues() {
        assertEquals(25, RalleThemeCatalog.themes().size());
        assertEquals(25, RalleThemeCatalog.ids().stream().distinct().count());
        assertEquals("default", RalleThemeCatalog.themes().getFirst().id());
        assertEquals(0x041330, RalleThemeCatalog.get("default").background());
        assertEquals(0xFFFFFF, RalleThemeCatalog.get("default").outline());
        assertEquals(0xF2B84B, RalleThemeCatalog.get("default").accent());
        var countx = RalleThemeCatalog.get("countx-sini");
        assertEquals("Nothesinistrtype", countx.contributor());
        assertEquals(0x6DDFFF, countx.background());
        assertEquals("_Hotchocolate", RalleThemeCatalog.get("hot-chocolate").contributor());
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

package org.kingdomfoxes.ralle.ui.theme;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeJsonExportTest {
    @Test void exportsEffectiveColorsWithCatalogMetadataAndStableFields() {
        var session = new ThemePreviewSession();
        session.setColor("joker", ThemePreviewSession.Role.BACKGROUND, 0x0000FF);
        var json = ThemeJsonExport.serialize(session.effectiveTheme("joker"));
        assertEquals("{\n  \"name\": \"Joker\",\n  \"author\": \"NeonRider\",\n  \"background\": \"#0000FF\",\n  \"outline\": \"#020202\",\n  \"accent\": \"#BB8856\"\n}", json);
    }

    @Test void defaultMetadataIsSpecialAndNamesAreEscaped() {
        var json = JsonParser.parseString(ThemeJsonExport.serialize(RalleThemeCatalog.get("default"))).getAsJsonObject();
        assertEquals("RALLE Default", json.get("name").getAsString());
        assertEquals("RALLE", json.get("author").getAsString());
        var custom = new RalleThemeCatalog.Theme("escaped", "Tom \"The Fox\"", "A\\B", 1, 2, 3);
        var parsed = JsonParser.parseString(ThemeJsonExport.serialize(custom)).getAsJsonObject();
        assertEquals(custom.name(), parsed.get("name").getAsString());
        assertEquals(custom.contributor(), parsed.get("author").getAsString());
    }

    @Test void hexInputRequiresOneCompleteRgbValue() {
        assertTrue(ThemeJsonExport.validHex("#Aa00fF"));
        assertFalse(ThemeJsonExport.validHex("#AA00"));
        assertFalse(ThemeJsonExport.validHex("AA00FF"));
        assertFalse(ThemeJsonExport.validHex("#GG00FF"));
        assertEquals(0xAA00FF, ThemeJsonExport.parseHex("#Aa00fF"));
    }
}

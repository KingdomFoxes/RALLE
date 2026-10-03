package org.kingdomfoxes.ralle.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SupporterSettingsTest {
    @TempDir Path directory;

    @Test void editedConfigCannotApplySupporterPreferencesWhileLocked() throws Exception {
        var path = directory.resolve("ralle.properties");
        Files.writeString(path, "cosmetics.show-own-nametag=true\ncosmetics.material-resolution=0.5\n");
        var allowed = new AtomicBoolean();
        var settings = registry(path, allowed);
        var ownName = settings.setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class);
        var resolution = settings.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class);

        assertFalse(ownName.value());
        assertEquals("1", resolution.value());
        assertEquals(1, CosmeticAppearance.from(settings,
                NameplateStyle.byId("supporter-gold").orElseThrow()).resolution());
        assertFalse(settings.available(RalleSettings.NAMEPLATE_COLOR_ID));
        assertTrue(settings.visible(RalleSettings.NAMEPLATE_COLOR_ID));
        assertTrue(settings.available("nameplate-preview"));
        // An unrelated save preserves preferences pending entitlement confirmation.
        settings.setting("chat-timestamps", BooleanSetting.class).set(true);
        assertTrue(Files.readString(path).contains("cosmetics.material-resolution=0.5"));
        allowed.set(true);
        assertTrue(ownName.value());
        assertEquals("0.5", resolution.value());
        assertTrue(settings.available(RalleSettings.NAMEPLATE_COLOR_ID));
        allowed.set(false);
        assertFalse(ownName.value());
        assertEquals("1", resolution.value());
    }

    @Test void directChangesAreRejectedWithoutUpdatingSavedValuesAndRemainRejectedAfterRevocation() {
        var allowed = new AtomicBoolean();
        var settings = registry(directory.resolve("ralle.properties"), allowed);
        var ownName = settings.setting(RalleSettings.SHOW_OWN_NAMETAG_ID, BooleanSetting.class);
        var resolution = settings.setting(RalleSettings.MATERIAL_RESOLUTION_ID, ChoiceSetting.class);
        ownName.set(true);
        resolution.set("2");
        assertEquals("false", ownName.serialize());
        assertEquals("1", resolution.serialize());
        allowed.set(true);
        ownName.set(true);
        resolution.set("0.5");
        allowed.set(false);
        ownName.set(false);
        resolution.set("2");
        assertFalse(ownName.value());
        assertEquals("1", resolution.value());
        assertEquals("true", ownName.serialize());
        assertEquals("0.5", resolution.serialize());
        assertFalse(settings.setting("chat-timestamps", BooleanSetting.class).value());
    }

    private SettingsRegistry registry(Path path, AtomicBoolean allowed) {
        var settings = new SettingsRegistry(path);
        RalleSettings.register(settings);
        RalleSettings.requireSupporterAccess(settings, allowed::get);
        settings.seal();
        return settings;
    }
}

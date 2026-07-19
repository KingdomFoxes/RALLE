package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RalleTypographyTest {
    @TempDir Path directory;

    @Test
    void boundResolverReflectsRuntimeFontChanges() {
        var registry = new SettingsRegistry(directory.resolve("ralle.properties"));
        RalleSettings.register(registry);
        registry.seal();
        RalleTypography.bind(registry);
        assertFalse(RalleTypography.usesKarla());

        registry.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class).set("karla");
        assertTrue(RalleTypography.usesKarla());
    }

    @Test
    void dropdownGlyphDoesNotInheritTheKarlaBodyFont() {
        var registry = new SettingsRegistry(directory.resolve("ralle-dropdown.properties"));
        RalleSettings.register(registry);
        registry.seal();
        RalleTypography.bind(registry);
        registry.setting(RalleSettings.INTERFACE_FONT_ID, ChoiceSetting.class).set("karla");

        var label = RalleTheme.dropdownLabel(Component.literal("Karla label"));

        assertEquals(2, label.getSiblings().size());
        assertNotNull(label.getSiblings().getFirst().getStyle().getFont());
        assertEquals(FontDescription.DEFAULT, label.getSiblings().getLast().getStyle().getFont());
        assertEquals(" ▾", label.getSiblings().getLast().getString());
    }
}

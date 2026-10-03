package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import static org.junit.jupiter.api.Assertions.*;

class FoxExclusiveSettingTitleTest {
    @Test
    void onlyRestrictedSettingNamesCarryAccentSuffixWithoutChangingRegistryTitles() {
        for (var id : RalleSettings.FOX_EXCLUSIVE_IDS) {
            var setting = new BooleanSetting(id, Component.literal("Setting"), Component.empty());
            var title = RalleSettingsScreen.titleForDisplay(setting);
            assertEquals("Setting ralle.settings.fox-exclusive", title.getString());
            var suffix = title.getSiblings().getLast().getSiblings().getFirst();
            assertEquals(RalleTheme.accentRgb(), suffix.getStyle().getColor().getValue());
            assertEquals("Setting", setting.title().getString());
        }
        var ordinary = new BooleanSetting("chat-timestamps", Component.literal("Timestamps"), Component.empty());
        assertEquals("Timestamps", RalleSettingsScreen.titleForDisplay(ordinary).getString());
    }
}

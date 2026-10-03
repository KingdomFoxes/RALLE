package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LfgKeybindHintsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void boundActionsAppendTheirDisplayKeyAndUnboundActionsDoNot() {
        var settings = settings();
        var hints = new LfgKeybindHints(settings);

        assertEquals("Join [F2]", hints.joinLabel("Join"));
        settings.setting(RaidLfgKeybinds.JOIN_ID, KeybindSetting.class).set(KeybindSetting.UNBOUND);
        assertEquals("Join", hints.joinLabel("Join"));
        settings.setting(RaidLfgKeybinds.JOIN_ID, KeybindSetting.class)
                .set("key.keyboard.f2");
        assertEquals("Join [F2]", hints.joinLabel("Join"));
        settings.setting(RaidLfgKeybinds.JOIN_ID, KeybindSetting.class)
                .set("key.keyboard.equal");
        assertEquals("Join [=]", hints.joinLabel("Join"));
        settings.setting(RaidLfgKeybinds.JOIN_ID, KeybindSetting.class)
                .set(KeybindSetting.UNBOUND);
        settings.setting(RaidLfgKeybinds.PARTY_FILLED_ID, KeybindSetting.class)
                .set("key.keyboard.equal");
        assertEquals("Party filled [=]", hints.partyFilledLabel("Party filled"));
    }

    @Test
    void conflictingRalleBindingsHideUnusableHints() {
        var settings = settings();
        var hints = new LfgKeybindHints(settings);
        settings.setting(RaidLfgKeybinds.JOIN_ID, KeybindSetting.class)
                .set("key.keyboard.g");
        settings.setting(RaidLfgKeybinds.CLOSE_ID, KeybindSetting.class)
                .set("key.keyboard.g");

        assertEquals("Join", hints.joinLabel("Join"));
    }

    private SettingsRegistry settings() {
        var settings = new SettingsRegistry(temporaryDirectory.resolve("ralle.properties"));
        RalleSettings.register(settings);
        return settings;
    }
}

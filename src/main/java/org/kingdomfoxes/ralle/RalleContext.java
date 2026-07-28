package org.kingdomfoxes.ralle;

import org.kingdomfoxes.ralle.api.feature.FeatureRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotService;
import org.kingdomfoxes.ralle.lfg.client.HostPartyInviteController;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

public record RalleContext(
        FeatureRegistry features,
        SettingsRegistry settings,
        SettingsScreenFactory settingsScreens,
        ChatLayoutService chatLayout,
        ChatBehaviorService chatBehavior,
        ChatScreenshotService chatScreenshots,
        RaidLfgService raidLfg,
        HostPartyInviteController hostPartyInvites,
        LfgSoundPlayer lfgSounds
) {
}

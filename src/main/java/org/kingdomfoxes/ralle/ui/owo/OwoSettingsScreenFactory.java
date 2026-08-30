package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.screens.Screen;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.rank.GuildRankService;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;

import java.util.Objects;

public final class OwoSettingsScreenFactory implements SettingsScreenFactory {
    private final SettingsRegistry settings;
    private final ChatLayoutService chatLayout;
    private final SettingsNavigationState navigation;
    private final GuildRankService guildRanks;

    public OwoSettingsScreenFactory(
            SettingsRegistry settings,
            ChatLayoutService chatLayout,
            SettingsNavigationState navigation,
            GuildRankService guildRanks
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chatLayout = Objects.requireNonNull(chatLayout, "chatLayout");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.guildRanks = Objects.requireNonNull(guildRanks, "guildRanks");
    }

    @Override
    public Screen create(Screen parent) {
        return new RalleSettingsScreen(parent, settings, chatLayout, navigation, guildRanks);
    }
}

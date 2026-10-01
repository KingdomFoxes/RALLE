package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.screens.Screen;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;
import org.kingdomfoxes.ralle.chat.rank.GuildRankService;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.SettingsScreenFactory;
import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelRegistry;
import org.kingdomfoxes.ralle.cosmetics.NameplateDirectorySession;
import org.kingdomfoxes.ralle.cosmetics.CosmeticStyleSelection;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;
import io.wispforest.owo.ui.core.UIComponent;

import java.util.Objects;

public final class OwoSettingsScreenFactory implements SettingsScreenFactory {
    private final SettingsRegistry settings;
    private final ChatLayoutService chatLayout;
    private final SettingsNavigationState navigation;
    private final GuildRankService guildRanks;
    private final ConsumableHighlightStore consumableHighlights;
    private final NameplateDirectorySession cosmetics;
    private final CosmeticStyleSelection cosmeticStyleSelection;

    public OwoSettingsScreenFactory(
            SettingsRegistry settings,
            ChatLayoutService chatLayout,
            SettingsNavigationState navigation,
            GuildRankService guildRanks,
            ConsumableHighlightStore consumableHighlights,
            NameplateDirectorySession cosmetics,
            CosmeticStyleSelection cosmeticStyleSelection
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.chatLayout = Objects.requireNonNull(chatLayout, "chatLayout");
        this.navigation = Objects.requireNonNull(navigation, "navigation");
        this.guildRanks = Objects.requireNonNull(guildRanks, "guildRanks");
        this.consumableHighlights = Objects.requireNonNull(consumableHighlights, "consumableHighlights");
        this.cosmetics = Objects.requireNonNull(cosmetics, "cosmetics");
        this.cosmeticStyleSelection = Objects.requireNonNull(cosmeticStyleSelection, "cosmeticStyleSelection");
    }

    @Override
    public Screen create(Screen parent) {
        var panels = new CustomSettingsPanelRegistry<OwoCustomSettingsPanelContext, UIComponent>();
        panels.register(new ConsumableHighlightsPanelProvider(consumableHighlights));
        panels.register(new NameplatePreviewPanelProvider());
        return new RalleSettingsScreen(parent, settings, chatLayout, navigation, guildRanks, panels,
                cosmetics, cosmeticStyleSelection);
    }
}

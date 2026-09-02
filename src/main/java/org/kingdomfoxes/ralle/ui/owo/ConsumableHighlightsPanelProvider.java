package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelProvider;
import org.kingdomfoxes.ralle.api.settings.SettingsEntry;
import io.wispforest.owo.ui.core.UIComponent;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** owo adapter for the metadata-only consumable editor settings entry. */
public final class ConsumableHighlightsPanelProvider
        implements CustomSettingsPanelProvider<OwoCustomSettingsPanelContext, UIComponent> {
    private final ConsumableHighlightStore store;
    private final Set<Integer> expanded = new HashSet<>();
    private volatile ConsumableHighlightsPanel current;

    public ConsumableHighlightsPanelProvider(ConsumableHighlightStore store) {
        this.store = Objects.requireNonNull(store, "store");
        store.onChanged(ignored -> Minecraft.getInstance().schedule(() -> {
            var panel = current;
            if (panel != null) panel.rebuild();
        }));
    }

    @Override public String id() { return RalleSettings.CONSUMABLE_HIGHLIGHT_PROVIDER_ID; }

    @Override public UIComponent render(OwoCustomSettingsPanelContext context) {
        current = new ConsumableHighlightsPanel(context.screen(), store, expanded);
        return current;
    }
}

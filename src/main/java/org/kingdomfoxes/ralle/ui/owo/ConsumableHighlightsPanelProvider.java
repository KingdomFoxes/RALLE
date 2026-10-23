package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelProvider;
import io.wispforest.owo.ui.core.UIComponent;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/** owo adapter for the metadata-only consumable editor settings entry. */
public final class ConsumableHighlightsPanelProvider
        implements CustomSettingsPanelProvider<OwoCustomSettingsPanelContext, UIComponent> {
    private final ConsumableHighlightStore store;
    private final Set<Integer> expanded = new HashSet<>();

    public ConsumableHighlightsPanelProvider(ConsumableHighlightStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override public String id() { return RalleSettings.CONSUMABLE_HIGHLIGHT_PROVIDER_ID; }

    @Override public UIComponent render(OwoCustomSettingsPanelContext context) {
        return new ConsumableHighlightsPanel(context.screen(), store, expanded);
    }
}

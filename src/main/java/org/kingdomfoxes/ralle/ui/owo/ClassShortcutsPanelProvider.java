package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.CustomSettingsPanelProvider;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.classes.ClassSelectorStore;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import io.wispforest.owo.ui.core.UIComponent;

/** Keeps the toolkit out of character discovery and persistence. */
public final class ClassShortcutsPanelProvider implements CustomSettingsPanelProvider<OwoCustomSettingsPanelContext, UIComponent> {
    private final ClassSelectorStore store;
    private final SettingsRegistry settings;

    public ClassShortcutsPanelProvider(ClassSelectorStore store, SettingsRegistry settings) {
        this.store = store;
        this.settings = settings;
    }
    @Override public String id() { return RalleSettings.CLASS_SHORTCUTS_ID; }
    @Override public UIComponent render(OwoCustomSettingsPanelContext context) {
        return new ClassShortcutsPanel(context.screen(), store, settings);
    }
}

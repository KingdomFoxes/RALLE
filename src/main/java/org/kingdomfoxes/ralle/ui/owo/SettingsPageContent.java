package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.SettingsEntry;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.List;

/** Pure dependency-driven projection for one settings page. */
final class SettingsPageContent {
    private SettingsPageContent() {}

    static List<SettingsEntry> visibleEntries(SettingsRegistry registry, List<SettingsEntry> entries) {
        return entries.stream().filter(entry -> registry.visible(entry.id())).toList();
    }

    static List<String> unmetParentTitles(SettingsRegistry registry, List<SettingsEntry> entries) {
        return entries.stream().flatMap(entry -> registry.unmetDependencies(entry.id()).stream())
                .map(setting -> setting.title().getString()).distinct().toList();
    }
}

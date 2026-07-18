package org.kingdomfoxes.ralle.api.settings;

import java.util.List;
import java.util.Locale;

/** Pure ordered search projection used by the settings screen and tests. */
public final class SettingsSearch {
    private SettingsSearch() {}

    public static List<ResultGroup> find(SettingsRegistry registry, String rawQuery) {
        var query = rawQuery.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        return registry.categories().stream().flatMap(category -> {
            boolean categoryMatches = matches(category.id(), category.title().getString(), category.description().getString(), query);
            return category.subcategories().stream().map(subcategory -> {
                boolean groupMatches = categoryMatches || matches(
                        subcategory.id(), subcategory.title().getString(), subcategory.description().getString(), query
                );
                var entries = subcategory.entries().stream()
                        .filter(entry -> groupMatches || matches(
                                entry.id(), entry.title().getString(), entry.description().getString(), query
                        ))
                        .toList();
                return new ResultGroup(category, subcategory, entries);
            });
        }).filter(group -> !group.entries().isEmpty()).toList();
    }

    private static boolean matches(String id, String title, String description, String query) {
        return id.toLowerCase(Locale.ROOT).contains(query)
                || title.toLowerCase(Locale.ROOT).contains(query)
                || description.toLowerCase(Locale.ROOT).contains(query);
    }

    public record ResultGroup(
            SettingsCategory category,
            SettingsSubcategory subcategory,
            List<SettingsEntry> entries
    ) {}
}

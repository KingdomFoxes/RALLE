package org.kingdomfoxes.ralle.api.settings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SettingsRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsRegistry.class);
    private final Map<String, SettingsCategory> categories = new LinkedHashMap<>();
    private final Path storagePath;
    private boolean sealed;

    public SettingsRegistry(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
    }

    public void registerCategory(SettingsCategory category) {
        requireOpen();
        Objects.requireNonNull(category, "category");
        if (categories.containsKey(category.id())) {
            throw new IllegalArgumentException("Duplicate settings category id: " + category.id());
        }

        var settingIds = new HashSet<String>();
        categories.values().stream()
                .flatMap(registeredCategory -> registeredCategory.entries().stream())
                .map(SettingsEntry::id)
                .forEach(settingIds::add);
        for (var entry : category.entries()) {
            if (!settingIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate settings entry id: " + entry.id());
            }
        }

        categories.put(category.id(), category);
    }

    public Collection<SettingsCategory> categories() {
        return List.copyOf(categories.values());
    }

    public <S extends Setting<?>> S setting(String id, Class<S> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");

        return categories.values().stream()
                .flatMap(category -> category.entries().stream())
                .filter(Setting.class::isInstance)
                .map(entry -> (Setting<?>) entry)
                .filter(setting -> setting.id().equals(id))
                .findFirst()
                .map(setting -> {
                    if (!type.isInstance(setting)) {
                        throw new IllegalArgumentException("Setting " + id + " is not a " + type.getSimpleName());
                    }
                    return type.cast(setting);
                })
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + id));
    }

    public void seal() {
        load();
        categories.values().stream()
                .flatMap(category -> category.entries().stream())
                .filter(Setting.class::isInstance)
                .map(entry -> (Setting<?>) entry)
                .forEach(setting -> setting.onChanged(ignored -> save()));
        sealed = true;
    }

    private void load() {
        if (!Files.exists(storagePath)) return;

        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read RALLE settings from {}. Defaults will be used", storagePath, exception);
            return;
        }

        for (var category : categories.values()) {
            for (var entry : category.entries()) {
                if (!(entry instanceof Setting<?> setting)) continue;
                var value = properties.getProperty(category.id() + "." + setting.id());
                if (value == null) continue;
                try {
                    setting.load(value);
                } catch (IllegalArgumentException exception) {
                    // Invalid or obsolete values safely fall back to the declared default.
                    LOGGER.warn("Ignoring invalid RALLE setting {}.{}={}", category.id(), setting.id(), value);
                }
            }
        }
    }

    private void save() {
        var properties = new Properties();
        for (var category : categories.values()) {
            for (var entry : category.entries()) {
                if (!(entry instanceof Setting<?> setting)) continue;
                properties.setProperty(category.id() + "." + setting.id(), setting.serialize());
            }
        }

        try {
            Files.createDirectories(storagePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                properties.store(writer, "RALLE local settings");
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save RALLE settings to {}", storagePath, exception);
        }
    }

    private void requireOpen() {
        if (sealed) throw new IllegalStateException("Settings registration is sealed");
    }
}

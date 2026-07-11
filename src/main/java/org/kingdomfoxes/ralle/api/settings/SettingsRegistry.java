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
                .flatMap(registeredCategory -> registeredCategory.settings().stream())
                .map(Setting::id)
                .forEach(settingIds::add);
        for (var setting : category.settings()) {
            if (!settingIds.add(setting.id())) {
                throw new IllegalArgumentException("Duplicate setting id: " + setting.id());
            }
        }

        categories.put(category.id(), category);
    }

    public Collection<SettingsCategory> categories() {
        return List.copyOf(categories.values());
    }

    public void seal() {
        load();
        categories.values().stream()
                .flatMap(category -> category.settings().stream())
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
            for (var setting : category.settings()) {
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
            for (var setting : category.settings()) {
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

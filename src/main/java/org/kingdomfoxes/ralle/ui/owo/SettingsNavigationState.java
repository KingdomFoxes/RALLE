package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/** Persists settings-screen navigation separately from feature settings. */
public final class SettingsNavigationState {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsNavigationState.class);
    private final Path path;
    private Snapshot snapshot;

    public SettingsNavigationState(Path path, SettingsRegistry registry) {
        this.path = Objects.requireNonNull(path, "path");
        this.snapshot = load(Objects.requireNonNull(registry, "registry"));
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void showAbout() {
        snapshot = Snapshot.aboutPage();
        save();
    }

    public void showCategory(String categoryId, String subcategoryId, double scrollProgress) {
        snapshot = new Snapshot(false, categoryId, subcategoryId, clampProgress(scrollProgress));
        save();
    }

    private Snapshot load(SettingsRegistry registry) {
        if (!Files.exists(path)) return Snapshot.aboutPage();
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read RALLE settings navigation from {}", path, exception);
            return Snapshot.aboutPage();
        }
        if (Boolean.parseBoolean(properties.getProperty("about", "true"))) return Snapshot.aboutPage();

        var categoryId = properties.getProperty("category", "");
        var subcategoryId = properties.getProperty("subcategory", "");
        var category = registry.categories().stream().filter(candidate -> candidate.id().equals(categoryId)).findFirst();
        if (category.isEmpty() || category.get().subcategories().stream().noneMatch(value -> value.id().equals(subcategoryId))) {
            return Snapshot.aboutPage();
        }
        double progress;
        try {
            progress = Double.parseDouble(properties.getProperty("scroll", "0"));
        } catch (NumberFormatException ignored) {
            progress = 0;
        }
        return new Snapshot(false, categoryId, subcategoryId, clampProgress(progress));
    }

    private void save() {
        var properties = new Properties();
        properties.setProperty("about", Boolean.toString(snapshot.about()));
        if (!snapshot.about()) {
            properties.setProperty("category", snapshot.categoryId());
            properties.setProperty("subcategory", snapshot.subcategoryId());
            properties.setProperty("scroll", Double.toString(snapshot.scrollProgress()));
        }
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "RALLE settings screen navigation");
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not save RALLE settings navigation to {}", path, exception);
        }
    }

    private static double clampProgress(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public record Snapshot(boolean about, String categoryId, String subcategoryId, double scrollProgress) {
        public Snapshot {
            if (!about) {
                Objects.requireNonNull(categoryId, "categoryId");
                Objects.requireNonNull(subcategoryId, "subcategoryId");
            }
        }

        public static Snapshot aboutPage() { return new Snapshot(true, null, null, 0); }
    }
}

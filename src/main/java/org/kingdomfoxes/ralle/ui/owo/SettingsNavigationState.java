package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.function.LongSupplier;

/** Persists settings-screen navigation separately from feature settings. */
public final class SettingsNavigationState {
    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsNavigationState.class);
    private final Path path;
    static final long SAVE_IDLE_MILLIS = 300;
    private final LongSupplier clock;
    private Snapshot snapshot;
    private Snapshot saved;
    private long changedAt;

    public SettingsNavigationState(Path path, SettingsRegistry registry) {
        this(path, registry, () -> System.nanoTime() / 1_000_000L);
    }

    SettingsNavigationState(Path path, SettingsRegistry registry, LongSupplier clock) {
        this.path = Objects.requireNonNull(path, "path");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.snapshot = load(Objects.requireNonNull(registry, "registry"));
        this.saved = snapshot;
    }

    public Snapshot snapshot() { return snapshot; }

    public void showAbout() {
        remember(Snapshot.aboutPage());
        flush();
    }

    public void showCategory(String categoryId, double scrollProgress) {
        remember(Snapshot.categoryPage(categoryId, scrollProgress));
        flush();
    }

    public void showSubcategory(String categoryId, String subcategoryId, double scrollProgress) {
        remember(Snapshot.subcategoryPage(categoryId, subcategoryId, scrollProgress));
        flush();
    }

    /** Scroll tracking updates memory immediately, including subcategory tracking. */
    public void remember(Snapshot next) {
        Objects.requireNonNull(next, "next");
        if (snapshot.equals(next)) return;
        snapshot = next;
        changedAt = clock.getAsLong();
    }

    public void flushIfIdle() {
        if (clock.getAsLong() - changedAt >= SAVE_IDLE_MILLIS) flush();
    }

    /** Also called on removal, including opening the HUD editor or disconnecting. */
    public void flush() {
        if (snapshot.equals(saved)) return;
        // Back off after an I/O failure instead of retrying every client tick.
        changedAt = clock.getAsLong();
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

        var categoryId = properties.getProperty("category", "");
        var subcategoryId = properties.getProperty("subcategory", "");
        var category = registry.categories().stream().filter(candidate -> candidate.id().equals(categoryId)).findFirst();
        double progress = parseProgress(properties.getProperty("scroll", "0"));

        var pageValue = properties.getProperty("page");
        if (pageValue == null) {
            if (Boolean.parseBoolean(properties.getProperty("about", "true"))) return Snapshot.aboutPage();
            return validSubcategory(category, subcategoryId)
                    ? Snapshot.subcategoryPage(categoryId, subcategoryId, progress)
                    : Snapshot.aboutPage();
        }

        final Page page;
        try {
            page = Page.valueOf(pageValue.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Snapshot.aboutPage();
        }
        return switch (page) {
            case ABOUT -> Snapshot.aboutPage();
            case CATEGORY -> category.isPresent() && !"about".equals(categoryId)
                    ? Snapshot.categoryPage(categoryId, progress) : Snapshot.aboutPage();
            case SUBCATEGORY -> validSubcategory(category, subcategoryId)
                    ? Snapshot.subcategoryPage(categoryId, subcategoryId, progress) : Snapshot.aboutPage();
        };
    }

    private static boolean validSubcategory(java.util.Optional<SettingsCategory> category, String subcategoryId) {
        return category.isPresent() && !"about".equals(category.get().id())
                && category.get().subcategories().stream().anyMatch(value -> value.id().equals(subcategoryId));
    }

    private static double parseProgress(String value) {
        try {
            return clampProgress(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void save() {
        var properties = new Properties();
        properties.setProperty("page", snapshot.page().name().toLowerCase(java.util.Locale.ROOT));
        properties.setProperty("about", Boolean.toString(snapshot.about()));
        if (!snapshot.about()) {
            properties.setProperty("category", snapshot.categoryId());
            if (snapshot.subcategoryId() != null) properties.setProperty("subcategory", snapshot.subcategoryId());
            properties.setProperty("scroll", Double.toString(snapshot.scrollProgress()));
        }
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "RALLE settings screen navigation");
            }
            saved = snapshot;
        } catch (IOException exception) {
            LOGGER.warn("Could not save RALLE settings navigation to {}", path, exception);
        }
    }

    private static double clampProgress(double value) {
        return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0;
    }

    public enum Page { ABOUT, CATEGORY, SUBCATEGORY }

    public record Snapshot(Page page, String categoryId, String subcategoryId, double scrollProgress) {
        public Snapshot {
            Objects.requireNonNull(page, "page");
            if (page != Page.ABOUT) Objects.requireNonNull(categoryId, "categoryId");
            if (page == Page.SUBCATEGORY) Objects.requireNonNull(subcategoryId, "subcategoryId");
            scrollProgress = clampProgress(scrollProgress);
        }

        public boolean about() { return page == Page.ABOUT; }
        public boolean categoryPage() { return page == Page.CATEGORY; }
        public boolean subcategoryPage() { return page == Page.SUBCATEGORY; }
        public static Snapshot aboutPage() { return new Snapshot(Page.ABOUT, null, null, 0); }
        public static Snapshot categoryPage(String categoryId, double progress) {
            return new Snapshot(Page.CATEGORY, categoryId, null, progress);
        }
        public static Snapshot subcategoryPage(String categoryId, String subcategoryId, double progress) {
            return new Snapshot(Page.SUBCATEGORY, categoryId, subcategoryId, progress);
        }
    }
}

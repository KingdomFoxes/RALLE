package org.kingdomfoxes.ralle.api.hud;

import org.kingdomfoxes.ralle.api.feature.IdentifierRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class HudPlacementRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(HudPlacementRegistry.class);

    private final Path storagePath;
    private final Map<String, ElementDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, NormalizedBounds> customBounds = new LinkedHashMap<>();
    private boolean sealed;

    public HudPlacementRegistry(Path storagePath) {
        this.storagePath = Objects.requireNonNull(storagePath, "storagePath");
    }

    public void register(ElementDefinition definition) {
        if (sealed) throw new IllegalStateException("HUD placement registration is sealed");
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate HUD element id: " + definition.id());
        }
    }

    public void seal() {
        load();
        sealed = true;
    }

    public ElementDefinition definition(String id) {
        return Optional.ofNullable(definitions.get(id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown HUD element: " + id));
    }

    public Optional<NormalizedBounds> customBounds(String id) {
        definition(id);
        return Optional.ofNullable(customBounds.get(id));
    }

    public Optional<Rectangle> resolveCustom(String id, int viewportWidth, int viewportHeight) {
        var definition = definition(id);
        return customBounds(id).map(bounds -> bounds.toPixels(viewportWidth, viewportHeight, definition));
    }

    public Rectangle resolve(String id, int viewportWidth, int viewportHeight, Rectangle fallback) {
        var definition = definition(id);
        return resolveCustom(id, viewportWidth, viewportHeight)
                .orElseGet(() -> fallback.clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight()));
    }

    public void setPixels(String id, Rectangle rectangle, int viewportWidth, int viewportHeight) {
        var definition = definition(id);
        var clamped = rectangle.clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight());
        customBounds.put(id, NormalizedBounds.fromPixels(clamped, viewportWidth, viewportHeight));
        save();
    }

    public void reset(String id) {
        definition(id);
        if (customBounds.remove(id) != null) save();
    }

    private void load() {
        if (!Files.exists(storagePath)) return;

        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(storagePath)) {
            properties.load(reader);
        } catch (IOException exception) {
            LOGGER.warn("Could not read RALLE HUD layout from {}. Vanilla placements will be used", storagePath, exception);
            return;
        }

        for (var definition : definitions.values()) {
            var prefix = definition.id() + ".";
            if (!properties.containsKey(prefix + "x")
                    && !properties.containsKey(prefix + "y")
                    && !properties.containsKey(prefix + "width")
                    && !properties.containsKey(prefix + "height")) {
                continue;
            }
            try {
                customBounds.put(definition.id(), new NormalizedBounds(
                        requireProperty(properties, prefix + "x"),
                        requireProperty(properties, prefix + "y"),
                        requireProperty(properties, prefix + "width"),
                        requireProperty(properties, prefix + "height")
                ));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring invalid RALLE HUD layout for {}", definition.id());
            }
        }
    }

    private double requireProperty(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Missing property " + key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number for " + key, exception);
        }
    }

    private void save() {
        var properties = new Properties();
        for (var entry : customBounds.entrySet()) {
            var prefix = entry.getKey() + ".";
            var bounds = entry.getValue();
            properties.setProperty(prefix + "x", Double.toString(bounds.x()));
            properties.setProperty(prefix + "y", Double.toString(bounds.y()));
            properties.setProperty(prefix + "width", Double.toString(bounds.width()));
            properties.setProperty(prefix + "height", Double.toString(bounds.height()));
        }

        try {
            Files.createDirectories(storagePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                properties.store(writer, "RALLE normalized HUD placements");
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save RALLE HUD layout to {}", storagePath, exception);
        }
    }

    public record ElementDefinition(String id, int minimumWidth, int minimumHeight) {
        public ElementDefinition {
            id = IdentifierRules.requireValid(id, "HUD element id");
            if (minimumWidth < 1 || minimumHeight < 1) {
                throw new IllegalArgumentException("HUD element minimum dimensions must be positive");
            }
        }
    }

    public record Rectangle(int x, int y, int width, int height) {
        public Rectangle {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("HUD rectangle dimensions must be positive");
            }
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        public Rectangle clampTo(int viewportWidth, int viewportHeight, int minimumWidth, int minimumHeight) {
            requireViewport(viewportWidth, viewportHeight);
            var clampedMinimumWidth = Math.min(minimumWidth, viewportWidth);
            var clampedMinimumHeight = Math.min(minimumHeight, viewportHeight);
            var clampedWidth = Math.clamp(width, clampedMinimumWidth, viewportWidth);
            var clampedHeight = Math.clamp(height, clampedMinimumHeight, viewportHeight);
            var clampedX = Math.clamp(x, 0, viewportWidth - clampedWidth);
            var clampedY = Math.clamp(y, 0, viewportHeight - clampedHeight);
            return new Rectangle(clampedX, clampedY, clampedWidth, clampedHeight);
        }
    }

    public record NormalizedBounds(double x, double y, double width, double height) {
        public NormalizedBounds {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requireFinite(width, "width");
            requireFinite(height, "height");
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Normalized HUD dimensions must be positive");
            }
        }

        public static NormalizedBounds fromPixels(Rectangle rectangle, int viewportWidth, int viewportHeight) {
            requireViewport(viewportWidth, viewportHeight);
            return new NormalizedBounds(
                    rectangle.x() / (double) viewportWidth,
                    rectangle.y() / (double) viewportHeight,
                    rectangle.width() / (double) viewportWidth,
                    rectangle.height() / (double) viewportHeight
            );
        }

        public Rectangle toPixels(int viewportWidth, int viewportHeight, ElementDefinition definition) {
            requireViewport(viewportWidth, viewportHeight);
            var rectangle = new Rectangle(
                    (int) Math.round(x * viewportWidth),
                    (int) Math.round(y * viewportHeight),
                    Math.max(1, (int) Math.round(width * viewportWidth)),
                    Math.max(1, (int) Math.round(height * viewportHeight))
            );
            return rectangle.clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight());
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireViewport(int viewportWidth, int viewportHeight) {
        if (viewportWidth < 1 || viewportHeight < 1) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
    }
}

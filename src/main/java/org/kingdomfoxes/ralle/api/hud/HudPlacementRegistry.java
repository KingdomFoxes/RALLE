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
    private final Map<String, SidePlacement> sidePlacements = new LinkedHashMap<>();
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
        var definition = definition(id);
        if (definition.placementPolicy() != PlacementPolicy.RESIZABLE_RECTANGLE) return Optional.empty();
        return Optional.ofNullable(customBounds.get(id));
    }

    public Optional<Rectangle> resolveCustom(String id, int viewportWidth, int viewportHeight) {
        var definition = definition(id);
        if (definition.placementPolicy() == PlacementPolicy.FIXED_SIDE_ANCHORED) {
            return Optional.ofNullable(sidePlacements.get(id))
                    .map(placement -> placement.toPixels(viewportWidth, viewportHeight, definition));
        }
        return customBounds(id).map(bounds -> bounds.toPixels(viewportWidth, viewportHeight, definition));
    }

    public Map<String, Rectangle> resolveAllCustom(int viewportWidth, int viewportHeight) {
        var resolved = new LinkedHashMap<String, Rectangle>();
        for (var definition : definitions.values()) {
            resolveCustom(definition.id(), viewportWidth, viewportHeight)
                    .ifPresent(bounds -> resolved.put(definition.id(), bounds));
        }
        return Map.copyOf(resolved);
    }

    public Rectangle resolve(String id, int viewportWidth, int viewportHeight, Rectangle fallback) {
        var definition = definition(id);
        return resolveCustom(id, viewportWidth, viewportHeight)
                .orElseGet(() -> fallback.clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight()));
    }

    public void setPixels(String id, Rectangle rectangle, int viewportWidth, int viewportHeight) {
        var definition = definition(id);
        if (definition.placementPolicy() == PlacementPolicy.FIXED_SIDE_ANCHORED) {
            var fixed = new Rectangle(
                    rectangle.x(),
                    rectangle.y(),
                    definition.minimumWidth(),
                    definition.minimumHeight()
            ).clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight());
            var anchor = fixed.x() + fixed.width() / 2 < viewportWidth / 2
                    ? SideAnchor.LEFT : SideAnchor.RIGHT;
            sidePlacements.put(id, SidePlacement.fromPixels(anchor, fixed.y(), viewportHeight, fixed.height()));
            customBounds.remove(id);
            save();
            return;
        }
        var clamped = rectangle.clampTo(viewportWidth, viewportHeight, definition.minimumWidth(), definition.minimumHeight());
        customBounds.put(id, NormalizedBounds.fromPixels(clamped, viewportWidth, viewportHeight));
        sidePlacements.remove(id);
        save();
    }

    public Rectangle resolveSideAnchored(
            String id,
            int viewportWidth,
            int viewportHeight,
            SideAnchor fallbackAnchor,
            int fallbackTop
    ) {
        var definition = definition(id);
        if (definition.placementPolicy() != PlacementPolicy.FIXED_SIDE_ANCHORED) {
            throw new IllegalArgumentException("HUD element is not side-anchored: " + id);
        }
        return Optional.ofNullable(sidePlacements.get(id))
                .orElseGet(() -> SidePlacement.fromPixels(
                        fallbackAnchor, fallbackTop, viewportHeight, definition.minimumHeight()))
                .toPixels(viewportWidth, viewportHeight, definition);
    }

    public boolean hasCustomPlacement(String id) {
        var definition = definition(id);
        return definition.placementPolicy() == PlacementPolicy.FIXED_SIDE_ANCHORED
                ? sidePlacements.containsKey(id)
                : customBounds.containsKey(id);
    }

    public void reset(String id) {
        definition(id);
        boolean changed = customBounds.remove(id) != null;
        changed |= sidePlacements.remove(id) != null;
        if (changed) save();
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
            if (definition.placementPolicy() == PlacementPolicy.FIXED_SIDE_ANCHORED
                    && (properties.containsKey(prefix + "anchor") || properties.containsKey(prefix + "vertical"))) {
                try {
                    sidePlacements.put(definition.id(), new SidePlacement(
                            SideAnchor.valueOf(properties.getProperty(prefix + "anchor")),
                            requireProperty(properties, prefix + "vertical")
                    ));
                } catch (IllegalArgumentException exception) {
                    LOGGER.warn("Ignoring invalid side-anchored RALLE HUD layout for {}", definition.id());
                }
                continue;
            }
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
        for (var entry : sidePlacements.entrySet()) {
            var prefix = entry.getKey() + ".";
            properties.setProperty(prefix + "anchor", entry.getValue().anchor().name());
            properties.setProperty(prefix + "vertical", Double.toString(entry.getValue().normalizedTop()));
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

    public enum PlacementPolicy {
        RESIZABLE_RECTANGLE,
        FIXED_SIDE_ANCHORED
    }

    public enum SideAnchor {
        LEFT,
        RIGHT
    }

    public record ElementDefinition(String id, int minimumWidth, int minimumHeight,
                                    PlacementPolicy placementPolicy) {
        public ElementDefinition(String id, int minimumWidth, int minimumHeight) {
            this(id, minimumWidth, minimumHeight, PlacementPolicy.RESIZABLE_RECTANGLE);
        }

        public ElementDefinition {
            id = IdentifierRules.requireValid(id, "HUD element id");
            placementPolicy = Objects.requireNonNull(placementPolicy, "placementPolicy");
            if (minimumWidth < 1 || minimumHeight < 1) {
                throw new IllegalArgumentException("HUD element minimum dimensions must be positive");
            }
        }
    }

    public record SidePlacement(SideAnchor anchor, double normalizedTop) {
        private static final int EDGE_MARGIN = 8;

        public SidePlacement {
            anchor = Objects.requireNonNull(anchor, "anchor");
            requireFinite(normalizedTop, "normalizedTop");
        }

        public static SidePlacement fromPixels(SideAnchor anchor, int top, int viewportHeight, int height) {
            requireViewport(1, viewportHeight);
            var maximumTop = Math.max(0, viewportHeight - Math.min(height, viewportHeight));
            var clampedTop = Math.clamp(top, 0, maximumTop);
            return new SidePlacement(anchor, maximumTop == 0 ? 0 : clampedTop / (double) maximumTop);
        }

        public Rectangle toPixels(int viewportWidth, int viewportHeight, ElementDefinition definition) {
            requireViewport(viewportWidth, viewportHeight);
            int width = Math.min(definition.minimumWidth(), viewportWidth);
            int height = Math.min(definition.minimumHeight(), viewportHeight);
            int margin = Math.min(EDGE_MARGIN, Math.max(0, (viewportWidth - width) / 2));
            int x = anchor == SideAnchor.LEFT ? margin : viewportWidth - width - margin;
            int maximumTop = Math.max(0, viewportHeight - height);
            int y = (int) Math.round(Math.clamp(normalizedTop, 0d, 1d) * maximumTop);
            return new Rectangle(x, y, width, height);
        }

        private static void requireFinite(double value, String name) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
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

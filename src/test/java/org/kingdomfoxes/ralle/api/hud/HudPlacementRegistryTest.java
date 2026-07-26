package org.kingdomfoxes.ralle.api.hud;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.ElementDefinition;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.PlacementPolicy;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.SideAnchor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPlacementRegistryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesBoundsRelativeToTheViewport() {
        var path = temporaryDirectory.resolve("layout.properties");
        var registry = registry(path);
        registry.setPixels("chat", new Rectangle(192, 108, 960, 540), 1920, 1080);
        registry = registry(path);

        assertEquals(
                new Rectangle(96, 54, 480, 270),
                registry.resolveCustom("chat", 960, 540).orElseThrow()
        );
    }

    @Test
    void clampsMovedAndResizedElementsInsideTheViewport() {
        var registry = registry(temporaryDirectory.resolve("layout.properties"));
        registry.setPixels("chat", new Rectangle(-50, 500, 20, 20), 800, 600);

        assertEquals(
                new Rectangle(0, 500, 120, 45),
                registry.resolveCustom("chat", 800, 600).orElseThrow()
        );
    }

    @Test
    void ignoresCorruptSavedBounds() throws IOException {
        var path = temporaryDirectory.resolve("layout.properties");
        Files.writeString(path, "chat.x=oops\nchat.y=0.5\nchat.width=0.5\nchat.height=0.5\n");

        var registry = registry(path);

        assertTrue(registry.customBounds("chat").isEmpty());
    }

    @Test
    void resolvesAllCustomBoundsForEditorPreviews() {
        var registry = registry(temporaryDirectory.resolve("layout.properties"));
        registry.setPixels("chat", new Rectangle(40, 50, 200, 100), 800, 600);

        assertEquals(
                new Rectangle(40, 50, 200, 100),
                registry.resolveAllCustom(800, 600).get("chat")
        );
    }

    @Test
    void fixedElementsKeepSizeSnapSidesAndPersistVerticalPosition() {
        var path = temporaryDirectory.resolve("layout.properties");
        var registry = new HudPlacementRegistry(path);
        registry.register(new ElementDefinition("notifications", 260, 100, PlacementPolicy.FIXED_SIDE_ANCHORED));
        registry.seal();
        registry.setPixels("notifications", new Rectangle(700, 250, 500, 300), 1000, 600);

        registry = new HudPlacementRegistry(path);
        registry.register(new ElementDefinition("notifications", 260, 100, PlacementPolicy.FIXED_SIDE_ANCHORED));
        registry.seal();

        assertEquals(new Rectangle(732, 250, 260, 100),
                registry.resolveSideAnchored("notifications", 1000, 600, SideAnchor.LEFT, 0));
        assertEquals(new Rectangle(532, 125, 260, 100),
                registry.resolveSideAnchored("notifications", 800, 350, SideAnchor.LEFT, 0));
    }

    private HudPlacementRegistry registry(Path path) {
        var registry = new HudPlacementRegistry(path);
        registry.register(new ElementDefinition("chat", 120, 45));
        registry.seal();
        return registry;
    }
}

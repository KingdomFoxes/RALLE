package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LfgNotificationOverlayTest {
    @Test
    void newestCardOccupiesBottomAnchorAndWholeStackClamps() {
        var anchor = new Rectangle(732, 492, 260, 100);
        var stack = LfgNotificationOverlay.stackBounds(anchor, 3, 1000, 600);

        assertEquals(new Rectangle(732, 492, 260, 100), stack.get(0));
        assertEquals(new Rectangle(732, 386, 260, 100), stack.get(1));
        assertEquals(new Rectangle(732, 280, 260, 100), stack.get(2));
    }

    @Test
    void upperAnchorStacksDownAndClampsAsAUnit() {
        var anchor = new Rectangle(8, -20, 260, 100);
        var stack = LfgNotificationOverlay.stackBounds(anchor, 3, 800, 350);

        assertEquals(new Rectangle(8, 0, 260, 100), stack.get(0));
        assertEquals(new Rectangle(8, 106, 260, 100), stack.get(1));
        assertEquals(new Rectangle(8, 212, 260, 100), stack.get(2));
    }
}

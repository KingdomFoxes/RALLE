package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void rosterSlotsUseTheFullCardWidthWithoutChangingTheirSize() {
        var card = new Rectangle(30, 40, 260, 100);

        assertEquals(38, LfgNotificationOverlay.rosterSlotX(card, 0));
        assertEquals(113, LfgNotificationOverlay.rosterSlotX(card, 1));
        assertEquals(187, LfgNotificationOverlay.rosterSlotX(card, 2));
        assertEquals(262, LfgNotificationOverlay.rosterSlotX(card, 3));
        assertThrows(IllegalArgumentException.class, () -> LfgNotificationOverlay.rosterSlotX(card, 4));
    }

    @Test
    void notificationRegionColorsMatchTheRaidLfgSemanticScale() {
        assertEquals(0xFF00FF55, LfgNotificationOverlay.regionColor(LfgProtocol.Region.EU));
        assertEquals(0xFFFFFF00, LfgNotificationOverlay.regionColor(LfgProtocol.Region.NA));
        assertEquals(0xFFFF3333, LfgNotificationOverlay.regionColor(LfgProtocol.Region.AS));
    }
}

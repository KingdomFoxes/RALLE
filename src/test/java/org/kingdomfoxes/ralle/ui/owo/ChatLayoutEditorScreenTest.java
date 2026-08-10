package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.RalleHudElements;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLayoutEditorScreenTest {
    @Test
    void allElementsModeOnlyEditsCurrentlyEnabledHudElements() {
        assertEquals(
                List.of(RalleHudElements.CHAT),
                ChatLayoutEditorScreen.enabledEditableElementIds(false)
        );
        assertEquals(
                List.of(RalleHudElements.CHAT, RalleHudElements.LFG_NOTIFICATIONS),
                ChatLayoutEditorScreen.enabledEditableElementIds(true)
        );
        assertEquals(
                List.of(RalleHudElements.CHAT, RalleHudElements.LFG_NOTIFICATIONS, RalleHudElements.LFG_ACTION_BAR),
                ChatLayoutEditorScreen.enabledPreviewElementIds(true)
        );
    }

    @Test
    void selectedElementNameAndPositionUseSeparateCenteredLines() {
        var bounds = new Rectangle(10, 20, 190, 100);

        assertEquals(60, ChatLayoutEditorScreen.centeredTextY(bounds, -6));
        assertEquals(72, ChatLayoutEditorScreen.centeredTextY(bounds, 6));
    }

    @Test
    void detectsEveryResizeEdgeAndLeavesTheInteriorForMoving() {
        assertEquals(ChatLayoutEditorScreen.AxisEdge.START, ChatLayoutEditorScreen.edgeAt(10, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.END, ChatLayoutEditorScreen.edgeAt(209, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.NONE, ChatLayoutEditorScreen.edgeAt(100, 10, 210, 6));
    }

    @Test
    void resizesFromTopAndLeftWhileKeepingOppositeEdgesAnchored() {
        var resized = ChatLayoutEditorScreen.resize(
                new Rectangle(100, 80, 200, 100),
                ChatLayoutEditorScreen.AxisEdge.START,
                ChatLayoutEditorScreen.AxisEdge.START,
                50, 40, 0, 0, 500, 300
        );

        assertEquals(new Rectangle(50, 40, 250, 140), resized);
    }

    @Test
    void resizesFromBottomAndRightAndClampsToTheViewport() {
        var resized = ChatLayoutEditorScreen.resize(
                new Rectangle(100, 80, 200, 100),
                ChatLayoutEditorScreen.AxisEdge.END,
                ChatLayoutEditorScreen.AxisEdge.END,
                800, 600, 0, 0, 500, 300
        );

        assertEquals(new Rectangle(100, 80, 400, 220), resized);
    }

    @Test
    void cannotResizeBelowTheMinimumChatSize() {
        var resized = ChatLayoutEditorScreen.resize(
                new Rectangle(100, 80, 200, 100),
                ChatLayoutEditorScreen.AxisEdge.START,
                ChatLayoutEditorScreen.AxisEdge.END,
                290, 90, 0, 0, 500, 300
        );

        assertEquals(new Rectangle(180, 80, 120, 45), resized);
    }

    @Test
    void snapToGridUsesTheClosestElementEdge() {
        var snapped = ChatLayoutEditorScreen.snapAxis(123, 84, 500, true, true);

        assertEquals(120, snapped.start());
        assertEquals(120, snapped.guide());
    }

    @Test
    void alignmentGuidesSnapElementCentersToTheScreenCenter() {
        var snapped = ChatLayoutEditorScreen.snapAxis(187, 120, 500, false, true);

        assertEquals(190, snapped.start());
        assertEquals(250, snapped.guide());
    }
}

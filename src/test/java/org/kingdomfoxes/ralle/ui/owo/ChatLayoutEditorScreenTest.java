package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLayoutEditorScreenTest {
    @Test
    void detectsEveryResizeEdgeAndLeavesTheInteriorForMoving() {
        assertEquals(ChatLayoutEditorScreen.AxisEdge.START, ChatLayoutEditorScreen.edgeAt(10, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.START, ChatLayoutEditorScreen.edgeAt(6, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.END, ChatLayoutEditorScreen.edgeAt(209, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.END, ChatLayoutEditorScreen.edgeAt(214, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.NONE, ChatLayoutEditorScreen.edgeAt(100, 10, 210, 6));
    }

    @Test
    void expandsResizableEditorWindowWithoutChangingElementBounds() {
        var element = new Rectangle(20, 30, 100, 50);

        var interaction = ChatLayoutEditorScreen.resizeInteractionBounds(element, 200, 120);

        assertEquals(new Rectangle(16, 26, 108, 58), interaction);
        assertEquals(new Rectangle(20, 30, 100, 50), element);
    }

    @Test
    void clipsExpandedEditorWindowToEveryViewportEdge() {
        var interaction = ChatLayoutEditorScreen.resizeInteractionBounds(
                new Rectangle(0, 0, 200, 120),
                200,
                120
        );

        assertEquals(new Rectangle(0, 0, 200, 120), interaction);
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

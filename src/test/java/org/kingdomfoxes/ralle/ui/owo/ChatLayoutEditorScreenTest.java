package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLayoutEditorScreenTest {
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
}

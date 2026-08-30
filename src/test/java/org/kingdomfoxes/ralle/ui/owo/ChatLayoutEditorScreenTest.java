package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.PlacementPolicy;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.RalleHudElements;
import org.kingdomfoxes.ralle.chat.ChatLayoutService;

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
        assertEquals(ChatLayoutEditorScreen.AxisEdge.START, ChatLayoutEditorScreen.edgeAt(6, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.END, ChatLayoutEditorScreen.edgeAt(209, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.END, ChatLayoutEditorScreen.edgeAt(214, 10, 210, 6));
        assertEquals(ChatLayoutEditorScreen.AxisEdge.NONE, ChatLayoutEditorScreen.edgeAt(100, 10, 210, 6));
    }

    @Test
    void expandsEveryEditableEditorWindowWithoutChangingElementBounds() {
        var element = new Rectangle(20, 30, 100, 50);

        var interaction = ChatLayoutEditorScreen.editorInteractionBounds(element, 200, 120);

        assertEquals(new Rectangle(17, 27, 106, 56), interaction);
        assertEquals(new Rectangle(20, 30, 100, 50), element);
    }

    @Test
    void connectedOuterFrameStaysInsideTheExpandedEditorWindow() {
        var frame = new Rectangle(17, 27, 106, 56);

        assertEquals(
                List.of(
                        new Rectangle(17, 27, 106, 2),
                        new Rectangle(17, 81, 106, 2),
                        new Rectangle(17, 27, 2, 56),
                        new Rectangle(121, 27, 2, 56)
                ),
                ChatLayoutEditorScreen.connectedFrameSegments(
                        frame,
                        ChatLayoutEditorScreen.EDITOR_OUTER_FRAME_THICKNESS
                )
        );
    }

    @Test
    void nestedInsetFrameMatchesSavedBoundsAwayFromViewportEdges() {
        var interaction = new Rectangle(17, 27, 106, 56);

        assertEquals(
                new Rectangle(20, 30, 100, 50),
                ChatLayoutEditorScreen.nestedInsetFrame(
                        interaction,
                        ChatLayoutEditorScreen.EDITOR_WINDOW_MARGIN
                )
        );
    }

    @Test
    void nestedInsetFrameKeepsItsGapWhenOuterFrameIsClampedToViewportEdges() {
        assertEquals(
                new Rectangle(3, 3, 97, 47),
                ChatLayoutEditorScreen.nestedInsetFrame(
                        new Rectangle(0, 0, 103, 53),
                        ChatLayoutEditorScreen.EDITOR_WINDOW_MARGIN
                )
        );
        assertEquals(
                new Rectangle(100, 50, 97, 47),
                ChatLayoutEditorScreen.nestedInsetFrame(
                        new Rectangle(97, 47, 103, 53),
                        ChatLayoutEditorScreen.EDITOR_WINDOW_MARGIN
                )
        );
    }

    @Test
    void editorFrameKeepsOldGoldOutsideAndDarkGoldOnTheInsetDesign() {
        assertEquals(0xFFE5B94C, ChatLayoutEditorScreen.EDITOR_OUTER_FRAME);
        assertEquals(RalleTheme.DARK_GOLD_ARGB, ChatLayoutEditorScreen.EDITOR_INNER_FRAME);
        assertEquals(ChatLayoutEditorScreen.EDITOR_OUTER_FRAME, ChatLayoutEditorScreen.EDITOR_LABEL);
    }

    @Test
    void fixedSizeElementsOmitTheResizableCornerAccents() {
        assertEquals(
                List.of(),
                ChatLayoutEditorScreen.cornerAccentStrips(
                        new Rectangle(20, 30, 190, 100),
                        PlacementPolicy.FIXED_SIDE_ANCHORED
                )
        );
    }

    @Test
    void cornerAccentsTaperInwardAndRotateAcrossAllFourCorners() {
        var frame = new Rectangle(20, 30, 100, 50);

        assertEquals(
                List.of(
                        new Rectangle(20, 31, 6, 1),
                        new Rectangle(114, 31, 6, 1),
                        new Rectangle(20, 78, 6, 1),
                        new Rectangle(114, 78, 6, 1),
                        new Rectangle(20, 32, 5, 1),
                        new Rectangle(115, 32, 5, 1),
                        new Rectangle(20, 77, 5, 1),
                        new Rectangle(115, 77, 5, 1),
                        new Rectangle(20, 33, 4, 1),
                        new Rectangle(116, 33, 4, 1),
                        new Rectangle(20, 76, 4, 1),
                        new Rectangle(116, 76, 4, 1),
                        new Rectangle(20, 34, 3, 1),
                        new Rectangle(117, 34, 3, 1),
                        new Rectangle(20, 75, 3, 1),
                        new Rectangle(117, 75, 3, 1),
                        new Rectangle(20, 35, 2, 1),
                        new Rectangle(118, 35, 2, 1),
                        new Rectangle(20, 74, 2, 1),
                        new Rectangle(118, 74, 2, 1)
                ),
                ChatLayoutEditorScreen.cornerAccentStrips(
                        frame,
                        PlacementPolicy.RESIZABLE_RECTANGLE
                )
        );
    }

    @Test
    void clipsExpandedEditorWindowToEveryViewportEdge() {
        var interaction = ChatLayoutEditorScreen.editorInteractionBounds(
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
                50, 40, 0, 0, 500, 300,
                ChatLayoutService.MINIMUM_WIDTH, ChatLayoutService.MINIMUM_HEIGHT
        );

        assertEquals(new Rectangle(50, 40, 250, 140), resized);
    }

    @Test
    void resizesFromBottomAndRightAndClampsToTheViewport() {
        var resized = ChatLayoutEditorScreen.resize(
                new Rectangle(100, 80, 200, 100),
                ChatLayoutEditorScreen.AxisEdge.END,
                ChatLayoutEditorScreen.AxisEdge.END,
                800, 600, 0, 0, 500, 300,
                ChatLayoutService.MINIMUM_WIDTH, ChatLayoutService.MINIMUM_HEIGHT
        );

        assertEquals(new Rectangle(100, 80, 400, 220), resized);
    }

    @Test
    void cannotResizeBelowTheMinimumChatSize() {
        var resized = ChatLayoutEditorScreen.resize(
                new Rectangle(100, 80, 200, 100),
                ChatLayoutEditorScreen.AxisEdge.START,
                ChatLayoutEditorScreen.AxisEdge.END,
                290, 90, 0, 0, 500, 300,
                ChatLayoutService.MINIMUM_WIDTH, ChatLayoutService.MINIMUM_HEIGHT
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

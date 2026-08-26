package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatScreenshotGeometryTest {
    @Test
    void wrappedLinesRemainPartOfTheirCompleteLogicalMessage() {
        var snapshot = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);

        assertEquals(new ChatScreenshotGeometry.LineRange(0, 4),
                ChatScreenshotGeometry.selectedLines(snapshot, 2, 0));
        assertEquals(new ChatScreenshotGeometry.LineRange(2, 2),
                ChatScreenshotGeometry.selectedLines(snapshot, 1, 1));
    }

    @Test
    void hitTestingSupportsBothVisualDirections() {
        var bottomUp = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);
        var topDown = snapshot(ChatBehaviorService.MessageDirection.TOP_DOWN);

        assertEquals(2, ChatScreenshotGeometry.messageAt(bottomUp, 0, 20, 5).orElseThrow());
        assertEquals(0, ChatScreenshotGeometry.messageAt(bottomUp, 0, 20, 45).orElseThrow());
        assertEquals(0, ChatScreenshotGeometry.messageAt(topDown, 0, 20, 5).orElseThrow());
        assertEquals(2, ChatScreenshotGeometry.messageAt(topDown, 0, 20, 45).orElseThrow());
    }

    @Test
    void visibleBoundsClipACompleteReverseRangeToTheViewport() {
        var snapshot = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);
        var range = ChatScreenshotGeometry.selectedLines(snapshot, 2, 1);

        assertEquals(new ChatScreenshotGeometry.Rectangle(10, 0, 110, 32),
                ChatScreenshotGeometry.visibleBounds(snapshot, 0, range));
    }

    @Test
    void selectionBoundsAddTwoLogicalPixelsPerSideWithoutEscapingTheViewport() {
        var snapshot = snapshot(ChatBehaviorService.MessageDirection.TOP_DOWN);

        assertEquals(new ChatScreenshotGeometry.Rectangle(10, 8, 110, 32),
                ChatScreenshotGeometry.visibleBounds(snapshot, 0, new ChatScreenshotGeometry.LineRange(1, 2)));
        assertEquals(24, ChatScreenshotGeometry.captureVisualHeight(2, 10, 1.0));
        assertEquals(14, ChatScreenshotGeometry.captureVisualHeight(2, 5, 1.0));
    }

    @Test
    void snapToTextCropsToTheWidestSelectedLineForEitherAlignment() {
        var left = snapshot(ChatBehaviorService.MessageDirection.TOP_DOWN);
        var right = new ChatScreenshotSnapshot(
                left.lines(), left.initialScroll(), left.linesPerPage(), left.lineHeight(),
                left.textBaselineOffset(), left.chatScale(), left.textOpacity(), left.viewportLeft(),
                left.viewportTop(), left.viewportRight(), left.viewportBottom(), left.direction(),
                ChatBehaviorService.HorizontalAlignment.RIGHT, left.shadow()
        );
        var vertical = new ChatScreenshotGeometry.Rectangle(10, 8, 110, 32);

        assertEquals(24, ChatScreenshotGeometry.maximumTextWidth(left, new ChatScreenshotGeometry.LineRange(0, 2)));
        assertEquals(new ChatScreenshotGeometry.Rectangle(10, 8, 40, 32),
                ChatScreenshotGeometry.snapToTextBounds(left, vertical, 24));
        assertEquals(new ChatScreenshotGeometry.Rectangle(85, 8, 110, 32),
                ChatScreenshotGeometry.snapToTextBounds(right, vertical, 24));
        assertEquals(31, ChatScreenshotGeometry.snappedCaptureVisualWidth(24, 1.0));
        assertEquals(10, ChatScreenshotGeometry.snappedCaptureTextOffset(0.5));
        assertEquals(19, ChatScreenshotGeometry.snappedCaptureVisualWidth(24, 0.5));

        assertEquals(List.of(
                        new ChatScreenshotGeometry.Rectangle(10, 0, 28, 10),
                        new ChatScreenshotGeometry.Rectangle(10, 10, 34, 20),
                        new ChatScreenshotGeometry.Rectangle(10, 20, 40, 32)
                ),
                ChatScreenshotGeometry.snappedLineBounds(
                        left, 0, new ChatScreenshotGeometry.LineRange(0, 2)));
        assertEquals(List.of(
                        new ChatScreenshotGeometry.Rectangle(97, 0, 110, 10),
                        new ChatScreenshotGeometry.Rectangle(91, 10, 110, 20),
                        new ChatScreenshotGeometry.Rectangle(85, 20, 110, 32)
                ),
                ChatScreenshotGeometry.snappedLineBounds(
                        right, 0, new ChatScreenshotGeometry.LineRange(0, 2)));

        var scaled = new ChatScreenshotSnapshot(
                left.lines(), left.initialScroll(), left.linesPerPage(), left.lineHeight(),
                left.textBaselineOffset(), 0.75, left.textOpacity(), left.viewportLeft(),
                left.viewportTop(), left.viewportRight(), 38, left.direction(), left.alignment(), left.shadow()
        );
        var scaledBounds = ChatScreenshotGeometry.snappedLineBounds(
                scaled, 0, new ChatScreenshotGeometry.LineRange(0, 2));
        assertEquals(scaledBounds.get(0).bottom(), scaledBounds.get(1).top());
        assertEquals(scaledBounds.get(1).bottom(), scaledBounds.get(2).top());
    }

    @Test
    void smoothExpansionUsesCubicEaseOut() {
        assertEquals(0.0, ChatScreenshotGeometry.cubicEaseOut(0.0));
        assertEquals(0.875, ChatScreenshotGeometry.cubicEaseOut(0.5));
        assertEquals(1.0, ChatScreenshotGeometry.cubicEaseOut(1.0));
        assertTrue(ChatScreenshotGeometry.cubicEaseOut(0.75) > 0.75);
    }

    private static ChatScreenshotSnapshot snapshot(ChatBehaviorService.MessageDirection direction) {
        return new ChatScreenshotSnapshot(
                List.of(line(0, 12), line(0, 18), line(1, 24), line(2, 30), line(2, 36)),
                0,
                5,
                10,
                2,
                1.0,
                1.0F,
                10,
                0,
                110,
                50,
                direction,
                ChatBehaviorService.HorizontalAlignment.LEFT,
                ChatBehaviorService.TextShadow.VANILLA
        );
    }

    private static ChatScreenshotSnapshot.FrozenLine line(int message, int width) {
        return new ChatScreenshotSnapshot.FrozenLine(FormattedCharSequence.forward("line", Style.EMPTY), message, width);
    }
}

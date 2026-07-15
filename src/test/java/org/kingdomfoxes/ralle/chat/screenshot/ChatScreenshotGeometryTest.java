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

        assertEquals(new ChatScreenshotGeometry.Rectangle(10, 0, 110, 30),
                ChatScreenshotGeometry.visibleBounds(snapshot, 0, range));
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
                List.of(line(0), line(0), line(1), line(2), line(2)),
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

    private static ChatScreenshotSnapshot.FrozenLine line(int message) {
        return new ChatScreenshotSnapshot.FrozenLine(FormattedCharSequence.forward("line", Style.EMPTY), message);
    }
}

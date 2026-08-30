package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSelectionAutoscrollTest {
    @Test
    void topAndBottomBandsWorkImmediatelyInsideAndOutsideInBothDirections() {
        var bottomUp = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);
        var topDown = snapshot(ChatBehaviorService.MessageDirection.TOP_DOWN);

        assertEquals(1, ChatSelectionAutoscroll.direction(bottomUp, 50, 1));
        assertEquals(1, ChatSelectionAutoscroll.direction(bottomUp, 50, -1));
        assertEquals(-1, ChatSelectionAutoscroll.direction(bottomUp, 50, 49));
        assertEquals(-1, ChatSelectionAutoscroll.direction(bottomUp, 50, 51));
        assertEquals(-1, ChatSelectionAutoscroll.direction(topDown, 50, -1));
        assertEquals(1, ChatSelectionAutoscroll.direction(topDown, 50, 51));
    }

    @Test
    void delayAndRepeatAreBoundedAndLeavingEitherAxisStopsScrolling() {
        var controller = new ChatSelectionAutoscroll();
        var snapshot = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);

        assertTrue(controller.nextAmount(snapshot, 50, 1, 1_000).isEmpty());
        assertTrue(controller.nextAmount(snapshot, 50, 1, 1_249).isEmpty());
        assertEquals(1, controller.nextAmount(snapshot, 50, 1, 1_250).orElseThrow());
        assertTrue(controller.nextAmount(snapshot, 50, 1, 1_349).isEmpty());
        assertEquals(1, controller.nextAmount(snapshot, 50, 1, 1_350).orElseThrow());
        assertTrue(controller.nextAmount(snapshot, 123, 1, 1_500).isEmpty());
        assertTrue(controller.nextAmount(snapshot, 50, 13, 1_800).isEmpty());
        assertTrue(controller.nextAmount(snapshot, 50, 1, 1_801).isEmpty());
    }

    @Test
    void hitPointClampsToTheNewExtremeLineAfterARealScroll() {
        var controller = new ChatSelectionAutoscroll();
        var snapshot = snapshot(ChatBehaviorService.MessageDirection.BOTTOM_UP);
        controller.nextAmount(snapshot, -1, 1, 0);
        controller.nextAmount(snapshot, -1, 1, 250);

        var hit = controller.clampedHitPoint(snapshot, -1);
        assertEquals(10.0, hit.x());
        assertEquals(0.5, hit.y());
        assertEquals(5, ChatScreenshotGeometry.messageAt(snapshot, 1, hit.x(), hit.y()).orElseThrow());
    }

    private static ChatScreenshotSnapshot snapshot(ChatBehaviorService.MessageDirection direction) {
        return new ChatScreenshotSnapshot(
                List.of(line(0), line(1), line(2), line(3), line(4), line(5)),
                0, 5, 10, 2, 1.0, 1.0F, 100,
                10, 0, 110, 50, direction,
                ChatBehaviorService.HorizontalAlignment.LEFT,
                ChatBehaviorService.TextShadow.VANILLA
        );
    }

    private static ChatScreenshotSnapshot.FrozenLine line(int message) {
        return new ChatScreenshotSnapshot.FrozenLine(FormattedCharSequence.forward("line", Style.EMPTY), message, 24);
    }
}

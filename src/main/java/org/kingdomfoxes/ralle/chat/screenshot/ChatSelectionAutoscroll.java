package org.kingdomfoxes.ralle.chat.screenshot;

import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.OptionalInt;

/** Timing and edge geometry for screenshot selection-driven chat scrolling. */
final class ChatSelectionAutoscroll {
    record HitPoint(double x, double y) {}

    private int activeDirection;
    private long edgeEnteredAt;
    private long lastScrollAt;

    OptionalInt nextAmount(ChatScreenshotSnapshot snapshot, double x, double y, long now) {
        int direction = direction(snapshot, x, y);
        if (direction == 0) {
            reset();
            return OptionalInt.empty();
        }
        if (direction != activeDirection) {
            activeDirection = direction;
            edgeEnteredAt = now;
            lastScrollAt = now;
            return OptionalInt.empty();
        }
        if (now - edgeEnteredAt < ChatScreenshotTokens.EDGE_DELAY_MILLIS
                || now - lastScrollAt < ChatScreenshotTokens.EDGE_REPEAT_MILLIS) {
            return OptionalInt.empty();
        }
        lastScrollAt = now;
        return OptionalInt.of(direction);
    }

    HitPoint clampedHitPoint(ChatScreenshotSnapshot snapshot, double pointerX) {
        double x = Math.max(snapshot.viewportLeft(), Math.min(pointerX, Math.nextDown((double) snapshot.viewportRight())));
        double y = activeDirection == directionAtTop(snapshot)
                ? snapshot.viewportTop() + 0.5
                : snapshot.viewportBottom() - 0.5;
        return new HitPoint(x, y);
    }

    void reset() {
        activeDirection = 0;
        edgeEnteredAt = 0L;
        lastScrollAt = 0L;
    }

    static int direction(ChatScreenshotSnapshot snapshot, double x, double y) {
        int band = ChatScreenshotTokens.EDGE_BAND;
        boolean horizontallyNear = x >= snapshot.viewportLeft() - band && x <= snapshot.viewportRight() + band;
        if (!horizontallyNear) return 0;
        boolean top = y >= snapshot.viewportTop() - band && y < snapshot.viewportTop() + band;
        boolean bottom = y > snapshot.viewportBottom() - band && y <= snapshot.viewportBottom() + band;
        if (top == bottom) return 0;
        if (snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN) return top ? -1 : 1;
        return top ? 1 : -1;
    }

    private static int directionAtTop(ChatScreenshotSnapshot snapshot) {
        return snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN ? -1 : 1;
    }
}

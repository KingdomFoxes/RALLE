package org.kingdomfoxes.ralle.chat.screenshot;

import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.OptionalInt;

/** Pure range and geometry calculations used by input, overlay, and capture code. */
public final class ChatScreenshotGeometry {
    private ChatScreenshotGeometry() {}

    public static OptionalInt messageAt(ChatScreenshotSnapshot snapshot, int scroll, double mouseX, double mouseY) {
        if (mouseX < snapshot.viewportLeft() || mouseX >= snapshot.viewportRight()
                || mouseY < snapshot.viewportTop() || mouseY >= snapshot.viewportBottom()) {
            return OptionalInt.empty();
        }
        double lineHeight = snapshot.lineHeight() * snapshot.chatScale();
        int topSlot = (int) Math.floor((mouseY - snapshot.viewportTop()) / lineHeight);
        if (topSlot < 0 || topSlot >= snapshot.linesPerPage()) return OptionalInt.empty();
        int relative = snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN
                ? topSlot
                : snapshot.linesPerPage() - 1 - topSlot;
        int lineIndex = scroll + relative;
        if (lineIndex < 0 || lineIndex >= snapshot.lines().size()) return OptionalInt.empty();
        return OptionalInt.of(snapshot.lines().get(lineIndex).messageIndex());
    }

    public static LineRange selectedLines(ChatScreenshotSnapshot snapshot, int firstMessage, int secondMessage) {
        int lower = Math.min(firstMessage, secondMessage);
        int upper = Math.max(firstMessage, secondMessage);
        int first = -1;
        int last = -1;
        for (int index = 0; index < snapshot.lines().size(); index++) {
            int message = snapshot.lines().get(index).messageIndex();
            if (message >= lower && message <= upper) {
                if (first == -1) first = index;
                last = index;
            }
        }
        if (first == -1) throw new IllegalArgumentException("Selected messages are absent from the snapshot");
        return new LineRange(first, last);
    }

    public static Rectangle visibleBounds(ChatScreenshotSnapshot snapshot, int scroll, LineRange range) {
        double scaledLineHeight = snapshot.lineHeight() * snapshot.chatScale();
        int top = snapshot.viewportBottom();
        int bottom = snapshot.viewportTop();
        for (int index = range.first(); index <= range.last(); index++) {
            int relative = index - scroll;
            if (relative < 0 || relative >= snapshot.linesPerPage()) continue;
            int topSlot = snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN
                    ? relative
                    : snapshot.linesPerPage() - 1 - relative;
            int lineTop = snapshot.viewportTop() + (int) Math.floor(topSlot * scaledLineHeight);
            int lineBottom = snapshot.viewportTop() + (int) Math.ceil((topSlot + 1) * scaledLineHeight);
            top = Math.min(top, lineTop);
            bottom = Math.max(bottom, lineBottom);
        }
        top = Math.max(snapshot.viewportTop(), top);
        bottom = Math.min(snapshot.viewportBottom(), bottom);
        if (bottom <= top) return new Rectangle(snapshot.viewportLeft(), snapshot.viewportTop(), snapshot.viewportRight(), snapshot.viewportTop());
        return new Rectangle(snapshot.viewportLeft(), top, snapshot.viewportRight(), bottom);
    }

    public static double cubicEaseOut(double progress) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        double inverse = 1.0 - clamped;
        return 1.0 - inverse * inverse * inverse;
    }

    public record LineRange(int first, int last) {
        public LineRange {
            if (first < 0 || last < first) throw new IllegalArgumentException("Invalid line range");
        }
        public int count() { return last - first + 1; }
    }

    public record Rectangle(int left, int top, int right, int bottom) {
        public int width() { return Math.max(0, right - left); }
        public int height() { return Math.max(0, bottom - top); }
        public boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}

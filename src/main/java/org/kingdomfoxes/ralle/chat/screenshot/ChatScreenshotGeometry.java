package org.kingdomfoxes.ralle.chat.screenshot;

import org.kingdomfoxes.ralle.chat.ChatBehaviorService;
import org.kingdomfoxes.ralle.chat.ChatRenderLayout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        top = Math.max(snapshot.viewportTop(), top - ChatScreenshotTokens.VERTICAL_PADDING);
        bottom = Math.min(snapshot.viewportBottom(), bottom + ChatScreenshotTokens.VERTICAL_PADDING);
        return new Rectangle(snapshot.viewportLeft(), top, snapshot.viewportRight(), bottom);
    }

    public static int maximumTextWidth(ChatScreenshotSnapshot snapshot, LineRange range) {
        int maximum = 0;
        for (int index = range.first(); index <= range.last(); index++) {
            maximum = Math.max(maximum, snapshot.lines().get(index).textWidth());
        }
        return Math.max(1, maximum);
    }

    public static Rectangle snapToTextBounds(
            ChatScreenshotSnapshot snapshot,
            Rectangle verticalBounds,
            int maximumTextWidth
    ) {
        if (maximumTextWidth < 1) throw new IllegalArgumentException("maximumTextWidth must be positive");
        int textOffset = ChatScreenshotTokens.CHAT_TEXT_OFFSET + ChatRenderLayout.horizontalOffset(
                snapshot.alignment(), snapshot.contentWidth(), maximumTextWidth
        );
        int textLeft = snapshot.viewportLeft() + (int) Math.floor(textOffset * snapshot.chatScale());
        int textRight = snapshot.viewportLeft()
                + (int) Math.ceil((textOffset + maximumTextWidth) * snapshot.chatScale());
        int left = Math.max(snapshot.viewportLeft(), textLeft - ChatScreenshotTokens.SNAP_LEFT_PADDING);
        int right = Math.min(snapshot.viewportRight(), textRight + ChatScreenshotTokens.SNAP_OTHER_PADDING);
        if (right <= left) right = Math.min(snapshot.viewportRight(), left + 1);
        return new Rectangle(left, verticalBounds.top(), right, verticalBounds.bottom());
    }

    public static List<Rectangle> snappedLineBounds(
            ChatScreenshotSnapshot snapshot,
            int scroll,
            LineRange range
    ) {
        var bounds = new ArrayList<Rectangle>();
        double scaledLineHeight = snapshot.lineHeight() * snapshot.chatScale();
        for (int index = range.first(); index <= range.last(); index++) {
            int relative = index - scroll;
            if (relative < 0 || relative >= snapshot.linesPerPage()) continue;
            int topSlot = snapshot.direction() == ChatBehaviorService.MessageDirection.TOP_DOWN
                    ? relative
                    : snapshot.linesPerPage() - 1 - relative;
            int top = snapshot.viewportTop() + (int) Math.floor(topSlot * scaledLineHeight);
            int bottom = snapshot.viewportTop() + (int) Math.ceil((topSlot + 1) * scaledLineHeight);
            var vertical = new Rectangle(snapshot.viewportLeft(), top, snapshot.viewportRight(), bottom);
            bounds.add(snapToTextBounds(snapshot, vertical,
                    Math.max(1, snapshot.lines().get(index).textWidth())));
        }
        bounds.sort(Comparator.comparingInt(Rectangle::top));
        if (bounds.isEmpty()) return List.of();

        for (int index = 1; index < bounds.size(); index++) {
            Rectangle previous = bounds.get(index - 1);
            Rectangle current = bounds.get(index);
            int boundary = current.top();
            bounds.set(index - 1, new Rectangle(
                    previous.left(), previous.top(), previous.right(), boundary
            ));
        }

        Rectangle first = bounds.getFirst();
        bounds.set(0, new Rectangle(
                first.left(),
                Math.max(snapshot.viewportTop(), first.top() - ChatScreenshotTokens.VERTICAL_PADDING),
                first.right(),
                first.bottom()
        ));
        int lastIndex = bounds.size() - 1;
        Rectangle last = bounds.get(lastIndex);
        bounds.set(lastIndex, new Rectangle(
                last.left(),
                last.top(),
                last.right(),
                Math.min(snapshot.viewportBottom(), last.bottom() + ChatScreenshotTokens.VERTICAL_PADDING)
        ));
        return List.copyOf(bounds);
    }

    public static int snappedCaptureVisualWidth(int maximumTextWidth, double chatScale) {
        if (maximumTextWidth < 1 || chatScale <= 0) throw new IllegalArgumentException("Invalid capture width");
        int textOffset = snappedCaptureTextOffset(chatScale);
        return Math.addExact((int) Math.ceil((textOffset + maximumTextWidth) * chatScale),
                ChatScreenshotTokens.SNAP_OTHER_PADDING);
    }

    public static int snappedCaptureTextOffset(double chatScale) {
        if (chatScale <= 0) throw new IllegalArgumentException("chatScale must be positive");
        return Math.max(1, (int) Math.ceil(ChatScreenshotTokens.SNAP_LEFT_PADDING / chatScale));
    }

    public static int captureVisualHeight(int lineCount, int lineHeight, double chatScale) {
        if (lineCount < 1 || lineHeight < 1 || chatScale <= 0) throw new IllegalArgumentException("Invalid capture dimensions");
        return Math.addExact(
                (int) Math.ceil(lineCount * lineHeight * chatScale),
                ChatScreenshotTokens.VERTICAL_PADDING * 2
        );
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

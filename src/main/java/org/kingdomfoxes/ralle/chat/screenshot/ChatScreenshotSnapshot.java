package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.util.FormattedCharSequence;
import org.kingdomfoxes.ralle.chat.ChatBehaviorService;

import java.util.List;

/** Immutable rendered-chat state captured at the beginning of a selection. */
public record ChatScreenshotSnapshot(
        List<FrozenLine> lines,
        int initialScroll,
        int linesPerPage,
        int lineHeight,
        int textBaselineOffset,
        double chatScale,
        float textOpacity,
        int contentWidth,
        int viewportLeft,
        int viewportTop,
        int viewportRight,
        int viewportBottom,
        ChatBehaviorService.MessageDirection direction,
        ChatBehaviorService.HorizontalAlignment alignment,
        ChatBehaviorService.TextShadow shadow
) {
    public ChatScreenshotSnapshot {
        lines = List.copyOf(lines);
        if (linesPerPage < 1) throw new IllegalArgumentException("linesPerPage must be positive");
        if (lineHeight < 1) throw new IllegalArgumentException("lineHeight must be positive");
        if (chatScale <= 0) throw new IllegalArgumentException("chatScale must be positive");
        if (textOpacity < 0 || textOpacity > 1) throw new IllegalArgumentException("textOpacity must be between zero and one");
        if (contentWidth < 1) throw new IllegalArgumentException("contentWidth must be positive");
    }

    public int visualWidth() {
        return Math.max(1, viewportRight - viewportLeft);
    }

    public record FrozenLine(FormattedCharSequence content, int messageIndex, int textWidth) {
        public FrozenLine {
            if (textWidth < 0) throw new IllegalArgumentException("textWidth must not be negative");
        }
    }
}

package org.kingdomfoxes.ralle.chat;

public final class ChatRenderLayout {
    private ChatRenderLayout() {}

    public static int lineIndex(
            ChatBehaviorService.MessageDirection direction,
            int vanillaIndex,
            int linesPerPage
    ) {
        if (direction == ChatBehaviorService.MessageDirection.BOTTOM_UP) return vanillaIndex;
        return Math.max(0, linesPerPage - 1 - vanillaIndex);
    }

    public static int horizontalOffset(
            ChatBehaviorService.HorizontalAlignment alignment,
            int contentWidth,
            int lineWidth
    ) {
        if (alignment == ChatBehaviorService.HorizontalAlignment.LEFT) return 0;
        return Math.max(0, contentWidth - lineWidth);
    }
}

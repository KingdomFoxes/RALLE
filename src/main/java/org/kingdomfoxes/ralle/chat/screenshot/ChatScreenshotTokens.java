package org.kingdomfoxes.ralle.chat.screenshot;

/** Shared presentation constants for the chat screenshot overlay. */
public final class ChatScreenshotTokens {
    public static final int SELECTION_FILL = 0xE6041330;
    public static final int SELECTION_GOLD = 0xFFFFC83D;
    public static final int CONFIRMATION_FILL = 0xFF041330;
    public static final int CONFIRMATION_TEXT = 0xFFFFFFFF;
    public static final int CONFIRMATION_HIGHLIGHT = 0xFFFFE29A;
    public static final int CONFIRMATION_DEPTH_COLOR = 0xFF080D16;
    public static final long COPIED_FADE_MILLIS = 250L;
    public static final int OUTLINE_WIDTH = 1;
    public static final int DASH_LENGTH = 4;
    public static final int DASH_GAP = 3;
    public static final int CONFIRMATION_HORIZONTAL_PADDING = 2;
    public static final int CONFIRMATION_VERTICAL_PADDING = 1;
    public static final int CONFIRMATION_DEPTH = 2;
    public static final int EDGE_BAND = 12;
    public static final int VERTICAL_PADDING = 2;
    public static final long EDGE_DELAY_MILLIS = 250L;
    public static final long EDGE_REPEAT_MILLIS = 100L;
    public static final long EXPANSION_MILLIS = 120L;

    public static int withOpacity(int color, float opacity) {
        int baseAlpha = color >>> 24;
        int alpha = Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, opacity)));
        return color & 0x00FFFFFF | alpha << 24;
    }

    private ChatScreenshotTokens() {}
}

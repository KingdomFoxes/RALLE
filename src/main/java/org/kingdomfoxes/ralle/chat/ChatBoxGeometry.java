package org.kingdomfoxes.ralle.chat;

/** Converts Minecraft's configured chat width into the complete rendered background width. */
public final class ChatBoxGeometry {
    // ChatComponent translates four pixels right, then extends line backgrounds from -4 through width + 8.
    static final int BACKGROUND_EXTENSION = 12;

    private ChatBoxGeometry() {}

    public static int renderedWidth(int chatWidth, double chatScale) {
        if (chatWidth < 1 || chatScale <= 0.0) throw new IllegalArgumentException("Invalid chat width");
        int canvasWidth = (int) Math.ceil(chatWidth / chatScale);
        return Math.max(1, (int) Math.ceil((canvasWidth + BACKGROUND_EXTENSION) * chatScale));
    }

    public static int chatWidthForRenderedWidth(int renderedWidth, double chatScale) {
        if (renderedWidth < 1 || chatScale <= 0.0) throw new IllegalArgumentException("Invalid rendered chat width");
        int low = 1;
        int high = renderedWidth;
        int best = 1;
        while (low <= high) {
            int candidate = low + (high - low) / 2;
            if (renderedWidth(candidate, chatScale) <= renderedWidth) {
                best = candidate;
                low = candidate + 1;
            } else {
                high = candidate - 1;
            }
        }
        return best;
    }
}

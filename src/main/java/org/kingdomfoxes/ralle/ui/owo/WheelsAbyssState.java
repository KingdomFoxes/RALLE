package org.kingdomfoxes.ralle.ui.owo;

/** Pure scroll, audio, backdrop-fade, and bottom artwork layout rules for the Wheels settings page. */
final class WheelsAbyssState {
    /** Empty vertical descent above the bottom artwork (~15s of continuous wheel scrolling at 24px/step). */
    static final int VOID_HEIGHT = 4400;
    static final int AND_THEN_WIDTH = 1000;
    static final int AND_THEN_HEIGHT = 537;
    static final int AND_THEN_CHUNKS = 3;
    static final long AND_THEN_CHUNK_MILLIS = 250L;
    static final int EYE_TEXTURE_WIDTH = 552;
    static final int EYE_TEXTURE_HEIGHT = 328;
    static final int EYE_MAX_DISPLAY_WIDTH = 220;
    static final int EYE_BOTTOM_MARGIN = 28;
    static final long MONOCHROME_HOLD_MILLIS = 5_000L;

    private WheelsAbyssState() {}

    static double scrollProgress(double currentScrollPosition, int maxScroll) {
        if (maxScroll <= 0 || !Double.isFinite(currentScrollPosition)) return 0.0;
        return Math.clamp(currentScrollPosition / maxScroll, 0.0, 1.0);
    }

    static float audioVolume(double progress) {
        if (!Double.isFinite(progress)) return 0.0F;
        return (float) Math.clamp(progress, 0.0, 1.0);
    }

    static int fadeAlpha(double progress) {
        if (!Double.isFinite(progress)) return 0;
        return (int) Math.round(Math.clamp(progress, 0.0, 1.0) * 255.0);
    }

    static int andThenChunk(long nowMillis) {
        return Math.floorMod(Math.floorDiv(nowMillis, AND_THEN_CHUNK_MILLIS), AND_THEN_CHUNKS);
    }

    static int andThenDrawX(int componentX, int componentWidth, int horizontalPadding, int chunk) {
        int clampedChunk = Math.clamp(chunk, 0, AND_THEN_CHUNKS - 1);
        int leftAligned = componentX - horizontalPadding;
        int rightAligned = componentX + componentWidth + horizontalPadding - AND_THEN_WIDTH;
        if (rightAligned >= leftAligned) {
            return componentX + (componentWidth - AND_THEN_WIDTH) / 2;
        }
        return leftAligned + (rightAligned - leftAligned) * clampedChunk / (AND_THEN_CHUNKS - 1);
    }

    static EyeBounds eyeBounds(int containerWidth, int containerHeight) {
        int displayWidth = Math.min(EYE_MAX_DISPLAY_WIDTH, Math.max(80, containerWidth - 16));
        int displayHeight = Math.max(48, (int) Math.round(
                displayWidth * (double) EYE_TEXTURE_HEIGHT / EYE_TEXTURE_WIDTH));
        int left = (containerWidth - displayWidth) / 2;
        int top = Math.max(0, containerHeight - displayHeight - EYE_BOTTOM_MARGIN);
        return new EyeBounds(left, top, displayWidth, displayHeight);
    }

    record EyeBounds(int left, int top, int width, int height) {
        boolean contains(double localX, double localY) {
            return localX >= left && localX < left + width && localY >= top && localY < top + height;
        }
    }
}

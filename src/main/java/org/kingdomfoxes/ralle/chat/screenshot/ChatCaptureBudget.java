package org.kingdomfoxes.ralle.chat.screenshot;

/** Bounds each RGBA surface to 16 MiB before any native/GPU allocation. */
final class ChatCaptureBudget {
    static final long MAX_PIXELS = 4_194_304L;
    static final int MAX_DIMENSION = 8192;

    private ChatCaptureBudget() {}

    static Size validate(int visualWidth, int visualHeight, int density, int deviceLimit) {
        long width = (long) visualWidth * density;
        long height = (long) visualHeight * density;
        int limit = Math.min(MAX_DIMENSION, deviceLimit);
        if (visualWidth < 1 || visualHeight < 1 || density < 1 || width > limit || height > limit
                || width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Selection is too large to copy. Select fewer messages or lower GUI scale.");
        }
        return new Size((int) width, (int) height);
    }

    record Size(int width, int height) {}
}

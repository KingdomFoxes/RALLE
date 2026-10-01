package org.kingdomfoxes.ralle.chat.screenshot;

import java.nio.ByteBuffer;

/** One pass from bottom-up premultiplied RGBA8 readback to top-down straight RGBA. */
final class CapturePixels {
    private CapturePixels() {}

    static byte[] toStraightAlphaRgba(ByteBuffer source, int width, int height) {
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int input = (x + y * width) * 4;
                int output = (x + (height - y - 1) * width) * 4;
                int alpha = source.get(input + 3) & 255;
                for (int channel = 0; channel < 3; channel++) {
                    int value = source.get(input + channel) & 255;
                    rgba[output + channel] = (byte) (alpha > 0 && alpha < 255
                            ? Math.min(255, value * 255 / alpha) : value);
                }
                rgba[output + 3] = (byte) alpha;
            }
        }
        return rgba;
    }
}

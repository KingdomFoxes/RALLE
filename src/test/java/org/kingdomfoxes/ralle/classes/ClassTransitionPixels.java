package org.kingdomfoxes.ralle.classes;

import java.nio.ByteBuffer;

/** Black handoff measurement from the actual underlying frame, excluding edge HUDs/crosshair.
 * Used only after a validated target click and the matching server container-close packet. */
public final class ClassTransitionPixels {
    public static final int WIDTH = 64, HEIGHT = 36;
    private ClassTransitionPixels() { }

    public static boolean black(ByteBuffer rgba, int width, int height) {
        if (width < 16 || height < 16 || rgba.remaining() != width * height * 4) return false;
        int base = rgba.position(), dark = 0, total = 0;
        for (int y = height / 4; y < height * 3 / 4; y++) {
            for (int x = width / 4; x < width * 3 / 4; x++) {
                if (Math.abs(x - width / 2) <= 1 && Math.abs(y - height / 2) <= 1) continue;
                int offset = base + (y * width + x) * 4;
                if (Byte.toUnsignedInt(rgba.get(offset)) <= 8 && Byte.toUnsignedInt(rgba.get(offset + 1)) <= 8
                        && Byte.toUnsignedInt(rgba.get(offset + 2)) <= 8) dark++;
                total++;
            }
        }
        return total > 0 && dark * 1000 >= total * 995;
    }
}

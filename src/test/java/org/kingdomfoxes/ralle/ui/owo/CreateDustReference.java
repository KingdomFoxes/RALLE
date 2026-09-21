package org.kingdomfoxes.ralle.ui.owo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Independent forward-scatter reference from 94489fa, plus a decoder for the baked asset. */
final class CreateDustReference {
    static BufferedImage atlas() throws IOException {
        return ImageIO.read(Path.of("src/main/resources/assets/ralle/textures/gui/wheel/create_dissolve.png").toFile());
    }

    static int contributor(BufferedImage atlas, int x, int y) {
        int index = y / 8 * 512 + x / 8;
        int encoded = atlas.getRGB(index % 2048, atlas.getHeight() - 128 + index / 2048);
        int tile = (encoded >> 16 & 255) | (encoded >> 8 & 255) << 8 | (encoded & 255) << 16;
        return atlas.getRGB(tile % 256 * 8 + x % 8, tile / 256 * 8 + y % 8);
    }

    static double threshold(int x, int y) {
        int hash = x * 0x1f123bb5 ^ y * 0x5f356495;
        hash ^= hash >>> 16;
        hash *= 0x45d9f3b;
        hash ^= hash >>> 16;
        return .12 + .52 * (hash & 0xffff) / 65535d;
    }

    /** Source RGBA is premultiplied, just like the real GPU capture. */
    static BufferedImage render(BufferedImage source, int frame, BufferedImage atlas) {
        int w = source.getWidth(), h = source.getHeight();
        var pixels = new double[w * h * 4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (atlas == null) {
                double threshold = threshold(x - w / 2, y - h / 2);
                double dust = Math.clamp((frame / 63d - threshold) / .36, 0, 1);
                int dx = (int) Math.round(dust * (3 + 4 * threshold));
                int dy = (int) Math.round(dust * 6);
                int tx = x + dx, ty = y - dy;
                if (tx < w && ty >= 0) blend(pixels, (ty * w + tx) * 4, source.getRGB(x, y), (int) Math.round((1 - dust) * 255));
            } else {
                int ax = frame % 8 * 256 + Math.floorMod(x - w / 2 + 128, 256);
                int ay = frame / 8 * 256 + Math.floorMod(y - h / 2 + 128, 256);
                for (int page = 0; page < 4; page++) {
                    int packed = contributor(atlas, ax + page % 2 * 2048, ay + page / 2 * 2048);
                    for (int pair = 0; pair < 2; pair++) {
                        int code = pair == 0 ? packed >> 16 & 255 : packed & 255;
                        int opacity = pair == 0 ? packed >> 8 & 255 : packed >>> 24;
                        if (code == 0) continue;
                        int sx = x - (code - 1) % 7, sy = y + (code - 1) / 7;
                        if (sx >= 0 && sy < h) blend(pixels, (y * w + x) * 4, source.getRGB(sx, sy), opacity);
                    }
                }
            }
        }
        var result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int at = (y * w + x) * 4;
            double alpha = pixels[at + 3];
            int color = (int) Math.round(alpha * 255) << 24;
            if (alpha > 0) for (int c = 0; c < 3; c++) color |= (int) Math.round(pixels[at + c] / alpha * 255) << (16 - c * 8);
            result.setRGB(x, y, color);
        }
        return result;
    }

    private static void blend(double[] out, int at, int source, int opacity) {
        double factor = opacity / 255d;
        double alpha = (source >>> 24) / 255d * factor;
        for (int c = 0; c < 3; c++) out[at + c] = ((source >> (16 - c * 8)) & 255) / 255d * factor + out[at + c] * (1 - alpha);
        out[at + 3] = alpha + out[at + 3] * (1 - alpha);
    }
}

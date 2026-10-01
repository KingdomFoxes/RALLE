package org.kingdomfoxes.ralle.cosmetics;

/** CPU reference for the approved seeded, two-octave liquid material. No rendering or network side effects. */
public final class LiquidMaterial {
    public static final int SEED = 7349;
    public static final double SPEED = .45;
    public static final double SCALE = 4;
    public static final double WARP = .63;
    public static final double SHINE = .71;
    private static final double[][] GRADIENTS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {.707, .707}, {-.707, .707}, {.707, -.707}, {-.707, -.707}
    };

    private LiquidMaterial() {}

    public static int width(double resolution) {
        checkResolution(resolution);
        return (int) Math.round(256 / resolution);
    }

    public static int height(double resolution) {
        checkResolution(resolution);
        return (int) Math.round(64 / resolution);
    }

    /** Fills a caller-owned RGB buffer. Resolution controls texture density, never screen geometry. */
    public static void fill(NameplateStyle style, double resolution, double timeSeconds,
                            int width, int height, int[] rgb) {
        checkResolution(resolution);
        if (style == null || !Double.isFinite(timeSeconds)
                || width != width(resolution) || height != height(resolution)
                || rgb == null || rgb.length < width * height) throw new IllegalArgumentException("Invalid material frame");
        double time = timeSeconds * SPEED;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            double u = (double) x / width, v = (double) y / height;
            double px = u * 8 / SCALE, py = v * 2 / SCALE;
            double q = fbm(px + time * .16, py - time * .09);
            double r = fbm(px + 4.7 - time * .1, py + 8.1 + time * .11);
            double n = fbm(px + q * WARP * 3, py + r * WARP * 3);
            double fold = (px * .55 + py * .32 + n * 2.4) * Math.PI * 3 + time * .6;
            double band = .5 + .5 * Math.sin(fold);
            double light = .19 + band * .62 + Math.pow(band, 14) * .19;
            light = clamp(.52 + (light - .5) * (.55 + SHINE));
            int a = light < .55 ? style.shadow() : style.midtone();
            int b = light < .55 ? style.midtone() : style.highlight();
            double mix = light < .55 ? light / .55 : (light - .55) / .45;
            rgb[y * width + x] = 0xff000000 | channel(a, b, 16, mix) << 16
                    | channel(a, b, 8, mix) << 8 | channel(a, b, 0, mix);
        }
    }

    private static void checkResolution(double resolution) {
        if (resolution != .5 && resolution != 1 && resolution != 2)
            throw new IllegalArgumentException("Unknown material resolution");
    }

    private static int channel(int a, int b, int shift, double mix) {
        return (int) (((a >> shift) & 255) + (((b >> shift) & 255) - ((a >> shift) & 255)) * mix);
    }

    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static double smooth(double value) { return value * value * value * (value * (value * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static int hash(int x, int y) {
        int h = x * 374761393 ^ y * 668265263 ^ SEED;
        h = (h ^ h >>> 13) * 1274126177;
        return h ^ h >>> 16;
    }
    private static double dot(int x, int y, double dx, double dy) {
        double[] gradient = GRADIENTS[hash(x, y) & 7];
        return gradient[0] * dx + gradient[1] * dy;
    }
    private static double noise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy, u = smooth(fx), v = smooth(fy);
        return lerp(lerp(dot(ix, iy, fx, fy), dot(ix + 1, iy, fx - 1, fy), u),
                lerp(dot(ix, iy + 1, fx, fy - 1), dot(ix + 1, iy + 1, fx - 1, fy - 1), u), v);
    }
    private static double fbm(double x, double y) {
        return noise(x, y) * .7 + noise(x * 2.03 + 13.1, y * 2.03 + 7.7) * .3;
    }
}

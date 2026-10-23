import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;

/** Rebuild with Java 21: java scripts/GenerateClassIce.java (no external assets/dependencies). */
class GenerateClassIce {
    static final int W = 768, H = 432;
    static final double[] arrival = new double[W * H];
    static final Random random = new Random(0x52414c4c45L);
    /** Every tip continues to the viewport boundary; old stem pixels never dilate. */
    static void branch(double x, double y, double angle, double start, int depth) {
        double vx = Math.cos(angle) * .5, vy = Math.sin(angle) * .5;
        // A straight ray must leave this rectangle within its diagonal length.
        int bound = (int) Math.ceil(Math.hypot(W, H) * 2) + 4;
        int spacing = depth == 2 ? 52 : 38;
        for (int s = 0; s < bound; s++) {
            double px = x + vx * s, py = y + vy * s;
            int ix = (int) Math.round(px), iy = (int) Math.round(py);
            if (ix < 0 || iy < 0 || ix >= W || iy >= H) return;
            double time = start + s * .5 * (.72 / 470);
            arrival[iy * W + ix] = Math.min(arrival[iy * W + ix], time);
            if (depth > 0 && s > 0 && s % spacing == 0) {
                for (int sign : new int[] {-1, 1}) {
                    branch(px, py, angle + sign * (.70 + random.nextDouble() * .18), time, depth - 1);
                }
            }
        }
        throw new IllegalStateException("Branch failed to leave viewport");
    }
    public static void main(String[] args) throws Exception {
        for (int style = 1; style <= 4; style++) generate(style, args);
    }
    static void generate(int style, String[] args) throws Exception {
        random.setSeed(0x52414c4c45L);
        Arrays.fill(arrival, Double.POSITIVE_INFINITY);
        if (style == 3) {
            // Staggered nucleation, with Manhattan-distance growth producing diamonds.
            for (int seed = 0; seed < 18; seed++) {
                int sx = 30 + random.nextInt(W - 60), sy = 30 + random.nextInt(H - 60);
                double start = seed == 0 ? 0 : .035 + seed * .024;
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                    int i = y * W + x;
                    arrival[i] = Math.min(arrival[i], start + (Math.abs(x - sx) + Math.abs(y - sy)) * .003);
                }
            }
        } else {
            for (int i = 0; i < (style == 1 ? 12 : style == 2 ? 4 : 2); i++) {
                double x, y;
                if (style != 1) {
                    x = (i % 2 == 0) ? 0 : W - 1;
                    y = (style == 4 ? i == 0 : i < 2) ? 0 : H - 1;
                } else if (i < 6) { x = W * ((i % 3 + .3 + random.nextDouble() * .4) / 3); y = i < 3 ? 0 : H - 1; }
                else { x = i < 9 ? 0 : W - 1; y = H * ((i % 3 + .3 + random.nextDouble() * .4) / 3); }
                double angle = Math.atan2(H * .5 - y, W * .5 - x) + (random.nextDouble() - .5) * .5;
                branch(x, y, angle, i * .004, 2);
            }
        }
        double max = Arrays.stream(arrival).filter(Double::isFinite).max().orElseThrow();
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < arrival.length; i++) {
            // Only the terminal full-coverage frame closes the inter-branch pinholes.
            int t = Double.isFinite(arrival[i]) ? 1 + (int) Math.round(arrival[i] / max * 65534) : 65535;
            image.setRGB(i % W, i / W, ((t >> 8) << 16) | ((t & 255) << 8));
        }
        String suffix = style == 1 ? "" : "_style" + style;
        ImageIO.write(image, "png", Path.of("src/main/resources/assets/ralle/textures/gui/class_ice" + suffix + ".png").toFile());
        if (args.length > 0) {
            int[] dx = {-1, 1, 0, 0}, dy = {0, 0, -1, 1};
            var preview = new BufferedImage(W * 3, H * 2, BufferedImage.TYPE_INT_RGB);
            double[] stages = {.015, .045, .1, .2, .45, 1};
            for (int stage = 0; stage < stages.length; stage++) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int limit = (int) (65535 * stages[stage]);
                boolean frozen = (image.getRGB(x, y) >> 8 & 65535) <= limit;
                boolean edge = false;
                for (int n = 0; n < 4; n++) {
                    int nx = Math.clamp(x + dx[n], 0, W - 1), ny = Math.clamp(y + dy[n], 0, H - 1);
                    edge |= (image.getRGB(nx, ny) >> 8 & 65535) > limit;
                }
                preview.setRGB(stage % 3 * W + x, stage / 3 * H + y, frozen ? edge ? 0xFFFFFF : 0 : 0x41617C);
            }
            ImageIO.write(preview, "png", Path.of(args[0] + "-style" + style + ".png").toFile());
        }
    }
}

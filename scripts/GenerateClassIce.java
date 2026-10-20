import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.*;

/** Rebuild with Java 21: java scripts/GenerateClassIce.java (no external assets/dependencies). */
class GenerateClassIce {
    static final int W = 768, H = 432;
    static final double[] arrival = new double[W * H];
    static final Random random = new Random(0x52414c4c45L);
    record Cell(int index, double time) implements Comparable<Cell> {
        public int compareTo(Cell other) { return Double.compare(time, other.time); }
    }
    static void branch(double x, double y, double angle, double length, double start, int depth) {
        double speed = .72 / 470;
        int steps = (int) Math.ceil(length * 2);
        double bend = (random.nextDouble() - .5) * .20;
        for (int s = 0; s <= steps; s++) {
            double distance = s * .5;
            double a = angle + bend * distance / length;
            double px = x + Math.cos(a) * distance, py = y + Math.sin(a) * distance;
            int ix = (int) Math.round(px), iy = (int) Math.round(py);
            if (ix < 0 || iy < 0 || ix >= W || iy >= H) continue;
            double time = start + distance * speed;
            arrival[iy * W + ix] = Math.min(arrival[iy * W + ix], time);
            // Paired, angled side shoots share the parent's exact arrival time.
            if (depth > 0 && s > 14 && s % (depth == 2 ? 26 : 14) == 0) {
                double child = length * (1 - distance / length) * (depth == 2 ? .36 : .30);
                for (int sign : new int[] {-1, 1}) branch(px, py, a + sign * (.70 + random.nextDouble() * .18), child, time, depth - 1);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Arrays.fill(arrival, Double.POSITIVE_INFINITY);
        for (int i = 0; i < 12; i++) {
            double x, y;
            if (i < 6) { x = W * ((i % 3 + .3 + random.nextDouble() * .4) / 3); y = i < 3 ? 0 : H - 1; }
            else { x = i < 9 ? 0 : W - 1; y = H * ((i % 3 + .3 + random.nextDouble() * .4) / 3); }
            double angle = Math.atan2(H * .5 - y, W * .5 - x) + (random.nextDouble() - .5) * .5;
            branch(x, y, angle, 230 + random.nextDouble() * 230, i * .004, 2);
        }
        // First grow the fine dendrites; only then let a second arrival wave thicken/fill them.
        // Using one simultaneous distance field would turn the crystals into round blobs too early.
        double stemMax = Arrays.stream(arrival).filter(Double::isFinite).max().orElseThrow();
        double[] fill = new double[arrival.length];
        Arrays.fill(fill, Double.POSITIVE_INFINITY);
        var queue = new PriorityQueue<Cell>();
        for (int i = 0; i < arrival.length; i++) if (Double.isFinite(arrival[i])) {
            arrival[i] = arrival[i] / stemMax * .62;
            fill[i] = .62 + arrival[i] * .25;
            queue.add(new Cell(i, fill[i]));
        }
        int[] dx = {-1, 1, 0, 0}, dy = {0, 0, -1, 1};
        while (!queue.isEmpty()) {
            var cell = queue.remove();
            if (cell.time != fill[cell.index]) continue;
            int x = cell.index % W, y = cell.index / W;
            for (int n = 0; n < 4; n++) {
                int nx = x + dx[n], ny = y + dy[n];
                if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                int next = ny * W + nx;
                double time = cell.time + .006;
                if (time < fill[next]) { fill[next] = time; queue.add(new Cell(next, time)); }
            }
        }
        for (int i = 0; i < arrival.length; i++) arrival[i] = Math.min(arrival[i], fill[i]);
        double max = Arrays.stream(arrival).max().orElseThrow();
        var image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < arrival.length; i++) {
            int t = 1 + (int) Math.round(arrival[i] / max * 65534);
            image.setRGB(i % W, i / W, ((t >> 8) << 16) | ((t & 255) << 8));
        }
        ImageIO.write(image, "png", Path.of("src/main/resources/assets/ralle/textures/gui/class_ice.png").toFile());
        if (args.length > 0) {
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
            ImageIO.write(preview, "png", Path.of(args[0]).toFile());
        }
    }
}

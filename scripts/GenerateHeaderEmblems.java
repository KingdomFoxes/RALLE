import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Run from the repository root with: java scripts/GenerateHeaderEmblems.java */
class GenerateHeaderEmblems {
    private static final Path GUI = Path.of("src/main/resources/assets/ralle/textures/gui");

    public static void main(String[] args) throws Exception {
        var source = ImageIO.read(GUI.resolve("fox_overlay.png").toFile());
        if (source == null) throw new IllegalArgumentException("Missing Fox crest source");
        Files.createDirectories(GUI.resolve("header"));
        for (int scale = 1; scale <= 16; scale++) {
            var image = downsample(source, 18 * scale, 28 * scale, true);
            ImageIO.write(image, "png", GUI.resolve("header/fox_" + scale + "x.png").toFile());
        }
        System.out.println("Generated native-pixel header crests for GUI scales 1 through 16.");
    }

    /** Integrate alpha across each complete source footprint, including fractional edge pixels. */
    static BufferedImage downsample(BufferedImage source, int width, int height) {
        return downsample(source, width, height, false);
    }

    static BufferedImage downsample(BufferedImage source, int width, int height, boolean shadow) {
        // One logical pixel of right/bottom gutter contains the half-pixel shadow.
        int outputWidth = width + (shadow ? width / 18 : 0);
        int outputHeight = height + (shadow ? height / 28 : 0);
        var result = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_ARGB);
        double stepX = source.getWidth() / (double) width;
        double stepY = source.getHeight() / (double) height;
        double shadowX = source.getWidth() / 36.0;
        double shadowY = source.getHeight() / 56.0;
        for (int y = 0; y < outputHeight; y++) {
            double top = y * stepY;
            double bottom = (y + 1) * stepY;
            for (int x = 0; x < outputWidth; x++) {
                double left = x * stepX;
                double right = (x + 1) * stepX;
                double coverage = 0;
                double whiteCoverage = 0;
                for (int sy = (int) top; sy < Math.ceil(bottom); sy++) {
                    double overlapY = Math.min(bottom, sy + 1) - Math.max(top, sy);
                    for (int sx = (int) left; sx < Math.ceil(right); sx++) {
                        double overlapX = Math.min(right, sx + 1) - Math.max(left, sx);
                        double white = alpha(source, sx, sy);
                        double black = shadow ? interpolatedAlpha(source, sx - shadowX, sy - shadowY) : 0;
                        double weight = overlapX * overlapY;
                        // Composite at source resolution before filtering premultiplied color.
                        // This keeps the white crest intact wherever it covers the shadow.
                        coverage += (white + black * (1 - white / 255.0)) * weight;
                        whiteCoverage += white * weight;
                    }
                }
                int alpha = (int) Math.round(coverage / (stepX * stepY));
                int gray = coverage == 0 ? 255 : (int) Math.round(255 * whiteCoverage / coverage);
                result.setRGB(x, y, (alpha << 24) | (gray * 0x010101));
            }
        }
        return result;
    }

    private static int alpha(BufferedImage source, int x, int y) {
        return x < 0 || y < 0 || x >= source.getWidth() || y >= source.getHeight()
                ? 0 : source.getRGB(x, y) >>> 24;
    }

    private static double interpolatedAlpha(BufferedImage source, double x, double y) {
        int left = (int) Math.floor(x), top = (int) Math.floor(y);
        double dx = x - left, dy = y - top;
        double upper = alpha(source, left, top) * (1 - dx) + alpha(source, left + 1, top) * dx;
        double lower = alpha(source, left, top + 1) * (1 - dx) + alpha(source, left + 1, top + 1) * dx;
        return upper * (1 - dy) + lower * dy;
    }
}

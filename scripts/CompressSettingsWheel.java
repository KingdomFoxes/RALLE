import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;

/** Deliberately crush the supplied wheel photo into a tiny, visibly degraded Minecraft texture. */
class CompressSettingsWheel {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the original wheel photo path");
        var source = ImageIO.read(Path.of(args[0]).toFile());
        var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, 64, 64, null);
        graphics.dispose();
        // Re-encode fifty times at 1% JPEG quality, then keep the damage in a PNG
        // because Minecraft's resource texture loader expects that format.
        for (int pass = 0; pass < 50; pass++) {
            var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            var settings = writer.getDefaultWriteParam();
            settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            settings.setCompressionQuality(.01f);
            var bytes = new ByteArrayOutputStream();
            try (var output = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, null), settings);
            } finally { writer.dispose(); }
            image = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        }
        byte[] shades = new byte[16];
        for (int i = 0; i < shades.length; i++) shades[i] = (byte) (i * 17);
        var palette = new IndexColorModel(4, 16, shades, shades, shades);
        var crushed = new BufferedImage(64, 64, BufferedImage.TYPE_BYTE_BINARY, palette);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int rgb = image.getRGB(x, y);
                int luminance = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000;
                crushed.getRaster().setSample(x, y, 0, Math.min(15, (luminance + 8) / 17));
            }
        }
        var target = Path.of("src/main/resources/assets/ralle/textures/gui/wheel/settings_wheel.png");
        ImageIO.write(crushed, "png", target.toFile());
        System.out.println("Wrote 64 x 64, 16-shade wheel after 50 JPEG passes: " + java.nio.file.Files.size(target) + " bytes");
    }
}

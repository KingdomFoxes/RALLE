package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelRendererTest {
    @Test void rasterMatchesAnnularHitGeometryAndLeavesCenterTransparent() throws Exception {
        var preview = new BufferedImage(360, 180, BufferedImage.TYPE_INT_ARGB);
        for (int mode = 0; mode < 2; mode++) {
            var sectors = SelectorWheelModel.ring(mode == 0 ? 6 : 3, mode == 0 ? 24 : 14,
                    mode == 0 ? 76 : 40, true);
            for (int i = 0; i < sectors.size(); i++) {
                var s = sectors.get(i);
                assertEquals(0, SelectorWheelRenderer.color(s, 0, 0, true));
                assertEquals(0, SelectorWheelRenderer.color(s, s.outer(), s.outer(), false));
                assertNotEquals(0, SelectorWheelRenderer.color(s, s.centerX(), s.centerY(), false));
                for (int y = -82; y < 82; y++) for (int x = -82; x < 82; x++) {
                    int pixel = SelectorWheelRenderer.color(s, x + .5, y + .5, i == 0);
                    if (pixel == 0) continue;
                    assertTrue(s.contains(x + .5, y + .5, 0));
                    preview.setRGB(90 + mode * 180 + x, 90 + y + (i == 0 ? -5 : 0), pixel);
                }
            }
        }
        var output = Path.of("build", "reports", "wheel-geometry.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(preview, "png", output.toFile());
    }
}

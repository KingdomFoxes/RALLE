package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelRendererTest {
    @Test void compactKickLayoutPreviewAtTwoScales() throws Exception {
        var preview = new BufferedImage(660, 400, BufferedImage.TYPE_INT_ARGB);
        var canvas = preview.createGraphics();
        canvas.setColor(new java.awt.Color(0x101824));
        canvas.fillRect(0, 0, 660, 400);
        for (int row = 0; row < 2; row++) for (int count = 1; count <= 3; count++) {
            double scale = row == 0 ? 1 : 1.5;
            int cx = (count - 1) * 220 + 110;
            int cy = row * 200 + 100;
            for (var sector : SelectorWheelModel.kickGeometry(count)) {
                for (int y = -40; y < 40; y++) for (int x = -40; x < 40; x++) {
                    int color = SelectorWheelRenderer.color(sector, x + .5, y + .5, false);
                    if (color == 0) continue;
                    canvas.setColor(new java.awt.Color(color, true));
                    int left = cx + (int) Math.floor(x * scale);
                    int top = cy + (int) Math.floor(y * scale);
                    canvas.fillRect(left, top, (int) Math.ceil(scale), (int) Math.ceil(scale));
                }
                canvas.setColor(new java.awt.Color(0xBC915F));
                canvas.fillRect(cx + (int) Math.round((sector.centerX() - 8) * scale),
                        cy + (int) Math.round((sector.centerY() - 8) * scale),
                        (int) (16 * scale), (int) (16 * scale));
            }
            canvas.setColor(java.awt.Color.WHITE);
            canvas.drawLine(cx - 3, cy, cx + 3, cy);
            canvas.drawLine(cx, cy - 3, cx, cy + 3);
            canvas.drawString(count + " member / scale " + scale, cx - 65, cy + 88);
        }
        canvas.dispose();
        var output = Path.of("build", "reports", "wheel-kick-layouts.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(preview, "png", output.toFile());
    }
    @Test void everyInteriorPixelUsesOnlySolidNavyAndHasNoGrayOrBlackEdgeArtifacts() {
        var sectors = SelectorWheelModel.ring(6, 24, 76, true);
        for (boolean selected : new boolean[] {false, true}) for (var sector : sectors) {
            int fill = selected ? 0xF0223552 : 0xEE0A1830;
            int outline = selected ? 0xFF000000 | RalleTheme.ACCENT_RGB : 0xFFFFFFFF;
            for (int y = -76; y < 76; y++) for (int x = -76; x < 76; x++) {
                int color = SelectorWheelRenderer.createColor(sector, x + .5, y + .5, selected);
                assertTrue(color == 0 || color == fill || color == outline,
                        "Unexpected edge color 0x" + Integer.toHexString(color) + " at " + x + "," + y);
                if (sector.contains(x + .5, y + .5, 1)) assertEquals(fill, color);
            }
        }
    }

    @Test void allKickVariantsUseTheCleanCreatePalette() {
        for (int count = 1; count <= 3; count++) for (var sector : SelectorWheelModel.kickGeometry(count)) {
            for (boolean selected : new boolean[] {false, true}) {
                int fill = selected ? 0xF0223552 : 0xEE0A1830;
                int outline = selected ? 0xFF000000 | RalleTheme.ACCENT_RGB : 0xFFFFFFFF;
                for (int y = -40; y < 40; y++) for (int x = -40; x < 40; x++) {
                    int color = SelectorWheelRenderer.color(sector, x + .5, y + .5, selected);
                    assertTrue(color == 0 || color == fill || color == outline);
                }
            }
        }
    }

    @Test void premadeFadePreviewKeepsOtherSectorsAndEndsWithAnEmptySelectedSector() throws Exception {
        var preview = new BufferedImage(880, 460, BufferedImage.TYPE_INT_ARGB);
        var canvas = preview.createGraphics();
        canvas.setColor(new java.awt.Color(0x101824));
        canvas.fillRect(0, 0, 880, 460);
        var ring = SelectorWheelModel.ring(6, 24, 76, true);
        var mask = ImageIO.read(Path.of("src/main/resources/assets/ralle/textures/gui/wheel/create_dissolve.png").toFile());
        double[] stages = {0, .35, .7, 1};
        for (int row = 0; row < 2; row++) for (int frame = 0; frame < stages.length; frame++) {
            double scale = row == 0 ? 1 : .75;
            int cx = frame * 220 + 110;
            int cy = row * 220 + 120;
            for (int i = 1; i < ring.size(); i++) {
                for (int y = -76; y < 76; y++) for (int x = -76; x < 76; x++) {
                    int color = SelectorWheelRenderer.createColor(ring.get(i), x + .5, y + .5, false);
                    if (color == 0) continue;
                    canvas.setColor(new java.awt.Color(color, true));
                    canvas.fillRect(cx + (int) Math.floor(x * scale), cy + (int) Math.floor(y * scale), 1, 1);
                }
            }
            int[] count = {0};
            var source = new BufferedImage(168, 168, BufferedImage.TYPE_INT_ARGB);
            for (int y = -76; y < 76; y++) for (int x = -76; x < 76; x++) {
                int color = SelectorWheelRenderer.createColor(ring.getFirst(), x + .5, y + .5, true);
                int alpha = color >>> 24;
                int premultiplied = alpha << 24;
                for (int shift : new int[] {16, 8, 0}) premultiplied |= ((color >> shift & 255) * alpha / 255) << shift;
                source.setRGB(x + 84, y + 84, premultiplied);
            }
            var dust = CreateDustReference.render(source, CreateWheelAnimation.frame(stages[frame]), mask);
            for (int y = 0; y < 168; y++) for (int x = 0; x < 168; x++) {
                int color = dust.getRGB(x, y);
                if (color >>> 24 == 0) continue;
                count[0]++;
                canvas.setColor(new java.awt.Color(color, true));
                canvas.fillRect(cx + (int) Math.floor((x - 84) * scale),
                        cy + (int) Math.floor((y - 84 - (stages[frame] == 0 ? 5 : 10)) * scale), 1, 1);
            }
            if (stages[frame] == 1) assertEquals(0, count[0]);
            else assertTrue(count[0] > 0);
            canvas.setColor(java.awt.Color.WHITE);
            canvas.drawString((int) (stages[frame] * 100) + "% / scale " + scale, cx - 65, cy + 95);
        }
        canvas.dispose();
        var output = Path.of("build", "reports", "wheel-create-dust.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(preview, "png", output.toFile());
    }

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
                    int pixel = mode == 0
                            ? SelectorWheelRenderer.createColor(s, x + .5, y + .5, i == 0)
                            : SelectorWheelRenderer.color(s, x + .5, y + .5, i == 0);
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

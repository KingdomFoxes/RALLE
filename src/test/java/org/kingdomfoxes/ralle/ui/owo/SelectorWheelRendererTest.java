package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelRendererTest {
    @Test void croppedImagesMatchOriginalPixelsForEveryRingAndBothHoverStates() {
        var random = new java.util.Random(0xFACE);
        for (int count : new int[] {1, 7, 8, 15, 32, 64}) {
            long imagePixels = 0;
            for (var sector : SelectorWheelModel.classGeometry(count)) {
                var raster = SelectorWheelRenderer.rasterize(sector);
                imagePixels += (long) raster.width() * raster.height();
                for (int y = 0; y < raster.height(); y++) for (int x = 0; x < raster.width(); x++) {
                    for (boolean selected : new boolean[] {false, true})
                        assertEquals(SelectorWheelRenderer.color(sector, raster.left() + x + .5,
                                raster.top() + y + .5, selected), raster.colorAt(x, y, selected));
                }
                // Independently sample outside the crop to detect any clipped arc/border pixels.
                int extent = (int) Math.ceil(sector.outer());
                for (int sample = 0; sample < 4096; sample++) {
                    int x = random.nextInt(-extent, extent), y = random.nextInt(-extent, extent);
                    if (x >= raster.left() && x < raster.left() + raster.width()
                            && y >= raster.top() && y < raster.top() + raster.height()) continue;
                    assertEquals(0, SelectorWheelRenderer.color(sector, x + .5, y + .5, false));
                }
            }
            // Both states fit comfortably in a bounded screen-owned cache even at the 64-class limit.
            assertTrue(imagePixels * 2 * Integer.BYTES < 48L * 1024 * 1024,
                    "Texture budget exceeded for " + count + " options");
        }
    }

    @Test void writesClassHalfCircleAndConcentricRingPreview() throws Exception {
        int panelSize = 500;
        var preview = new BufferedImage(panelSize * 3, panelSize * 2, BufferedImage.TYPE_INT_ARGB);
        var canvas = preview.createGraphics();
        canvas.setColor(new java.awt.Color(0x101824));
        canvas.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        int[] counts = {1, 5, 7, 8, 14, 15};
        for (int panel = 0; panel < counts.length; panel++) {
            int cx = panel % 3 * panelSize + panelSize / 2;
            int cy = panel / 3 * panelSize + panelSize / 2;
            var sectors = SelectorWheelModel.classGeometry(counts[panel]);
            for (int i = 0; i < sectors.size(); i++) {
                var sector = sectors.get(i);
                int dx = i == 0 ? (int) Math.round(Math.cos(sector.angle()) * 5) : 0;
                int dy = i == 0 ? (int) Math.round(Math.sin(sector.angle()) * 5) : 0;
                var raster = SelectorWheelRenderer.rasterize(sector);
                for (int y = 0; y < raster.height(); y++) for (int x = 0; x < raster.width(); x++) {
                    int color = raster.colorAt(x, y, i == 0);
                    if (color != 0) preview.setRGB(cx + raster.left() + x + dx, cy + raster.top() + y + dy, color);
                }
                int x = cx + (int) Math.round(sector.centerX()) + dx;
                int y = cy + (int) Math.round(sector.centerY()) + dy;
                canvas.setColor(new java.awt.Color(0xBC915F));
                canvas.fillRect(x - 8, y - 22, 16, 16);
                canvas.setColor(java.awt.Color.WHITE);
                canvas.drawString("Class " + (i + 1), x - 18, y + 4);
                canvas.drawString("[K]", x - 8, y + 27);
            }
            canvas.setColor(java.awt.Color.WHITE);
            canvas.drawLine(cx - 3, cy, cx + 3, cy);
            canvas.drawLine(cx, cy - 3, cx, cy + 3);
            canvas.drawString(counts[panel] + " class options", cx - 45, cy - 235);
        }
        canvas.dispose();
        var output = Path.of("build", "reports", "wheel-class-rings.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(preview, "png", output.toFile());
    }

    @Test void writesBarsGeometryPreviewUsingSharedFaceColors() throws Exception {
        var preview = new BufferedImage(1020, 860, BufferedImage.TYPE_INT_ARGB);
        var canvas = preview.createGraphics();
        canvas.setColor(new java.awt.Color(0x101824));
        canvas.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        int[] counts = {5, 6, 12, 18, 1, 0};
        for (int panel = 0; panel < counts.length; panel++) {
            int cx = panel % 3 * 340 + 170, cy = panel / 3 * 430 + 225;
            int side = panel < 4 ? 160 : 80;
            int viewport = panel >= 1 && panel <= 3 ? 280 : 600;
            var faces = SelectorWheelLayout.bars(counts[panel], side, viewport);
            for (int i = 0; i < faces.size(); i++) {
                var face = faces.get(i);
                int dy = i == 0 ? -5 : 0;
                canvas.setColor(new java.awt.Color(SelectorWheelRenderer.outlineColor(i == 0), true));
                canvas.fillRect(cx + face.left(), cy + face.top() + dy, face.right() - face.left(), face.bottom() - face.top());
                canvas.setColor(new java.awt.Color(SelectorWheelRenderer.fillColor(i == 0), true));
                canvas.fillRect(cx + face.left() + 1, cy + face.top() + dy + 1, face.right() - face.left() - 2, face.bottom() - face.top() - 2);
                canvas.setColor(java.awt.Color.WHITE);
                canvas.drawString(Integer.toString(i + 1), cx + (int) face.centerX() - 3, cy + (int) face.centerY() + dy);
            }
            canvas.setColor(java.awt.Color.WHITE);
            canvas.drawString(counts[panel] + " options / S=" + side + (panel == 1 ? " / wrapped" : ""), cx - 90, cy - 195);
        }
        canvas.dispose();
        var output = Path.of("build", "reports", "wheel-bars-layouts.png");
        Files.createDirectories(output.getParent());
        ImageIO.write(preview, "png", output.toFile());
    }
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

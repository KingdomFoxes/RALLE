package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelViewportTest {
    @Test void allFifteenClassOptionsFitAtTheReportedScreenshotSizeEvenWithALongNameInTheFirstFive() throws Exception {
        // Approximately 1250 x 1003 framebuffer pixels at GUI scale 2.
        var widths = new java.util.ArrayList<>(java.util.Collections.nCopies(15, 80));
        widths.set(4, 100);
        var faces = SelectorWheelLayout.classBars(widths, 625);
        var viewport = new SelectorWheelViewport();
        viewport.layout(faces, 501);
        assertEquals(15, faces.size());
        assertFalse(viewport.scrollable());
        for (var face : faces) {
            assertEquals(88, face.bottom() - face.top());
            assertTrue(viewport.contains(viewport.originY() + face.top() - SelectorWheelModel.EXTENSION));
            assertTrue(viewport.contains(viewport.originY() + face.bottom() - .5));
        }
        var image = new java.awt.image.BufferedImage(1250, 1002, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var canvas = image.createGraphics();
        canvas.scale(2, 2);
        canvas.setColor(new java.awt.Color(0x101824));
        canvas.fillRect(0, 0, 625, 501);
        canvas.setColor(java.awt.Color.WHITE);
        canvas.drawString("15 class options / fixed 88-pixel height", 170, 30);
        canvas.translate(625 / 2d, viewport.originY());
        for (int i = 0; i < faces.size(); i++) {
            var face = faces.get(i);
            canvas.setColor(new java.awt.Color(SelectorWheelRenderer.outlineColor(false), true));
            canvas.fillRect(face.left(), face.top(), face.right() - face.left(), face.bottom() - face.top());
            canvas.setColor(new java.awt.Color(SelectorWheelRenderer.fillColor(false), true));
            canvas.fillRect(face.left() + 1, face.top() + 1, face.right() - face.left() - 2, face.bottom() - face.top() - 2);
            int x = (int) face.centerX(), y = (int) face.centerY();
            canvas.setColor(new java.awt.Color(0xBC915F));
            canvas.fillRect(x - 8, y - 22, 16, 16);
            canvas.setColor(java.awt.Color.WHITE);
            String label = "Class " + (i + 1);
            canvas.drawString(label, x - canvas.getFontMetrics().stringWidth(label) / 2, y + 6);
        }
        canvas.dispose();
        var output = java.nio.file.Path.of("build/reports/wheel-class-bars-15.png");
        java.nio.file.Files.createDirectories(output.getParent());
        javax.imageio.ImageIO.write(image, "png", output.toFile());
    }

    @Test void fittingWrappedRowsIncludeEveryBottomOutlineBelowTheScreenCenter() {
        var faces = SelectorWheelLayout.bars(12, 200, 374);
        var viewport = new SelectorWheelViewport();
        viewport.layout(faces, 430);
        assertFalse(viewport.scrollable());
        for (var face : faces) {
            assertTrue(viewport.contains(viewport.originY() + face.top() - SelectorWheelModel.EXTENSION));
            assertTrue(viewport.contains(viewport.originY() + face.bottom() - .5));
        }
    }

    @Test void keyboardCanRevealBottomOutlinesOfSecondThirdAndLaterRowsWithoutResizing() {
        var faces = SelectorWheelLayout.bars(28, 200, 374);
        var viewport = new SelectorWheelViewport();
        viewport.layout(faces, 260);
        assertTrue(viewport.scrollable());
        for (var face : faces) {
            viewport.reveal(face, faces);
            assertTrue(viewport.contains(viewport.originY() + face.top() - SelectorWheelModel.EXTENSION));
            assertTrue(viewport.contains(viewport.originY() + face.bottom() - .5));
            assertEquals(40, face.right() - face.left());
            assertEquals(110, face.bottom() - face.top());
        }
        viewport.scroll(-10000, faces);
        assertTrue(viewport.contains(viewport.originY() + faces.getFirst().top() - SelectorWheelModel.EXTENSION));
        viewport.scroll(10000, faces);
        assertTrue(viewport.contains(viewport.originY() + faces.getLast().bottom() - .5));
    }
}

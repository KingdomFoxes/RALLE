package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelLayoutTest {
    @Test void classBarHeightIsTheOriginalCompactHeightRegardlessOfCountOrAnyNameLength() {
        for (int count = 1; count <= 28; count++) {
            for (int labelWidth : new int[] {0, 24, 42, 80, 100, 300}) {
                var widths = java.util.Collections.nCopies(count, labelWidth);
                for (int viewportWidth : new int[] {375, 633, 1000}) {
                    var faces = SelectorWheelLayout.classBars(widths, viewportWidth);
                    for (var face : faces) assertEquals(88, face.bottom() - face.top());
                }
            }
        }
    }

    @Test void additionalClassBarsKeepTheFiveOptionFaceSizeAcrossMultipleRows() {
        int side = SelectorWheelLayout.referenceSide(80, 600);
        var five = SelectorWheelLayout.bars(5, side, 600).getFirst();
        for (int count = 6; count <= 28; count++) {
            var faces = SelectorWheelLayout.bars(count, side, 600);
            for (var face : faces) {
                assertEquals(five.right() - five.left(), face.right() - face.left());
                assertEquals(five.bottom() - five.top(), face.bottom() - face.top());
            }
        }
    }

    @Test void initialWidthDividesEquallyAndHeightIsReducedByFortyFivePercent() {
        assertTrue(SelectorWheelLayout.bars(0, 120, 500).isEmpty());
        for (int count = 1; count <= 5; count++) {
            var faces = SelectorWheelLayout.bars(count, 120, 500);
            assertEquals(-60, faces.getFirst().left());
            assertEquals(60, faces.getLast().right());
            for (int i = 0; i < count; i++) {
                var face = faces.get(i);
                assertEquals(120 / count, face.right() - face.left());
                assertEquals(66, face.bottom() - face.top());
                assertEquals(i, SelectorWheelLayout.hit(faces, face.centerX(), face.centerY()));
            }
        }
    }

    @Test void sixthOptionStartsACenteredSecondRowEvenOnWideScreens() {
        for (int viewportWidth : new int[] {374, 375, 500, 1000}) {
            var faces = SelectorWheelLayout.bars(6, 200, viewportWidth);
            for (var face : faces) {
                assertEquals(40, face.right() - face.left());
                assertEquals(110, face.bottom() - face.top());
            }
            assertEquals(faces.getFirst().top(), faces.get(4).top());
            assertEquals(8, faces.getLast().top() - faces.getFirst().bottom());
            assertEquals(-20, faces.getLast().left());
            assertEquals(20, faces.getLast().right());
        }
    }

    @Test void kickBarsRetainTheirWidthsWithTheReducedHeight() {
        for (int count = 1; count <= 3; count++) {
            var faces = SelectorWheelLayout.bars(count, 80, 500);
            assertEquals(80, faces.getLast().right() - faces.getFirst().left());
            for (var face : faces) assertEquals(44, face.bottom() - face.top());
        }
    }

    @Test void roundingNeverAddsAnUnexpectedRowToTheInitialSquare() {
        var faces = SelectorWheelLayout.bars(3, 190, 297); // cap 190
        assertEquals(3, faces.size());
        assertEquals(faces.getFirst().top(), faces.getLast().top());
        assertEquals(190, faces.getLast().right() - faces.getFirst().left());
    }

    @Test void barHitEnvelopeIsStableAndSharedEdgesHaveOneOwner() {
        var faces = SelectorWheelLayout.bars(3, 120, 500);
        var face = faces.getFirst();
        assertEquals(0, SelectorWheelLayout.hit(faces, face.centerX(), face.top() - 5));
        assertEquals(-1, SelectorWheelLayout.hit(faces, face.centerX(), face.top() - 6));
        assertEquals(1, SelectorWheelLayout.hit(faces, face.right(), face.centerY()));
        assertEquals(-1, SelectorWheelLayout.hit(faces, faces.getLast().right(), face.centerY()));
        assertEquals(0, face.motionX());
        assertEquals(-1, face.motionY());
    }

    @Test void circleAdapterPreservesExistingHitGeometryAndMotion() {
        var sectors = SelectorWheelModel.ring(6, 24, 76, true);
        var faces = SelectorWheelLayout.circles(sectors);
        for (int x = -85; x <= 85; x++) for (int y = -85; y <= 85; y++)
            assertEquals(SelectorWheelModel.hit(sectors, x, y), SelectorWheelLayout.hit(faces, x, y));
    }
}

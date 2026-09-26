package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HsvColorModelTest {
    @Test void rgbRoundTripAndHitRegionsAreStable() {
        assertEquals(0x228B22, HsvColorModel.toRgb(HsvColorModel.fromRgb(0x228B22)));
        assertEquals(HsvColorModel.Hit.WHEEL, HsvColorModel.hit(50, 1, 100));
        assertEquals(HsvColorModel.Hit.TRIANGLE, HsvColorModel.hit(50, 50, 100));
        assertEquals(HsvColorModel.Hit.NONE, HsvColorModel.hit(1, 1, 100));
    }

    @Test void hexAndRainbowDraftStaySynchronizedAndCancelRestoresOriginal() {
        var original = new HighlightStyle(0x112233, false);
        var draft = new ColorStyleDraft(original);
        draft.hex("#ABCDEF");
        draft.rainbow(true);
        assertEquals("#ABCDEF", draft.hex());
        assertEquals(new HighlightStyle(0xABCDEF, true), draft.style());
        assertEquals(original, draft.cancel());
        assertNotEquals(original, draft.style());
    }

    @Test void chromaAndRainbowAreExclusiveAndRgbSurvivesModeChanges() {
        var draft = new ColorStyleDraft(new HighlightStyle(0x123456, false));
        draft.chroma(true);
        assertEquals(new HighlightStyle(0x123456, false, true), draft.style());
        draft.rainbow(true);
        assertEquals(new HighlightStyle(0x123456, true, false), draft.style());
        draft.rainbow(false);
        assertEquals(new HighlightStyle(0x123456, false, false), draft.style());
    }

    @Test void triangleSupportsTheFullSaturationValueRange() {
        var original = new HsvColorModel.Hsv(.35f, .72f, .61f);
        var point = HsvColorModel.triangleSelection(original, 150);
        assertTrue(HsvColorModel.insideTriangle(point.x(), point.y(), 150));
        var sampled = HsvColorModel.updateTriangle(original, point.x(), point.y(), 150);
        assertEquals(original.saturation(), sampled.saturation(), .0001f);
        assertEquals(original.value(), sampled.value(), .0001f);
    }

    @Test void triangleIsCenteredAndSymmetric() {
        var triangle = HsvColorModel.triangle(150);
        double center = 75;
        assertEquals(center, triangle.hue().x(), .0001);
        assertEquals(center, (triangle.white().x() + triangle.black().x()) / 2, .0001);
        assertEquals(triangle.white().y(), triangle.black().y(), .0001);
        assertEquals(HsvColorModel.innerRadius(150),
                Math.hypot(triangle.hue().x() - center, triangle.hue().y() - center), .0001);
        assertEquals(HsvColorModel.innerRadius(150),
                Math.hypot(triangle.white().x() - center, triangle.white().y() - center), .0001);
        assertEquals(HsvColorModel.innerRadius(150),
                Math.hypot(triangle.black().x() - center, triangle.black().y() - center), .0001);
    }
}

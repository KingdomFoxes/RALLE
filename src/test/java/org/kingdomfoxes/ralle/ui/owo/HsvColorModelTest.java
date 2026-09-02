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

    @Test void triangleSupportsTheFullSaturationValueRange() {
        var original = new HsvColorModel.Hsv(.35f, .72f, .61f);
        var point = HsvColorModel.triangleSelection(original, 150);
        assertTrue(HsvColorModel.insideTriangle(point.x(), point.y(), 150));
        var sampled = HsvColorModel.updateTriangle(original, point.x(), point.y(), 150);
        assertEquals(original.saturation(), sampled.saturation(), .0001f);
        assertEquals(original.value(), sampled.value(), .0001f);
    }
}

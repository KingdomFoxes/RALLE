package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectorWheelModelTest {
    @Test void oneHoldSubmitsAtMostOnceAndRequiresRelease() {
        var model = new SelectorWheelModel();
        model.open(0); model.hover(0, 0);
        assertTrue(model.submit());
        assertFalse(model.submit());
        model.cancel();
        assertEquals(SelectorWheelModel.State.AWAITING_RELEASE, model.state());
        model.released();
        assertEquals(SelectorWheelModel.State.CLOSED, model.state());
    }

    @Test void hitRegionDoesNotMoveWithAnimation() {
        var geometry = SelectorWheelModel.ring(6, 24, 76, true);
        assertEquals(0, SelectorWheelModel.hit(geometry, 0, -50));
        assertEquals(0, SelectorWheelModel.hit(geometry, 0, -79));
        assertEquals(-1, SelectorWheelModel.hit(geometry, 0, 0));
        assertEquals(-1, SelectorWheelModel.hit(geometry, 0, -90));
    }

    @Test void rapidHoverReversalStartsAtCurrentOffset() {
        var model = new SelectorWheelModel();
        model.open(0); model.hover(0, 0);
        double halfway = model.offset(0, 60);
        model.hover(-1, 60);
        assertEquals(halfway, model.offset(0, 60), 0.0001);
        assertEquals(0, model.offset(0, 180), 0.0001);
    }

    @Test void oldSegmentSlidesInWhileNewSegmentSlidesOut() {
        var model = new SelectorWheelModel();
        model.open(0); model.hover(0, 0);
        model.hover(1, 120);
        assertEquals(5, model.offset(0, 120));
        assertEquals(0, model.offset(1, 120));
        assertTrue(model.offset(0, 180) > 0);
        assertTrue(model.offset(1, 180) > 0);
        double previous = model.offset(0, 180);
        model.hover(0, 180);
        assertEquals(previous, model.offset(0, 180));
        assertEquals(5, model.offset(0, 300));
        assertEquals(0, model.offset(1, 300));
    }

    @Test void curvedSectorsOwnTheirAreaAndLeaveAnEmptyCenter() {
        for (int count = 1; count <= 6; count++) {
            var ring = SelectorWheelModel.ring(count, 14, 40, count != 2);
            for (int i = 0; i < count; i++) {
                var s = ring.get(i);
                assertEquals(i, SelectorWheelModel.hit(ring, s.centerX(), s.centerY()));
                assertFalse(s.contains(0, 0, 0));
                assertFalse(s.contains(40, 40, 0));
            }
        }
    }
}

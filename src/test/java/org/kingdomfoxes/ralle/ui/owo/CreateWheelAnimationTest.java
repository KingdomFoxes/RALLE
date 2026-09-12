package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreateWheelAnimationTest {
    @Test void dissolveUsesEightyPercentOfSampleThenHoldsForOneTenthSecond() {
        for (long duration : new long[] {3011, 3219, 3725}) {
            var animation = new CreateWheelAnimation(100, duration);
            assertEquals(0, animation.progress(100));
            long dissolve = Math.round(duration * .8);
            assertEquals(1, animation.progress(100 + dissolve));
            assertFalse(animation.finished(100 + dissolve + 99));
            assertTrue(animation.finished(100 + dissolve + 100));
            assertEquals(0, animation.extension(100));
            assertEquals(5, animation.extension(280));
        }
    }

    @Test void manyIndividualPixelsFadeConcurrentlyWithoutReappearing() {
        int partiallyFaded = 0;
        int untouched = 0;
        for (int y = -76; y < 76; y++) for (int x = -76; x < 76; x++) {
            assertEquals(1, CreateWheelAnimation.opacity(x, y, 0));
            assertEquals(0, CreateWheelAnimation.opacity(x, y, 1), .000001);
            assertTrue(CreateWheelAnimation.visible(x, y, 0));
            assertFalse(CreateWheelAnimation.visible(x, y, 1));
            double middle = CreateWheelAnimation.opacity(x, y, .5);
            assertTrue(middle >= CreateWheelAnimation.opacity(x, y, .6));
            if (middle > 0 && middle < 1) partiallyFaded++;
            if (middle == 1) untouched++;
        }
        assertTrue(partiallyFaded > 10_000);
        assertTrue(untouched > 100);
    }
}

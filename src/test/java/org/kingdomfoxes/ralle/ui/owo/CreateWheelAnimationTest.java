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

    @Test void premadeFramesFadeManyPixelsConcurrentlyWithoutReappearing() throws Exception {
        var atlas = javax.imageio.ImageIO.read(java.nio.file.Path.of(
                "src/main/resources/assets/ralle/textures/gui/wheel/create_dissolve.png").toFile());
        assertEquals(1024, atlas.getWidth());
        assertEquals(1024, atlas.getHeight());
        int partiallyFaded = 0;
        int untouched = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int previous = 255;
            for (int frame = 0; frame < 64; frame++) {
                int alpha = atlas.getRGB(frame % 8 * 128 + x, frame / 8 * 128 + y) >>> 24;
                assertTrue(alpha <= previous);
                previous = alpha;
                if (frame == 0) assertEquals(255, alpha);
                if (frame == 63) assertEquals(0, alpha);
                if (frame == 31 && alpha > 0 && alpha < 255) partiallyFaded++;
                if (frame == 31 && alpha == 255) untouched++;
            }
        }
        assertTrue(partiallyFaded > 10_000);
        assertTrue(untouched > 100);
    }

    @Test void frameSelectionIsBoundedAndRetainsFinalEmptyFrame() {
        assertEquals(0, CreateWheelAnimation.frame(-1));
        assertEquals(0, CreateWheelAnimation.frame(0));
        assertEquals(31, CreateWheelAnimation.frame(.5));
        assertEquals(63, CreateWheelAnimation.frame(1));
        assertEquals(63, CreateWheelAnimation.frame(2));
    }
}

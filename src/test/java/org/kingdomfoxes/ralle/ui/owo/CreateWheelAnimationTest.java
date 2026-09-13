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

    @Test void bakedDustMatchesOriginalTrajectoriesAndOverlapOrderForEveryFrame() throws Exception {
        var atlas = CreateDustReference.atlas();
        assertEquals(4096, atlas.getWidth());
        assertEquals(4096, atlas.getHeight());
        var source = new java.awt.image.BufferedImage(180, 180, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 172; y++) for (int x = 8; x < 172; x++) {
            if ((x + y) % 7 != 0) source.setRGB(x, y, 0xC0000000 | (x % 192) << 16 | (y % 192) << 8 | 80);
        }
        for (int frame = 0; frame < 64; frame++) {
            var expected = CreateDustReference.render(source, frame, null);
            var actual = CreateDustReference.render(source, frame, atlas);
            assertArrayEquals(expected.getRGB(0, 0, 180, 180, null, 0, 180),
                    actual.getRGB(0, 0, 180, 180, null, 0, 180), "Frame " + frame);
        }
    }

    @Test void frameSelectionIsBoundedAndRetainsFinalEmptyFrame() {
        assertEquals(0, CreateWheelAnimation.frame(-1));
        assertEquals(0, CreateWheelAnimation.frame(0));
        assertEquals(31, CreateWheelAnimation.frame(.5));
        assertEquals(63, CreateWheelAnimation.frame(1));
        assertEquals(63, CreateWheelAnimation.frame(2));
    }
}

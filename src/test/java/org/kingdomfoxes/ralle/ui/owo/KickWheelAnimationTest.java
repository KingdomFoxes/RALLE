package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class KickWheelAnimationTest {
    @Test void animationFinishesOnceAndUsesTheSuppliedFrameTiming() {
        assertEquals(0, KickWheelAnimation.frame(100, 100));
        assertEquals(0, KickWheelAnimation.frame(100, 179));
        assertEquals(1, KickWheelAnimation.frame(100, 180));
        assertEquals(16, KickWheelAnimation.frame(100, 1459));
        assertFalse(KickWheelAnimation.finished(100, 1459));
        assertTrue(KickWheelAnimation.finished(100, 1460));
        assertEquals(-1, KickWheelAnimation.frame(100, 10000));
    }

    @Test void spriteHasAllFramesAndNoSheetBackgroundOrGridPixels() throws Exception {
        var atlas = ImageIO.read(Path.of("src/main/resources/assets/ralle/textures/gui/wheel/kick_explosion.png").toFile());
        assertEquals(71 * 17, atlas.getWidth());
        assertEquals(100, atlas.getHeight());
        for (int x = 0; x < atlas.getWidth(); x++) for (int y = 0; y < atlas.getHeight(); y++) {
            int pixel = atlas.getRGB(x, y);
            if (pixel >>> 24 == 0) continue;
            assertNotEquals(0x79E6EA, pixel & 0xFFFFFF);
            assertNotEquals(0x37B1B6, pixel & 0xFFFFFF);
        }
    }
}

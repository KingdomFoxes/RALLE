package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class ClassIceMapTest {
    @Test void bakedRevealGrowsFromEdgesWithoutDetachedCrystalsAndCoversEveryPixel() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/ralle/textures/gui/class_ice.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            int w = image.getWidth(), h = image.getHeight();
            var times = new int[w * h];
            for (int i = 0; i < times.length; i++) {
                times[i] = image.getRGB(i % w, i / w) >> 8 & 65535;
                assertTrue(times[i] > 0 && times[i] <= 65535);
            }
            for (double progress : new double[] {.001, .01, .025, .05, .1, .2, .4, .7, 1}) {
                int threshold = (int) (65535 * progress), visible = 0;
                var seen = new boolean[times.length];
                var queue = new ArrayDeque<Integer>();
                for (int i = 0; i < times.length; i++) {
                    if (times[i] > threshold) continue;
                    visible++;
                    int x = i % w, y = i / w;
                    if (x == 0 || x == w - 1 || y == 0 || y == h - 1) { seen[i] = true; queue.add(i); }
                }
                int connected = 0;
                while (!queue.isEmpty()) {
                    int i = queue.remove(); connected++;
                    int x = i % w, y = i / w;
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int n = ny * w + nx;
                        if (!seen[n] && times[n] <= threshold) { seen[n] = true; queue.add(n); }
                    }
                }
                assertEquals(visible, connected, "Detached growth at " + progress);
                if (progress == 1) assertEquals(w * h, visible);
            }
        }
    }
}

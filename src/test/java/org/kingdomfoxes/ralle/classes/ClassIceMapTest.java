package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.imageio.ImageIO;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class ClassIceMapTest {
    @ParameterizedTest @ValueSource(ints = {1, 2, 4})
    void bakedRevealGrowsFromEdgesWithoutDetachedCrystalsAndCoversEveryPixel(int style) throws Exception {
        String suffix = style == 1 ? "" : "_style" + style;
        try (var stream = getClass().getResourceAsStream("/assets/ralle/textures/gui/class_ice" + suffix + ".png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            int w = image.getWidth(), h = image.getHeight();
            var times = new int[w * h];
            for (int i = 0; i < times.length; i++) {
                times[i] = image.getRGB(i % w, i / w) >> 8 & 65535;
                assertTrue(times[i] > 0 && times[i] <= 65535);
            }
            if (style != 1) {
                // Earliest edge growth belongs only to the requested corners.
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    if (times[y * w + x] > 655 || x != 0 && x != w - 1 && y != 0 && y != h - 1) continue;
                    assertTrue(Math.min(x, w - 1 - x) < 30 && Math.min(y, h - 1 - y) < 30);
                    if (style == 4) assertEquals(x < w / 2, y < h / 2);
                }
            }
            int previousVisible = 0;
            for (double progress : new double[] {.001, .01, .025, .05, .1, .2, .4, .7, .9, .99, 1}) {
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
                assertTrue(visible > previousVisible, "Growth must continue through the late reveal at " + progress);
                previousVisible = visible;
                if (progress == 1) assertEquals(w * h, visible);
            }
        }
    }
    @Test void diamondsNucleateInsideTheScreenInSequenceAndGrowAlongFourAxes() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/ralle/textures/gui/class_ice_style3.png")) {
            assertNotNull(stream);
            var image = ImageIO.read(stream);
            int w = image.getWidth(), h = image.getHeight(), seeds = 0, firstSeeds = 0;
            for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
                int t = image.getRGB(x, y) >> 8 & 65535;
                int left = image.getRGB(x - 1, y) >> 8 & 65535;
                int right = image.getRGB(x + 1, y) >> 8 & 65535;
                int up = image.getRGB(x, y - 1) >> 8 & 65535;
                int down = image.getRGB(x, y + 1) >> 8 & 65535;
                assertTrue(t > 0);
                if (t < left && t < right && t < up && t < down) {
                    seeds++;
                    if (t == 1) firstSeeds++;
                    assertEquals(left, right);
                    assertEquals(up, down);
                    assertEquals(left, up);
                }
            }
            assertEquals(1, firstSeeds);
            assertTrue(seeds > 1 && seeds <= 18);
        }
    }
}

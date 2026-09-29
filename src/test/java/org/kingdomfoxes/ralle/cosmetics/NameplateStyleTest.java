package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NameplateStyleTest {
    @Test
    void catalogPreservesApprovedRecipesAndFoxOrder() {
        assertEquals(List.of("supporter-gold", "contributor-green", "contributor-green-alt",
                "contributor-blue", "admin-white", "admin-red"),
                NameplateStyle.CATALOG.stream().map(NameplateStyle::id).toList());
        assertEquals(List.of(0x8b5c20, 0x176f9b, 0x388a15, 0x176f9b, 0, 0),
                NameplateStyle.CATALOG.stream().map(NameplateStyle::shadow).toList());
        assertEquals(List.of(0xe5b94c, 0x62f500, 0x62f500, 0x00b8f5, 0, 0),
                NameplateStyle.CATALOG.stream().map(NameplateStyle::midtone).toList());
        assertEquals(List.of(0xfff0b5, 0xffffff, 0xffffff, 0xffffff, 0xffffff, 0xff0000),
                NameplateStyle.CATALOG.stream().map(NameplateStyle::highlight).toList());
        assertEquals(1, NameplateStyle.CATALOG.getFirst().defaultResolution());
        assertTrue(NameplateStyle.CATALOG.stream().skip(1).allMatch(style -> style.defaultResolution() == .5));
        assertTrue(NameplateStyle.byId("untrusted-style").isEmpty());
    }

    @Test
    void grantsFollowFoxHierarchyWithoutInferringUnknownStyles() {
        assertTrue(NameplateStyle.available(Set.of()).isEmpty());
        assertEquals(List.of("supporter-gold"), NameplateStyle.available(Set.of("supporter"))
                .stream().map(NameplateStyle::id).toList());
        assertEquals(4, NameplateStyle.available(Set.of("contributor")).size());
        assertEquals(NameplateStyle.CATALOG, NameplateStyle.available(Set.of("admin")));
        for (var style : NameplateStyle.CATALOG) {
            assertFalse(NameplateStyle.allowed(style.id(), Set.of()));
            assertEquals(style.role().equals("supporter"), NameplateStyle.allowed(style.id(), Set.of("supporter")));
            assertEquals(!style.role().equals("admin"), NameplateStyle.allowed(style.id(), Set.of("contributor")));
            assertTrue(NameplateStyle.allowed(style.id(), Set.of("admin")));
        }
        assertFalse(NameplateStyle.allowed("admin-purple", Set.of("admin")));
    }

    @Test
    void liquidFrameIsBoundedDeterministicAndUsesRolePalette() {
        int[] first = new int[256 * 64], second = new int[first.length], later = new int[first.length];
        LiquidMaterial.fill(NameplateStyle.CATALOG.getFirst(), 1, 2, 256, 64, first);
        LiquidMaterial.fill(NameplateStyle.CATALOG.getFirst(), 1, 2, 256, 64, second);
        LiquidMaterial.fill(NameplateStyle.CATALOG.getFirst(), 1, 4, 256, 64, later);
        assertArrayEquals(first, second);
        assertFalse(java.util.Arrays.equals(first, later));
        assertTrue(java.util.Arrays.stream(first).distinct().count() > 100);
        assertThrows(IllegalArgumentException.class, () -> LiquidMaterial.fill(NameplateStyle.CATALOG.getFirst(), 1, 0, 513, 64, new int[513 * 64]));
        for (double resolution : new double[]{.5, 1, 2}) {
            int width = LiquidMaterial.width(resolution), height = LiquidMaterial.height(resolution);
            assertEquals((int) (256 / resolution), width);
            assertEquals((int) (64 / resolution), height);
            for (var style : NameplateStyle.CATALOG) {
                int[] frame = new int[width * height];
                LiquidMaterial.fill(style, resolution, 0, width, height, frame);
                assertTrue(java.util.Arrays.stream(frame).allMatch(pixel -> (pixel >>> 24) == 0xff));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> LiquidMaterial.width(.75));
    }
}

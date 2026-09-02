package org.kingdomfoxes.ralle.war.consumables;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumableSlotBorderTest {
    @Test void borderIsOnePixelAroundTheFullEighteenPixelPerimeter() {
        var geometry = ConsumableSlotBorder.geometry(10, 20);
        assertEquals(new ConsumableSlotBorder.Geometry(9, 19, 27, 37), geometry);
        assertEquals(18, geometry.width());
        assertEquals(18, geometry.height());
    }

    @Test void rainbowIsDeterministicPositionalAndFullyOpaque() {
        int first = ConsumableSlotBorder.rainbowColor(0f, 500);
        assertEquals(first, ConsumableSlotBorder.rainbowColor(0f, 500));
        assertEquals(0xFF000000, first & 0xFF000000);
        org.junit.jupiter.api.Assertions.assertNotEquals(first,
                ConsumableSlotBorder.rainbowColor(.5f, 500));
    }
}

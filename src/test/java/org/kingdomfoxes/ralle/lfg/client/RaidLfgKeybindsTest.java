package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidLfgKeybindsTest {
    @Test
    void onlyTopRowOneThroughSixResolveAsChordDigits() {
        assertEquals(1, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_1));
        assertEquals(6, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_6));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_7));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_KP_2));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_UNKNOWN));
    }
}

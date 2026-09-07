package org.kingdomfoxes.ralle.war.hqdistance;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InspectionKeybindTest {
    @Test void settingsAndVanillaChangesSynchronizeIncludingUnbinding() {
        assertEquals("key.keyboard.g", InspectionKeybind.synchronizedValue("key.keyboard.g", null, "key.keyboard.left.control"));
        assertEquals("key.keyboard.h", InspectionKeybind.synchronizedValue("key.keyboard.g", "key.keyboard.g", "key.keyboard.h"));
        assertEquals("key.keyboard.g", InspectionKeybind.synchronizedValue("key.keyboard.g", "key.keyboard.h", "key.keyboard.h"));
        assertEquals(KeybindSetting.UNBOUND, InspectionKeybind.synchronizedValue("key.keyboard.g", "key.keyboard.g", KeybindSetting.UNBOUND));
        assertEquals(KeybindSetting.UNBOUND, InspectionKeybind.synchronizedValue(KeybindSetting.UNBOUND, "key.keyboard.g", "key.keyboard.g"));
    }
}

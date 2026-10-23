package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.*;
import org.kingdomfoxes.ralle.settings.RalleSettings;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClassSelectorBindingsTest {
    @TempDir Path directory;

    @Test void aPhysicalHoldOpensOnceAndCancelDisableFocusLossRequireRelease() {
        for (int reason = 0; reason < 3; reason++) {
            var hold = new ClassModifierState();
            assertTrue(hold.observe(true, true));
            assertTrue(hold.active());
            assertFalse(hold.observe(true, true));
            if (reason == 0) hold.close(true);
            else assertFalse(hold.observe(true, false));
            assertFalse(hold.active());
            assertFalse(hold.observe(true, true));
            assertFalse(hold.observe(false, true));
            assertTrue(hold.observe(true, true));
        }
        var hold = new ClassModifierState();
        assertFalse(hold.observe(true, false));
        assertFalse(hold.observe(true, true));
        assertFalse(hold.observe(false, true));
        assertTrue(hold.observe(true, true));
    }

    @Test void bothSettingsAndMinecraftControlsCanRebindWithoutClobberingAnUnboundFirstLoad() {
        assertEquals("key.keyboard.f10", ClassSelectorBindings.synchronizedValue("key.keyboard.f10", null, "key.keyboard.unknown"));
        assertEquals("key.keyboard.f11", ClassSelectorBindings.synchronizedValue("unbound", null, "key.keyboard.f11"));
        assertEquals("key.keyboard.f12", ClassSelectorBindings.synchronizedValue("key.keyboard.f12", "key.keyboard.f10", "key.keyboard.f10"));
        assertEquals("key.keyboard.f11", ClassSelectorBindings.synchronizedValue("key.keyboard.f10", "key.keyboard.f10", "key.keyboard.f11"));
        assertEquals("unbound", ClassSelectorBindings.synchronizedValue("key.keyboard.f10", "key.keyboard.f10", "key.keyboard.unknown"));
    }

    @Test void modifierAndLfgActionConflictsUseOneReciprocalPolicy() {
        var settings = new SettingsRegistry(directory.resolve("settings.properties"));
        RalleSettings.register(settings); settings.seal();
        var modifier = settings.setting(RalleSettings.CLASS_MODIFIER_ID, KeybindSetting.class);
        settings.setting(RalleSettings.CLASS_SELECTOR_ID, BooleanSetting.class).set(true);
        modifier.set("key.keyboard.f8");
        assertTrue(ClassBindingConflicts.modifier(settings));
        assertTrue(ClassBindingConflicts.action(settings, "key.keyboard.f8"));
        assertFalse(ClassBindingConflicts.action(settings, "key.keyboard.f9"));
        modifier.set("key.keyboard.f10");
        assertFalse(ClassBindingConflicts.modifier(settings));
        assertFalse(ClassBindingConflicts.action(settings, "key.keyboard.f8"));
        settings.setting(RalleSettings.CLASS_SELECTOR_ID, BooleanSetting.class).set(false);
        modifier.set("key.keyboard.f8");
        assertFalse(ClassBindingConflicts.action(settings, "key.keyboard.f8"));
        assertFalse(ClassBindingConflicts.reserved(settings).contains("key.keyboard.f10"));
    }

    @Test void unavailableOptionReleaseDoesNotEndTheHoldOrSubmitAnOlderTarget() {
        var input = new ClassSelectorInput();
        input.begin(List.of());
        input.press("g", "available");
        input.press("h", null);
        assertNull(input.release("g", true));
        assertNull(input.release("h", true));
        assertFalse(input.finished());
        input.press("g", "available");
        assertEquals("available", input.release("g", true));
        assertTrue(input.finished());
        assertNull(input.click("another", true));
    }
}

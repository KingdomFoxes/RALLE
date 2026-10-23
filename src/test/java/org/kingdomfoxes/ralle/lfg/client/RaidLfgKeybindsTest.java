package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RaidLfgKeybindsTest {
    @Test void noViewAndDisabledWheelsUseTheExistingChordPath() {
        for (var style : new String[] {"circle-segments", "bars", "no-view"})
            assertEquals(false, RaidLfgKeybinds.graphicalWheel(false, style));
        assertEquals(true, RaidLfgKeybinds.graphicalWheel(true, "circle-segments"));
        assertEquals(true, RaidLfgKeybinds.graphicalWheel(true, "bars"));
        assertEquals(false, RaidLfgKeybinds.graphicalWheel(true, "no-view"));
    }
    @Test
    void onlyTopRowOneThroughSixResolveAsChordDigits() {
        assertEquals(1, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_1));
        assertEquals(6, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_6));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_7));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_KP_2));
        assertEquals(0, RaidLfgKeybinds.topRowDigit(GLFW.GLFW_KEY_UNKNOWN));
    }

    @Test
    void pingCooldownFeedbackIncludesTheRemainingSeconds() {
        assertEquals("Ping on cooldown (17s remaining)",
                RaidLfgKeybinds.pingCooldownMessage(17));
    }

    @Test
    void modSettingWinsWhenItChangedSinceTheLastSynchronization() {
        assertEquals("key.keyboard.g", RaidLfgKeybinds.synchronizedValue(
                "key.keyboard.g", "unbound", "key.keyboard.h"));
    }

    @Test
    void existingVanillaBindingMigratesWhenTheModSettingIsStillAtItsDefault() {
        assertEquals("key.keyboard.h", RaidLfgKeybinds.synchronizedValue(
                "unbound", null, "key.keyboard.h"));
    }

    @Test
    void existingModBindingWinsInitialSynchronization() {
        assertEquals("key.keyboard.g", RaidLfgKeybinds.synchronizedValue(
                "key.keyboard.g", null, "key.keyboard.h"));
    }

    @Test
    void persistedModBindingSurvivesAnUnknownVanillaValueAtInitialSynchronization() {
        assertEquals("key.keyboard.period", RaidLfgKeybinds.synchronizedValue(
                "key.keyboard.period", null, "key.keyboard.unknown"));
    }

    @Test
    void vanillaMappingWinsWhenOnlyItChangedSinceTheLastSynchronization() {
        assertEquals("key.keyboard.h", RaidLfgKeybinds.synchronizedValue(
                "key.keyboard.g", "key.keyboard.g", "key.keyboard.h"));
    }

    @Test
    void vanillaUnknownMappingBecomesTheModUnboundValue() {
        assertEquals("unbound", RaidLfgKeybinds.synchronizedValue(
                "key.keyboard.g", "key.keyboard.g", "key.keyboard.unknown"));
    }

    @Test
    void lockAndUnlockPressesUseTheirDistinctVaultCuesImmediately() {
        var sounds = new Sounds();

        sounds.playLockToggle(true);
        sounds.playLockToggle(false);

        assertEquals(1, sounds.locked);
        assertEquals(1, sounds.unlocked);
    }

    private static final class Sounds implements LfgSoundPlayer {
        int locked;
        int unlocked;

        @Override public void playPartyLocked() { locked++; }
        @Override public void playPartyUnlocked() { unlocked++; }
    }
}

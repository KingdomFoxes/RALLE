package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchAudioTest {
    @Test void mutesExistingAndNewChannelGainsWithoutChangingTheirUpstreamValues() {
        var refreshes = new AtomicInteger();
        float[] upstreamVolumes = {0f, .17f, .7f, 1f}; // Includes existing fades and category/option gains.
        var audio = new ClassSwitchAudio(refreshes::incrementAndGet);
        try {
            audio.mute(); audio.mute();
            assertEquals(1, refreshes.get());
            for (float gain : upstreamVolumes) assertEquals(0f, ClassSwitchAudio.outputVolume(gain));
            assertEquals(0f, ClassSwitchAudio.outputVolume(.34f)); // A new sound while already muted.
        } finally { audio.close(); audio.close(); }
        assertEquals(2, refreshes.get());
        for (float gain : upstreamVolumes) assertEquals(gain, ClassSwitchAudio.outputVolume(gain));
    }

    @Test void anAudioThreadSeesTheGateAndAnUnownedCloseCannotUnmuteIt() throws Exception {
        var owner = new ClassSwitchAudio(() -> { });
        var unused = new ClassSwitchAudio(() -> { });
        try {
            owner.mute(); unused.close();
            var seen = new java.util.concurrent.atomic.AtomicReference<Float>();
            var thread = new Thread(() -> seen.set(ClassSwitchAudio.outputVolume(.6f)));
            thread.start(); thread.join(2000);
            assertFalse(thread.isAlive());
            assertEquals(0f, seen.get());
        } finally { owner.close(); }
        assertEquals(.6f, ClassSwitchAudio.outputVolume(.6f));
    }

    @Test void reloadRefreshFailureStillReleasesTheMuteLease() {
        var audio = new ClassSwitchAudio(() -> { throw new IllegalStateException("sound device unavailable"); });
        assertThrows(IllegalStateException.class, audio::mute);
        assertEquals(0f, ClassSwitchAudio.outputVolume(.5f));
        assertThrows(IllegalStateException.class, audio::close);
        assertEquals(.5f, ClassSwitchAudio.outputVolume(.5f));
        assertDoesNotThrow(audio::close);
    }
}

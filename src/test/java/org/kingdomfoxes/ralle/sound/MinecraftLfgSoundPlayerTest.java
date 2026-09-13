package org.kingdomfoxes.ralle.sound;

import net.minecraft.SharedConstants;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftLfgSoundPlayerTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void backendCuesCannotMutateSoundEngineMapDuringClientTick() throws Exception {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var activeSounds = new HashMap<SoundInstance, Boolean>();
        Thread clientThread = Thread.currentThread();
        var sounds = new MinecraftLfgSoundPlayer(tasks::add, () -> true, sound -> {
            assertSame(clientThread, Thread.currentThread());
            activeSounds.put(sound, true);
        });
        sounds.playPartyCreated();
        tasks.remove().run();
        var iterator = activeSounds.entrySet().iterator();
        iterator.next();
        var worker = new Thread(() -> {
            sounds.playNotificationIn();
            sounds.playRosterSlotOccupied(2);
            sounds.playPartyJoined();
            sounds.playKickSucceeded();
        }, "lfg-network-test");
        worker.start();
        worker.join();
        // Matches the operation which crashed in SoundEngine.tickInGameSound.
        assertDoesNotThrow(iterator::remove);
        assertTrue(activeSounds.isEmpty());
        assertEquals(4, tasks.size());
        while (!tasks.isEmpty()) tasks.remove().run();
        assertEquals(4, activeSounds.size());
    }

    @Test void queuedNotificationsRecheckSoundSettingOnClientThread() {
        var tasks = new ConcurrentLinkedQueue<Runnable>();
        var enabled = new AtomicBoolean(true);
        var played = new java.util.ArrayList<SoundInstance>();
        var sounds = new MinecraftLfgSoundPlayer(tasks::add, enabled::get, played::add);
        sounds.playPartyCreated();
        sounds.playNotificationIn();
        enabled.set(false);
        while (!tasks.isEmpty()) tasks.remove().run();
        assertTrue(played.isEmpty());
        sounds.playLocalPartyPing(); // Explicit host feedback remains independent of notification sounds.
        tasks.remove().run();
        assertEquals(1, played.size());
    }
}

package org.kingdomfoxes.ralle.classes;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Temporary output mute. Never changes Minecraft options or another feature's category gains.
 * The audio thread reads only this atomic gate; refreshes are requested on the client thread. */
public final class ClassSwitchAudio implements AutoCloseable {
    private static final AtomicInteger LEASES = new AtomicInteger();
    private final Runnable refresh;
    private boolean held;

    public ClassSwitchAudio(Runnable refresh) { this.refresh = Objects.requireNonNull(refresh); }
    public void mute() {
        if (held) return;
        held = true;
        LEASES.incrementAndGet();
        refresh.run();
    }
    public static float outputVolume(float volume) { return LEASES.get() > 0 ? 0f : volume; }
    @Override public void close() {
        if (!held) return;
        held = false;
        LEASES.decrementAndGet();
        refresh.run();
    }
}

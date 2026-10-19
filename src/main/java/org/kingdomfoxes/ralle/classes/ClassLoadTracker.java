package org.kingdomfoxes.ralle.classes;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Observed selections survive presentation cancellation; only a real load makes them current. */
public final class ClassLoadTracker {
    private final ClassSelectorStore store;
    private final java.util.function.LongSupplier clock;
    private UUID account;
    private String current, pending, token;
    private int book = -1;
    private boolean closed, welcomed, saveFailed;
    private long selectionUntil, welcomeUntil;

    public ClassLoadTracker(ClassSelectorStore store) { this(store, () -> System.nanoTime() / 1_000_000); }
    ClassLoadTracker(ClassSelectorStore store, java.util.function.LongSupplier clock) { this.store = store; this.clock = clock; }
    public void session(UUID account) {
        if (!Objects.equals(this.account, account)) { reset(); this.account = account; }
        expire();
    }
    public String current() { return current; }
    public boolean hasSelection() { expire(); return pending != null; }
    public void selectionStage() { current = pending = token = null; book = -1; closed = welcomed = false; }
    public void selected(String reference, int container) {
        selectionStage();
        pending = ClassCharacter.text(reference, 128);
        book = container;
        selectionUntil = clock.getAsLong() + ClassSwitchController.TIMEOUT_MILLIS;
    }
    public void closed(int packetContainer, int liveContainer) {
        expire();
        if (pending != null && (packetContainer == book || liveContainer == book
                || packetContainer == 0 && liveContainer == 0)) closed = true;
    }
    public void welcome() { expire(); welcomed = true; welcomeUntil = clock.getAsLong() + ClassSwitchController.TIMEOUT_MILLIS; }
    public void compass(String token) {
        this.token = token;
        if (current != null && pending == null && token != null) save(current);
    }
    public boolean awaitingWorld() { expire(); return welcomed && (pending == null || closed); }

    /** Called after a complete frame with local-player camera, no menu and normal gameplay mode. */
    public boolean gameplay(boolean usable) {
        if (!usable || !awaitingWorld() || account == null) return false;
        if (pending == null && token == null) return false; // Wait for a late natural compass refresh.
        String reference = pending != null ? pending : store.referenceForToken(account, token);
        current = reference;
        pending = null; book = -1; closed = welcomed = false;
        if (reference != null) save(reference);
        return true;
    }
    private void save(String reference) {
        if (saveFailed) return;
        try { store.recordLoad(account, reference, token); }
        catch (IOException | RuntimeException failure) {
            saveFailed = true;
            // Current session knowledge still works if disk storage fails.
            org.slf4j.LoggerFactory.getLogger(ClassLoadTracker.class).warn("Could not save loaded class", failure);
        }
    }
    private void expire() {
        long now = clock.getAsLong();
        if (pending != null && now >= selectionUntil) selectionStage();
        else if (welcomed && now >= welcomeUntil) { welcomed = false; token = null; }
    }
    public void reset() { account = null; saveFailed = false; selectionStage(); }
}

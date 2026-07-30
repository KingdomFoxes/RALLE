package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Coalesces rapid Lock/Unlock toggles into at most one authoritative mutation. */
public final class LfgLockDebouncer {
    public static final Duration DELAY = Duration.ofSeconds(1);

    private final LongSupplier clockMillis;
    private UUID lobbyId;
    private boolean desiredLocked;
    private Origin origin;
    private long dueAtMillis;
    private boolean dispatched;
    private long version;

    public LfgLockDebouncer() {
        this(System::currentTimeMillis);
    }

    LfgLockDebouncer(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public synchronized Optional<Boolean> toggle(LfgProtocol.Lobby lobby, Origin requestedOrigin) {
        if (dispatched) return Optional.empty();
        if (lobbyId == null || !lobbyId.equals(lobby.lobbyId())) {
            lobbyId = lobby.lobbyId();
            desiredLocked = !lobby.locked();
        } else {
            desiredLocked = !desiredLocked;
        }
        origin = requestedOrigin;
        dueAtMillis = clockMillis.getAsLong() + DELAY.toMillis();
        version++;
        return Optional.of(desiredLocked);
    }

    public synchronized Optional<Command> poll(LfgProtocol.Lobby currentHostLobby,
                                                boolean online, boolean requestPending) {
        if (lobbyId == null) return Optional.empty();
        if (!online || currentHostLobby == null || !lobbyId.equals(currentHostLobby.lobbyId())) {
            clearInternal();
            return Optional.empty();
        }
        if (dispatched || requestPending || clockMillis.getAsLong() < dueAtMillis) {
            return Optional.empty();
        }
        if (currentHostLobby.locked() == desiredLocked) {
            clearInternal();
            return Optional.empty();
        }
        dispatched = true;
        version++;
        return Optional.of(new Command(lobbyId, desiredLocked, origin));
    }

    public synchronized Optional<Boolean> desiredLocked(UUID requestedLobbyId) {
        return lobbyId != null && lobbyId.equals(requestedLobbyId)
                ? Optional.of(desiredLocked) : Optional.empty();
    }

    public synchronized boolean dispatched(UUID requestedLobbyId) {
        return dispatched && lobbyId != null && lobbyId.equals(requestedLobbyId);
    }

    public synchronized void complete(UUID completedLobbyId) {
        if (lobbyId != null && lobbyId.equals(completedLobbyId)) clearInternal();
    }

    public synchronized void clear() {
        if (lobbyId != null) clearInternal();
    }

    public synchronized long version() {
        return version;
    }

    private void clearInternal() {
        lobbyId = null;
        desiredLocked = false;
        origin = null;
        dueAtMillis = 0;
        dispatched = false;
        version++;
    }

    public enum Origin { KEYBIND, SCREEN }

    public record Command(UUID lobbyId, boolean locked, Origin origin) {}
}

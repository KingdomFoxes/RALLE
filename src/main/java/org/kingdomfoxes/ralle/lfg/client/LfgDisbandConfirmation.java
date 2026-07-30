package org.kingdomfoxes.ralle.lfg.client;

import java.time.Duration;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Shared five-second confirmation state used by keybind and HUD-card disband actions. */
public final class LfgDisbandConfirmation {
    public static final Duration DURATION = Duration.ofSeconds(5);

    private final LongSupplier clockMillis;
    private UUID lobbyId;
    private long lobbyRevision;
    private String prompt;
    private long expiresAtMillis;

    public LfgDisbandConfirmation() {
        this(System::currentTimeMillis);
    }

    LfgDisbandConfirmation(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    public synchronized Result request(UUID requestedLobbyId, long requestedRevision, String requestedPrompt) {
        var current = snapshot();
        if (current != null
                && current.lobbyId().equals(requestedLobbyId)
                && current.lobbyRevision() == requestedRevision) {
            clear();
            return Result.CONFIRMED;
        }
        lobbyId = requestedLobbyId;
        lobbyRevision = requestedRevision;
        prompt = requestedPrompt;
        expiresAtMillis = clockMillis.getAsLong() + DURATION.toMillis();
        return Result.ARMED;
    }

    public synchronized Snapshot snapshot() {
        if (lobbyId == null) return null;
        if (clockMillis.getAsLong() >= expiresAtMillis) {
            clear();
            return null;
        }
        return new Snapshot(lobbyId, lobbyRevision, prompt, expiresAtMillis);
    }

    public synchronized String promptFor(UUID requestedLobbyId) {
        var current = snapshot();
        return current != null && current.lobbyId().equals(requestedLobbyId) ? current.prompt() : null;
    }

    public synchronized void validate(UUID currentLobbyId, long currentRevision, boolean stillAuthorized) {
        var current = snapshot();
        if (current == null) return;
        if (!stillAuthorized
                || !current.lobbyId().equals(currentLobbyId)
                || current.lobbyRevision() != currentRevision) {
            clear();
        }
    }

    public synchronized void clear() {
        lobbyId = null;
        lobbyRevision = 0;
        prompt = null;
        expiresAtMillis = 0;
    }

    public enum Result { ARMED, CONFIRMED }

    public record Snapshot(UUID lobbyId, long lobbyRevision, String prompt, long expiresAtMillis) {}
}

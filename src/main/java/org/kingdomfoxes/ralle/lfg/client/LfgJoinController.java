package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Persistent, presentation-neutral owner of the single client-side LFG join
 * countdown and submission.
 */
public final class LfgJoinController {
    public static final Duration COUNTDOWN_DURATION = Duration.ofSeconds(3);

    private final RaidLfgService service;
    private final LfgStateAccess stateAccess;
    private final LongSupplier clockMillis;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private UUID lobbyId;
    private long startedAtMillis;
    private Phase phase = Phase.IDLE;
    private Outcome outcome = Outcome.NONE;
    private String failureText;

    LfgJoinController(RaidLfgService service, LongSupplier clockMillis) {
        this.service = service;
        this.stateAccess = service.stateAccess();
        this.clockMillis = clockMillis;
    }

    public boolean start(UUID requestedLobbyId) {
        try (var stateScope = stateAccess.enter()) {
            if (phase.terminal()) clear();
            if (phase != Phase.IDLE || requestedLobbyId == null || !joinable(lobby(requestedLobbyId))) return false;
            lobbyId = requestedLobbyId;
            startedAtMillis = clockMillis.getAsLong();
            phase = Phase.COUNTDOWN;
            outcome = Outcome.NONE;
            failureText = null;
            notifyListeners();
            return true;
        }
    }

    public boolean cancel() {
        try (var stateScope = stateAccess.enter()) {
            if (phase != Phase.COUNTDOWN) return false;
            clear();
            notifyListeners();
            return true;
        }
    }

    public void tick() {
        try (var stateScope = stateAccess.enter()) {
            if (phase != Phase.COUNTDOWN) return;
            var current = lobby(lobbyId);
            if (!joinable(current)) {
                finish(current != null && current.members().size() >= current.capacity()
                        ? Outcome.PARTY_FILLED : Outcome.PARTY_UNAVAILABLE, null);
                return;
            }
            if (clockMillis.getAsLong() - startedAtMillis < COUNTDOWN_DURATION.toMillis()) return;

            phase = Phase.SUBMITTING;
            notifyListeners();
            var submittedLobbyId = lobbyId;
            service.join(submittedLobbyId).whenComplete((mutation, failure) -> {
                try (var callbackScope = stateAccess.enter()) {
                    if (!submittedLobbyId.equals(lobbyId) || phase != Phase.SUBMITTING) return;
                    if (failure == null) {
                        finish(Outcome.ACCEPTED, null);
                    } else {
                        var authoritative = lobby(submittedLobbyId);
                        if (!joinable(authoritative)) {
                            finish(authoritative != null && authoritative.members().size() >= authoritative.capacity()
                                    ? Outcome.PARTY_FILLED : Outcome.PARTY_UNAVAILABLE, null);
                        } else {
                            var mapped = mapFailure(unwrap(failure));
                            finish(mapped.outcome(), mapped.text());
                        }
                    }
                }
            });
        }
    }

    public Snapshot snapshot() {
        try (var stateScope = stateAccess.enter()) {
            long remaining = phase == Phase.COUNTDOWN
                    ? Math.max(0, COUNTDOWN_DURATION.toMillis() - (clockMillis.getAsLong() - startedAtMillis))
                    : 0;
            int seconds = remaining == 0 ? 0 : (int) Math.ceil(remaining / 1000d);
            double fraction = phase == Phase.COUNTDOWN
                    ? Math.clamp(remaining / (double) COUNTDOWN_DURATION.toMillis(), 0d, 1d)
                    : 0d;
            return new Snapshot(lobbyId, phase, seconds, fraction, outcome, failureText);
        }
    }

    public void acknowledge(UUID expectedLobbyId) {
        try (var stateScope = stateAccess.enter()) {
            if (phase.terminal() && java.util.Objects.equals(lobbyId, expectedLobbyId)) {
                clear();
                notifyListeners();
            }
        }
    }

    public AutoCloseable observe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void finish(Outcome nextOutcome, String text) {
        phase = nextOutcome == Outcome.ACCEPTED ? Phase.SUCCEEDED : Phase.FAILED;
        outcome = nextOutcome;
        failureText = sanitizeFailure(text);
        notifyListeners();
    }

    private void clear() {
        lobbyId = null;
        startedAtMillis = 0;
        phase = Phase.IDLE;
        outcome = Outcome.NONE;
        failureText = null;
    }

    private LfgProtocol.Lobby lobby(UUID id) {
        return id == null ? null : service.store().state().lobbies().get(id);
    }

    private boolean joinable(LfgProtocol.Lobby lobby) {
        var viewer = service.store().state().viewer();
        return service.lifecycle() == RaidLfgService.LifecycleState.ONLINE
                && lobby != null
                && viewer != null
                && !lobby.contains(viewer.minecraftUuid())
                && lobby.status() == LfgProtocol.LobbyStatus.OPEN
                && !lobby.locked()
                && lobby.members().size() < lobby.capacity()
                && lobby.capabilities() != null
                && lobby.capabilities().join()
                && !service.pending(lobby.lobbyId(), "join");
    }

    private static Failure mapFailure(Throwable failure) {
        if (failure instanceof LfgGatewayException gateway) {
            return switch (gateway.error().code()) {
                case "LOBBY_FULL" -> new Failure(Outcome.PARTY_FILLED, "Party filled");
                case "LOBBY_LOCKED", "LOBBY_NOT_FOUND", "LOBBY_REMOVED", "LOBBY_IN_RAID",
                     "NOT_VISIBLE", "INELIGIBLE_GUILD" ->
                        new Failure(Outcome.PARTY_UNAVAILABLE, "Party unavailable");
                case "PLAYER_ALREADY_ACTIVE" -> new Failure(Outcome.REJECTED, "Already in another party");
                case "KICK_BLOCKED", "KICK_COOLDOWN" -> new Failure(Outcome.REJECTED, "Rejoin blocked");
                default -> new Failure(Outcome.REJECTED, "Join failed");
            };
        }
        return new Failure(Outcome.REJECTED, "Join failed");
    }

    static String sanitizeFailure(String text) {
        if (text == null || text.isBlank()) return null;
        var cleaned = text.replaceAll("(?:\\u00a7|&)[0-9A-FK-ORa-fk-or]", "")
                .replaceAll("\\p{Cc}", "")
                .strip();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= 48 ? cleaned : cleaned.substring(0, 48);
    }

    private void notifyListeners() {
        for (var listener : listeners) stateAccess.afterUnlock(listener);
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    public enum Phase {
        IDLE,
        COUNTDOWN,
        SUBMITTING,
        SUCCEEDED,
        FAILED;

        public boolean terminal() {
            return this == SUCCEEDED || this == FAILED;
        }
    }

    public enum Outcome {
        NONE,
        ACCEPTED,
        PARTY_FILLED,
        PARTY_UNAVAILABLE,
        REJECTED
    }

    public record Snapshot(UUID lobbyId, Phase phase, int secondsRemaining,
                           double remainingFraction, Outcome outcome, String failureText) {
        public boolean active() {
            return phase == Phase.COUNTDOWN || phase == Phase.SUBMITTING;
        }

        public boolean activeFor(UUID expectedLobbyId) {
            return active() && java.util.Objects.equals(lobbyId, expectedLobbyId);
        }
    }

    private record Failure(Outcome outcome, String text) {}
}

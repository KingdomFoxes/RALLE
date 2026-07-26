package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Persistent lifecycle and queue for Raid LFG discovery HUD cards. */
public final class LfgNotificationManager {
    public static final int MAX_VISIBLE = 3;
    public static final long PASSIVE_MILLIS = 20_000;
    public static final long FEEDBACK_MILLIS = 2_000;
    public static final long FILLED_SUCCESS_MILLIS = 5_000;
    public static final long ANIMATION_MILLIS = 500;

    private final RaidLfgService service;
    private final LfgSoundPlayer sounds;
    private final LongSupplier clockMillis;
    private final BooleanSupplier newPartyEnabled;
    private final BooleanSupplier reopenedPartyEnabled;
    private final BooleanSupplier lfgScreenOpen;
    private final Map<UUID, Card> cards = new LinkedHashMap<>();
    private final ArrayDeque<UUID> visible = new ArrayDeque<>();
    private final ArrayDeque<UUID> queued = new ArrayDeque<>();

    public LfgNotificationManager(RaidLfgService service, SettingsRegistry settings,
                                  LfgSoundPlayer sounds, BooleanSupplier lfgScreenOpen) {
        this(service, sounds, System::currentTimeMillis,
                settings.setting("new-party-notifications", BooleanSetting.class)::value,
                settings.setting("reopened-party-notifications", BooleanSetting.class)::value,
                lfgScreenOpen);
    }

    LfgNotificationManager(RaidLfgService service, LfgSoundPlayer sounds, LongSupplier clockMillis,
                           BooleanSupplier newPartyEnabled, BooleanSupplier reopenedPartyEnabled,
                           BooleanSupplier lfgScreenOpen) {
        this.service = service;
        this.sounds = sounds;
        this.clockMillis = clockMillis;
        this.newPartyEnabled = newPartyEnabled;
        this.reopenedPartyEnabled = reopenedPartyEnabled;
        this.lfgScreenOpen = lfgScreenOpen;
        service.store().observeLobbyChanges(this::onLobbyChange);
    }

    private synchronized void onLobbyChange(RaidLfgStore.LobbyChange change) {
        var card = cards.get(change.lobbyId());
        if (card != null) synchronize(card, change.current());
        if (change.origin() != RaidLfgStore.UpdateOrigin.LIVE || lfgScreenOpen.getAsBoolean()) return;

        var current = change.current();
        if (!potentiallyJoinable(current)) return;
        DiscoveryKind kind = change.previous() == null
                ? DiscoveryKind.NEW
                : !potentiallyJoinable(change.previous()) ? DiscoveryKind.REOPENED : null;
        if (kind == null
                || kind == DiscoveryKind.NEW && !newPartyEnabled.getAsBoolean()
                || kind == DiscoveryKind.REOPENED && !reopenedPartyEnabled.getAsBoolean()) return;
        discover(current, kind);
    }

    private void discover(LfgProtocol.Lobby lobby, DiscoveryKind kind) {
        long now = clockMillis.getAsLong();
        var existing = cards.get(lobby.lobbyId());
        if (existing != null) {
            existing.lobby = lobby;
            existing.kind = kind;
            existing.mode = CardMode.READY;
            existing.feedbackText = null;
            existing.remainingPassiveMillis = PASSIVE_MILLIS;
            existing.animationStartedAt = now;
            existing.entranceCompleted = false;
            existing.lastTickAt = now;
            if (visible.remove(lobby.lobbyId())) {
                visible.addLast(lobby.lobbyId());
                sounds.playNotificationIn();
            } else if (queued.remove(lobby.lobbyId())) {
                queued.addLast(lobby.lobbyId());
            }
            return;
        }

        var card = new Card(lobby, kind);
        cards.put(lobby.lobbyId(), card);
        queued.addLast(lobby.lobbyId());
        promote();
    }

    public synchronized void tick() {
        long now = clockMillis.getAsLong();
        applyJoinSnapshot(service.joinController().snapshot(), now);

        var removals = new ArrayList<UUID>();
        for (var id : visible) {
            var card = cards.get(id);
            if (card == null) continue;
            var current = service.store().state().lobbies().get(id);
            synchronize(card, current);
            tickCard(card, now);
            if (card.mode == CardMode.REMOVED) removals.add(id);
        }
        removals.forEach(this::removeCompletely);
        promote();
    }

    private void applyJoinSnapshot(LfgJoinController.Snapshot snapshot, long now) {
        if (snapshot.phase() == LfgJoinController.Phase.IDLE) return;
        if (snapshot.outcome() == LfgJoinController.Outcome.ACCEPTED) {
            sounds.playPartyJoined();
        }
        var card = cards.get(snapshot.lobbyId());
        if (card == null) {
            if (snapshot.phase().terminal()) service.joinController().acknowledge(snapshot.lobbyId());
            return;
        }
        if (snapshot.phase() == LfgJoinController.Phase.COUNTDOWN) {
            card.mode = CardMode.COUNTDOWN;
        } else if (snapshot.phase() == LfgJoinController.Phase.SUBMITTING) {
            card.mode = CardMode.SUBMITTING;
        } else if (snapshot.outcome() == LfgJoinController.Outcome.ACCEPTED) {
            card.mode = CardMode.JOINED;
            card.feedbackText = null;
            service.joinController().acknowledge(snapshot.lobbyId());
        } else if (snapshot.outcome() == LfgJoinController.Outcome.PARTY_FILLED) {
            feedback(card, CardMode.FILLED_RACE, "Party filled", now + FEEDBACK_MILLIS);
            sounds.playPartyFilledRaceLost();
            service.joinController().acknowledge(snapshot.lobbyId());
        } else if (snapshot.outcome() == LfgJoinController.Outcome.PARTY_UNAVAILABLE) {
            feedback(card, CardMode.UNAVAILABLE, "Party unavailable", now + FEEDBACK_MILLIS);
            service.joinController().acknowledge(snapshot.lobbyId());
        } else if (snapshot.outcome() == LfgJoinController.Outcome.REJECTED) {
            feedback(card, CardMode.FAILURE,
                    snapshot.failureText() == null ? "Join failed" : snapshot.failureText(),
                    now + FEEDBACK_MILLIS);
            service.joinController().acknowledge(snapshot.lobbyId());
        }
    }

    private void synchronize(Card card, LfgProtocol.Lobby current) {
        long now = clockMillis.getAsLong();
        if (current == null) {
            if (card.mode != CardMode.EXITING && card.mode != CardMode.REMOVED
                    && !card.mode.timedFeedback()) {
                feedback(card, CardMode.UNAVAILABLE, "Party unavailable", now + FEEDBACK_MILLIS);
            }
            return;
        }

        card.lobby = current;

        if (card.mode == CardMode.JOINED) {
            if (current.members().size() >= current.capacity()) {
                feedback(card, CardMode.FILLED_SUCCESS, "Party filled", now + FILLED_SUCCESS_MILLIS);
            } else if (current.status() != LfgProtocol.LobbyStatus.OPEN) {
                feedback(card, CardMode.UNAVAILABLE, "Party unavailable", now + FEEDBACK_MILLIS);
            }
            return;
        }

        if ((card.mode == CardMode.READY || card.mode == CardMode.FAILURE)
                && !potentiallyJoinable(current)) {
            feedback(card, CardMode.UNAVAILABLE, "Party unavailable", now + FEEDBACK_MILLIS);
        }
    }

    private void tickCard(Card card, long now) {
        if (card.mode != CardMode.EXITING && !card.entranceCompleted
                && now - card.animationStartedAt >= ANIMATION_MILLIS) {
            card.entranceCompleted = true;
            if (card.kind == DiscoveryKind.NEW) sounds.playNewPartyReady();
        }

        long elapsed = Math.max(0, now - card.lastTickAt);
        card.lastTickAt = now;
        if (card.mode == CardMode.READY) {
            card.remainingPassiveMillis -= elapsed;
            if (card.remainingPassiveMillis <= 0) beginExit(card);
        } else if (card.mode.timedFeedback() && now >= card.feedbackUntil) {
            if (card.mode == CardMode.FAILURE && potentiallyJoinable(card.lobby)) {
                card.mode = CardMode.READY;
                card.feedbackText = null;
                card.remainingPassiveMillis = PASSIVE_MILLIS;
                card.animationStartedAt = now - ANIMATION_MILLIS;
                card.entranceCompleted = true;
            } else {
                beginExit(card);
            }
        } else if (card.mode == CardMode.EXITING && now - card.animationStartedAt >= ANIMATION_MILLIS) {
            card.mode = CardMode.REMOVED;
        }
    }

    private void feedback(Card card, CardMode mode, String text, long until) {
        if (card.mode == CardMode.EXITING || card.mode == CardMode.REMOVED) return;
        card.mode = mode;
        card.feedbackText = LfgJoinController.sanitizeFailure(text);
        card.feedbackUntil = until;
        card.lastTickAt = clockMillis.getAsLong();
    }

    public synchronized boolean join(UUID lobbyId) {
        var card = cards.get(lobbyId);
        if (card == null || card.mode != CardMode.READY) return false;
        if (!service.joinController().start(lobbyId)) return false;
        card.mode = CardMode.COUNTDOWN;
        return true;
    }

    public synchronized boolean cancel(UUID lobbyId) {
        var card = cards.get(lobbyId);
        if (card == null || !service.joinController().snapshot().activeFor(lobbyId)) return false;
        if (!service.joinController().cancel()) return false;
        card.mode = CardMode.READY;
        card.lastTickAt = clockMillis.getAsLong();
        return true;
    }

    public synchronized void close(UUID lobbyId) {
        var card = cards.get(lobbyId);
        if (card != null) beginExit(card);
    }

    public synchronized void removeImmediately(UUID lobbyId) {
        removeCompletely(lobbyId);
        promote();
    }

    public synchronized List<CardSnapshot> visibleCards() {
        var result = new ArrayList<CardSnapshot>();
        var ids = new ArrayList<>(visible);
        for (int index = ids.size() - 1; index >= 0; index--) {
            var card = cards.get(ids.get(index));
            if (card != null) result.add(card.snapshot(clockMillis.getAsLong(), service.joinController().snapshot()));
        }
        return List.copyOf(result);
    }

    public synchronized int queuedCount() {
        return queued.size();
    }

    private void beginExit(Card card) {
        if (card.mode == CardMode.EXITING || card.mode == CardMode.REMOVED) return;
        card.exitMode = card.mode;
        card.mode = CardMode.EXITING;
        card.animationStartedAt = clockMillis.getAsLong();
        sounds.playNotificationOut();
    }

    private void promote() {
        while (visible.size() < MAX_VISIBLE && !queued.isEmpty()) {
            var id = queued.removeFirst();
            var card = cards.get(id);
            var current = service.store().state().lobbies().get(id);
            if (card == null || !potentiallyJoinable(current)) {
                cards.remove(id);
                continue;
            }
            card.lobby = current;
            long now = clockMillis.getAsLong();
            visible.addLast(id);
            card.remainingPassiveMillis = PASSIVE_MILLIS;
            card.animationStartedAt = now;
            card.lastTickAt = now;
            card.entranceCompleted = false;
            sounds.playNotificationIn();
        }
    }

    private void removeCompletely(UUID id) {
        visible.remove(id);
        queued.remove(id);
        cards.remove(id);
    }

    private boolean potentiallyJoinable(LfgProtocol.Lobby lobby) {
        var viewer = service.store().state().viewer();
        return lobby != null
                && viewer != null
                && !lobby.hostedBy(viewer.minecraftUuid())
                && !lobby.contains(viewer.minecraftUuid())
                && lobby.status() == LfgProtocol.LobbyStatus.OPEN
                && !lobby.locked()
                && lobby.members().size() < lobby.capacity()
                && lobby.capabilities() != null
                && lobby.capabilities().join();
    }

    public enum DiscoveryKind { NEW, REOPENED }

    public enum CardMode {
        READY,
        COUNTDOWN,
        SUBMITTING,
        JOINED,
        FILLED_SUCCESS,
        FILLED_RACE,
        FAILURE,
        UNAVAILABLE,
        EXITING,
        REMOVED;

        boolean timedFeedback() {
            return this == FILLED_SUCCESS || this == FILLED_RACE || this == FAILURE || this == UNAVAILABLE;
        }
    }

    public record CardSnapshot(LfgProtocol.Lobby lobby, DiscoveryKind kind, CardMode mode,
                               CardMode presentedMode, String feedbackText, int countdownSeconds,
                               double countdownFraction, double animationProgress) {}

    private static final class Card {
        private LfgProtocol.Lobby lobby;
        private DiscoveryKind kind;
        private CardMode mode = CardMode.READY;
        private String feedbackText;
        private long remainingPassiveMillis = PASSIVE_MILLIS;
        private long animationStartedAt;
        private long lastTickAt;
        private long feedbackUntil;
        private boolean entranceCompleted;
        private CardMode exitMode = CardMode.READY;

        private Card(LfgProtocol.Lobby lobby, DiscoveryKind kind) {
            this.lobby = lobby;
            this.kind = kind;
        }

        private CardSnapshot snapshot(long now, LfgJoinController.Snapshot join) {
            double raw = Math.clamp((now - animationStartedAt) / (double) ANIMATION_MILLIS, 0d, 1d);
            double eased = raw < 0.5 ? 4 * raw * raw * raw : 1 - Math.pow(-2 * raw + 2, 3) / 2;
            double animation = mode == CardMode.EXITING ? 1 - eased : eased;
            int seconds = join.activeFor(lobby.lobbyId()) ? join.secondsRemaining() : 0;
            double fraction = join.activeFor(lobby.lobbyId()) ? join.remainingFraction() : 0;
            var presentation = mode == CardMode.EXITING ? exitMode : mode;
            return new CardSnapshot(lobby, kind, mode, presentation, feedbackText,
                    seconds, fraction, animation);
        }
    }
}

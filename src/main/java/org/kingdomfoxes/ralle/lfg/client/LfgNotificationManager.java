package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Lifecycle and queue for transient discovery and persistent party-status HUD cards. */
public final class LfgNotificationManager {
    public static final int MAX_VISIBLE = 3;
    public static final long PASSIVE_MILLIS = 20_000;
    public static final long FEEDBACK_MILLIS = 2_000;
    public static final long FILLED_SUCCESS_MILLIS = 5_000;
    public static final long ANIMATION_MILLIS = 500;
    static final long MAIN_UI_SUPPRESSION_MILLIS = 60_000;

    private final RaidLfgService service;
    private final LfgSoundPlayer sounds;
    private final LongSupplier clockMillis;
    private final BooleanSupplier newPartyEnabled;
    private final BooleanSupplier reopenedPartyEnabled;
    private final BooleanSupplier partyStatusEnabled;
    private final BooleanSupplier mainUiAutoPopOutEnabled;
    private final BooleanSupplier lfgScreenOpen;
    private final Map<UUID, Card> cards = new LinkedHashMap<>();
    private final ArrayDeque<UUID> visible = new ArrayDeque<>();
    private final ArrayDeque<UUID> queued = new ArrayDeque<>();
    private final Map<Long, PendingSuppression> mainUiSuppressions = new LinkedHashMap<>();
    private final Set<UUID> dismissedPersistentCards = new HashSet<>();
    private long nextSuppressionId;

    public LfgNotificationManager(RaidLfgService service, SettingsRegistry settings,
                                  LfgSoundPlayer sounds, BooleanSupplier lfgScreenOpen) {
        this(service, sounds, System::currentTimeMillis,
                settings.setting("new-party-notifications", BooleanSetting.class)::value,
                settings.setting("reopened-party-notifications", BooleanSetting.class)::value,
                settings.setting("party-status-notifications", BooleanSetting.class)::value,
                settings.setting("auto-pop-out-main-ui", BooleanSetting.class)::value,
                lfgScreenOpen);
    }

    LfgNotificationManager(RaidLfgService service, LfgSoundPlayer sounds, LongSupplier clockMillis,
                           BooleanSupplier newPartyEnabled, BooleanSupplier reopenedPartyEnabled,
                           BooleanSupplier partyStatusEnabled, BooleanSupplier mainUiAutoPopOutEnabled,
                           BooleanSupplier lfgScreenOpen) {
        this.service = service;
        this.sounds = sounds;
        this.clockMillis = clockMillis;
        this.newPartyEnabled = newPartyEnabled;
        this.reopenedPartyEnabled = reopenedPartyEnabled;
        this.partyStatusEnabled = partyStatusEnabled;
        this.mainUiAutoPopOutEnabled = mainUiAutoPopOutEnabled;
        this.lfgScreenOpen = lfgScreenOpen;
        service.store().observeLobbyChanges(this::onLobbyChange);
    }

    private synchronized void onLobbyChange(RaidLfgStore.LobbyChange change) {
        var viewer = service.store().state().viewer();
        if (change.current() != null && viewer != null
                && !change.current().contains(viewer.minecraftUuid())) {
            dismissedPersistentCards.remove(change.lobbyId());
        }
        var card = cards.get(change.lobbyId());
        if (card != null) playObservedRosterGrowth(card, change);
        if (viewerDeparted(change)) {
            dismissedPersistentCards.remove(change.lobbyId());
            if (card != null) beginExit(card);
            return;
        }
        if (card != null) synchronize(card, change.current());
        if (shouldShowPartyStatus(change)) {
            showPersistent(change.current(), DiscoveryKind.PARTY_STATUS);
            return;
        }
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

    private void playObservedRosterGrowth(Card card, RaidLfgStore.LobbyChange change) {
        if (!visible.contains(change.lobbyId())
                || lfgScreenOpen.getAsBoolean()
                || service.joinController().snapshot().activeFor(change.lobbyId())
                || card.mode == CardMode.EXITING
                || card.mode == CardMode.REMOVED
                || change.origin() == RaidLfgStore.UpdateOrigin.SNAPSHOT
                || change.origin() == RaidLfgStore.UpdateOrigin.CLEAR
                || change.previous() == null
                || change.current() == null) {
            return;
        }

        var viewer = service.store().state().viewer();
        if (viewer == null
                || change.previous().contains(viewer.minecraftUuid())
                || change.current().contains(viewer.minecraftUuid())) {
            return;
        }

        int previousSize = change.previous().members().size();
        int currentSize = change.current().members().size();
        for (int occupied = previousSize + 1; occupied <= currentSize; occupied++) {
            sounds.playRosterSlotOccupied(Math.min(change.current().capacity(), occupied));
        }
    }

    private boolean shouldShowPartyStatus(RaidLfgStore.LobbyChange change) {
        if (change.current() == null) return false;
        var viewer = service.store().state().viewer();
        boolean becameMember = viewer != null
                && change.current().contains(viewer.minecraftUuid())
                && (change.previous() == null || !change.previous().contains(viewer.minecraftUuid()));
        if (!becameMember) return false;
        if (dismissedPersistentCards.contains(change.lobbyId())) return false;
        if (consumeMainUiSuppression(change.lobbyId())) {
            removeCompletely(change.lobbyId());
            if (mainUiAutoPopOutEnabled.getAsBoolean()) {
                showPersistent(change.current(), DiscoveryKind.MAIN_UI);
            }
            return false;
        }
        return partyStatusEnabled.getAsBoolean();
    }

    private boolean viewerDeparted(RaidLfgStore.LobbyChange change) {
        if (change.origin() == RaidLfgStore.UpdateOrigin.CLEAR || change.previous() == null) return false;
        var viewer = service.store().state().viewer();
        return viewer != null
                && change.previous().contains(viewer.minecraftUuid())
                && (change.current() == null || !change.current().contains(viewer.minecraftUuid()));
    }

    /**
     * Suppresses the next automatic party-status card for a main-UI create.
     * The lobby ID is not known until the authoritative mutation arrives.
     */
    public synchronized PartyStatusSuppression suppressNextMainUiPartyStatus() {
        return registerMainUiSuppression(null);
    }

    /** Suppresses automatic party status for a main-UI join of the given lobby. */
    public synchronized PartyStatusSuppression suppressMainUiPartyStatus(UUID lobbyId) {
        return registerMainUiSuppression(java.util.Objects.requireNonNull(lobbyId, "lobbyId"));
    }

    public boolean mainUiAutoPopOutEnabled() {
        return mainUiAutoPopOutEnabled.getAsBoolean();
    }

    private PartyStatusSuppression registerMainUiSuppression(UUID lobbyId) {
        removeExpiredSuppressions();
        long id = ++nextSuppressionId;
        mainUiSuppressions.put(id, new PendingSuppression(
                lobbyId, clockMillis.getAsLong() + MAIN_UI_SUPPRESSION_MILLIS));
        return new PartyStatusSuppression(this, id);
    }

    private boolean consumeMainUiSuppression(UUID lobbyId) {
        removeExpiredSuppressions();
        Long wildcard = null;
        var iterator = mainUiSuppressions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (lobbyId.equals(entry.getValue().lobbyId())) {
                iterator.remove();
                return true;
            }
            if (entry.getValue().lobbyId() == null && wildcard == null) wildcard = entry.getKey();
        }
        if (wildcard == null) return false;
        mainUiSuppressions.remove(wildcard);
        return true;
    }

    private void removeExpiredSuppressions() {
        long now = clockMillis.getAsLong();
        mainUiSuppressions.values().removeIf(suppression -> now >= suppression.expiresAtMillis());
    }

    private synchronized void releaseMainUiSuppression(long id) {
        mainUiSuppressions.remove(id);
    }

    private synchronized void releaseMainUiSuppressions(UUID lobbyId) {
        mainUiSuppressions.values().removeIf(suppression -> lobbyId.equals(suppression.lobbyId()));
    }

    private void discover(LfgProtocol.Lobby lobby, DiscoveryKind kind) {
        long now = clockMillis.getAsLong();
        var existing = cards.get(lobby.lobbyId());
        if (existing != null) {
            existing.lobby = lobby;
            if (!existing.persistent) existing.kind = kind;
            existing.mode = CardMode.READY;
            existing.feedbackText = null;
            if (!existing.persistent) existing.remainingPassiveMillis = PASSIVE_MILLIS;
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

    /** Explicitly keeps a live lobby card on the HUD until the player closes it. */
    public synchronized void showPersistent(LfgProtocol.Lobby lobby) {
        dismissedPersistentCards.remove(lobby.lobbyId());
        showPersistent(lobby, DiscoveryKind.MANUAL);
    }

    private void showPersistent(LfgProtocol.Lobby lobby, DiscoveryKind kind) {
        long now = clockMillis.getAsLong();
        var card = cards.get(lobby.lobbyId());
        boolean wasVisible = visible.contains(lobby.lobbyId());
        boolean wasQueued = queued.contains(lobby.lobbyId());
        if (card == null) {
            card = new Card(lobby, kind);
            cards.put(lobby.lobbyId(), card);
        }
        card.lobby = lobby;
        card.kind = kind;
        card.persistent = true;

        // Promoting an existing discovery card changes its lifetime, not its presentation identity.
        // Keep its current entrance progress and queue position so joining through the card cannot
        // replay the slide-in animation or notification-in cue.
        if (wasVisible && card.mode != CardMode.EXITING && card.mode != CardMode.REMOVED) return;
        if (wasQueued) return;

        card.mode = CardMode.READY;
        card.feedbackText = null;
        card.animationStartedAt = now;
        card.entranceCompleted = false;
        card.lastTickAt = now;
        visible.remove(lobby.lobbyId());
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
            if (card.kind == DiscoveryKind.PARTY_STATUS && !partyStatusEnabled.getAsBoolean()) {
                beginExit(card);
            }
            if (card.kind == DiscoveryKind.MAIN_UI && !mainUiAutoPopOutEnabled.getAsBoolean()) {
                beginExit(card);
            }
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
        if (snapshot.phase().terminal()) releaseMainUiSuppressions(snapshot.lobbyId());
        if (snapshot.outcome() == LfgJoinController.Outcome.ACCEPTED) {
            sounds.playPartyJoined();
        }
        var card = cards.get(snapshot.lobbyId());
        if (card == null) {
            if (snapshot.phase().terminal()) service.joinController().acknowledge(snapshot.lobbyId());
            return;
        }
        if (card.mode == CardMode.EXITING || card.mode == CardMode.REMOVED) {
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
            if (card.mode == CardMode.EXITING || card.mode == CardMode.REMOVED) return;
            if (card.persistent) {
                card.mode = CardMode.UNAVAILABLE;
                card.feedbackText = "Party unavailable";
                card.lastTickAt = now;
                return;
            }
            if (card.mode != CardMode.EXITING && card.mode != CardMode.REMOVED
                    && !card.mode.timedFeedback()) {
                feedback(card, CardMode.UNAVAILABLE, "Party unavailable", now + FEEDBACK_MILLIS);
            }
            return;
        }

        card.lobby = current;
        if (card.persistent) {
            if (card.mode != CardMode.COUNTDOWN && card.mode != CardMode.SUBMITTING
                    && card.mode != CardMode.EXITING && card.mode != CardMode.REMOVED) {
                card.mode = CardMode.READY;
                card.feedbackText = null;
            }
            return;
        }

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
        if (card.persistent && card.mode != CardMode.EXITING) return;
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
        if (card == null || card.mode != CardMode.READY || !potentiallyJoinable(card.lobby)) return false;
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
        if (card != null) {
            if (card.persistent) dismissedPersistentCards.add(lobbyId);
            beginExit(card);
        }
    }

    /** Newest/topmost visible card, including a card with an active Join in progress. */
    public synchronized java.util.Optional<UUID> newestVisibleCardId() {
        var ids = new ArrayList<>(visible);
        for (int index = ids.size() - 1; index >= 0; index--) {
            var card = cards.get(ids.get(index));
            if (card != null && card.mode != CardMode.EXITING && card.mode != CardMode.REMOVED) {
                return java.util.Optional.of(ids.get(index));
            }
        }
        return java.util.Optional.empty();
    }

    /** Newest/topmost visible card which currently exposes a usable Join action. */
    public synchronized java.util.Optional<UUID> newestJoinableCardId() {
        var ids = new ArrayList<>(visible);
        for (int index = ids.size() - 1; index >= 0; index--) {
            var card = cards.get(ids.get(index));
            if (card != null && card.mode == CardMode.READY && potentiallyJoinable(card.lobby)) {
                return java.util.Optional.of(ids.get(index));
            }
        }
        return java.util.Optional.empty();
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

    /** True only while an active, persistent card still owns the lobby presentation. */
    public synchronized boolean hasPersistentCard(UUID lobbyId) {
        var card = cards.get(lobbyId);
        return card != null
                && card.persistent
                && card.mode != CardMode.EXITING
                && card.mode != CardMode.REMOVED;
    }

    public synchronized boolean hasVisiblePersistentCard(UUID lobbyId) {
        var card = cards.get(lobbyId);
        return visible.contains(lobbyId)
                && card != null
                && card.persistent
                && card.mode != CardMode.EXITING
                && card.mode != CardMode.REMOVED;
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
            if (card == null || !card.persistent && !potentiallyJoinable(current)) {
                cards.remove(id);
                continue;
            }
            if (current != null) card.lobby = current;
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

    public enum DiscoveryKind { NEW, REOPENED, PARTY_STATUS, MAIN_UI, MANUAL }

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
                               double countdownFraction, double animationProgress,
                               boolean persistent) {}

    public static final class PartyStatusSuppression implements AutoCloseable {
        private final LfgNotificationManager owner;
        private final long id;

        private PartyStatusSuppression(LfgNotificationManager owner, long id) {
            this.owner = owner;
            this.id = id;
        }

        @Override
        public void close() {
            owner.releaseMainUiSuppression(id);
        }
    }

    private record PendingSuppression(UUID lobbyId, long expiresAtMillis) {}

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
        private boolean persistent;
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
                    seconds, fraction, animation, persistent);
        }
    }
}

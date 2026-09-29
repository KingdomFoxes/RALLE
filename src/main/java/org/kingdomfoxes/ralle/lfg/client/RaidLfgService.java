package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.kingdomfoxes.ralle.lfg.protocol.LfgNoteText;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Persistent owner of authentication, connection lifecycle, live state, and mutations. */
public final class RaidLfgService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidLfgService.class);
    public enum LifecycleState {
        DISABLED, NOT_ON_WYNNCRAFT, AUTHENTICATING, SYNCING, ONLINE, RECONNECTING,
        INELIGIBLE, OUTDATED, UNAVAILABLE
    }

    private static final long[] RECONNECT_SECONDS = {1, 2, 4, 8, 15};
    private static final long PING_COOLDOWN_MILLIS = 30_000L;
    private static final int COMMAND_DEDUPLICATION_LIMIT = 128;
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final LfgGateway gateway;
    private final RaidLfgEnvironment environment;
    private final MinecraftSessionProof sessionProof;
    private final LongSupplier clockMillis;
    private final DoubleSupplier jitter;
    private final LfgNotificationSink notifications;
    private final PartyCommandExecutor partyCommands;
    private final LfgStateAccess stateAccess = new LfgStateAccess();
    private final RaidLfgStore store = new RaidLfgStore(stateAccess);
    private final LfgJoinController joinController;
    private final Set<String> pending = new HashSet<>();
    private final Map<UUID, Long> pingCooldowns = new HashMap<>();
    private final Set<UUID> processedCommandIds = new HashSet<>();
    private final ArrayDeque<UUID> processedCommandOrder = new ArrayDeque<>();

    private volatile LifecycleState lifecycle = LifecycleState.DISABLED;
    private volatile String statusMessage = "Raid LFG is disabled in settings.";
    private volatile UUID focusLobbyId;
    private String contextKey = "";
    private String bearerToken;
    private LfgGateway.LiveConnection liveConnection;
    private long generation;
    private boolean awaitingSnapshot;
    private boolean authenticationInFlight;
    private CompletableFuture<LfgProtocol.Snapshot> synchronizationFuture;
    private boolean synchronizationPending;
    private CompletableFuture<LfgProtocol.Snapshot> refreshFuture;
    private int reconnectAttempt;
    private long reconnectAtMillis;
    private long renewalSequence;
    private RenewalAttempt renewal;
    private Instant renewalExpiresAt;
    private int renewalRetryAttempt;
    private long renewalRetryAtMillis;
    private final Map<String, PendingMutation> unknownMutations = new HashMap<>();

    public RaidLfgService(LfgGateway gateway, RaidLfgEnvironment environment,
                          MinecraftSessionProof sessionProof) {
        this(gateway, environment, sessionProof, System::currentTimeMillis, Math::random,
                LfgNotificationSink.IGNORE, PartyCommandExecutor.IGNORE);
    }

    public RaidLfgService(LfgGateway gateway, RaidLfgEnvironment environment,
                          MinecraftSessionProof sessionProof, LfgNotificationSink notifications,
                          PartyCommandExecutor partyCommands) {
        this(gateway, environment, sessionProof, System::currentTimeMillis, Math::random,
                notifications, partyCommands);
    }

    RaidLfgService(LfgGateway gateway, RaidLfgEnvironment environment,
                   MinecraftSessionProof sessionProof, LongSupplier clockMillis,
                   DoubleSupplier jitter) {
        this(gateway, environment, sessionProof, clockMillis, jitter,
                LfgNotificationSink.IGNORE, PartyCommandExecutor.IGNORE);
    }

    RaidLfgService(LfgGateway gateway, RaidLfgEnvironment environment,
                   MinecraftSessionProof sessionProof, LongSupplier clockMillis,
                   DoubleSupplier jitter, LfgNotificationSink notifications,
                   PartyCommandExecutor partyCommands) {
        this.gateway = gateway;
        this.environment = environment;
        this.sessionProof = sessionProof;
        this.clockMillis = clockMillis;
        this.jitter = jitter;
        this.notifications = notifications;
        this.partyCommands = partyCommands;
        this.joinController = new LfgJoinController(this, clockMillis);
    }

    LfgStateAccess stateAccess() { return stateAccess; }
    public RaidLfgStore store() { return store; }
    public LfgJoinController joinController() { return joinController; }
    public LifecycleState lifecycle() { return lifecycle; }
    public String statusMessage() { return statusMessage; }
    public UUID focusLobbyId() { return focusLobbyId; }
    public void clearFocus() { focusLobbyId = null; }
    public void focusLobby(UUID lobbyId) { focusLobbyId = lobbyId; }

    public int pingCooldownSeconds(UUID lobbyId) {
        try (var stateScope = stateAccess.enter()) {
            long remaining = pingCooldowns.getOrDefault(lobbyId, 0L) - clockMillis.getAsLong();
            if (remaining <= 0) {
                pingCooldowns.remove(lobbyId);
                return 0;
            }
            return (int) Math.ceil(remaining / 1000d);
        }
    }

    public void connectionChanged() {
        try (var stateScope = stateAccess.enter()) {
            contextKey = "";
        }
    }

    public boolean pending(UUID lobbyId, String action) {
        try (var stateScope = stateAccess.enter()) {
            return pending.contains(pendingKey(lobbyId, action));
        }
    }

    public boolean pendingCreate() {
        try (var stateScope = stateAccess.enter()) {
            return pending.contains("create");
        }
    }

    /** Reevaluate every client tick; it performs no network request outside the opt-in Wynncraft state. */
    public void tick() {
        try (var stateScope = stateAccess.enter()) {
            joinController.tick();
            var host = normalizedHost(environment.serverHost());
            var nextContext = environment.enabled() + "|" + host + "|" + environment.playerId();
            if (!nextContext.equals(contextKey)) {
                contextKey = nextContext;
                invalidate(environment.enabled() ? LifecycleState.NOT_ON_WYNNCRAFT : LifecycleState.DISABLED,
                        environment.enabled() ? "Connect to Wynncraft to use Raid LFG." : "Raid LFG is disabled in settings.");
            }
            if (!environment.enabled()) return;
            if (!isWynncraft(host)) {
                if (lifecycle != LifecycleState.NOT_ON_WYNNCRAFT) {
                    invalidate(LifecycleState.NOT_ON_WYNNCRAFT, "Connect to Wynncraft to use Raid LFG.");
                }
                return;
            }
            if (lifecycle == LifecycleState.NOT_ON_WYNNCRAFT || lifecycle == LifecycleState.DISABLED) {
                authenticate();
            } else if (lifecycle == LifecycleState.RECONNECTING && clockMillis.getAsLong() >= reconnectAtMillis) {
                authenticate();
            } else if (lifecycle == LifecycleState.ONLINE && renewal == null && renewalExpiresAt != null
                    && clockMillis.getAsLong() >= renewalRetryAtMillis) {
                beginRenewal(renewalExpiresAt);
            }
            if (renewalExpiresAt != null && bearerToken != null
                    && clockMillis.getAsLong() >= renewalExpiresAt.toEpochMilli()) {
                enterRenewalReadOnly();
            }
        }
    }

    public CompletableFuture<LfgProtocol.Snapshot> refresh() {
        final String token;
        final long expected;
        try (var callbackScope = stateAccess.enter()) {
            if (refreshFuture != null && !refreshFuture.isDone()) return refreshFuture;
            if (bearerToken == null || lifecycle != LifecycleState.ONLINE) {
                if (!environment.enabled() || !isWynncraft(normalizedHost(environment.serverHost()))
                        || lifecycle == LifecycleState.OUTDATED || lifecycle == LifecycleState.INELIGIBLE) {
                    return unavailableFuture();
                }
                if (synchronizationFuture == null || !synchronizationPending) {
                    synchronizationFuture = new CompletableFuture<>();
                    synchronizationPending = true;
                }
                reconnectAtMillis = clockMillis.getAsLong();
                if (!authenticationInFlight && renewal == null) authenticate();
                return synchronizationFuture;
            }
            token = bearerToken;
            expected = generation;
            lifecycle = LifecycleState.SYNCING;
            statusMessage = "Refreshing complete lobby state...";
        }
        var request = gateway.snapshot(token).thenApply(snapshot -> {
            try (var callbackScope = stateAccess.enter()) {
                if (expected != generation) return snapshot;
                requireProtocol(snapshot.protocolVersion());
                store.replace(snapshot, RaidLfgStore.UpdateOrigin.SNAPSHOT);
                reconcileUnknownMutations(snapshot);
                lifecycle = LifecycleState.ONLINE;
                statusMessage = "Live";
            }
            return snapshot;
        }).whenComplete((ignored, failure) -> {
            try (var callbackScope = stateAccess.enter()) { refreshFuture = null; }
            if (failure != null) handleAsyncFailure(expected, failure, true);
        });
        try (var callbackScope = stateAccess.enter()) { refreshFuture = request; }
        return request;
    }

    /**
     * Request fresh LFG state from the browser, retrying the connection immediately after a failure.
     * Disabled and off-Wynncraft contexts remain inert, and an authentication or synchronization
     * already in progress is not restarted.
     */
    public void requestRefresh() {
        try (var stateScope = stateAccess.enter()) {
            if (!environment.enabled() || !isWynncraft(normalizedHost(environment.serverHost()))) return;
            if (lifecycle == LifecycleState.AUTHENTICATING || lifecycle == LifecycleState.SYNCING) return;
            if (lifecycle == LifecycleState.ONLINE) {
                refresh();
                return;
            }
            reconnectAttempt = 0;
            authenticate();
        }
    }

    public CompletableFuture<LfgProtocol.Mutation> create(LfgProtocol.RaidType raid,
                                                           LfgProtocol.Region region, String note) {
        return createWithParty(raid, region, note, List.of());
    }

    public CompletableFuture<LfgProtocol.Mutation> createWithParty(LfgProtocol.RaidType raid,
            LfgProtocol.Region region, String note, List<String> partyMembers) {
        final String cleaned;
        final List<String> members;
        try {
            cleaned = sanitizeNote(note);
            members = List.copyOf(partyMembers);
            org.kingdomfoxes.ralle.lfg.protocol.StrictLfgJson.createRequest(raid, region, cleaned, members);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return mutate(new PendingMutation("create", null, raid, region, cleaned, false, null),
                (token, key) -> gateway.createWithParty(token, raid, region, cleaned, members, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> join(UUID lobbyId) {
        return mutate(new PendingMutation("join", lobbyId, null, null, null, false, null),
                (token, key) -> gateway.join(token, lobbyId, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> leave(UUID lobbyId) {
        return mutate(new PendingMutation("leave", lobbyId, null, null, null, false, null),
                (token, key) -> gateway.leave(token, lobbyId, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> disband(UUID lobbyId) {
        return mutate(new PendingMutation("disband", lobbyId, null, null, null, true,
                        partyCommands::disband),
                (token, key) -> gateway.disband(token, lobbyId, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> kick(UUID lobbyId, UUID targetId, String targetIgn) {
        if (targetIgn == null || !IGN.matcher(targetIgn).matches()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Target IGN is invalid."));
        }
        return mutate(new PendingMutation("kick", lobbyId, null, null, null, false,
                        () -> partyCommands.kick(targetIgn)),
                (token, key) -> gateway.kick(token, lobbyId, targetId, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> setLocked(UUID lobbyId, boolean locked) {
        return mutate(new PendingMutation("lock", lobbyId, null, null, null, locked, null),
                (token, key) -> gateway.setLocked(token, lobbyId, locked, key));
    }

    public CompletableFuture<LfgProtocol.Mutation> ping(UUID lobbyId) {
        return mutate(new PendingMutation("ping", lobbyId, null, null, null, false, null),
                (token, key) -> gateway.ping(token, lobbyId, key))
                .thenApply(mutation -> {
                    try (var callbackScope = stateAccess.enter()) {
                        pingCooldowns.put(lobbyId, clockMillis.getAsLong() + PING_COOLDOWN_MILLIS);
                    }
                    return mutation;
                });
    }

    private CompletableFuture<LfgProtocol.Mutation> mutate(PendingMutation operation, MutationCall call) {
        final String key = pendingKey(operation.lobbyId, operation.action);
        final String token;
        try (var callbackScope = stateAccess.enter()) {
            if (lifecycle != LifecycleState.ONLINE || bearerToken == null) return unavailableFuture();
            if (!pending.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("That action is already pending."));
            token = bearerToken;
            operation.pendingKey = key;
            operation.idempotencyKey = UUID.randomUUID();
            operation.contextKey = contextKey;
            operation.beforeLobby = operation.lobbyId == null ? null : store.state().lobbies().get(operation.lobbyId);
        }
        call.apply(token, operation.idempotencyKey).whenComplete((mutation, failure) -> {
            if (failure == null) completeKnownMutation(operation, mutation);
            else completeFailedMutation(operation, unwrap(failure));
        });
        return operation.result;
    }

    private void completeKnownMutation(PendingMutation operation, LfgProtocol.Mutation mutation) {
        try (var stateScope = stateAccess.enter()) {
            if (operation.completed || operation.result.isDone()) return;
            try {
                requireProtocol(mutation.protocolVersion());
                if (Objects.equals(operation.contextKey, contextKey)) {
                    if (operation.removesLobby()) {
                        store.remove(mutation.revision(), mutation.lobby().lobbyId(), RaidLfgStore.UpdateOrigin.LOCAL_MUTATION);
                    } else {
                        store.apply(mutation);
                    }
                    runAcceptedCommand(operation);
                }
                pending.remove(operation.pendingKey);
                unknownMutations.remove(operation.pendingKey);
                if (lifecycle == LifecycleState.ONLINE) statusMessage = "Live";
                operation.complete(stateAccess, mutation);
            } catch (RuntimeException exception) {
                pending.remove(operation.pendingKey);
                operation.fail(stateAccess, exception);
            }
        }
    }

    private void completeFailedMutation(PendingMutation operation, Throwable cause) {
        try (var stateScope = stateAccess.enter()) {
            if (operation.completed || operation.result.isDone()) return;
            LOGGER.warn("Raid LFG mutation failed: action={}, failureType={}",
                    operation.action, cause.getClass().getSimpleName());
            if (!Objects.equals(operation.contextKey, contextKey)) {
                pending.remove(operation.pendingKey);
                operation.fail(stateAccess, new LfgMutationOutcomeUnknownException(
                        operation.action, operation.idempotencyKey,
                        "Raid LFG context changed before the action could be confirmed."));
                return;
            }
            if (cause instanceof LfgGatewayException gatewayFailure && gatewayFailure.transportFailure()) {
                unknownMutations.put(operation.pendingKey, operation);
                statusMessage = "Action submitted, but live synchronization was lost. Reconnecting to confirm…";
                scheduleReconnect(statusMessage, gatewayFailure.error().retryAfterSeconds());
                return;
            }
            pending.remove(operation.pendingKey);
            if (cause instanceof LfgGatewayException gatewayFailure) {
                var error = gatewayFailure.error();
                statusMessage = error.message();
                if ("RAID_ALREADY_LISTED".equals(error.code()) && error.returnedLobby() != null) {
                    store.remember(error.returnedLobby());
                    focusLobbyId = error.returnedLobby().lobbyId();
                }
                if (gatewayFailure.status() == 401) restartAuthentication();
                else if (isIneligible(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.INELIGIBLE, error.message());
                } else if (isUnsupportedProtocol(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.OUTDATED, error.message());
                } else if (error.retryable() && !"RATE_LIMITED".equals(error.code())) {
                    scheduleReconnect(error.message(), error.retryAfterSeconds());
                }
            } else if (cause instanceof TerminalException terminal
                    && "UNSUPPORTED_PROTOCOL".equals(terminal.code)) {
                terminalState(LifecycleState.OUTDATED, terminal.getMessage());
            } else {
                statusMessage = cause.getMessage() == null || cause.getMessage().isBlank()
                        ? "The host action failed."
                        : cause.getMessage();
            }
            operation.fail(stateAccess, cause);
        }
    }

    private void authenticate() {
        try (var stateScope = stateAccess.enter()) {
            if (authenticationInFlight || renewal != null) return;
            closeLive();
            bearerToken = null;
            awaitingSnapshot = false;
            authenticationInFlight = true;
            if (synchronizationFuture == null || !synchronizationPending) {
                synchronizationFuture = new CompletableFuture<>();
                synchronizationPending = true;
            }
            lifecycle = LifecycleState.AUTHENTICATING;
            statusMessage = "Authenticating Minecraft account...";
            long expected = ++generation;
            gateway.status()
                    .thenCompose(status -> {
                        requireCurrent(expected);
                        if (!status.enabled()) throw terminal("LFG_DISABLED", "Raid LFG is currently unavailable.");
                        if (status.protocolVersion() != LfgProtocol.VERSION) {
                            throw terminal("UNSUPPORTED_PROTOCOL", "Fox Raid LFG uses an incompatible protocol.");
                        }
                        return gateway.challenge(environment.playerId(), environment.ign());
                    })
                    .thenCompose(challenge -> {
                        requireCurrent(expected);
                        requireProtocol(challenge.protocolVersion());
                        return sessionProof.authenticate(challenge.serverId(), () -> {
                            requireCurrent(expected);
                            return gateway.complete(challenge.challengeId());
                        });
                    })
                    .thenCompose(session -> {
                        try (var callbackScope = stateAccess.enter()) {
                            requireCurrent(expected);
                            requireProtocol(session.protocolVersion());
                            bearerToken = session.accessToken();
                            lifecycle = LifecycleState.SYNCING;
                            statusMessage = "Synchronizing live lobbies...";
                            awaitingSnapshot = true;
                        }
                        return gateway.connectLive(session.accessToken(), liveListener(expected));
                    })
                    .thenAccept(connection -> {
                        try (var callbackScope = stateAccess.enter()) {
                            if (expected != generation) connection.close();
                            else liveConnection = connection;
                        }
                    })
                    .exceptionally(failure -> {
                        try (var callbackScope = stateAccess.enter()) { authenticationInFlight = false; }
                        handleAsyncFailure(expected, failure, false);
                        return null;
                    });
        }
    }

    private LfgGateway.LiveListener liveListener(long expected) {
        return new LfgGateway.LiveListener() {
            @Override public void onFrame(LfgProtocol.LiveFrame frame) { applyLive(expected, frame); }
            @Override public void onClosed(int statusCode, String reason) { liveClosed(expected, statusCode, reason); }
            @Override public void onFailure(Throwable failure) { handleAsyncFailure(expected, failure, true); }
        };
    }

    private void beginRenewal(Instant expiresAt) {
        try (var stateScope = stateAccess.enter()) {
            if (expiresAt == null || authenticationInFlight) return;
            if (renewalExpiresAt == null || expiresAt.isBefore(renewalExpiresAt)) renewalExpiresAt = expiresAt;
            if (renewal != null) return;
            if (clockMillis.getAsLong() < renewalRetryAtMillis) return;
            if (lifecycle != LifecycleState.ONLINE && bearerToken == null) return;
            long id = ++renewalSequence;
            renewal = new RenewalAttempt(id);
            gateway.status()
                    .thenCompose(status -> {
                        requireRenewal(id);
                        if (!status.enabled()) throw terminal("LFG_DISABLED", "Raid LFG is currently unavailable.");
                        if (status.protocolVersion() != LfgProtocol.VERSION) {
                            throw terminal("UNSUPPORTED_PROTOCOL", "Fox Raid LFG uses an incompatible protocol.");
                        }
                        return gateway.challenge(environment.playerId(), environment.ign());
                    })
                    .thenCompose(challenge -> {
                        requireRenewal(id);
                        requireProtocol(challenge.protocolVersion());
                        return sessionProof.authenticate(challenge.serverId(), () -> {
                            requireRenewal(id);
                            return gateway.complete(challenge.challengeId());
                        });
                    })
                    .thenCompose(session -> {
                        try (var callbackScope = stateAccess.enter()) {
                            var attempt = requireRenewal(id);
                            requireProtocol(session.protocolVersion());
                            attempt.token = session.accessToken();
                        }
                        return gateway.connectLive(session.accessToken(), renewalListener(id));
                    })
                    .thenAccept(connection -> {
                        try (var callbackScope = stateAccess.enter()) {
                            var attempt = renewal;
                            if (attempt == null || attempt.id != id) connection.close();
                            else {
                                attempt.connection = connection;
                                tryCommitRenewal(attempt);
                            }
                        }
                    })
                    .exceptionally(failure -> {
                        renewalFailed(id, unwrap(failure));
                        return null;
                    });
        }
    }

    private LfgGateway.LiveListener renewalListener(long id) {
        return new LfgGateway.LiveListener() {
            @Override public void onFrame(LfgProtocol.LiveFrame frame) { receiveRenewalFrame(id, frame); }
            @Override public void onClosed(int statusCode, String reason) {
                renewalFailed(id, new LfgGatewayException(statusCode,
                        new LfgProtocol.Error("LIVE_CLOSED", "Renewal connection closed.", true, null, null)));
            }
            @Override public void onFailure(Throwable failure) { renewalFailed(id, unwrap(failure)); }
        };
    }

    private void receiveRenewalFrame(long id, LfgProtocol.LiveFrame frame) {
        try (var stateScope = stateAccess.enter()) {
            var attempt = renewal;
            if (attempt == null || attempt.id != id) return;
            try {
                if (attempt.snapshot == null) {
                    if (!(frame instanceof LfgProtocol.SnapshotFrame snapshotFrame)) {
                        throw new LfgProtocolException("Fox sent a renewal update before the required snapshot.");
                    }
                    requireProtocol(snapshotFrame.snapshot().protocolVersion());
                    attempt.snapshot = snapshotFrame.snapshot();
                } else {
                    if (attempt.bufferedFrames.size() >= COMMAND_DEDUPLICATION_LIMIT) {
                        throw new LfgProtocolException("Too many live frames arrived during credential renewal.");
                    }
                    attempt.bufferedFrames.add(frame);
                }
                tryCommitRenewal(attempt);
            } catch (RuntimeException failure) {
                renewalFailed(id, failure);
            }
        }
    }

    private void tryCommitRenewal(RenewalAttempt attempt) {
        try (var stateScope = stateAccess.enter()) {
            if (renewal != attempt || attempt.connection == null || attempt.snapshot == null) return;
            var old = liveConnection;
            generation++;
            liveConnection = attempt.connection;
            bearerToken = attempt.token;
            awaitingSnapshot = false;
            store.replace(attempt.snapshot, RaidLfgStore.UpdateOrigin.SNAPSHOT);
            reconcileUnknownMutations(attempt.snapshot);
            lifecycle = LifecycleState.ONLINE;
            statusMessage = "Live";
            reconnectAttempt = 0;
            renewalRetryAttempt = 0;
            renewalRetryAtMillis = 0;
            renewalExpiresAt = null;
            renewal = null;
            if (old != null && old != liveConnection) old.close();
            long activeGeneration = generation;
            for (var frame : attempt.bufferedFrames) applyLive(activeGeneration, frame);
        }
    }

    private void renewalFailed(long id, Throwable failure) {
        try (var stateScope = stateAccess.enter()) {
            var attempt = renewal;
            if (attempt == null || attempt.id != id) return;
            if (attempt.connection != null) attempt.connection.close();
            renewal = null;
            var cause = unwrap(failure);
            if (cause instanceof StaleGenerationException) return;
            if (cause instanceof LfgGatewayException gatewayFailure) {
                var error = gatewayFailure.error();
                if (isIneligible(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.INELIGIBLE, error.message());
                    return;
                }
                if (isUnsupportedProtocol(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.OUTDATED, error.message());
                    return;
                }
                scheduleRenewalRetry(error.retryAfterSeconds());
            } else if (cause instanceof LfgProtocolException) {
                LOGGER.error("Fox returned incompatible Raid LFG renewal data: {}", cause.getMessage(), cause);
                terminalUnavailable("Fox returned incompatible Raid LFG data.");
            } else if (cause instanceof TerminalException terminal) {
                if ("UNSUPPORTED_PROTOCOL".equals(terminal.code)) terminalState(LifecycleState.OUTDATED, terminal.getMessage());
                else terminalUnavailable(terminal.getMessage());
            } else {
                scheduleRenewalRetry(null);
            }
        }
    }

    private void scheduleRenewalRetry(Integer retryAfterSeconds) {
        long delay = retryDelayMillis(renewalRetryAttempt++, retryAfterSeconds);
        renewalRetryAtMillis = clockMillis.getAsLong() + delay;
        if (renewalExpiresAt != null && clockMillis.getAsLong() >= renewalExpiresAt.toEpochMilli()) {
            enterRenewalReadOnly();
            reconnectAtMillis = renewalRetryAtMillis;
        }
    }

    private void enterRenewalReadOnly() {
        if (bearerToken == null) return;
        generation++;
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
        lifecycle = LifecycleState.RECONNECTING;
        statusMessage = "Session renewal is still pending. Raid LFG is read-only while reconnecting…";
        reconnectAtMillis = Math.max(clockMillis.getAsLong(), renewalRetryAtMillis);
    }

    private RenewalAttempt requireRenewal(long id) {
        try (var stateScope = stateAccess.enter()) {
            if (renewal == null || renewal.id != id) throw new StaleGenerationException();
            return renewal;
        }
    }

    private void applyLive(long expected, LfgProtocol.LiveFrame frame) {
        try (var stateScope = stateAccess.enter()) {
            if (expected != generation) return;
            if (awaitingSnapshot && !(frame instanceof LfgProtocol.SnapshotFrame)) {
                terminalUnavailable("Fox sent a live update before the required snapshot.");
                return;
            }
            if (frame instanceof LfgProtocol.SnapshotFrame snapshotFrame) {
                requireProtocol(snapshotFrame.snapshot().protocolVersion());
                store.replace(snapshotFrame.snapshot(), RaidLfgStore.UpdateOrigin.SNAPSHOT);
                reconcileUnknownMutations(snapshotFrame.snapshot());
                awaitingSnapshot = false;
                authenticationInFlight = false;
                reconnectAttempt = 0;
                renewalExpiresAt = null;
                renewalRetryAttempt = 0;
                renewalRetryAtMillis = 0;
                lifecycle = LifecycleState.ONLINE;
                statusMessage = "Live";
                if (synchronizationPending) {
                    synchronizationPending = false;
                    stateAccess.complete(synchronizationFuture, snapshotFrame.snapshot());
                }
            } else if (frame instanceof LfgProtocol.UpsertFrame upsert) {
                requireProtocol(upsert.protocolVersion());
                store.upsert(upsert.revision(), upsert.lobby(), RaidLfgStore.UpdateOrigin.LIVE);
            } else if (frame instanceof LfgProtocol.RemoveFrame remove) {
                requireProtocol(remove.protocolVersion());
                store.remove(remove.revision(), remove.lobbyId(), RaidLfgStore.UpdateOrigin.LIVE, remove.reason());
            } else if (frame instanceof LfgProtocol.PartyPingFrame ping) {
                requireProtocol(ping.protocolVersion());
                focusLobbyId = ping.lobbyId();
                stateAccess.afterUnlock(() -> notifications.partyPing(ping));
            } else if (frame instanceof LfgProtocol.PartyKickCommandFrame command) {
                requireProtocol(command.protocolVersion());
                if (rememberCommand(command.eventId())) stateAccess.afterUnlock(() -> partyCommands.kick(command.targetIgn()));
            } else if (frame instanceof LfgProtocol.SessionExpiringFrame expiring) {
                requireProtocol(expiring.protocolVersion());
                beginRenewal(expiring.expiresAt());
            } else if (frame instanceof LfgProtocol.ErrorFrame error) {
                applyLiveError(error.error());
            }
        }
    }

    private void liveClosed(long expected, int statusCode, String reason) {
        try (var stateScope = stateAccess.enter()) {
            if (expected != generation) return;
            var code = reason == null ? "" : reason.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (statusCode == 4401 || "UNAUTHORIZED".equals(code)) {
                if (renewal != null) enterRenewalReadOnly();
                else {
                    authenticationInFlight = false;
                    restartAuthentication();
                }
            } else if (statusCode == 4403 || "INELIGIBLE".equals(code) || "INELIGIBLE_GUILD".equals(code)) {
                terminalState(LifecycleState.INELIGIBLE, "Alliance access is unavailable.");
            } else if (statusCode == 4406 || "UNSUPPORTED_PROTOCOL".equals(code)) {
                terminalState(LifecycleState.OUTDATED, "Fox Raid LFG uses an incompatible protocol.");
            } else if ("WYNNCRAFT_UNAVAILABLE".equals(code) || "UPSTREAM_UNAVAILABLE".equals(code)
                    || "TRANSPORT_FAILURE".equals(code) || statusCode < 4000) {
                if (renewal != null) enterRenewalReadOnly();
                else scheduleReconnect("Live connection lost. Reconnecting...");
            } else {
                terminalUnavailable("Fox rejected the live connection.");
            }
        }
    }

    private void handleAsyncFailure(long expected, Throwable failure, boolean connectedPhase) {
        try (var stateScope = stateAccess.enter()) {
            if (expected != generation) return;
            var cause = unwrap(failure);
            if (cause instanceof StaleGenerationException) return;
            if (cause instanceof LfgGatewayException gatewayFailure) {
                var error = gatewayFailure.error();
                if (gatewayFailure.status() == 401) {
                    if (renewal != null) enterRenewalReadOnly();
                    else {
                        authenticationInFlight = false;
                        scheduleReconnect("Authentication expired. Reconnecting...", error.retryAfterSeconds());
                    }
                    return;
                }
                if (isIneligible(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.INELIGIBLE, error.message());
                    return;
                }
                if (isUnsupportedProtocol(error, gatewayFailure.status())) {
                    terminalState(LifecycleState.OUTDATED, error.message());
                    return;
                }
                switch (error.code()) {
                    case "UNSUPPORTED_PROTOCOL" -> terminalState(LifecycleState.OUTDATED, error.message());
                    case "INELIGIBLE", "INELIGIBLE_GUILD" -> terminalState(LifecycleState.INELIGIBLE, error.message());
                    case "UNAUTHORIZED", "INVALID_CHALLENGE" -> scheduleReconnect("Authentication expired. Reconnecting...");
                    default -> {
                        if (gatewayFailure.transportFailure() || error.retryable()) {
                            if (renewal != null && connectedPhase) enterRenewalReadOnly();
                            else scheduleReconnect(error.message(), error.retryAfterSeconds());
                        }
                        else terminalUnavailable(error.message());
                    }
                }
            } else if (cause instanceof LfgProtocolException) {
                LOGGER.error("Fox returned incompatible Raid LFG data: {}", cause.getMessage(), cause);
                terminalUnavailable("Fox returned incompatible Raid LFG data.");
            } else if (cause instanceof TerminalException terminal) {
                if ("UNSUPPORTED_PROTOCOL".equals(terminal.code)) terminalState(LifecycleState.OUTDATED, terminal.getMessage());
                else terminalUnavailable(terminal.getMessage());
            } else {
                scheduleReconnect(connectedPhase ? "Live connection failed. Reconnecting..." : "Authentication failed. Reconnecting...");
            }
        }
    }

    private void restartAuthentication() {
        try (var stateScope = stateAccess.enter()) {
            if (!environment.enabled() || !isWynncraft(normalizedHost(environment.serverHost()))) return;
            if (authenticationInFlight) return;
            if (renewal != null) {
                enterRenewalReadOnly();
                return;
            }
            lifecycle = LifecycleState.NOT_ON_WYNNCRAFT;
            authenticate();
        }
    }

    private void applyLiveError(LfgProtocol.Error error) {
        if ("UNAUTHORIZED".equals(error.code()) || "INVALID_CHALLENGE".equals(error.code())) {
            restartAuthentication();
        } else if (isIneligible(error, 0)) {
            terminalState(LifecycleState.INELIGIBLE, error.message());
        } else if (isUnsupportedProtocol(error, 0)) {
            terminalState(LifecycleState.OUTDATED, error.message());
        } else if (error.retryable()) {
            scheduleReconnect(error.message(), error.retryAfterSeconds());
        } else {
            terminalUnavailable(error.message());
        }
    }

    private void scheduleReconnect(String message) {
        scheduleReconnect(message, null);
    }

    private void scheduleReconnect(String message, Integer retryAfterSeconds) {
        generation++;
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
        authenticationInFlight = false;
        lifecycle = LifecycleState.RECONNECTING;
        statusMessage = message;
        reconnectAtMillis = clockMillis.getAsLong() + retryDelayMillis(reconnectAttempt++, retryAfterSeconds);
    }

    private long retryDelayMillis(int attempt, Integer retryAfterSeconds) {
        long base = RECONNECT_SECONDS[Math.min(attempt, RECONNECT_SECONDS.length - 1)] * 1000L;
        double factor = 0.85 + Math.clamp(jitter.getAsDouble(), 0, 1) * 0.30;
        long jittered = Math.max(250, Math.round(base * factor));
        if (retryAfterSeconds == null) return jittered;
        return Math.max(jittered, Math.max(0L, retryAfterSeconds.longValue()) * 1000L);
    }

    private void terminalUnavailable(String message) {
        terminalState(LifecycleState.UNAVAILABLE, message);
    }

    private void terminalState(LifecycleState state, String message) {
        generation++;
        closeLive();
        closeRenewal();
        bearerToken = null;
        awaitingSnapshot = false;
        authenticationInFlight = false;
        failUnknownMutations(message);
        if (synchronizationPending) {
            synchronizationPending = false;
            stateAccess.fail(synchronizationFuture, new IllegalStateException(message));
        }
        lifecycle = state;
        statusMessage = message;
    }

    private void invalidate(LifecycleState next, String message) {
        generation++;
        closeLive();
        closeRenewal();
        bearerToken = null;
        awaitingSnapshot = false;
        authenticationInFlight = false;
        failUnknownMutations("Raid LFG context changed before the action could be confirmed.");
        if (synchronizationPending) {
            synchronizationPending = false;
            stateAccess.fail(synchronizationFuture, new IllegalStateException(message));
        }
        pending.clear();
        pingCooldowns.clear();
        processedCommandIds.clear();
        processedCommandOrder.clear();
        reconnectAttempt = 0;
        renewalExpiresAt = null;
        renewalRetryAttempt = 0;
        renewalRetryAtMillis = 0;
        focusLobbyId = null;
        store.clear();
        lifecycle = next;
        statusMessage = message;
    }

    private void closeLive() {
        if (liveConnection != null) {
            var old = liveConnection;
            liveConnection = null;
            old.close();
        }
    }

    private void closeRenewal() {
        var old = renewal;
        renewal = null;
        if (old != null && old.connection != null) old.connection.close();
    }

    public boolean outcomeUnknown(UUID lobbyId, String action) {
        try (var stateScope = stateAccess.enter()) {
            return unknownMutations.containsKey(pendingKey(lobbyId, action));
        }
    }

    public static boolean isOutcomeUnknown(Throwable failure) {
        return unwrap(failure) instanceof LfgMutationOutcomeUnknownException;
    }

    private void reconcileUnknownMutations(LfgProtocol.Snapshot snapshot) {
        if (unknownMutations.isEmpty()) return;
        var operations = List.copyOf(unknownMutations.values());
        for (var operation : operations) {
            var lobby = reconciledLobby(operation, snapshot);
            boolean accepted = switch (operation.action) {
                case "create", "join", "lock" -> lobby != null;
                case "leave", "disband" -> reconciledRemoval(operation, snapshot);
                default -> false;
            };
            pending.remove(operation.pendingKey);
            unknownMutations.remove(operation.pendingKey);
            if (accepted) {
                runAcceptedCommand(operation);
                var resultLobby = lobby != null ? lobby : operation.beforeLobby;
                operation.complete(stateAccess, new LfgProtocol.Mutation(
                        LfgProtocol.VERSION, snapshot.revision(), resultLobby));
            } else if ("kick".equals(operation.action) || "ping".equals(operation.action)) {
                operation.fail(stateAccess, new LfgMutationOutcomeUnknownException(
                        operation.action, operation.idempotencyKey,
                        "The action was submitted, but its outcome could not be confirmed after synchronization."));
            } else {
                operation.fail(stateAccess, new IllegalStateException(
                        "The action was not applied after synchronization."));
            }
        }
    }

    private LfgProtocol.Lobby reconciledLobby(PendingMutation operation, LfgProtocol.Snapshot snapshot) {
        var viewer = snapshot.viewer().minecraftUuid();
        if ("create".equals(operation.action)) {
            return snapshot.lobbies().stream()
                    .filter(lobby -> lobby.hostedBy(viewer)
                            && lobby.raidType() == operation.raid
                            && lobby.region() == operation.region
                            && Objects.equals(lobby.note(), operation.note))
                    .findFirst().orElse(null);
        }
        var lobby = snapshot.lobbies().stream()
                .filter(candidate -> candidate.lobbyId().equals(operation.lobbyId))
                .findFirst().orElse(null);
        if ("join".equals(operation.action)) {
            return lobby != null && lobby.contains(viewer) ? lobby : null;
        }
        if ("lock".equals(operation.action)) {
            return lobby != null && lobby.locked() == operation.desiredFlag ? lobby : null;
        }
        return lobby;
    }

    private boolean reconciledRemoval(PendingMutation operation, LfgProtocol.Snapshot snapshot) {
        var lobby = snapshot.lobbies().stream()
                .filter(candidate -> candidate.lobbyId().equals(operation.lobbyId))
                .findFirst().orElse(null);
        if ("disband".equals(operation.action)) return lobby == null;
        return lobby == null || !lobby.contains(snapshot.viewer().minecraftUuid());
    }

    private void runAcceptedCommand(PendingMutation operation) {
        if (operation.acceptedCommand == null || operation.commandExecuted) return;
        operation.commandExecuted = true;
        stateAccess.afterUnlock(() -> {
            try {
                operation.acceptedCommand.run();
            } catch (RuntimeException failure) {
                LOGGER.error("Accepted Raid LFG {} action could not run its bounded party command",
                        operation.action, failure);
            }
        });
    }

    private void failUnknownMutations(String message) {
        for (var operation : List.copyOf(unknownMutations.values())) {
            pending.remove(operation.pendingKey);
            operation.fail(stateAccess, new LfgMutationOutcomeUnknownException(
                    operation.action, operation.idempotencyKey, message));
        }
        unknownMutations.clear();
    }

    private void requireCurrent(long expected) {
        try (var scope = stateAccess.enter()) {
            if (expected != generation) throw new StaleGenerationException();
        }
    }

    private static void requireProtocol(int version) {
        if (version != LfgProtocol.VERSION) {
            throw terminal("UNSUPPORTED_PROTOCOL", "Fox Raid LFG uses an incompatible protocol.");
        }
    }

    private static String sanitizeNote(String note) {
        return LfgNoteText.sanitizeForSubmission(note);
    }

    private static String normalizedHost(String host) {
        return WynncraftHost.normalize(host);
    }

    /** Compatibility delegate retained for existing LFG callers. */
    public static boolean isWynncraft(String host) {
        return WynncraftHost.matches(host);
    }

    private static String pendingKey(UUID lobbyId, String action) {
        return lobbyId == null ? action : lobbyId + ":" + action;
    }

    private static boolean isIneligible(LfgProtocol.Error error, int status) {
        return status == 403 || "INELIGIBLE".equals(error.code()) || "INELIGIBLE_GUILD".equals(error.code());
    }

    private static boolean isUnsupportedProtocol(LfgProtocol.Error error, int status) {
        return status == 426 || "UNSUPPORTED_PROTOCOL".equals(error.code());
    }

    private boolean rememberCommand(UUID eventId) {
        if (!processedCommandIds.add(eventId)) return false;
        processedCommandOrder.addLast(eventId);
        while (processedCommandOrder.size() > COMMAND_DEDUPLICATION_LIMIT) {
            processedCommandIds.remove(processedCommandOrder.removeFirst());
        }
        return true;
    }

    private static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException) && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static TerminalException terminal(String code, String message) {
        return new TerminalException(code, message);
    }

    private static <T> CompletableFuture<T> unavailableFuture() {
        return CompletableFuture.failedFuture(new IllegalStateException("Raid LFG is not online."));
    }

    @FunctionalInterface
    private interface MutationCall {
        CompletableFuture<LfgProtocol.Mutation> apply(String token, UUID idempotencyKey);
    }

    private static final class PendingMutation {
        private final String action;
        private final UUID lobbyId;
        private final LfgProtocol.RaidType raid;
        private final LfgProtocol.Region region;
        private final String note;
        private final boolean desiredFlag;
        private final Runnable acceptedCommand;
        private final CompletableFuture<LfgProtocol.Mutation> result = new CompletableFuture<>();
        private String pendingKey;
        private UUID idempotencyKey;
        private String contextKey;
        private LfgProtocol.Lobby beforeLobby;
        private boolean commandExecuted;
        private boolean completed;

        private void complete(LfgStateAccess access, LfgProtocol.Mutation mutation) {
            completed = true;
            access.complete(result, mutation);
        }

        private void fail(LfgStateAccess access, Throwable failure) {
            completed = true;
            access.fail(result, failure);
        }

        private PendingMutation(String action, UUID lobbyId, LfgProtocol.RaidType raid,
                                LfgProtocol.Region region, String note, boolean desiredFlag,
                                Runnable acceptedCommand) {
            this.action = action;
            this.lobbyId = lobbyId;
            this.raid = raid;
            this.region = region;
            this.note = note;
            this.desiredFlag = desiredFlag;
            this.acceptedCommand = acceptedCommand;
        }

        private boolean removesLobby() {
            return "disband".equals(action);
        }
    }

    private static final class RenewalAttempt {
        private final long id;
        private final List<LfgProtocol.LiveFrame> bufferedFrames = new ArrayList<>();
        private String token;
        private LfgGateway.LiveConnection connection;
        private LfgProtocol.Snapshot snapshot;

        private RenewalAttempt(long id) {
            this.id = id;
        }
    }

    private static final class StaleGenerationException extends RuntimeException {}

    private static final class TerminalException extends RuntimeException {
        private final String code;
        private TerminalException(String code, String message) { super(message); this.code = code; }
    }
}

package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgNoteText;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
    private final RaidLfgStore store = new RaidLfgStore();
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
    private int reconnectAttempt;
    private long reconnectAtMillis;

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

    public RaidLfgStore store() { return store; }
    public LfgJoinController joinController() { return joinController; }
    public LifecycleState lifecycle() { return lifecycle; }
    public String statusMessage() { return statusMessage; }
    public UUID focusLobbyId() { return focusLobbyId; }
    public void clearFocus() { focusLobbyId = null; }
    public void focusLobby(UUID lobbyId) { focusLobbyId = lobbyId; }

    public synchronized int pingCooldownSeconds(UUID lobbyId) {
        long remaining = pingCooldowns.getOrDefault(lobbyId, 0L) - clockMillis.getAsLong();
        if (remaining <= 0) {
            pingCooldowns.remove(lobbyId);
            return 0;
        }
        return (int) Math.ceil(remaining / 1000d);
    }

    public synchronized void connectionChanged() {
        contextKey = "";
    }

    public synchronized boolean pending(UUID lobbyId, String action) {
        return pending.contains(pendingKey(lobbyId, action));
    }

    public synchronized boolean pendingCreate() {
        return pending.contains("create");
    }

    /** Reevaluate every client tick; it performs no network request outside the opt-in Wynncraft state. */
    public synchronized void tick() {
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
        }
    }

    public CompletableFuture<LfgProtocol.Snapshot> refresh() {
        final String token;
        final long expected;
        synchronized (this) {
            if (bearerToken == null || lifecycle != LifecycleState.ONLINE) return unavailableFuture();
            token = bearerToken;
            expected = generation;
            lifecycle = LifecycleState.SYNCING;
            statusMessage = "Refreshing complete lobby state...";
        }
        return gateway.snapshot(token).thenApply(snapshot -> {
            synchronized (this) {
                if (expected != generation) return snapshot;
                requireProtocol(snapshot.protocolVersion());
                store.replace(snapshot, RaidLfgStore.UpdateOrigin.SNAPSHOT);
                lifecycle = LifecycleState.ONLINE;
                statusMessage = "Live";
            }
            return snapshot;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) handleAsyncFailure(expected, failure, true);
        });
    }

    public CompletableFuture<LfgProtocol.Mutation> create(LfgProtocol.RaidType raid,
                                                           LfgProtocol.Region region, String note) {
        final String cleaned;
        try {
            cleaned = sanitizeNote(note);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return mutate("create", null, false, token -> gateway.create(token, raid, region, cleaned, UUID.randomUUID()));
    }

    public CompletableFuture<LfgProtocol.Mutation> join(UUID lobbyId) {
        return mutate("join", lobbyId, false, token -> gateway.join(token, lobbyId, UUID.randomUUID()));
    }

    public CompletableFuture<LfgProtocol.Mutation> leave(UUID lobbyId) {
        return mutate("leave", lobbyId, false, token -> gateway.leave(token, lobbyId, UUID.randomUUID()));
    }

    public CompletableFuture<LfgProtocol.Mutation> disband(UUID lobbyId) {
        return mutate("disband", lobbyId, true, token -> gateway.disband(token, lobbyId, UUID.randomUUID()));
    }

    public CompletableFuture<LfgProtocol.Mutation> kick(UUID lobbyId, UUID targetId, String targetIgn) {
        if (targetIgn == null || !IGN.matcher(targetIgn).matches()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Target IGN is invalid."));
        }
        return mutate("kick", lobbyId, false,
                token -> gateway.kick(token, lobbyId, targetId, UUID.randomUUID()))
                .thenApply(mutation -> {
                    partyCommands.kick(targetIgn);
                    return mutation;
                });
    }

    public CompletableFuture<LfgProtocol.Mutation> setLocked(UUID lobbyId, boolean locked) {
        return mutate("lock", lobbyId, false,
                token -> gateway.setLocked(token, lobbyId, locked, UUID.randomUUID()));
    }

    public CompletableFuture<LfgProtocol.Mutation> ping(UUID lobbyId) {
        return mutate("ping", lobbyId, false,
                token -> gateway.ping(token, lobbyId, UUID.randomUUID()))
                .thenApply(mutation -> {
                    synchronized (this) {
                        pingCooldowns.put(lobbyId, clockMillis.getAsLong() + PING_COOLDOWN_MILLIS);
                    }
                    return mutation;
                });
    }

    private CompletableFuture<LfgProtocol.Mutation> mutate(String action, UUID lobbyId, boolean removal,
                                                            java.util.function.Function<String, CompletableFuture<LfgProtocol.Mutation>> call) {
        final String key = pendingKey(lobbyId, action);
        final String token;
        final long expected;
        synchronized (this) {
            if (lifecycle != LifecycleState.ONLINE || bearerToken == null) return unavailableFuture();
            if (!pending.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("That action is already pending."));
            token = bearerToken;
            expected = generation;
        }
        return call.apply(token).thenApply(mutation -> {
            synchronized (this) {
                requireCurrent(expected);
            }
            requireProtocol(mutation.protocolVersion());
            if (removal) store.remove(mutation.revision(), mutation.lobby().lobbyId(),
                    RaidLfgStore.UpdateOrigin.LOCAL_MUTATION);
            else store.apply(mutation);
            statusMessage = "Live";
            return mutation;
        }).whenComplete((ignored, failure) -> {
            synchronized (this) { pending.remove(key); }
            if (failure != null) {
                var cause = unwrap(failure);
                if (cause instanceof StaleGenerationException) return;
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
                    } else if (gatewayFailure.transportFailure()
                            || (error.retryable() && !"RATE_LIMITED".equals(error.code()))) {
                        scheduleReconnect(error.message());
                    }
                } else if (cause instanceof TerminalException terminal
                        && "UNSUPPORTED_PROTOCOL".equals(terminal.code)) {
                    terminalState(LifecycleState.OUTDATED, terminal.getMessage());
                } else {
                    statusMessage = cause.getMessage() == null || cause.getMessage().isBlank()
                            ? "The host action failed."
                            : cause.getMessage();
                }
            }
        });
    }

    private synchronized void authenticate() {
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
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
                    return sessionProof.prove(challenge.serverId()).thenApply(ignored -> challenge);
                })
                .thenCompose(challenge -> gateway.complete(challenge.challengeId()))
                .thenCompose(session -> {
                    synchronized (this) {
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
                    synchronized (this) {
                        if (expected != generation) connection.close();
                        else liveConnection = connection;
                    }
                })
                .exceptionally(failure -> {
                    handleAsyncFailure(expected, failure, false);
                    return null;
                });
    }

    private LfgGateway.LiveListener liveListener(long expected) {
        return new LfgGateway.LiveListener() {
            @Override public void onFrame(LfgProtocol.LiveFrame frame) { applyLive(expected, frame); }
            @Override public void onClosed(int statusCode, String reason) { liveClosed(expected, statusCode, reason); }
            @Override public void onFailure(Throwable failure) { handleAsyncFailure(expected, failure, true); }
        };
    }

    private synchronized void applyLive(long expected, LfgProtocol.LiveFrame frame) {
        if (expected != generation) return;
        if (awaitingSnapshot && !(frame instanceof LfgProtocol.SnapshotFrame)) {
            terminalUnavailable("Fox sent a live update before the required snapshot.");
            return;
        }
        if (frame instanceof LfgProtocol.SnapshotFrame snapshotFrame) {
            requireProtocol(snapshotFrame.snapshot().protocolVersion());
            store.replace(snapshotFrame.snapshot(), RaidLfgStore.UpdateOrigin.SNAPSHOT);
            awaitingSnapshot = false;
            reconnectAttempt = 0;
            lifecycle = LifecycleState.ONLINE;
            statusMessage = "Live";
        } else if (frame instanceof LfgProtocol.UpsertFrame upsert) {
            requireProtocol(upsert.protocolVersion());
            store.upsert(upsert.revision(), upsert.lobby(), RaidLfgStore.UpdateOrigin.LIVE);
        } else if (frame instanceof LfgProtocol.RemoveFrame remove) {
            requireProtocol(remove.protocolVersion());
            store.remove(remove.revision(), remove.lobbyId(), RaidLfgStore.UpdateOrigin.LIVE);
        } else if (frame instanceof LfgProtocol.PartyPingFrame ping) {
            requireProtocol(ping.protocolVersion());
            focusLobbyId = ping.lobbyId();
            notifications.partyPing(ping);
        } else if (frame instanceof LfgProtocol.PartyKickCommandFrame command) {
            requireProtocol(command.protocolVersion());
            if (rememberCommand(command.eventId())) partyCommands.kick(command.targetIgn());
        } else if (frame instanceof LfgProtocol.SessionExpiringFrame) {
            restartAuthentication();
        } else if (frame instanceof LfgProtocol.ErrorFrame error) {
            applyLiveError(error.error());
        }
    }

    private synchronized void liveClosed(long expected, int statusCode, String reason) {
        if (expected != generation) return;
        var code = reason == null ? "" : reason.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (statusCode == 4401 || "UNAUTHORIZED".equals(code)) {
            restartAuthentication();
        } else if (statusCode == 4403 || "INELIGIBLE".equals(code) || "INELIGIBLE_GUILD".equals(code)) {
            terminalState(LifecycleState.INELIGIBLE, "Alliance access is unavailable.");
        } else if (statusCode == 4406 || "UNSUPPORTED_PROTOCOL".equals(code)) {
            terminalState(LifecycleState.OUTDATED, "Fox Raid LFG uses an incompatible protocol.");
        } else if ("WYNNCRAFT_UNAVAILABLE".equals(code) || "UPSTREAM_UNAVAILABLE".equals(code)
                || "TRANSPORT_FAILURE".equals(code) || statusCode < 4000) {
            scheduleReconnect("Live connection lost. Reconnecting...");
        } else {
            terminalUnavailable("Fox rejected the live connection.");
        }
    }

    private synchronized void handleAsyncFailure(long expected, Throwable failure, boolean connectedPhase) {
        if (expected != generation) return;
        var cause = unwrap(failure);
        if (cause instanceof StaleGenerationException) return;
        if (cause instanceof LfgGatewayException gatewayFailure) {
            var error = gatewayFailure.error();
            if (gatewayFailure.status() == 401) {
                scheduleReconnect("Authentication expired. Reconnecting...");
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
                    if (gatewayFailure.transportFailure() || error.retryable()) scheduleReconnect(error.message());
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

    private synchronized void restartAuthentication() {
        if (!environment.enabled() || !isWynncraft(normalizedHost(environment.serverHost()))) return;
        lifecycle = LifecycleState.NOT_ON_WYNNCRAFT;
        authenticate();
    }

    private void applyLiveError(LfgProtocol.Error error) {
        if ("UNAUTHORIZED".equals(error.code()) || "INVALID_CHALLENGE".equals(error.code())) {
            restartAuthentication();
        } else if (isIneligible(error, 0)) {
            terminalState(LifecycleState.INELIGIBLE, error.message());
        } else if (isUnsupportedProtocol(error, 0)) {
            terminalState(LifecycleState.OUTDATED, error.message());
        } else if (error.retryable()) {
            scheduleReconnect(error.message());
        } else {
            terminalUnavailable(error.message());
        }
    }

    private void scheduleReconnect(String message) {
        generation++;
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
        lifecycle = LifecycleState.RECONNECTING;
        statusMessage = message;
        long base = RECONNECT_SECONDS[Math.min(reconnectAttempt, RECONNECT_SECONDS.length - 1)] * 1000L;
        reconnectAttempt++;
        double factor = 0.85 + Math.clamp(jitter.getAsDouble(), 0, 1) * 0.30;
        reconnectAtMillis = clockMillis.getAsLong() + Math.max(250, Math.round(base * factor));
    }

    private void terminalUnavailable(String message) {
        terminalState(LifecycleState.UNAVAILABLE, message);
    }

    private void terminalState(LifecycleState state, String message) {
        generation++;
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
        lifecycle = state;
        statusMessage = message;
    }

    private void invalidate(LifecycleState next, String message) {
        generation++;
        closeLive();
        bearerToken = null;
        awaitingSnapshot = false;
        pending.clear();
        pingCooldowns.clear();
        processedCommandIds.clear();
        processedCommandOrder.clear();
        reconnectAttempt = 0;
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

    private void requireCurrent(long expected) {
        if (expected != generation) throw new StaleGenerationException();
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
        if (host == null) return "";
        var normalized = host.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[")) {
            int close = normalized.indexOf(']');
            return close >= 0 ? normalized.substring(1, close) : normalized;
        }
        int colon = normalized.indexOf(':');
        return colon >= 0 ? normalized.substring(0, colon) : normalized;
    }

    static boolean isWynncraft(String host) {
        return isDomainOrSubdomain(host, "wynncraft.com")
                || isDomainOrSubdomain(host, "wynncraft.net");
    }

    private static boolean isDomainOrSubdomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
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

    private static final class StaleGenerationException extends RuntimeException {}

    private static final class TerminalException extends RuntimeException {
        private final String code;
        private TerminalException(String code, String message) { super(message); this.code = code; }
    }
}

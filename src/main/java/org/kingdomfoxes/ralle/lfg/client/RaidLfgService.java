package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
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
    private static final Pattern FORMATTING = Pattern.compile("(?:\\u00a7|&)[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);

    private final LfgGateway gateway;
    private final RaidLfgEnvironment environment;
    private final MinecraftSessionProof sessionProof;
    private final LongSupplier clockMillis;
    private final DoubleSupplier jitter;
    private final RaidLfgStore store = new RaidLfgStore();
    private final Set<String> pending = new HashSet<>();

    private volatile LifecycleState lifecycle = LifecycleState.DISABLED;
    private volatile String statusMessage = "Raid LFG is disabled in settings.";
    private volatile String releaseUrl;
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
        this(gateway, environment, sessionProof, System::currentTimeMillis, Math::random);
    }

    RaidLfgService(LfgGateway gateway, RaidLfgEnvironment environment,
                   MinecraftSessionProof sessionProof, LongSupplier clockMillis,
                   DoubleSupplier jitter) {
        this.gateway = gateway;
        this.environment = environment;
        this.sessionProof = sessionProof;
        this.clockMillis = clockMillis;
        this.jitter = jitter;
    }

    public RaidLfgStore store() { return store; }
    public LifecycleState lifecycle() { return lifecycle; }
    public String statusMessage() { return statusMessage; }
    public String releaseUrl() { return releaseUrl; }
    public UUID focusLobbyId() { return focusLobbyId; }
    public void clearFocus() { focusLobbyId = null; }

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
        var host = normalizedHost(environment.serverHost());
        var nextContext = environment.enabled() + "|" + host + "|" + environment.playerId() + "|" + environment.modVersion();
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
                store.replace(snapshot);
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

    private CompletableFuture<LfgProtocol.Mutation> mutate(String action, UUID lobbyId, boolean removal,
                                                            java.util.function.Function<String, CompletableFuture<LfgProtocol.Mutation>> call) {
        final String key = pendingKey(lobbyId, action);
        final String token;
        synchronized (this) {
            if (lifecycle != LifecycleState.ONLINE || bearerToken == null) return unavailableFuture();
            if (!pending.add(key)) return CompletableFuture.failedFuture(new IllegalStateException("That action is already pending."));
            token = bearerToken;
        }
        return call.apply(token).thenApply(mutation -> {
            if (removal) store.remove(mutation.revision(), mutation.lobby().lobbyId());
            else store.apply(mutation);
            return mutation;
        }).whenComplete((ignored, failure) -> {
            synchronized (this) { pending.remove(key); }
            if (failure != null) {
                var cause = unwrap(failure);
                if (cause instanceof LfgGatewayException gatewayFailure) {
                    var error = gatewayFailure.error();
                    statusMessage = error.message();
                    if ("RAID_ALREADY_LISTED".equals(error.code()) && error.returnedLobby() != null) {
                        store.remember(error.returnedLobby());
                        focusLobbyId = error.returnedLobby().lobbyId();
                    }
                    if (gatewayFailure.status() == 401) restartAuthentication();
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
                    releaseUrl = status.modrinthReleaseUrl();
                    if (!status.enabled()) throw terminal("LFG_DISABLED", "Raid LFG is currently unavailable.");
                    if (status.protocolVersion() != LfgProtocol.VERSION || !environment.modVersion().equals(status.requiredClientVersion())) {
                        throw terminal("CLIENT_VERSION_MISMATCH", "Update RALLE to use Raid LFG.");
                    }
                    return gateway.challenge(environment.playerId(), environment.ign(), environment.modVersion());
                })
                .thenCompose(challenge -> {
                    requireCurrent(expected);
                    if (challenge.protocolVersion() != LfgProtocol.VERSION) throw new LfgProtocolException("Challenge protocol mismatch");
                    return sessionProof.prove(challenge.serverId()).thenApply(ignored -> challenge);
                })
                .thenCompose(challenge -> gateway.complete(challenge.challengeId()))
                .thenCompose(session -> {
                    synchronized (this) {
                        requireCurrent(expected);
                        if (session.protocolVersion() != LfgProtocol.VERSION) throw new LfgProtocolException("Session protocol mismatch");
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
            store.replace(snapshotFrame.snapshot());
            awaitingSnapshot = false;
            reconnectAttempt = 0;
            lifecycle = LifecycleState.ONLINE;
            statusMessage = "Live";
        } else if (frame instanceof LfgProtocol.UpsertFrame upsert) {
            requireProtocol(upsert.protocolVersion());
            store.upsert(upsert.revision(), upsert.lobby());
        } else if (frame instanceof LfgProtocol.RemoveFrame remove) {
            requireProtocol(remove.protocolVersion());
            store.remove(remove.revision(), remove.lobbyId());
        } else if (frame instanceof LfgProtocol.SessionExpiringFrame) {
            restartAuthentication();
        } else if (frame instanceof LfgProtocol.ErrorFrame error) {
            statusMessage = error.error().message();
        }
    }

    private synchronized void liveClosed(long expected, int statusCode, String reason) {
        if (expected != generation) return;
        if (statusCode == 4401) restartAuthentication();
        else if (statusCode == 4403) terminalUnavailable("Fox rejected this client protocol.");
        else scheduleReconnect("Live connection lost. Reconnecting...");
    }

    private synchronized void handleAsyncFailure(long expected, Throwable failure, boolean connectedPhase) {
        if (expected != generation) return;
        var cause = unwrap(failure);
        if (cause instanceof StaleGenerationException) return;
        if (cause instanceof LfgGatewayException gatewayFailure) {
            var error = gatewayFailure.error();
            switch (error.code()) {
                case "CLIENT_VERSION_MISMATCH", "UNSUPPORTED_PROTOCOL" -> terminalState(LifecycleState.OUTDATED, error.message());
                case "INELIGIBLE_GUILD" -> terminalState(LifecycleState.INELIGIBLE, error.message());
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
            if ("CLIENT_VERSION_MISMATCH".equals(terminal.code)) terminalState(LifecycleState.OUTDATED, terminal.getMessage());
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
        if (version != LfgProtocol.VERSION) throw new LfgProtocolException("Protocol version mismatch");
    }

    private static String sanitizeNote(String note) {
        if (note == null) return null;
        var cleaned = FORMATTING.matcher(note).replaceAll("");
        cleaned = cleaned.replaceAll("\\p{Cc}", "").replaceAll("[ \\t]+", " ").strip();
        if (cleaned.length() > 80) throw new IllegalArgumentException("Note must be at most 80 characters.");
        return cleaned.isEmpty() ? null : cleaned;
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

package org.kingdomfoxes.ralle.lfg.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;
import org.kingdomfoxes.ralle.lfg.protocol.StrictLfgJson;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.net.ssl.SSLException;

/** JDK HTTP/WebSocket implementation with bearer headers and one safe mutation retry. */
public final class HttpLfgGateway implements LfgGateway {
    public static final String INTERNAL_TEST_BASE_URL = "http://127.0.0.1:8001/api/ralle/v1";
    public static final String PRODUCTION_BASE_URL = "https://kingdomfoxes.com/api/ralle/v1";
    public static final String DEFAULT_BASE_URL = INTERNAL_TEST_BASE_URL;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    static final int MAX_HTTP_BODY_BYTES = StrictLfgJson.MAX_DOCUMENT_CHARS * 4;

    private final HttpClient client;
    private final URI baseUri;
    private final String clientVersion;
    private final Deque<ClientDiagnostic> diagnosticEvents = new ArrayDeque<>();
    private boolean diagnosticFlushInProgress;

    public HttpLfgGateway() {
        this("unknown");
    }

    public HttpLfgGateway(String clientVersion) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build(),
                System.getProperty("ralle.lfg.baseUrl", DEFAULT_BASE_URL), clientVersion);
    }

    public HttpLfgGateway(HttpClient client, String baseUrl) {
        this(client, baseUrl, "test");
    }

    public HttpLfgGateway(HttpClient client, String baseUrl, String clientVersion) {
        this.client = client;
        this.baseUri = validateBase(baseUrl);
        this.clientVersion = clientVersion == null || clientVersion.isBlank() ? "unknown" : clientVersion.strip();
    }

    @Override
    public CompletableFuture<LfgProtocol.Status> status() {
        return get("/status", null, StrictLfgJson::decodeStatus);
    }

    @Override
    public CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign) {
        return post("/auth/challenge", null, null,
                StrictLfgJson.challengeRequest(playerId, ign), StrictLfgJson::decodeChallenge, false);
    }

    @Override
    public CompletableFuture<LfgProtocol.Session> complete(String challengeId) {
        return post("/auth/complete", null, null, StrictLfgJson.completeRequest(challengeId),
                StrictLfgJson::decodeSession, false)
                .thenApply(session -> {
                    scheduleDiagnosticFlush(session.accessToken());
                    return session;
                });
    }

    @Override
    public CompletableFuture<LfgProtocol.Snapshot> snapshot(String bearerToken) {
        return get("/lobbies", bearerToken, StrictLfgJson::decodeSnapshot);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> create(String token, LfgProtocol.RaidType raid,
                                                           LfgProtocol.Region region, String note, UUID key) {
        return post("/lobbies", token, key, StrictLfgJson.createRequest(raid, region, note),
                StrictLfgJson::decodeMutation, true);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> join(String token, UUID lobbyId, UUID key) {
        return mutation("/lobbies/" + lobbyId + "/join", token, key);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> leave(String token, UUID lobbyId, UUID key) {
        return mutation("/lobbies/" + lobbyId + "/leave", token, key);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> disband(String token, UUID lobbyId, UUID key) {
        return mutation("/lobbies/" + lobbyId + "/disband", token, key);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> kick(String token, UUID lobbyId, UUID targetId, UUID key) {
        return post("/lobbies/" + lobbyId + "/kick", token, key, StrictLfgJson.kickRequest(targetId),
                StrictLfgJson::decodeMutation, true);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> setLocked(String token, UUID lobbyId, boolean locked, UUID key) {
        return post("/lobbies/" + lobbyId + "/lock", token, key, StrictLfgJson.lockRequest(locked),
                StrictLfgJson::decodeMutation, true);
    }

    @Override
    public CompletableFuture<LfgProtocol.Mutation> ping(String token, UUID lobbyId, UUID key) {
        return mutation("/lobbies/" + lobbyId + "/ping", token, key);
    }

    private CompletableFuture<LfgProtocol.Mutation> mutation(String path, String token, UUID key) {
        return post(path, token, key, "{}", StrictLfgJson::decodeMutation, true);
    }

    @Override
    public CompletableFuture<LiveConnection> connectLive(String bearerToken, LiveListener listener) {
        URI uri = websocketUri();
        var requestId = UUID.randomUUID();
        var operationId = UUID.randomUUID();
        var connectedAt = System.nanoTime();
        var adapter = new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();
            private boolean rejected;

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (rejected) return CompletableFuture.completedFuture(null);
                if (data.length() > StrictLfgJson.MAX_DOCUMENT_CHARS - text.length()) {
                    rejected = true;
                    text.setLength(0);
                    listener.onFailure(new LfgProtocolException("Live frame exceeds the protocol document limit"));
                    return webSocket.sendClose(1009, "frame too large");
                }
                text.append(data);
                if (last) {
                    var frameText = text.toString();
                    text.setLength(0);
                    try {
                        listener.onFrame(StrictLfgJson.decodeLiveFrame(frameText));
                    } catch (RuntimeException exception) {
                        listener.onFailure(exception);
                        webSocket.sendClose(1002, "incompatible protocol");
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                listener.onFailure(new LfgProtocolException("Binary live frames are unsupported"));
                return webSocket.sendClose(1003, "text frames required");
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                if (statusCode != 1000 && statusCode != 1001) {
                    rememberFailure(requestId, operationId, "live", 1,
                            statusCode >= 4500 ? "server_error" : "client_error",
                            "WEBSOCKET_CLOSE_" + statusCode, elapsedMillis(connectedAt),
                            null, null, statusCode);
                }
                listener.onClosed(statusCode, reason);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                rememberFailure(requestId, operationId, "live", 1,
                        transportOutcome(error), transportCode(error), elapsedMillis(connectedAt),
                        null, error.getClass().getSimpleName(), null);
                listener.onFailure(error);
            }
        };
        return client.newWebSocketBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("X-Request-ID", requestId.toString())
                .header("X-Operation-ID", operationId.toString())
                .header("X-Operation-Name", "live")
                .header("X-Client-Version", clientVersion)
                .header("X-Client-Attempt", "1")
                .header("X-Protocol-Version", Integer.toString(LfgProtocol.VERSION))
                .buildAsync(uri, adapter)
                .thenApply(socket -> (LiveConnection) () -> socket.sendClose(1000, "client lifecycle changed"))
                .exceptionallyCompose(failure -> {
                    var cause = unwrap(failure);
                    var converted = websocketFailure(failure);
                    var code = converted instanceof LfgGatewayException gateway
                            ? gateway.error().code() : transportCode(cause);
                    var status = converted instanceof LfgGatewayException gateway ? gateway.status() : 0;
                    rememberFailure(requestId, operationId, "live", 1,
                            status >= 500 ? "server_error" : status > 0 ? "client_error" : transportOutcome(cause),
                            code, elapsedMillis(connectedAt), status > 0 ? status : null,
                            cause.getClass().getSimpleName(), null);
                    return CompletableFuture.failedFuture(converted);
                });
    }

    private <T> CompletableFuture<T> get(String path, String token, Function<String, T> decoder) {
        var builder = HttpRequest.newBuilder(resolve(path)).timeout(REQUEST_TIMEOUT).GET();
        authorize(builder, token);
        return send(builder.build(), decoder, false, 0, UUID.randomUUID(), operationName(path), token);
    }

    private <T> CompletableFuture<T> post(String path, String token, UUID key, String body,
                                          Function<String, T> decoder, boolean retryTransport) {
        var builder = HttpRequest.newBuilder(resolve(path)).timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        authorize(builder, token);
        if (key != null) builder.header("Idempotency-Key", key.toString());
        return send(builder.build(), decoder, retryTransport, 0,
                key == null ? UUID.randomUUID() : key, operationName(path), token);
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Function<String, T> decoder,
                                          boolean retryTransport, int attempt,
                                          UUID operationId, String operationName,
                                          String bearerToken) {
        var requestId = UUID.randomUUID();
        var started = System.nanoTime();
        var outbound = instrument(request, requestId, operationId, operationName, attempt + 1);
        return client.sendAsync(outbound, HttpResponse.BodyHandlers.ofInputStream())
                .handle((response, failure) -> {
                    if (failure != null) {
                        if (retryTransport && attempt == 0) {
                            return send(request, decoder, true, 1, operationId, operationName, bearerToken);
                        }
                        var cause = unwrap(failure);
                        rememberFailure(requestId, operationId, operationName, attempt + 1,
                                transportOutcome(cause), transportCode(cause), elapsedMillis(started),
                                null, cause.getClass().getSimpleName(), null);
                        return CompletableFuture.<T>failedFuture(new LfgGatewayException(
                                "Fox Raid LFG could not be reached.", cause));
                    }
                    try {
                        var body = readBody(response.body());
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            var decoded = decoder.apply(body);
                            if (bearerToken != null) scheduleDiagnosticFlush(bearerToken);
                            return CompletableFuture.completedFuture(decoded);
                        }
                        var error = httpError(response.statusCode(), body);
                        if (response.statusCode() >= 500) {
                            rememberFailure(requestId, operationId, operationName, attempt + 1,
                                    "server_error", error.code(), elapsedMillis(started),
                                    response.statusCode(), null, null);
                        }
                        return CompletableFuture.<T>failedFuture(new LfgGatewayException(
                                response.statusCode(), error));
                    } catch (IOException exception) {
                        if (retryTransport && attempt == 0) {
                            return send(request, decoder, true, 1, operationId, operationName, bearerToken);
                        }
                        rememberFailure(requestId, operationId, operationName, attempt + 1,
                                "server_error", "UNREADABLE_RESPONSE", elapsedMillis(started),
                                response.statusCode(), exception.getClass().getSimpleName(), null);
                        return CompletableFuture.<T>failedFuture(new LfgGatewayException(
                                "Fox Raid LFG returned an unreadable response.", exception));
                    } catch (RuntimeException exception) {
                        rememberFailure(requestId, operationId, operationName, attempt + 1,
                                "server_error", "PROTOCOL_ERROR", elapsedMillis(started),
                                response.statusCode(), exception.getClass().getSimpleName(), null);
                        return CompletableFuture.<T>failedFuture(exception);
                    }
                }).thenCompose(Function.identity());
    }

    private HttpRequest instrument(HttpRequest request, UUID requestId, UUID operationId,
                                   String operationName, int attempt) {
        var builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout().orElse(REQUEST_TIMEOUT))
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.header("X-Request-ID", requestId.toString())
                .header("X-Operation-ID", operationId.toString())
                .header("X-Operation-Name", operationName)
                .header("X-Client-Version", clientVersion)
                .header("X-Client-Attempt", Integer.toString(attempt))
                .header("X-Protocol-Version", Integer.toString(LfgProtocol.VERSION))
                .build();
    }

    private static String operationName(String path) {
        if (path.equals("/lobbies")) return "lobbies";
        var clean = path.replaceAll("/[0-9a-fA-F-]{36}", "");
        var slash = clean.lastIndexOf('/');
        return slash >= 0 ? clean.substring(slash + 1) : clean;
    }

    private static String readBody(java.io.InputStream body) throws IOException {
        try (body) {
            var bytes = body.readNBytes(MAX_HTTP_BODY_BYTES + 1);
            if (bytes.length > MAX_HTTP_BODY_BYTES) {
                throw new LfgProtocolException("HTTP response exceeds the protocol document limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static LfgProtocol.Error httpError(int status, String body) {
        try {
            return StrictLfgJson.decodeError(body);
        } catch (RuntimeException ignored) {
            String message = status == 404
                    ? "This action is unavailable on the connected Fox backend (HTTP 404)."
                    : "Fox Raid LFG rejected the request (HTTP " + status + ").";
            return new LfgProtocol.Error("HTTP_" + status, message, status >= 500, null, null);
        }
    }

    private URI resolve(String path) {
        return URI.create(baseUri.toString() + path);
    }

    private URI websocketUri() {
        var scheme = baseUri.getScheme().equals("https") ? "wss" : "ws";
        return URI.create(scheme + "://" + baseUri.getRawAuthority() + baseUri.getRawPath() + "/live");
    }

    private void rememberFailure(UUID requestId, UUID operationId, String operation,
                                 int attempt, String outcome, String errorCode,
                                 long durationMillis, Integer httpStatus,
                                 String exceptionClass, Integer websocketCloseCode) {
        synchronized (diagnosticEvents) {
            while (diagnosticEvents.size() >= 100) diagnosticEvents.removeFirst();
            diagnosticEvents.addLast(new ClientDiagnostic(
                    UUID.randomUUID(), requestId, operationId, operation, outcome,
                    errorCode, Instant.now(), Math.max(0, durationMillis), Math.max(1, attempt),
                    httpStatus, exceptionClass, websocketCloseCode));
        }
    }

    private void scheduleDiagnosticFlush(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return;
        final List<ClientDiagnostic> batch;
        synchronized (diagnosticEvents) {
            if (diagnosticFlushInProgress || diagnosticEvents.isEmpty()) return;
            diagnosticFlushInProgress = true;
            batch = new ArrayList<>(diagnosticEvents.stream().limit(50).toList());
        }
        var array = new JsonArray();
        batch.forEach(event -> array.add(event.json()));
        var envelope = new JsonObject();
        envelope.add("events", array);
        var request = HttpRequest.newBuilder(resolve("/diagnostics/client-events"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("X-Client-Version", clientVersion)
                .header("X-Protocol-Version", Integer.toString(LfgProtocol.VERSION))
                .POST(HttpRequest.BodyPublishers.ofString(envelope.toString()))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, failure) -> {
                    boolean accepted = failure == null && response.statusCode() >= 200 && response.statusCode() < 300;
                    synchronized (diagnosticEvents) {
                        if (accepted) {
                            var ids = batch.stream().map(ClientDiagnostic::eventId).collect(java.util.stream.Collectors.toSet());
                            diagnosticEvents.removeIf(event -> ids.contains(event.eventId()));
                        }
                        diagnosticFlushInProgress = false;
                    }
                    if (accepted) scheduleDiagnosticFlush(bearerToken);
                });
    }

    private static long elapsedMillis(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    private static String transportOutcome(Throwable failure) {
        return failure instanceof HttpTimeoutException ? "timeout" : "transport_error";
    }

    private static String transportCode(Throwable failure) {
        if (failure instanceof HttpConnectTimeoutException) return "CONNECT_TIMEOUT";
        if (failure instanceof HttpTimeoutException) return "TIMEOUT";
        if (failure instanceof UnknownHostException) return "DNS_FAILURE";
        if (failure instanceof SSLException) return "TLS_FAILURE";
        if (failure instanceof ConnectException) return "CONNECT_FAILURE";
        return "TRANSPORT_FAILURE";
    }

    private record ClientDiagnostic(
            UUID eventId, UUID requestId, UUID operationId, String operation,
            String outcome, String errorCode, Instant occurredAt, long durationMillis,
            int attempt, Integer httpStatus, String exceptionClass,
            Integer websocketCloseCode) {
        JsonObject json() {
            var object = new JsonObject();
            object.addProperty("event_id", eventId.toString());
            object.addProperty("request_id", requestId.toString());
            object.addProperty("operation_id", operationId.toString());
            object.addProperty("operation", operation);
            object.addProperty("phase", operation.equals("live") ? "websocket.live" : "mod.api");
            object.addProperty("outcome", outcome);
            object.addProperty("error_code", errorCode);
            object.addProperty("occurred_at", occurredAt.toString());
            object.addProperty("duration_ms", durationMillis);
            object.addProperty("attempt", attempt);
            if (httpStatus != null) object.addProperty("http_status", httpStatus);
            if (exceptionClass != null) object.addProperty("exception_class", exceptionClass);
            if (websocketCloseCode != null) object.addProperty("websocket_close_code", websocketCloseCode);
            return object;
        }
    }

    private static void authorize(HttpRequest.Builder builder, String token) {
        if (token != null) builder.header("Authorization", "Bearer " + token);
    }

    private static URI validateBase(String value) {
        var trimmed = value == null ? "" : value.strip();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        var uri = URI.create(trimmed);
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) return uri;
        if (scheme.equals("http") && isLoopback(uri.getHost())) return uri;
        throw new IllegalArgumentException("RALLE LFG requires HTTPS; HTTP overrides are restricted to loopback");
    }

    private static boolean isLoopback(String host) {
        if (host == null) return false;
        var normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("::1") || normalized.equals("0:0:0:0:0:0:0:1")
                || normalized.startsWith("127.");
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static RuntimeException websocketFailure(Throwable failure) {
        var cause = unwrap(failure);
        if (cause instanceof WebSocketHandshakeException handshake) {
            int status = handshake.getResponse().statusCode();
            var error = switch (status) {
                case 401 -> new LfgProtocol.Error("UNAUTHORIZED", "Authentication expired.", false, null, null);
                case 403 -> new LfgProtocol.Error("INELIGIBLE", "Alliance access is unavailable.", false, null, null);
                case 426 -> new LfgProtocol.Error("UNSUPPORTED_PROTOCOL", "Fox Raid LFG uses an incompatible protocol.", false, null, null);
                default -> new LfgProtocol.Error("HTTP_" + status, "Fox rejected the live connection (HTTP " + status + ").",
                        status >= 500, null, null);
            };
            return new LfgGatewayException(status, error);
        }
        return new LfgGatewayException("Fox Raid LFG live connection could not be reached.", cause);
    }
}

package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocolException;
import org.kingdomfoxes.ralle.lfg.protocol.StrictLfgJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** JDK HTTP/WebSocket implementation with bearer headers and one safe mutation retry. */
public final class HttpLfgGateway implements LfgGateway {
    public static final String PRODUCTION_BASE_URL = "https://kingdomfoxes.com/api/ralle/v1";
    public static final String DEFAULT_BASE_URL = PRODUCTION_BASE_URL;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final HttpClient client;
    private final URI baseUri;

    public HttpLfgGateway() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build(),
                System.getProperty("ralle.lfg.baseUrl", DEFAULT_BASE_URL));
    }

    public HttpLfgGateway(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUri = validateBase(baseUrl);
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
                StrictLfgJson::decodeSession, false);
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
        var adapter = new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
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
                listener.onClosed(statusCode, reason);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                listener.onFailure(error);
            }
        };
        return client.newWebSocketBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .buildAsync(uri, adapter)
                .thenApply(socket -> (LiveConnection) () -> socket.sendClose(1000, "client lifecycle changed"))
                .exceptionallyCompose(failure -> CompletableFuture.failedFuture(websocketFailure(failure)));
    }

    private <T> CompletableFuture<T> get(String path, String token, Function<String, T> decoder) {
        var builder = HttpRequest.newBuilder(resolve(path)).timeout(REQUEST_TIMEOUT).GET();
        authorize(builder, token);
        return send(builder.build(), decoder, false, 0);
    }

    private <T> CompletableFuture<T> post(String path, String token, UUID key, String body,
                                          Function<String, T> decoder, boolean retryTransport) {
        var builder = HttpRequest.newBuilder(resolve(path)).timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        authorize(builder, token);
        if (key != null) builder.header("Idempotency-Key", key.toString());
        return send(builder.build(), decoder, retryTransport, 0);
    }

    private <T> CompletableFuture<T> send(HttpRequest request, Function<String, T> decoder,
                                          boolean retryTransport, int attempt) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, failure) -> {
                    if (failure != null) {
                        if (retryTransport && attempt == 0) return send(request, decoder, true, 1);
                        return CompletableFuture.<T>failedFuture(new LfgGatewayException(
                                "Fox Raid LFG could not be reached.", unwrap(failure)));
                    }
                    try {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return CompletableFuture.completedFuture(decoder.apply(response.body()));
                        }
                        return CompletableFuture.<T>failedFuture(new LfgGatewayException(
                                response.statusCode(), httpError(response.statusCode(), response.body())));
                    } catch (RuntimeException exception) {
                        return CompletableFuture.<T>failedFuture(exception);
                    }
                }).thenCompose(Function.identity());
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

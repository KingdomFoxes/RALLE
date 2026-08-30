package org.kingdomfoxes.ralle.chat.rank;

import org.kingdomfoxes.ralle.lfg.client.HttpLfgGateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** JDK HTTP client for the public {@code GET /api/ranks} endpoint. */
public final class HttpGuildRankGateway implements GuildRankGateway {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_BODY_BYTES = StrictGuildRankJson.MAX_DOCUMENT_CHARS * 4;

    private final HttpClient client;
    private final URI endpoint;

    public HttpGuildRankGateway() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), defaultEndpoint());
    }

    public HttpGuildRankGateway(HttpClient client, String endpoint) {
        this.client = client;
        this.endpoint = validateEndpoint(endpoint);
    }

    @Override
    public CompletableFuture<Map<String, String>> fetchTitles() {
        var request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (var ignored = response.body()) {
                    // Close the bounded response stream before reporting the public API failure.
                } catch (IOException ignored) {}
                throw new IllegalStateException("Fox rank API rejected the request (HTTP " + response.statusCode() + ")");
            }
            try (var body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) throw new IOException("Rank response exceeds the document limit");
                return StrictGuildRankJson.decodeApi(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
    }

    static String defaultEndpoint() {
        String lfgBase = HttpLfgGateway.DEFAULT_BASE_URL;
        int apiIndex = lfgBase.indexOf("/api/ralle/v1");
        if (apiIndex < 0) throw new IllegalStateException("RALLE backend URL does not contain /api/ralle/v1");
        return lfgBase.substring(0, apiIndex) + "/api/ranks";
    }

    private static URI validateEndpoint(String value) {
        var uri = URI.create(value == null ? "" : value.strip());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) return uri;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") && (host.equals("localhost") || host.equals("::1") || host.startsWith("127."))) {
            return uri;
        }
        throw new IllegalArgumentException("RALLE rank API requires HTTPS; HTTP is restricted to loopback");
    }
}

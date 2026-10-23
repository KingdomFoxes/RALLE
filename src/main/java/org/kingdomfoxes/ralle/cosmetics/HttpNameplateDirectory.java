package org.kingdomfoxes.ralle.cosmetics;

import org.kingdomfoxes.ralle.platform.SharedHttpTransport;
import org.kingdomfoxes.ralle.platform.BoundedHttpBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Fox cosmetics v1 bounded lookup; never initiates a request until lookup is called. */
public final class HttpNameplateDirectory implements NameplateDirectory {
    private static final int MAX_RESPONSE_BYTES = 32768;
    private final Supplier<HttpClient> client;
    private final URI endpoint;

    public HttpNameplateDirectory() {
        this(SharedHttpTransport.shared(), Boolean.getBoolean("ralle.localBackend")
                ? "http://127.0.0.1:8001/api/ralle/cosmetics/v1/lookup"
                : "https://kingdomfoxes.com/api/ralle/cosmetics/v1/lookup");
    }

    public HttpNameplateDirectory(Supplier<HttpClient> client, String endpoint) {
        this.client = Objects.requireNonNull(client, "client");
        this.endpoint = URI.create(endpoint);
        if (!"https".equals(this.endpoint.getScheme())
                && !("http".equals(this.endpoint.getScheme()) && "127.0.0.1".equals(this.endpoint.getHost())))
            throw new IllegalArgumentException("Cosmetic lookup requires HTTPS or local loopback");
    }

    @Override
    public CompletableFuture<CosmeticLookupJson.Lookup> lookup(List<UUID> uuids) {
        String body = CosmeticLookupJson.request(uuids);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return BoundedHttpBody.send(client.get(), request, MAX_RESPONSE_BYTES)
                .thenApply(response -> {
                    if (response.statusCode() != 200) throw new IllegalArgumentException("Fox cosmetic lookup unavailable");
                    return CosmeticLookupJson.decode(response.body(), uuids);
                });
    }
}

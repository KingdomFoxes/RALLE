package org.kingdomfoxes.ralle.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.kingdomfoxes.ralle.platform.SharedHttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Versioned Fox cosmetic self API. No LFG or website bearer is accepted by this client. */
public final class HttpCosmeticSelfGateway implements CosmeticSelfGateway {
    private static final int MAX_RESPONSE_BYTES = 8192;
    private final Supplier<HttpClient> client;
    private final URI base;

    public HttpCosmeticSelfGateway() {
        this(SharedHttpTransport.shared(), Boolean.getBoolean("ralle.localBackend")
                ? "http://127.0.0.1:8001/api/ralle/cosmetics/v1"
                : "https://kingdomfoxes.com/api/ralle/cosmetics/v1");
    }

    public HttpCosmeticSelfGateway(Supplier<HttpClient> client, String baseUrl) {
        this.client = Objects.requireNonNull(client, "client");
        this.base = URI.create(baseUrl + "/");
        if (!"https".equals(base.getScheme())
                && !("http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost())))
            throw new IllegalArgumentException("Cosmetic self API requires HTTPS or local loopback");
    }

    @Override public CompletableFuture<Challenge> challenge(UUID account, String ign) {
        if (account == null || ign == null || !ign.matches("[A-Za-z0-9_]{1,16}"))
            throw new IllegalArgumentException("Invalid Minecraft identity");
        JsonObject body = versioned();
        body.addProperty("minecraft_uuid", account.toString());
        body.addProperty("ign", ign);
        return send("auth/challenge", "POST", body, null).thenApply(result -> {
            requireVersion(result);
            int expires = integer(result, "expires_in");
            if (expires < 1 || expires > 60) throw new IllegalArgumentException("Invalid cosmetic challenge expiry");
            return new Challenge(string(result, "challenge_id"), string(result, "server_id"));
        });
    }

    @Override public CompletableFuture<Session> complete(String challengeId) {
        if (challengeId == null || challengeId.isBlank() || challengeId.length() > 128)
            throw new IllegalArgumentException("Invalid cosmetic challenge");
        JsonObject body = versioned();
        body.addProperty("challenge_id", challengeId);
        return send("auth/complete", "POST", body, null).thenApply(result -> {
            requireVersion(result);
            if (!"Bearer".equals(string(result, "token_type"))) throw new IllegalArgumentException("Invalid cosmetic token type");
            int expires = integer(result, "expires_in");
            if (expires < 1 || expires > 900) throw new IllegalArgumentException("Invalid cosmetic token expiry");
            return new Session(string(result, "access_token"),
                    UUID.fromString(string(result, "minecraft_uuid")),
                    System.currentTimeMillis() + expires * 1000L);
        });
    }

    @Override public CompletableFuture<CosmeticIdentity> select(String bearer, String styleId) {
        if (bearer == null || bearer.isBlank() || bearer.length() > 4096
                || styleId != null && NameplateStyle.byId(styleId).isEmpty())
            throw new IllegalArgumentException("Invalid cosmetic style selection");
        JsonObject body = versioned();
        body.addProperty("style_id", styleId);
        return send("me/style", "PUT", body, bearer).thenApply(result -> {
            requireVersion(result);
            var grantArray = result.getAsJsonArray("grants");
            if (grantArray == null || grantArray.size() > 3)
                throw new IllegalArgumentException("Invalid cosmetic grants");
            var grants = new HashSet<String>();
            for (JsonElement element : grantArray) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                        || !grants.add(element.getAsString()))
                    throw new IllegalArgumentException("Invalid cosmetic grant");
            }
            var selected = result.get("selected_style_id");
            if (selected == null || !selected.isJsonNull()
                    && (!selected.isJsonPrimitive() || !selected.getAsJsonPrimitive().isString()))
                throw new IllegalArgumentException("Invalid selected style");
            return new CosmeticIdentity(UUID.fromString(string(result, "minecraft_uuid")), grants,
                    selected.isJsonNull() ? null : selected.getAsString(),
                    longValue(result, "revision"));
        });
    }

    private CompletableFuture<JsonObject> send(String path, String method, JsonObject body, String bearer) {
        var builder = HttpRequest.newBuilder(base.resolve(path))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json");
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        HttpRequest request = builder.method(method,
                HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return client.get().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (var stream = response.body()) {
                byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES || response.statusCode() != 200)
                    throw new IllegalArgumentException("Fox cosmetic request failed (HTTP " + response.statusCode() + ")");
                return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException failure) {
                throw new IllegalArgumentException("Fox cosmetic request failed", failure);
            }
        });
    }

    private static JsonObject versioned() { var body = new JsonObject(); body.addProperty("version", 1); return body; }
    private static void requireVersion(JsonObject body) {
        if (integer(body, "version") != 1) throw new IllegalArgumentException("Unsupported cosmetic protocol");
    }
    private static String string(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid cosmetic " + key);
        return value.getAsString();
    }
    private static int integer(JsonObject body, String key) {
        long value = longValue(body, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid cosmetic " + key);
        return (int) value;
    }
    private static long longValue(JsonObject body, String key) {
        JsonElement value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid cosmetic " + key);
        long result = value.getAsLong();
        if (!Long.toString(result).equals(value.getAsString())) throw new IllegalArgumentException("Invalid cosmetic " + key);
        return result;
    }
}

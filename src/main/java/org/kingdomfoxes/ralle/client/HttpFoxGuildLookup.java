package org.kingdomfoxes.ralle.client;

import com.google.gson.JsonParser;
import org.kingdomfoxes.ralle.platform.SharedHttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Public Wynncraft player profile lookup by canonical UUID, without Fox credentials.
 * https://docs.wynncraft.com/modules/player/get-player
 */
public final class HttpFoxGuildLookup {
    static final int MAX_BODY_BYTES = 256 * 1024;

    public CompletableFuture<Boolean> lookup(UUID player) {
        var request = HttpRequest.newBuilder(URI.create("https://api.wynncraft.com/v3/player/" + player))
                .timeout(Duration.ofSeconds(12)).version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json").GET().build();
        return SharedHttpTransport.shared().get().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    try (var body = response.body()) {
                        if (response.statusCode() != 200)
                            throw new IOException("Wynncraft guild lookup failed (HTTP " + response.statusCode() + ")");
                        var bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Player profile exceeds the document limit");
                        return isFoxMember(new String(bytes, StandardCharsets.UTF_8), player);
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                }).orTimeout(12, TimeUnit.SECONDS);
    }

    static boolean isFoxMember(String json, UUID player) {
        var profile = JsonParser.parseString(json).getAsJsonObject();
        var uuid = profile.get("uuid");
        if (uuid == null || !uuid.isJsonPrimitive() || !uuid.getAsJsonPrimitive().isString()
                || !player.equals(UUID.fromString(uuid.getAsString())))
            throw new IllegalArgumentException("Player profile identity mismatch");
        if (!profile.has("guild")) throw new IllegalArgumentException("Player profile omitted guild membership");
        var guild = profile.get("guild");
        if (guild.isJsonNull()) return false;
        var prefix = guild.getAsJsonObject().get("prefix");
        if (prefix == null || !prefix.isJsonPrimitive() || !prefix.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid guild prefix");
        return "FOX".equalsIgnoreCase(prefix.getAsString());
    }
}

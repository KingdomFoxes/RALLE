package org.kingdomfoxes.ralle.client;

import com.google.gson.JsonParser;
import org.kingdomfoxes.ralle.platform.SharedHttpTransport;
import org.kingdomfoxes.ralle.platform.BoundedHttpBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Public Wynncraft player profile lookup by canonical UUID, without Fox credentials.
 * https://docs.wynncraft.com/modules/player/get-player
 */
public final class HttpFoxGuildLookup {
    static final int MAX_BODY_BYTES = 256 * 1024;

    public CompletableFuture<Boolean> lookup(UUID player) {
        var request = HttpRequest.newBuilder(URI.create("https://api.wynncraft.com/v3/player/" + player))
                .timeout(Duration.ofSeconds(12)).version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json").GET().build();
        return BoundedHttpBody.send(SharedHttpTransport.shared().get(), request, MAX_BODY_BYTES)
                .thenApply(response -> {
                    if (response.statusCode() != 200)
                        throw new CompletionException(new IOException("Wynncraft guild lookup failed (HTTP " + response.statusCode() + ")"));
                    return isFoxMember(response.body(), player);
                });
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

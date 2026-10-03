package org.kingdomfoxes.ralle.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import org.kingdomfoxes.ralle.platform.SharedHttpTransport;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Unauthenticated, bounded lookup of published RALLE versions on Modrinth. */
public final class ModrinthUpdateLookup {
    static final int MAX_BODY_BYTES = 512 * 1024;
    private final String installedVersion;
    private final String minecraftVersion;

    public ModrinthUpdateLookup(String installedVersion, String minecraftVersion) {
        this.installedVersion = installedVersion;
        this.minecraftVersion = minecraftVersion;
    }

    HttpRequest request() {
        var gameVersions = URLEncoder.encode("[\"" + minecraftVersion + "\"]", StandardCharsets.UTF_8);
        return HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/ralle/version"
                        + "?loaders=%5B%22fabric%22%5D&game_versions=" + gameVersions + "&include_changelog=false"))
                .timeout(Duration.ofSeconds(12)).version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("User-Agent", "RALLE/" + installedVersion + " (https://github.com/KingdomFoxes/RALLE)")
                .GET().build();
    }

    public CompletableFuture<Optional<ModrinthRelease>> lookup() {
        var response = SharedHttpTransport.shared().get()
                .sendAsync(request(), HttpResponse.BodyHandlers.ofInputStream());
        var result = response.thenApplyAsync(reply -> {
            try (var body = reply.body()) {
                if (reply.statusCode() != 200)
                    throw new IOException("Modrinth update check failed (HTTP " + reply.statusCode() + ")");
                var bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) throw new IOException("Modrinth response exceeds the document limit");
                return newerRelease(new String(bytes, StandardCharsets.UTF_8), installedVersion, minecraftVersion);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).orTimeout(12, TimeUnit.SECONDS);
        // Closing a timed-out stream also stops a slow body read after headers have arrived.
        result.whenComplete((release, failure) -> {
            if (failure != null) response.thenAccept(reply -> {
                try { reply.body().close(); } catch (IOException ignored) { }
            });
        });
        return result;
    }

    static Optional<ModrinthRelease> newerRelease(String json, String installedVersion, String minecraftVersion) {
        final SemanticVersion installed;
        try { installed = SemanticVersion.parse(installedVersion); }
        catch (VersionParsingException failure) { return Optional.empty(); }
        var versions = JsonParser.parseString(json).getAsJsonArray();
        SemanticVersion newest = installed;
        ModrinthRelease selected = null;
        for (var element : versions) {
            try {
                var version = element.getAsJsonObject();
                if (!"listed".equals(string(version, "status"))
                        || !contains(version.getAsJsonArray("loaders"), "fabric")
                        || !contains(version.getAsJsonArray("game_versions"), minecraftVersion)) continue;
                var id = string(version, "id");
                var number = string(version, "version_number");
                var parsed = SemanticVersion.parse(number);
                if (!id.matches("[A-Za-z0-9]{1,64}") || parsed.compareTo(newest) <= 0) continue;
                var name = safeName(string(version, "name"));
                if (name.isBlank()) continue;
                selected = new ModrinthRelease(id, name, number);
                newest = parsed;
            } catch (RuntimeException | VersionParsingException ignored) {
                // A malformed listing must not hide other valid versions or reach chat.
            }
        }
        return Optional.ofNullable(selected);
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid Modrinth " + key);
        return value.getAsString();
    }

    private static boolean contains(JsonArray array, String expected) {
        if (array == null) return false;
        for (var value : array) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && expected.equals(value.getAsString())) return true;
        }
        return false;
    }

    private static String safeName(String name) {
        var safe = new StringBuilder();
        name.codePoints().filter(code -> code != '\u00a7' && !Character.isISOControl(code)
                        && Character.getType(code) != Character.FORMAT)
                .limit(120).forEach(safe::appendCodePoint);
        return safe.toString().strip();
    }
}

package org.kingdomfoxes.ralle.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Fox cosmetics v1 public lookup codec. Remote data is never used as an entitlement authority. */
public final class CosmeticLookupJson {
    public static final int MAX_BATCH = 32;
    private static final int MAX_DOCUMENT_CHARS = 32768;

    private CosmeticLookupJson() {}

    public static String request(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty() || uuids.size() > MAX_BATCH || uuids.stream().anyMatch(java.util.Objects::isNull)
                || new HashSet<>(uuids).size() != uuids.size()) throw new IllegalArgumentException("Invalid cosmetic lookup batch");
        var root = new JsonObject();
        root.addProperty("version", 1);
        var array = new JsonArray();
        uuids.forEach(id -> array.add(id.toString()));
        root.add("uuids", array);
        return root.toString();
    }

    public static Lookup decode(String json, List<UUID> requested) {
        if (json == null || json.length() > MAX_DOCUMENT_CHARS) throw new IllegalArgumentException("Invalid cosmetic response size");
        request(requested);
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (integer(root, "version") != 1) throw new IllegalArgumentException("Unsupported cosmetic response version");
            Duration ttl = ttl(root, "cache_ttl_seconds");
            JsonArray players = root.getAsJsonArray("players");
            if (players == null || players.size() != requested.size()) throw new IllegalArgumentException("Missing cosmetic results");
            List<CosmeticIdentity> identities = new ArrayList<>(players.size());
            for (int index = 0; index < players.size(); index++) {
                JsonObject item = players.get(index).getAsJsonObject();
                UUID uuid = UUID.fromString(string(item, "minecraft_uuid"));
                if (!uuid.equals(requested.get(index))) throw new IllegalArgumentException("Cosmetic UUID mismatch");
                JsonArray grantArray = item.getAsJsonArray("grants");
                if (grantArray == null || grantArray.size() > 3) throw new IllegalArgumentException("Invalid cosmetic grants");
                Set<String> grants = new HashSet<>();
                for (JsonElement grant : grantArray) {
                    if (!grant.isJsonPrimitive() || !grant.getAsJsonPrimitive().isString()
                            || !grants.add(grant.getAsString()))
                        throw new IllegalArgumentException("Invalid cosmetic grant");
                }
                JsonElement selected = item.get("selected_style_id");
                if (selected == null) throw new IllegalArgumentException("Missing selected style");
                String styleId = selected.isJsonNull() ? null : selected.getAsString();
                long revision = longValue(item, "revision");
                Duration playerTtl = ttl(item, "cache_ttl_seconds");
                if (playerTtl.compareTo(ttl) < 0) ttl = playerTtl;
                identities.add(new CosmeticIdentity(uuid, grants, styleId, revision));
            }
            return new Lookup(List.copyOf(identities), ttl);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException) throw failure;
            throw new IllegalArgumentException("Invalid Fox cosmetic response", failure);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing cosmetic " + key);
        return value.getAsString();
    }

    private static int integer(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing cosmetic " + key);
        long result = longValue(object, key);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid cosmetic " + key);
        return (int) result;
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing cosmetic " + key);
        long result = value.getAsLong();
        if (!Long.toString(result).equals(value.getAsString())) throw new IllegalArgumentException("Invalid cosmetic " + key);
        return result;
    }

    private static Duration ttl(JsonObject object, String key) {
        int seconds = integer(object, key);
        if (seconds < 1 || seconds > 30) throw new IllegalArgumentException("Invalid cosmetic TTL");
        return Duration.ofSeconds(seconds);
    }

    public record Lookup(List<CosmeticIdentity> players, Duration ttl) {}
}

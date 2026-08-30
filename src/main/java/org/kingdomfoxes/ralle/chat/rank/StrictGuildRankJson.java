package org.kingdomfoxes.ralle.chat.rank;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded decoder for the public rank response and the smaller local cache format. */
final class StrictGuildRankJson {
    static final int MAX_DOCUMENT_CHARS = 1_000_000;
    private static final int MAX_MEMBERS = 1_000;
    private static final int CACHE_VERSION = 1;

    private StrictGuildRankJson() {}

    static Map<String, String> decodeApi(String json) {
        var root = object(json, "rank response");
        var membersElement = root.get("members");
        if (membersElement == null || !membersElement.isJsonArray()) {
            throw new IllegalArgumentException("Rank response members must be an array");
        }
        var members = membersElement.getAsJsonArray();
        if (members.size() > MAX_MEMBERS) throw new IllegalArgumentException("Rank response has too many members");

        var titles = new LinkedHashMap<String, String>();
        for (var memberElement : members) {
            if (!memberElement.isJsonObject()) continue;
            var member = memberElement.getAsJsonObject();
            String player = string(member, "name");
            if (GuildRankSnapshot.normalizePlayer(player) == null) continue;
            var ranksElement = member.get("ranks");
            if (ranksElement == null || !ranksElement.isJsonArray()) continue;
            for (var rankElement : ranksElement.getAsJsonArray()) {
                if (!rankElement.isJsonObject()) continue;
                var rank = rankElement.getAsJsonObject();
                if (!"fox".equalsIgnoreCase(string(rank, "kind"))) continue;
                String title = GuildRankSnapshot.normalizeTitle(string(rank, "name"));
                if (title != null) titles.put(player, title);
                break;
            }
        }
        return Map.copyOf(titles);
    }

    static GuildRankSnapshot decodeCache(String json) {
        var root = object(json, "rank cache");
        if (!root.has("version") || root.get("version").getAsInt() != CACHE_VERSION) {
            throw new IllegalArgumentException("Unsupported rank cache version");
        }
        if (!root.has("fetched_at") || !root.get("fetched_at").isJsonPrimitive()) {
            throw new IllegalArgumentException("Rank cache is missing fetched_at");
        }
        var titlesElement = root.get("titles");
        if (titlesElement == null || !titlesElement.isJsonObject()) {
            throw new IllegalArgumentException("Rank cache titles must be an object");
        }
        if (titlesElement.getAsJsonObject().size() > MAX_MEMBERS) {
            throw new IllegalArgumentException("Rank cache has too many members");
        }
        var titles = new LinkedHashMap<String, String>();
        titlesElement.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getValue().isJsonPrimitive()) titles.put(entry.getKey(), entry.getValue().getAsString());
        });
        return new GuildRankSnapshot(root.get("fetched_at").getAsLong(), titles);
    }

    static String encodeCache(GuildRankSnapshot snapshot) {
        var root = new JsonObject();
        root.addProperty("version", CACHE_VERSION);
        root.addProperty("fetched_at", snapshot.fetchedAtMillis());
        var titles = new JsonObject();
        snapshot.titlesByPlayer().forEach(titles::addProperty);
        root.add("titles", titles);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator();
    }

    private static JsonObject object(String json, String description) {
        if (json == null || json.length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException(description + " exceeds the document limit");
        }
        var parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException(description + " must be an object");
        return parsed.getAsJsonObject();
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }
}

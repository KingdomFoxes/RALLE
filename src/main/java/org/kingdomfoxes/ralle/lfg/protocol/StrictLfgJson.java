package org.kingdomfoxes.ralle.lfg.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol.*;

/** Explicit protocol-v1 codec. It rejects unknown and missing fields at every object level. */
public final class StrictLfgJson {
    private StrictLfgJson() {}

    public static Status decodeStatus(String json) {
        var object = object(json, "status");
        fields(object, "status", Set.of("enabled", "protocol_version", "required_client_version", "modrinth_release_url"), Set.of());
        return new Status(bool(object, "enabled"), integer(object, "protocol_version"),
                string(object, "required_client_version"), string(object, "modrinth_release_url"));
    }

    public static Challenge decodeChallenge(String json) {
        var object = object(json, "challenge");
        fields(object, "challenge", Set.of("challenge_id", "server_id", "expires_in", "protocol_version"), Set.of());
        return new Challenge(string(object, "challenge_id"), string(object, "server_id"),
                integer(object, "expires_in"), integer(object, "protocol_version"));
    }

    public static Session decodeSession(String json) {
        var object = object(json, "session");
        fields(object, "session", Set.of("access_token", "token_type", "expires_in", "expires_at", "protocol_version", "player"), Set.of());
        return new Session(string(object, "access_token"), string(object, "token_type"),
                integer(object, "expires_in"), instant(object, "expires_at"),
                integer(object, "protocol_version"), player(child(object, "player"), "session.player"));
    }

    public static Snapshot decodeSnapshot(String json) {
        return snapshot(object(json, "snapshot"), false);
    }

    public static Mutation decodeMutation(String json) {
        var object = object(json, "mutation");
        fields(object, "mutation", Set.of("protocol_version", "revision", "lobby"), Set.of());
        return new Mutation(integer(object, "protocol_version"), number(object, "revision"),
                lobby(child(object, "lobby"), "mutation.lobby"));
    }

    public static LfgProtocol.Error decodeError(String json) {
        var envelope = object(json, "error envelope");
        fields(envelope, "error envelope", Set.of("error"), Set.of());
        return error(child(envelope, "error"), "error");
    }

    public static LiveFrame decodeLiveFrame(String json) {
        var object = object(json, "live frame");
        var type = string(object, "type");
        return switch (type) {
            case "snapshot" -> new SnapshotFrame(snapshot(object, true));
            case "lobby.upsert" -> {
                fields(object, "lobby.upsert", Set.of("type", "protocol_version", "revision", "lobby"), Set.of());
                yield new UpsertFrame(integer(object, "protocol_version"), number(object, "revision"),
                        lobby(child(object, "lobby"), "lobby.upsert.lobby"));
            }
            case "lobby.remove" -> {
                fields(object, "lobby.remove", Set.of("type", "protocol_version", "revision", "lobby_id"), Set.of());
                yield new RemoveFrame(integer(object, "protocol_version"), number(object, "revision"), uuid(object, "lobby_id"));
            }
            case "session.expiring" -> {
                fields(object, "session.expiring", Set.of("type", "protocol_version", "expires_at"), Set.of());
                yield new SessionExpiringFrame(integer(object, "protocol_version"), instant(object, "expires_at"));
            }
            case "error" -> {
                fields(object, "live error", Set.of("type", "error"), Set.of());
                yield new ErrorFrame(error(child(object, "error"), "live error.error"));
            }
            default -> throw malformed("Unknown live frame type: " + type);
        };
    }

    public static String challengeRequest(UUID playerId, String ign, String modVersion) {
        var object = new JsonObject();
        object.addProperty("minecraft_uuid", playerId.toString());
        object.addProperty("ign", ign);
        object.addProperty("protocol_version", VERSION);
        object.addProperty("mod_version", modVersion);
        return object.toString();
    }

    public static String completeRequest(String challengeId) {
        var object = new JsonObject();
        object.addProperty("challenge_id", challengeId);
        return object.toString();
    }

    public static String createRequest(RaidType raidType, Region region, String note) {
        var object = new JsonObject();
        object.addProperty("raid_type", raidType.name());
        object.addProperty("region", region.name());
        if (note == null || note.isBlank()) object.add("note", JsonNull.INSTANCE);
        else object.addProperty("note", note);
        return object.toString();
    }

    private static Snapshot snapshot(JsonObject object, boolean live) {
        var required = new HashSet<>(Set.of("protocol_version", "revision", "viewer", "capabilities", "lobbies"));
        if (live) required.add("type");
        fields(object, "snapshot", required, Set.of());
        var lobbies = new ArrayList<Lobby>();
        var array = array(object, "lobbies");
        for (int i = 0; i < array.size(); i++) {
            lobbies.add(lobby(asObject(array.get(i), "snapshot.lobbies[" + i + "]"), "snapshot.lobbies[" + i + "]"));
        }
        return new Snapshot(integer(object, "protocol_version"), number(object, "revision"),
                player(child(object, "viewer"), "snapshot.viewer"),
                viewerCapabilities(child(object, "capabilities"), "snapshot.capabilities"), lobbies);
    }

    private static PlayerIdentity player(JsonObject object, String path) {
        fields(object, path, Set.of("minecraft_uuid", "ign", "guild"), Set.of());
        return new PlayerIdentity(uuid(object, "minecraft_uuid"), string(object, "ign"),
                guild(child(object, "guild"), path + ".guild"));
    }

    private static GuildIdentity guild(JsonObject object, String path) {
        fields(object, path, Set.of("uuid", "name", "tag", "color"), Set.of());
        return new GuildIdentity(uuid(object, "uuid"), string(object, "name"),
                string(object, "tag"), string(object, "color"));
    }

    private static Lobby lobby(JsonObject object, String path) {
        fields(object, path, Set.of("lobby_id", "raid_type", "region", "note", "visibility", "status", "locked",
                "host_minecraft_uuid", "host_guild_uuid", "created_at", "last_activity_at", "revision", "capacity",
                "members", "capabilities"), Set.of());
        var members = new ArrayList<Member>();
        var array = array(object, "members");
        for (int i = 0; i < array.size(); i++) {
            members.add(member(asObject(array.get(i), path + ".members[" + i + "]"), path + ".members[" + i + "]"));
        }
        int capacity = integer(object, "capacity");
        if (capacity != 4 || members.isEmpty() || members.size() > capacity) {
            throw malformed(path + " must contain one to four members and capacity 4");
        }
        var hostId = uuid(object, "host_minecraft_uuid");
        long hostCount = members.stream().filter(member -> member.role() == MemberRole.HOST
                && member.minecraftUuid().equals(hostId)).count();
        if (hostCount != 1 || members.stream().map(Member::minecraftUuid).distinct().count() != members.size()) {
            throw malformed(path + " contains an invalid or duplicate roster");
        }
        var note = nullableString(object, "note");
        if (note != null && (note.length() > 80 || note.chars().anyMatch(Character::isISOControl))) {
            throw malformed(path + ".note is not valid single-line plain text");
        }
        var capabilitiesElement = object.get("capabilities");
        var capabilities = capabilitiesElement == null || capabilitiesElement.isJsonNull()
                ? null : lobbyCapabilities(asObject(capabilitiesElement, path + ".capabilities"), path + ".capabilities");
        return new Lobby(uuid(object, "lobby_id"), enumeration(object, "raid_type", RaidType.class),
                enumeration(object, "region", Region.class), note,
                enumeration(object, "visibility", Visibility.class), enumeration(object, "status", LobbyStatus.class),
                bool(object, "locked"), hostId, uuid(object, "host_guild_uuid"),
                instant(object, "created_at"), instant(object, "last_activity_at"), number(object, "revision"),
                capacity, members, capabilities);
    }

    private static Member member(JsonObject object, String path) {
        fields(object, path, Set.of("minecraft_uuid", "ign", "guild", "role", "source", "joined_at", "discord_user_id"), Set.of());
        var discord = object.get("discord_user_id");
        Long discordId = discord == null || discord.isJsonNull() ? null : longPrimitive(discord, path + ".discord_user_id");
        return new Member(uuid(object, "minecraft_uuid"), string(object, "ign"),
                guild(child(object, "guild"), path + ".guild"), enumeration(object, "role", MemberRole.class),
                enumeration(object, "source", MemberSource.class), instant(object, "joined_at"), discordId);
    }

    private static LobbyCapabilities lobbyCapabilities(JsonObject object, String path) {
        fields(object, path, Set.of("join", "leave", "disabled_reasons"), Set.of());
        return new LobbyCapabilities(bool(object, "join"), bool(object, "leave"), stringMap(child(object, "disabled_reasons"), path));
    }

    private static ViewerCapabilities viewerCapabilities(JsonObject object, String path) {
        fields(object, path, Set.of("create", "browse", "disabled_reasons"), Set.of());
        return new ViewerCapabilities(bool(object, "create"), bool(object, "browse"), stringMap(child(object, "disabled_reasons"), path));
    }

    private static LfgProtocol.Error error(JsonObject object, String path) {
        fields(object, path, Set.of("code", "message", "retryable"), Set.of("lobby_id", "details"));
        UUID lobbyId = object.has("lobby_id") && !object.get("lobby_id").isJsonNull() ? uuid(object, "lobby_id") : null;
        Lobby returned = null;
        if (object.has("details") && !object.get("details").isJsonNull()) {
            var details = asObject(object.get("details"), path + ".details");
            if (details.has("lobby") && !details.get("lobby").isJsonNull()) {
                returned = lobby(asObject(details.get("lobby"), path + ".details.lobby"), path + ".details.lobby");
            }
        }
        return new LfgProtocol.Error(string(object, "code"), string(object, "message"), bool(object, "retryable"), lobbyId, returned);
    }

    private static Map<String, String> stringMap(JsonObject object, String path) {
        var result = new HashMap<String, String>();
        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                throw malformed(path + " contains a non-string reason");
            }
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static JsonObject object(String json, String path) {
        try {
            return asObject(JsonParser.parseString(json), path);
        } catch (LfgProtocolException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new LfgProtocolException("Malformed " + path + " JSON", exception);
        }
    }

    private static JsonObject asObject(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) throw malformed(path + " must be an object");
        return element.getAsJsonObject();
    }

    private static void fields(JsonObject object, String path, Set<String> required, Set<String> optional) {
        for (var field : required) {
            if (!object.has(field)) throw malformed(path + " is missing field '" + field + "'");
        }
        for (var field : object.keySet()) {
            if (!required.contains(field) && !optional.contains(field)) {
                throw malformed(path + " contains unknown field '" + field + "'");
            }
        }
    }

    private static JsonObject child(JsonObject object, String name) {
        return asObject(required(object, name), name);
    }

    private static JsonArray array(JsonObject object, String name) {
        var element = required(object, name);
        if (!element.isJsonArray()) throw malformed(name + " must be an array");
        return element.getAsJsonArray();
    }

    private static JsonElement required(JsonObject object, String name) {
        var element = object.get(name);
        if (element == null) throw malformed("Missing field '" + name + "'");
        return element;
    }

    private static String string(JsonObject object, String name) {
        var element = required(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) throw malformed(name + " must be a string");
        return element.getAsString();
    }

    private static String nullableString(JsonObject object, String name) {
        var element = required(object, name);
        return element.isJsonNull() ? null : string(object, name);
    }

    private static boolean bool(JsonObject object, String name) {
        var element = required(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw malformed(name + " must be a boolean");
        return element.getAsBoolean();
    }

    private static int integer(JsonObject object, String name) {
        long value = number(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw malformed(name + " is outside integer range");
        return (int) value;
    }

    private static long number(JsonObject object, String name) {
        return longPrimitive(required(object, name), name);
    }

    private static long longPrimitive(JsonElement element, String path) {
        try {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw malformed(path + " must be an integer");
            var decimal = element.getAsBigDecimal();
            return decimal.longValueExact();
        } catch (ArithmeticException exception) {
            throw malformed(path + " must be an integer");
        }
    }

    private static UUID uuid(JsonObject object, String name) {
        try {
            var value = string(object, name);
            var parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value.toLowerCase(java.util.Locale.ROOT))) {
                throw malformed(name + " must be a canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw malformed(name + " must be a canonical UUID");
        }
    }

    private static Instant instant(JsonObject object, String name) {
        try {
            return Instant.parse(string(object, name));
        } catch (DateTimeParseException exception) {
            throw malformed(name + " must be an ISO-8601 instant");
        }
    }

    private static <T extends Enum<T>> T enumeration(JsonObject object, String name, Class<T> type) {
        var value = string(object, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw malformed("Unknown " + name + " value: " + value);
        }
    }

    private static LfgProtocolException malformed(String message) {
        return new LfgProtocolException(message);
    }
}

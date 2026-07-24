package org.kingdomfoxes.ralle.lfg.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Strict, immutable Java representation of Fox Raid LFG protocol v1. */
public final class LfgProtocol {
    public static final int VERSION = 1;

    private LfgProtocol() {}

    public enum RaidType { DAILIES, NOTG, NOL, TCC, TNA, TWP }
    public enum Region { NA, EU, AS }
    public enum Visibility { PUBLIC, PRIVATE }
    public enum LobbyStatus { OPEN, IN_RAID }
    public enum MemberRole { HOST, MEMBER }
    public enum MemberSource { RALLE, DISCORD, MANUAL }

    public record Status(boolean enabled, int protocolVersion, String requiredClientVersion,
                         String modrinthReleaseUrl) {}

    public record Challenge(String challengeId, String serverId, int expiresIn,
                            int protocolVersion) {}

    public record Session(String accessToken, String tokenType, int expiresIn,
                          Instant expiresAt, int protocolVersion, PlayerIdentity player) {}

    public record GuildIdentity(UUID uuid, String name, String tag, String color) {}

    public record PlayerIdentity(UUID minecraftUuid, String ign, GuildIdentity guild) {}

    public record Member(UUID minecraftUuid, String ign, GuildIdentity guild, MemberRole role,
                         MemberSource source, Instant joinedAt, Long discordUserId) {}

    public record LobbyCapabilities(boolean join, boolean leave,
                                    Map<String, String> disabledReasons) {
        public LobbyCapabilities {
            disabledReasons = Map.copyOf(disabledReasons);
        }

        public String reason(String action) {
            return disabledReasons.get(action);
        }
    }

    public record Lobby(UUID lobbyId, RaidType raidType, Region region, String note,
                        Visibility visibility, LobbyStatus status, boolean locked,
                        UUID hostMinecraftUuid, UUID hostGuildUuid, Instant createdAt,
                        Instant lastActivityAt, long revision, int capacity,
                        List<Member> members, LobbyCapabilities capabilities) {
        public Lobby {
            members = List.copyOf(members);
        }

        public boolean contains(UUID playerId) {
            return members.stream().anyMatch(member -> member.minecraftUuid().equals(playerId));
        }

        public boolean hostedBy(UUID playerId) {
            return hostMinecraftUuid.equals(playerId);
        }
    }

    public record ViewerCapabilities(boolean create, boolean browse,
                                     Map<String, String> disabledReasons) {
        public ViewerCapabilities {
            disabledReasons = Map.copyOf(disabledReasons);
        }
    }

    public record Snapshot(int protocolVersion, long revision, PlayerIdentity viewer,
                           ViewerCapabilities capabilities, List<Lobby> lobbies) {
        public Snapshot {
            lobbies = List.copyOf(lobbies);
        }
    }

    public record Mutation(int protocolVersion, long revision, Lobby lobby) {}

    public record Error(String code, String message, boolean retryable, UUID lobbyId,
                        Lobby returnedLobby) {}

    public sealed interface LiveFrame permits SnapshotFrame, UpsertFrame, RemoveFrame,
            PartyPingFrame, PartyKickCommandFrame, SessionExpiringFrame, ErrorFrame {}

    public record SnapshotFrame(Snapshot snapshot) implements LiveFrame {}
    public record UpsertFrame(int protocolVersion, long revision, Lobby lobby) implements LiveFrame {}
    public record RemoveFrame(int protocolVersion, long revision, UUID lobbyId) implements LiveFrame {}
    public record PartyPingFrame(int protocolVersion, UUID eventId, UUID lobbyId,
                                 UUID hostMinecraftUuid, String hostIgn,
                                 Instant occurredAt) implements LiveFrame {}
    public record PartyKickCommandFrame(int protocolVersion, UUID eventId, UUID lobbyId,
                                        UUID targetMinecraftUuid, String targetIgn,
                                        Instant occurredAt) implements LiveFrame {}
    public record SessionExpiringFrame(int protocolVersion, Instant expiresAt) implements LiveFrame {}
    public record ErrorFrame(Error error) implements LiveFrame {}
}

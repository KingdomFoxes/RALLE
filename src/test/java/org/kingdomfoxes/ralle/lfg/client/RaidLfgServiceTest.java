package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RaidLfgServiceTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUILD = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Test
    void disabledAndNonWynncraftStatesMakeNoRequests() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        var service = service(gateway, env);
        service.tick();
        assertEquals(RaidLfgService.LifecycleState.DISABLED, service.lifecycle());
        assertEquals(0, gateway.statusCalls);
        env.enabled = true;
        env.host = "example.org";
        service.tick();
        assertEquals(RaidLfgService.LifecycleState.NOT_ON_WYNNCRAFT, service.lifecycle());
        assertEquals(0, gateway.statusCalls);
    }

    @Test
    void enablingMidSessionAuthenticatesAndRequiresSnapshotBeforeOnline() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "play.wynncraft.com:25565";
        var proofCalls = new int[1];
        var service = new RaidLfgService(gateway, env, serverId -> {
            assertEquals("server-proof", serverId);
            proofCalls[0]++;
            return CompletableFuture.completedFuture(null);
        }, () -> 0L, () -> 0.5);
        service.tick();
        assertEquals(1, gateway.challengeCalls);
        assertEquals(1, proofCalls[0]);
        assertEquals(RaidLfgService.LifecycleState.SYNCING, service.lifecycle());
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshot()));
        assertEquals(RaidLfgService.LifecycleState.ONLINE, service.lifecycle());
    }

    @Test
    void unsupportedStatusProtocolStopsBeforeChallengeWithoutReleaseState() {
        var gateway = new FakeGateway();
        gateway.status = new LfgProtocol.Status(true, 2);
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";

        var service = service(gateway, env);
        service.tick();

        assertEquals(RaidLfgService.LifecycleState.OUTDATED, service.lifecycle());
        assertEquals(0, gateway.challengeCalls);
        assertTrue(service.statusMessage().contains("incompatible protocol"));
    }

    @Test
    void liveHandshakeEligibilityFailureIsTerminal() {
        var gateway = new FakeGateway();
        gateway.liveFailure = new org.kingdomfoxes.ralle.lfg.protocol.LfgGatewayException(
                403, new LfgProtocol.Error("INELIGIBLE", "Guild is not registered.", false, null, null));
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";

        var service = service(gateway, env);
        service.tick();

        assertEquals(RaidLfgService.LifecycleState.INELIGIBLE, service.lifecycle());
        assertEquals("Guild is not registered.", service.statusMessage());
    }

    @Test
    void liveCloseAndErrorCodesUseDistinctLifecycleStates() {
        var ineligibleGateway = new FakeGateway();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var ineligible = service(ineligibleGateway, env);
        ineligible.tick();
        ineligibleGateway.listener.onClosed(4403, "INELIGIBLE");
        assertEquals(RaidLfgService.LifecycleState.INELIGIBLE, ineligible.lifecycle());

        var protocolGateway = new FakeGateway();
        var protocol = service(protocolGateway, env);
        protocol.tick();
        protocolGateway.listener.onClosed(4406, "UNSUPPORTED_PROTOCOL");
        assertEquals(RaidLfgService.LifecycleState.OUTDATED, protocol.lifecycle());

        var outageGateway = new FakeGateway();
        var outage = service(outageGateway, env);
        outage.tick();
        outageGateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshot()));
        outageGateway.listener.onFrame(new LfgProtocol.ErrorFrame(new LfgProtocol.Error(
                "WYNNCRAFT_UNAVAILABLE", "Wynncraft is temporarily unavailable.", true, null, null)));
        assertEquals(RaidLfgService.LifecycleState.RECONNECTING, outage.lifecycle());
    }

    @Test
    void manualRefreshRetriesImmediatelyAfterApiFailure() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var service = service(gateway, env);
        service.tick();
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshot()));
        gateway.listener.onFrame(new LfgProtocol.ErrorFrame(new LfgProtocol.Error(
                "UPSTREAM_UNAVAILABLE", "The API is temporarily unavailable.", true, null, null)));
        assertEquals(RaidLfgService.LifecycleState.RECONNECTING, service.lifecycle());
        assertEquals(1, gateway.statusCalls);

        service.requestRefresh();

        assertEquals(2, gateway.statusCalls);
        assertEquals(RaidLfgService.LifecycleState.SYNCING, service.lifecycle());
    }

    @Test
    void manualRefreshRemainsInertOutsideEnabledWynncraftContext() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        var service = service(gateway, env);

        service.requestRefresh();
        env.enabled = true;
        env.host = "example.org";
        service.requestRefresh();

        assertEquals(0, gateway.statusCalls);
    }

    @Test
    void disablingInvalidatesOutstandingAuthenticationCallback() {
        var gateway = new FakeGateway();
        gateway.challengeFuture = new CompletableFuture<>();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var service = service(gateway, env);
        service.tick();
        assertEquals(RaidLfgService.LifecycleState.AUTHENTICATING, service.lifecycle());
        env.enabled = false;
        service.tick();
        gateway.challengeFuture.complete(new LfgProtocol.Challenge("challenge", "server-proof", 60, 1));
        assertEquals(RaidLfgService.LifecycleState.DISABLED, service.lifecycle());
        assertEquals(0, gateway.completeCalls);
    }

    @Test
    void firstLiveFrameMustBeSnapshotAnd4401Reauthenticates() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var service = service(gateway, env);
        service.tick();
        gateway.listener.onFrame(new LfgProtocol.RemoveFrame(1, 2, UUID.randomUUID()));
        assertEquals(RaidLfgService.LifecycleState.UNAVAILABLE, service.lifecycle());

        service.connectionChanged();
        service.tick();
        assertEquals(2, gateway.challengeCalls);
        gateway.listener.onClosed(4401, "expired");
        assertEquals(3, gateway.challengeCalls);
        assertEquals(RaidLfgService.LifecycleState.SYNCING, service.lifecycle());
    }

    @Test
    void hostnameMatchingDoesNotAcceptLookalikes() {
        assertTrue(RaidLfgService.isWynncraft("wynncraft.com"));
        assertTrue(RaidLfgService.isWynncraft("play.wynncraft.com"));
        assertTrue(RaidLfgService.isWynncraft("wynncraft.net"));
        assertTrue(RaidLfgService.isWynncraft("play.wynncraft.net"));
        assertTrue(RaidLfgService.isWynncraft("eu.wynncraft.com"));
        assertFalse(RaidLfgService.isWynncraft("wynncraft.com.example.org"));
        assertFalse(RaidLfgService.isWynncraft("wynncraft.net.example.org"));
        assertFalse(RaidLfgService.isWynncraft("notwynncraft.com"));
        assertFalse(RaidLfgService.isWynncraft("notwynncraft.net"));
    }

    @Test
    void acceptedKickRunsOneBoundedCommandAndLiveEffectsAreDeduplicated() {
        var gateway = new FakeGateway();
        gateway.kickResult = new LfgProtocol.Mutation(1, 2, hostedLobby(false));
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var notifications = new java.util.ArrayList<LfgProtocol.PartyPingFrame>();
        var commands = new java.util.ArrayList<String>();
        var service = new RaidLfgService(
                gateway, env, ignored -> CompletableFuture.completedFuture(null),
                () -> 0L, () -> 0.5, notifications::add, commands::add);

        service.tick();
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshotWithLobby()));
        service.kick(hostedLobby(true).lobbyId(),
                UUID.fromString("00000000-0000-0000-0000-000000000002"), "Player02").join();
        assertEquals(List.of("Player02"), commands);

        var command = new LfgProtocol.PartyKickCommandFrame(
                1, UUID.fromString("00000000-0000-0000-0000-000000000020"),
                hostedLobby(false).lobbyId(),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "Player03", Instant.EPOCH);
        gateway.listener.onFrame(command);
        gateway.listener.onFrame(command);
        assertEquals(List.of("Player02", "Player03"), commands);

        var ping = new LfgProtocol.PartyPingFrame(
                1, UUID.fromString("00000000-0000-0000-0000-000000000021"),
                hostedLobby(false).lobbyId(), PLAYER, "Player01", Instant.EPOCH);
        gateway.listener.onFrame(ping);
        assertEquals(List.of(ping), notifications);
        assertEquals(ping.lobbyId(), service.focusLobbyId());
    }

    @Test
    void acceptedDisbandRunsOneBoundedPartyCommandOnlyAfterBackendSuccess() {
        var gateway = new FakeGateway();
        gateway.disbandResult = new LfgProtocol.Mutation(1, 2, hostedLobby(false));
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var commands = new TrackingCommands();
        var service = new RaidLfgService(
                gateway, env, ignored -> CompletableFuture.completedFuture(null),
                () -> 0L, () -> 0.5, LfgNotificationSink.IGNORE, commands);

        service.tick();
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshotWithLobby()));
        service.disband(hostedLobby(true).lobbyId()).join();

        assertEquals(1, commands.disbands);
    }

    @Test
    void rejectedDisbandDoesNotDisbandWynncraftParty() {
        var gateway = new FakeGateway();
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var commands = new TrackingCommands();
        var service = new RaidLfgService(
                gateway, env, ignored -> CompletableFuture.completedFuture(null),
                () -> 0L, () -> 0.5, LfgNotificationSink.IGNORE, commands);

        service.tick();
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshotWithLobby()));
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.disband(hostedLobby(true).lobbyId()).join());

        assertEquals(0, commands.disbands);
    }

    @Test
    void acceptedLockRoundTripUpdatesAuthoritativeLobbyState() {
        var gateway = new FakeGateway();
        gateway.lockResult = new LfgProtocol.Mutation(1, 2, lockedHostedLobby());
        var env = new MutableEnvironment();
        env.enabled = true;
        env.host = "wynncraft.com";
        var service = service(gateway, env);

        service.tick();
        gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshotWithLobby()));
        var mutation = service.setLocked(hostedLobby(true).lobbyId(), true).join();

        assertTrue(gateway.requestedLocked);
        assertTrue(mutation.lobby().locked());
        assertTrue(service.store().state().lobbyList().getFirst().locked());
    }

    private static RaidLfgService service(FakeGateway gateway, MutableEnvironment env) {
        return new RaidLfgService(gateway, env, ignored -> CompletableFuture.completedFuture(null),
                () -> 0L, () -> 0.5);
    }

    private static LfgProtocol.Snapshot snapshot() {
        var guild = new LfgProtocol.GuildIdentity(GUILD, "Fox", "FOX", "#FF8200");
        var player = new LfgProtocol.PlayerIdentity(PLAYER, "Player01", guild);
        return new LfgProtocol.Snapshot(1, 1, player,
                new LfgProtocol.ViewerCapabilities(true, true, Map.of()), List.of());
    }

    private static LfgProtocol.Snapshot snapshotWithLobby() {
        var base = snapshot();
        return new LfgProtocol.Snapshot(1, 1, base.viewer(), base.capabilities(),
                List.of(hostedLobby(true)));
    }

    private static LfgProtocol.Lobby hostedLobby(boolean includeMember) {
        var guild = new LfgProtocol.GuildIdentity(GUILD, "Fox", "FOX", "#FF8200");
        var host = new LfgProtocol.Member(PLAYER, "Player01", guild,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
        var members = new java.util.ArrayList<LfgProtocol.Member>();
        members.add(host);
        if (includeMember) {
            members.add(new LfgProtocol.Member(
                    UUID.fromString("00000000-0000-0000-0000-000000000002"), "Player02", guild,
                    LfgProtocol.MemberRole.MEMBER, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        }
        return new LfgProtocol.Lobby(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                PLAYER, GUILD, Instant.EPOCH, Instant.EPOCH, includeMember ? 1 : 2, 4,
                members, new LfgProtocol.LobbyCapabilities(false, false, Map.of()));
    }

    private static LfgProtocol.Lobby lockedHostedLobby() {
        var lobby = hostedLobby(true);
        return new LfgProtocol.Lobby(
                lobby.lobbyId(), lobby.raidType(), lobby.region(), lobby.note(), lobby.visibility(),
                lobby.status(), true, lobby.hostMinecraftUuid(), lobby.hostGuildUuid(),
                lobby.createdAt(), lobby.lastActivityAt(), 2, lobby.capacity(),
                lobby.members(), lobby.capabilities());
    }

    private static final class MutableEnvironment implements RaidLfgEnvironment {
        boolean enabled;
        String host = "";
        @Override public boolean enabled() { return enabled; }
        @Override public String serverHost() { return host; }
        @Override public UUID playerId() { return PLAYER; }
        @Override public String ign() { return "Player01"; }
    }

    private static final class TrackingCommands implements PartyCommandExecutor {
        int disbands;
        @Override public void kick(String ign) {}
        @Override public void disband() { disbands++; }
    }

    private static final class FakeGateway implements LfgGateway {
        int statusCalls;
        int challengeCalls;
        int completeCalls;
        CompletableFuture<LfgProtocol.Challenge> challengeFuture;
        LfgProtocol.Mutation disbandResult;
        LfgProtocol.Mutation kickResult;
        LfgProtocol.Mutation lockResult;
        boolean requestedLocked;
        LiveListener listener;
        LfgProtocol.Status status = new LfgProtocol.Status(true, 1);
        RuntimeException liveFailure;

        @Override public CompletableFuture<LfgProtocol.Status> status() {
            statusCalls++;
            return CompletableFuture.completedFuture(status);
        }
        @Override public CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign) {
            challengeCalls++;
            return challengeFuture != null ? challengeFuture : CompletableFuture.completedFuture(
                    new LfgProtocol.Challenge("challenge", "server-proof", 60, 1));
        }
        @Override public CompletableFuture<LfgProtocol.Session> complete(String challengeId) {
            completeCalls++;
            return CompletableFuture.completedFuture(new LfgProtocol.Session("secret", "Bearer", 900,
                    Instant.now().plusSeconds(900), 1, RaidLfgServiceTest.snapshot().viewer()));
        }
        @Override public CompletableFuture<LfgProtocol.Snapshot> snapshot(String bearerToken) { return CompletableFuture.completedFuture(RaidLfgServiceTest.snapshot()); }
        @Override public CompletableFuture<LfgProtocol.Mutation> create(String bearerToken, LfgProtocol.RaidType raid, LfgProtocol.Region region, String note, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> join(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> leave(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> disband(String bearerToken, UUID lobbyId, UUID idempotencyKey) {
            return disbandResult == null ? unsupported() : CompletableFuture.completedFuture(disbandResult);
        }
        @Override public CompletableFuture<LfgProtocol.Mutation> kick(String bearerToken, UUID lobbyId, UUID targetId, UUID idempotencyKey) {
            return kickResult == null ? unsupported() : CompletableFuture.completedFuture(kickResult);
        }
        @Override public CompletableFuture<LfgProtocol.Mutation> setLocked(String bearerToken, UUID lobbyId, boolean locked, UUID idempotencyKey) {
            requestedLocked = locked;
            return lockResult == null ? unsupported() : CompletableFuture.completedFuture(lockResult);
        }
        @Override public CompletableFuture<LfgProtocol.Mutation> ping(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LiveConnection> connectLive(String bearerToken, LiveListener listener) {
            this.listener = listener;
            if (liveFailure != null) return CompletableFuture.failedFuture(liveFailure);
            return CompletableFuture.completedFuture(() -> {});
        }
        private static <T> CompletableFuture<T> unsupported() { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
    }
}

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
        assertFalse(RaidLfgService.isWynncraft("wynncraft.com.example.org"));
        assertFalse(RaidLfgService.isWynncraft("notwynncraft.com"));
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

    private static final class MutableEnvironment implements RaidLfgEnvironment {
        boolean enabled;
        String host = "";
        @Override public boolean enabled() { return enabled; }
        @Override public String serverHost() { return host; }
        @Override public UUID playerId() { return PLAYER; }
        @Override public String ign() { return "Player01"; }
        @Override public String modVersion() { return "test"; }
    }

    private static final class FakeGateway implements LfgGateway {
        int statusCalls;
        int challengeCalls;
        int completeCalls;
        CompletableFuture<LfgProtocol.Challenge> challengeFuture;
        LiveListener listener;

        @Override public CompletableFuture<LfgProtocol.Status> status() {
            statusCalls++;
            return CompletableFuture.completedFuture(new LfgProtocol.Status(true, 1, "test", "https://modrinth.com/mod/ralle"));
        }
        @Override public CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign, String modVersion) {
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
        @Override public CompletableFuture<LfgProtocol.Mutation> disband(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LiveConnection> connectLive(String bearerToken, LiveListener listener) {
            this.listener = listener;
            return CompletableFuture.completedFuture(() -> {});
        }
        private static <T> CompletableFuture<T> unsupported() { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
    }
}

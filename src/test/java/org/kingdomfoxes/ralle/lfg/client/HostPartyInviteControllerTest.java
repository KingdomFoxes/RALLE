package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPartyInviteControllerTest {
    private static final UUID VIEWER_ID = uuid(1);
    private static final UUID OTHER_HOST_ID = uuid(2);
    private static final UUID GUILD_ID = uuid(100);
    private static final UUID LOBBY_ID = uuid(500);
    private static final LfgProtocol.GuildIdentity GUILD =
            new LfgProtocol.GuildIdentity(GUILD_ID, "Kingdom of Foxes", "FOX", "#FF8200");
    private static final LfgProtocol.PlayerIdentity VIEWER =
            new LfgProtocol.PlayerIdentity(VIEWER_ID, "Viewer", GUILD);

    @Test
    void onlyGenuineLiveHostFillTransitionsNotifyAndRefillsNotifyAgain() {
        var fixture = new Fixture();
        fixture.connect(lobby(1, 3, true));

        fixture.service.store().replace(snapshot(2, lobby(2, 4, true)), RaidLfgStore.UpdateOrigin.SNAPSHOT);
        assertTrue(fixture.sink.fills.isEmpty());

        fixture.service.store().upsert(3, lobby(3, 3, true), RaidLfgStore.UpdateOrigin.LIVE);
        fixture.service.store().upsert(4, lobby(4, 4, true), RaidLfgStore.UpdateOrigin.LIVE);
        assertEquals(1, fixture.sink.fills.size());
        assertEquals(List.of("Member1", "Member2", "Member3"),
                fixture.sink.fills.getFirst().stream().map(LfgProtocol.Member::ign).toList());

        assertFalse(fixture.service.store().upsert(
                4, lobby(5, 4, true), RaidLfgStore.UpdateOrigin.LIVE));
        assertEquals(1, fixture.sink.fills.size());

        fixture.service.store().upsert(5, lobby(5, 3, true), RaidLfgStore.UpdateOrigin.LIVE);
        fixture.service.store().upsert(6, lobby(6, 4, true), RaidLfgStore.UpdateOrigin.LIVE);
        assertEquals(2, fixture.sink.fills.size());

        fixture.service.store().replace(snapshot(7, lobby(7, 3, false)), RaidLfgStore.UpdateOrigin.SNAPSHOT);
        fixture.service.store().upsert(8, lobby(8, 4, false), RaidLfgStore.UpdateOrigin.LIVE);
        assertEquals(2, fixture.sink.fills.size());
    }

    @Test
    void persistentCardSuppressesChatOffersGreenActionAndRestoresBelowCapacity() {
        var fixture = new Fixture();
        fixture.persistentCards.add(LOBBY_ID);
        fixture.connect(lobby(1, 3, true));

        fixture.service.store().upsert(2, lobby(2, 4, true), RaidLfgStore.UpdateOrigin.LIVE);
        assertTrue(fixture.sink.fills.isEmpty());
        assertTrue(fixture.controller.hasCardOffer(LOBBY_ID));

        fixture.service.store().upsert(3, lobby(3, 3, true), RaidLfgStore.UpdateOrigin.LIVE);
        assertFalse(fixture.controller.hasCardOffer(LOBBY_ID));

        fixture.service.store().upsert(4, lobby(4, 4, true), RaidLfgStore.UpdateOrigin.LIVE);
        assertTrue(fixture.controller.hasCardOffer(LOBBY_ID));
        assertTrue(fixture.controller.inviteAll(LOBBY_ID));
        assertEquals(3, fixture.controller.pendingCount());
        assertFalse(fixture.controller.hasCardOffer(LOBBY_ID));
    }

    @Test
    void boundedQueueDeduplicatesPacesAndAllowsANameAgainAfterExecution() {
        var fixture = new Fixture();
        fixture.connect(lobby(1, 4, true));

        assertTrue(fixture.controller.inviteAll(LOBBY_ID));
        assertEquals(HostPartyInviteController.MAX_PENDING, fixture.controller.pendingCount());
        assertFalse(fixture.controller.inviteAll(LOBBY_ID));
        assertEquals(HostPartyInviteController.MAX_PENDING, fixture.controller.pendingCount());

        fixture.controller.tick();
        assertEquals(List.of("Member1"), fixture.commands.invites);
        assertFalse(fixture.controller.inviteAll(LOBBY_ID));
        assertTrue(fixture.controller.inviteMember(LOBBY_ID, uuid(201)));
        assertEquals(HostPartyInviteController.MAX_PENDING, fixture.controller.pendingCount());

        fixture.now[0] = 599;
        fixture.controller.tick();
        assertEquals(List.of("Member1"), fixture.commands.invites);
        fixture.now[0] = 600;
        fixture.controller.tick();
        fixture.now[0] = 1_200;
        fixture.controller.tick();
        fixture.now[0] = 1_800;
        fixture.controller.tick();
        assertEquals(List.of("Member1", "Member2", "Member3", "Member1"), fixture.commands.invites);

        assertTrue(fixture.controller.inviteAll(LOBBY_ID));
        assertEquals(HostPartyInviteController.MAX_PENDING, fixture.controller.pendingCount());
    }

    @Test
    void staleMembersAreSkippedAndAuthorityLossOrDisconnectCancelsPendingCommands() {
        var fixture = new Fixture();
        fixture.connect(lobby(1, 4, true));
        assertTrue(fixture.controller.inviteAll(LOBBY_ID));

        fixture.service.store().upsert(2, lobbyWithout(2, uuid(201)), RaidLfgStore.UpdateOrigin.LIVE);
        fixture.controller.tick();
        assertEquals(List.of("Member2"), fixture.commands.invites);
        assertFalse(fixture.controller.inviteMember(LOBBY_ID, uuid(201)));

        fixture.service.store().upsert(3, lobby(3, 3, false), RaidLfgStore.UpdateOrigin.LIVE);
        assertEquals(0, fixture.controller.pendingCount());
        assertFalse(fixture.controller.inviteAll(LOBBY_ID));
        assertTrue(fixture.sink.errors.contains("You are no longer the lobby host."));

        fixture.service.store().replace(snapshot(4, lobby(4, 4, true)), RaidLfgStore.UpdateOrigin.SNAPSHOT);
        assertTrue(fixture.controller.inviteAll(LOBBY_ID));
        fixture.environment.enabled = false;
        fixture.service.tick();
        fixture.controller.tick();
        assertEquals(0, fixture.controller.pendingCount());
    }

    private static LfgProtocol.Snapshot snapshot(long revision, LfgProtocol.Lobby lobby) {
        return new LfgProtocol.Snapshot(1, revision, VIEWER,
                new LfgProtocol.ViewerCapabilities(false, true, Map.of()), List.of(lobby));
    }

    private static LfgProtocol.Lobby lobby(long revision, int count, boolean viewerHosts) {
        UUID hostId = viewerHosts ? VIEWER_ID : OTHER_HOST_ID;
        var members = new ArrayList<LfgProtocol.Member>();
        members.add(member(hostId, viewerHosts ? "Viewer" : "OtherHost", LfgProtocol.MemberRole.HOST));
        for (int index = 1; index < count; index++) {
            members.add(member(uuid(200 + index), "Member" + index, LfgProtocol.MemberRole.MEMBER));
        }
        return lobby(revision, hostId, members);
    }

    private static LfgProtocol.Lobby lobbyWithout(long revision, UUID removed) {
        var members = new ArrayList<>(lobby(revision, 4, true).members());
        members.removeIf(member -> member.minecraftUuid().equals(removed));
        return lobby(revision, VIEWER_ID, members);
    }

    private static LfgProtocol.Lobby lobby(long revision, UUID hostId, List<LfgProtocol.Member> members) {
        return new LfgProtocol.Lobby(
                LOBBY_ID, LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                hostId, GUILD_ID, Instant.EPOCH, Instant.EPOCH, revision, 4, members,
                new LfgProtocol.LobbyCapabilities(members.size() < 4, false, Map.of())
        );
    }

    private static LfgProtocol.Member member(UUID id, String ign, LfgProtocol.MemberRole role) {
        return new LfgProtocol.Member(id, ign, GUILD, role, LfgProtocol.MemberSource.RALLE,
                Instant.EPOCH, null);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private static final class Fixture {
        final long[] now = {0};
        final Environment environment = new Environment();
        final Gateway gateway = new Gateway();
        final Commands commands = new Commands();
        final Sink sink = new Sink();
        final Set<UUID> persistentCards = new HashSet<>();
        final RaidLfgService service = new RaidLfgService(
                gateway, environment, ignored -> CompletableFuture.completedFuture(null),
                () -> now[0], () -> 0.5, LfgNotificationSink.IGNORE, commands);
        final HostPartyInviteController controller = new HostPartyInviteController(
                service, commands, sink, persistentCards::contains, () -> now[0]);

        void connect(LfgProtocol.Lobby initial) {
            service.tick();
            gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(snapshot(1, initial)));
        }
    }

    private static final class Commands implements PartyCommandExecutor {
        final List<String> invites = new ArrayList<>();
        @Override public void kick(String ign) {}
        @Override public void invite(String ign) { invites.add(ign); }
    }

    private static final class Sink implements HostPartyInviteSink {
        final List<List<LfgProtocol.Member>> fills = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        @Override public void partyFilled(UUID lobbyId, List<LfgProtocol.Member> targets) {
            fills.add(List.copyOf(targets));
        }
        @Override public void error(String message) { errors.add(message); }
    }

    private static final class Environment implements RaidLfgEnvironment {
        boolean enabled = true;
        @Override public boolean enabled() { return enabled; }
        @Override public String serverHost() { return "wynncraft.com"; }
        @Override public UUID playerId() { return VIEWER_ID; }
        @Override public String ign() { return "Viewer"; }
    }

    private static final class Gateway implements LfgGateway {
        LiveListener listener;
        @Override public CompletableFuture<LfgProtocol.Status> status() {
            return CompletableFuture.completedFuture(new LfgProtocol.Status(true, 1));
        }
        @Override public CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign) {
            return CompletableFuture.completedFuture(new LfgProtocol.Challenge("challenge", "proof", 60, 1));
        }
        @Override public CompletableFuture<LfgProtocol.Session> complete(String challengeId) {
            return CompletableFuture.completedFuture(new LfgProtocol.Session(
                    "secret", "Bearer", 60, Instant.EPOCH.plusSeconds(60), 1, VIEWER));
        }
        @Override public CompletableFuture<LfgProtocol.Snapshot> snapshot(String bearerToken) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> create(String bearerToken, LfgProtocol.RaidType raid, LfgProtocol.Region region, String note, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> join(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> leave(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> disband(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> kick(String bearerToken, UUID lobbyId, UUID targetId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> setLocked(String bearerToken, UUID lobbyId, boolean locked, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> ping(String bearerToken, UUID lobbyId, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LiveConnection> connectLive(String bearerToken, LiveListener listener) {
            this.listener = listener;
            return CompletableFuture.completedFuture(() -> {});
        }
        private static <T> CompletableFuture<T> unsupported() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }
}

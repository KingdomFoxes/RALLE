package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgNotificationManagerTest {
    private static final UUID VIEWER_ID = uuid(1);
    private static final UUID HOST_ID = uuid(2);
    private static final UUID GUILD_ID = uuid(100);
    private static final LfgProtocol.GuildIdentity GUILD =
            new LfgProtocol.GuildIdentity(GUILD_ID, "Fox", "FOX", "#FF8200");
    private static final LfgProtocol.PlayerIdentity VIEWER =
            new LfgProtocol.PlayerIdentity(VIEWER_ID, "Viewer", GUILD);

    @Test
    void notificationSlideAnimationLastsHalfASecond() {
        assertEquals(500, LfgNotificationManager.ANIMATION_MILLIS);
    }

    @Test
    void onlyQualifyingLiveChangesDiscoverAndReopenedSkipsReadyCue() {
        var fixture = new Fixture();
        fixture.connect();
        assertTrue(fixture.manager.visibleCards().isEmpty());

        var open = lobby(10, false, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        assertEquals(1, fixture.manager.visibleCards().size());
        assertEquals(LfgNotificationManager.DiscoveryKind.NEW,
                fixture.manager.visibleCards().getFirst().kind());
        assertEquals(1, fixture.sounds.toastIn);

        fixture.now[0] += LfgNotificationManager.ANIMATION_MILLIS;
        fixture.manager.tick();
        assertEquals(1, fixture.sounds.ready);

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 2, lobby(10, true, 2)));
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 3, lobby(10, false, 3)));
        assertEquals(1, fixture.manager.visibleCards().size());
        assertEquals(LfgNotificationManager.DiscoveryKind.REOPENED,
                fixture.manager.visibleCards().getFirst().kind());
        assertEquals(2, fixture.sounds.toastIn);

        fixture.now[0] += LfgNotificationManager.ANIMATION_MILLIS;
        fixture.manager.tick();
        assertEquals(1, fixture.sounds.ready);

        fixture.screenOpen[0] = true;
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 4, lobby(11, false, 1)));
        assertEquals(1, fixture.manager.visibleCards().size());
        assertEquals(0, fixture.manager.queuedCount());
    }

    @Test
    void fourthCardQueuesWithoutAgingUntilPromotion() {
        var fixture = new Fixture();
        fixture.connect();
        for (int index = 0; index < 4; index++) {
            fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                    1, index + 1, lobby(20 + index, false, 1)));
        }

        assertEquals(3, fixture.manager.visibleCards().size());
        assertEquals(1, fixture.manager.queuedCount());

        fixture.now[0] += LfgNotificationManager.PASSIVE_MILLIS - 1_000;
        fixture.manager.tick();
        fixture.manager.close(fixture.manager.visibleCards().getFirst().lobby().lobbyId());
        fixture.now[0] += LfgNotificationManager.ANIMATION_MILLIS;
        fixture.manager.tick();

        assertEquals(3, fixture.manager.visibleCards().size());
        assertEquals(0, fixture.manager.queuedCount());
        assertTrue(fixture.manager.visibleCards().stream()
                .anyMatch(card -> card.lobby().lobbyId().equals(uuid(23))));
    }

    @Test
    void sharedCountdownSubmitsOnceAndAcceptedJoinTracksTheRoster() {
        var fixture = new Fixture();
        fixture.connect();
        var open = lobby(30, false, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        fixture.gateway.joinResult = new LfgProtocol.Mutation(1, 2, joinedLobby(open));

        assertTrue(fixture.manager.join(open.lobbyId()));
        fixture.now[0] += 2_999;
        fixture.service.tick();
        assertEquals(0, fixture.gateway.joinCalls);
        fixture.now[0]++;
        fixture.service.tick();
        fixture.manager.tick();

        assertEquals(1, fixture.gateway.joinCalls);
        assertEquals(1, fixture.sounds.joined);
        assertEquals(LfgNotificationManager.CardMode.JOINED,
                fixture.manager.visibleCards().getFirst().mode());
    }

    @Test
    void acceptedCardlessMainUiJoinUsesTheSameSharedChargeCue() {
        var fixture = new Fixture();
        fixture.connect();
        fixture.screenOpen[0] = true;
        var open = lobby(31, false, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        fixture.gateway.joinResult = new LfgProtocol.Mutation(1, 2, joinedLobby(open));

        assertTrue(fixture.manager.visibleCards().isEmpty());
        assertTrue(fixture.service.joinController().start(open.lobbyId()));
        fixture.now[0] += 3_000;
        fixture.service.tick();
        fixture.manager.tick();

        assertEquals(1, fixture.gateway.joinCalls);
        assertEquals(1, fixture.sounds.joined);
        assertEquals(LfgJoinController.Phase.IDLE,
                fixture.service.joinController().snapshot().phase());
    }

    @Test
    void losingThePreSubmissionSlotRaceUsesOnlyTheFungusCue() {
        var fixture = new Fixture();
        fixture.connect();
        var open = lobbyWithMemberCount(40, 3, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        assertTrue(fixture.manager.join(open.lobbyId()));

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 2,
                lobbyWithMemberCount(40, 4, 2)));
        fixture.service.tick();
        fixture.manager.tick();

        assertEquals(LfgNotificationManager.CardMode.FILLED_RACE,
                fixture.manager.visibleCards().getFirst().mode());
        assertEquals(0, fixture.sounds.joined);
        assertEquals(1, fixture.sounds.fungus);
        assertEquals(0, fixture.sounds.occupied);
        assertEquals(0, fixture.gateway.joinCalls);

        fixture.now[0] += LfgNotificationManager.FEEDBACK_MILLIS - 1;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.FILLED_RACE,
                fixture.manager.visibleCards().getFirst().mode());
        fixture.now[0]++;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.EXITING,
                fixture.manager.visibleCards().getFirst().mode());
        assertEquals(LfgNotificationManager.CardMode.FILLED_RACE,
                fixture.manager.visibleCards().getFirst().presentedMode());
    }

    @Test
    void acceptedFullPartyKeepsGreenSuccessWithoutOwningTheMainUiMembershipCue() {
        var fixture = new Fixture();
        fixture.connect();
        var open = lobbyWithMemberCount(50, 1, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        fixture.gateway.joinResult = new LfgProtocol.Mutation(1, 2, fullJoinedLobby(open));
        assertTrue(fixture.manager.join(open.lobbyId()));

        fixture.now[0] += 3_000;
        fixture.service.tick();
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.FILLED_SUCCESS,
                fixture.manager.visibleCards().getFirst().mode());
        assertEquals(1, fixture.sounds.joined);
        assertEquals(0, fixture.sounds.occupied);
        assertEquals(0, fixture.sounds.fungus);

        fixture.now[0] += LfgNotificationManager.FILLED_SUCCESS_MILLIS - 1;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.FILLED_SUCCESS,
                fixture.manager.visibleCards().getFirst().mode());
        fixture.now[0]++;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.EXITING,
                fixture.manager.visibleCards().getFirst().mode());
        assertEquals(LfgNotificationManager.CardMode.FILLED_SUCCESS,
                fixture.manager.visibleCards().getFirst().presentedMode());
    }

    @Test
    void externalPartyMembershipStaysPersistentAcrossOrdinaryUpdatesUntilClosed() {
        var fixture = new Fixture();
        fixture.partyStatusEnabled[0] = true;
        fixture.connect();
        var hosted = viewerHostedLobby(60, 1);

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, hosted));

        var card = fixture.manager.visibleCards().getFirst();
        assertEquals(LfgNotificationManager.DiscoveryKind.PARTY_STATUS, card.kind());
        assertTrue(card.persistent());

        fixture.now[0] += LfgNotificationManager.PASSIVE_MILLIS * 10;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.READY,
                fixture.manager.visibleCards().getFirst().mode());

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                1, 2, viewerHostedLobby(60, 2)));
        fixture.now[0] += LfgNotificationManager.FEEDBACK_MILLIS * 10;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.READY,
                fixture.manager.visibleCards().getFirst().mode());

        fixture.manager.close(hosted.lobbyId());
        fixture.now[0] += LfgNotificationManager.ANIMATION_MILLIS;
        fixture.manager.tick();
        assertTrue(fixture.manager.visibleCards().isEmpty());
    }

    @Test
    void explicitMainUiCreateSuppressesLiveFirstStatusButPopOutAlwaysPersists() {
        var fixture = new Fixture();
        fixture.partyStatusEnabled[0] = true;
        fixture.screenOpen[0] = true;
        fixture.connect();
        var hosted = viewerHostedLobby(61, 1);
        var suppression = fixture.manager.suppressNextMainUiPartyStatus();

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, hosted));
        assertTrue(fixture.manager.visibleCards().isEmpty());
        suppression.close();

        fixture.partyStatusEnabled[0] = false;
        fixture.manager.showPersistent(hosted);
        assertTrue(fixture.manager.visibleCards().getFirst().persistent());

        fixture.now[0] += LfgNotificationManager.PASSIVE_MILLIS * 10;
        fixture.manager.tick();
        assertEquals(LfgNotificationManager.CardMode.READY,
                fixture.manager.visibleCards().getFirst().mode());
    }

    @Test
    void explicitMainUiJoinSuppressesLiveFirstMembershipForItsLobby() {
        var fixture = new Fixture();
        fixture.partyStatusEnabled[0] = true;
        fixture.connect();
        var open = lobby(63, false, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        assertEquals(1, fixture.manager.visibleCards().size());

        fixture.screenOpen[0] = true;
        var suppression = fixture.manager.suppressMainUiPartyStatus(open.lobbyId());

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                1, 2, joinedLobby(open)));

        assertTrue(fixture.manager.visibleCards().isEmpty());
        suppression.close();
    }

    @Test
    void externalLiveMembershipStillQueuesStatusWhileMainUiIsOpen() {
        var fixture = new Fixture();
        fixture.partyStatusEnabled[0] = true;
        fixture.screenOpen[0] = true;
        fixture.connect();

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                1, 1, viewerHostedLobby(62, 1)));

        assertEquals(LfgNotificationManager.DiscoveryKind.PARTY_STATUS,
                fixture.manager.visibleCards().getFirst().kind());
        assertTrue(fixture.manager.visibleCards().getFirst().persistent());
    }

    @Test
    void abandonedMainUiSuppressionExpiresBeforeLaterExternalMembership() {
        var fixture = new Fixture();
        fixture.partyStatusEnabled[0] = true;
        fixture.connect();
        fixture.manager.suppressNextMainUiPartyStatus();
        fixture.now[0] += LfgNotificationManager.MAIN_UI_SUPPRESSION_MILLIS;

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                1, 1, viewerHostedLobby(64, 1)));

        assertEquals(LfgNotificationManager.DiscoveryKind.PARTY_STATUS,
                fixture.manager.visibleCards().getFirst().kind());
    }

    @Test
    void enabledMainUiAutoPopOutTracksCreateAndClosesAfterDisband() {
        var fixture = new Fixture();
        fixture.mainUiAutoPopOutEnabled[0] = true;
        fixture.connect();
        var hosted = viewerHostedLobby(65, 1);
        fixture.manager.suppressNextMainUiPartyStatus();

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, hosted));

        var card = fixture.manager.visibleCards().getFirst();
        assertEquals(LfgNotificationManager.DiscoveryKind.MAIN_UI, card.kind());
        assertTrue(card.persistent());

        fixture.gateway.listener.onFrame(new LfgProtocol.RemoveFrame(1, 2, hosted.lobbyId()));
        assertEquals(LfgNotificationManager.CardMode.EXITING,
                fixture.manager.visibleCards().getFirst().mode());
    }

    @Test
    void mainUiAutoPopOutClosesAfterViewerLeavesLobby() {
        var fixture = new Fixture();
        fixture.mainUiAutoPopOutEnabled[0] = true;
        fixture.screenOpen[0] = true;
        fixture.connect();
        var open = lobby(66, false, 1);
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 1, open));
        fixture.manager.suppressMainUiPartyStatus(open.lobbyId());
        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(1, 2, joinedLobby(open)));
        assertEquals(LfgNotificationManager.DiscoveryKind.MAIN_UI,
                fixture.manager.visibleCards().getFirst().kind());

        fixture.gateway.listener.onFrame(new LfgProtocol.UpsertFrame(
                1, 3, lobby(66, false, 3)));

        assertEquals(LfgNotificationManager.CardMode.EXITING,
                fixture.manager.visibleCards().getFirst().mode());
    }

    private static LfgProtocol.Lobby lobby(int id, boolean locked, long revision) {
        var host = new LfgProtocol.Member(HOST_ID, "Host", GUILD,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
        return new LfgProtocol.Lobby(uuid(id), LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU,
                "Bring pots", LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, locked,
                HOST_ID, GUILD_ID, Instant.EPOCH, Instant.EPOCH, revision, 4, List.of(host),
                new LfgProtocol.LobbyCapabilities(!locked, false,
                        locked ? Map.of("join", "LOBBY_LOCKED") : Map.of()));
    }

    private static LfgProtocol.Lobby lobbyWithMemberCount(int id, int count, long revision) {
        var members = new java.util.ArrayList<LfgProtocol.Member>();
        members.add(new LfgProtocol.Member(HOST_ID, "Host", GUILD,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        for (int index = 1; index < count; index++) {
            members.add(new LfgProtocol.Member(uuid(200 + index), "Member" + index, GUILD,
                    LfgProtocol.MemberRole.MEMBER, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        }
        return new LfgProtocol.Lobby(uuid(id), LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU,
                null, LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                HOST_ID, GUILD_ID, Instant.EPOCH, Instant.EPOCH, revision, 4, members,
                new LfgProtocol.LobbyCapabilities(count < 4, false,
                        count < 4 ? Map.of() : Map.of("join", "LOBBY_FULL")));
    }

    private static LfgProtocol.Lobby joinedLobby(LfgProtocol.Lobby lobby) {
        var members = new java.util.ArrayList<>(lobby.members());
        members.add(new LfgProtocol.Member(VIEWER_ID, "Viewer", GUILD,
                LfgProtocol.MemberRole.MEMBER, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        return new LfgProtocol.Lobby(lobby.lobbyId(), lobby.raidType(), lobby.region(), lobby.note(),
                lobby.visibility(), lobby.status(), lobby.locked(), lobby.hostMinecraftUuid(),
                lobby.hostGuildUuid(), lobby.createdAt(), lobby.lastActivityAt(), 2, lobby.capacity(),
                members, new LfgProtocol.LobbyCapabilities(false, true, Map.of()));
    }

    private static LfgProtocol.Lobby fullJoinedLobby(LfgProtocol.Lobby lobby) {
        var joined = joinedLobby(lobby);
        return new LfgProtocol.Lobby(joined.lobbyId(), joined.raidType(), joined.region(), joined.note(),
                joined.visibility(), joined.status(), joined.locked(), joined.hostMinecraftUuid(),
                joined.hostGuildUuid(), joined.createdAt(), joined.lastActivityAt(), joined.revision(), 2,
                joined.members(), joined.capabilities());
    }

    private static LfgProtocol.Lobby viewerHostedLobby(int id, long revision) {
        var viewerHost = new LfgProtocol.Member(VIEWER_ID, "Viewer", GUILD,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.DISCORD, Instant.EPOCH, null);
        return new LfgProtocol.Lobby(uuid(id), LfgProtocol.RaidType.TWP, LfgProtocol.Region.EU,
                "Party status", LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                VIEWER_ID, GUILD_ID, Instant.EPOCH, Instant.EPOCH, revision, 4, List.of(viewerHost),
                new LfgProtocol.LobbyCapabilities(false, true, Map.of()));
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private static final class Fixture {
        final long[] now = {0};
        final boolean[] screenOpen = {false};
        final boolean[] partyStatusEnabled = {false};
        final boolean[] mainUiAutoPopOutEnabled = {false};
        final Gateway gateway = new Gateway();
        final Sounds sounds = new Sounds();
        final RaidLfgService service = new RaidLfgService(
                gateway, new Environment(), ignored -> CompletableFuture.completedFuture(null),
                () -> now[0], () -> 0.5, LfgNotificationSink.IGNORE, PartyCommandExecutor.IGNORE);
        final LfgNotificationManager manager = new LfgNotificationManager(
                service, sounds, () -> now[0], () -> true, () -> true,
                () -> partyStatusEnabled[0], () -> mainUiAutoPopOutEnabled[0],
                () -> screenOpen[0]);

        void connect() {
            service.tick();
            gateway.listener.onFrame(new LfgProtocol.SnapshotFrame(new LfgProtocol.Snapshot(
                    1, 0, VIEWER, new LfgProtocol.ViewerCapabilities(true, true, Map.of()), List.of())));
            manager.tick();
        }
    }

    private static final class Sounds implements LfgSoundPlayer {
        int toastIn;
        int ready;
        int joined;
        int occupied;
        int fungus;
        @Override public void playNotificationIn() { toastIn++; }
        @Override public void playNewPartyReady() { ready++; }
        @Override public void playPartyJoined() { joined++; }
        @Override public void playRosterSlotOccupied(int occupiedSlot) { occupied++; }
        @Override public void playPartyFilledRaceLost() { fungus++; }
    }

    private static final class Environment implements RaidLfgEnvironment {
        @Override public boolean enabled() { return true; }
        @Override public String serverHost() { return "wynncraft.com"; }
        @Override public UUID playerId() { return VIEWER_ID; }
        @Override public String ign() { return "Viewer"; }
        @Override public String modVersion() { return "test"; }
    }

    private static final class Gateway implements LfgGateway {
        LiveListener listener;
        int joinCalls;
        LfgProtocol.Mutation joinResult;
        @Override public CompletableFuture<LfgProtocol.Status> status() {
            return CompletableFuture.completedFuture(new LfgProtocol.Status(true, 1, "test", ""));
        }
        @Override public CompletableFuture<LfgProtocol.Challenge> challenge(UUID playerId, String ign, String modVersion) {
            return CompletableFuture.completedFuture(new LfgProtocol.Challenge("challenge", "proof", 60, 1));
        }
        @Override public CompletableFuture<LfgProtocol.Session> complete(String challengeId) {
            return CompletableFuture.completedFuture(new LfgProtocol.Session(
                    "secret", "Bearer", 60, Instant.EPOCH.plusSeconds(60), 1, VIEWER));
        }
        @Override public CompletableFuture<LfgProtocol.Snapshot> snapshot(String bearerToken) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> create(String bearerToken, LfgProtocol.RaidType raid, LfgProtocol.Region region, String note, UUID idempotencyKey) { return unsupported(); }
        @Override public CompletableFuture<LfgProtocol.Mutation> join(String bearerToken, UUID lobbyId, UUID idempotencyKey) {
            joinCalls++;
            return joinResult == null ? unsupported() : CompletableFuture.completedFuture(joinResult);
        }
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

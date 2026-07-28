package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LfgRosterSoundControllerTest {
    private static final UUID VIEWER_ID = uuid(1);
    private static final UUID HOST_ID = uuid(2);
    private static final UUID GUILD_ID = uuid(100);
    private static final LfgProtocol.GuildIdentity GUILD =
            new LfgProtocol.GuildIdentity(GUILD_ID, "Fox", "FOX", "#FF8200");
    private static final LfgProtocol.PlayerIdentity VIEWER =
            new LfgProtocol.PlayerIdentity(VIEWER_ID, "Viewer", GUILD);

    @Test
    void liveRosterGrowthPlaysEveryNewOccupiedSlotWhileViewerRemainsMember() {
        var store = new RaidLfgStore();
        var sounds = new Sounds();
        new LfgRosterSoundController(store, sounds);
        store.replace(snapshot(1, lobbyWithMembers(10, 1, true, true)));

        store.upsert(2, lobbyWithMembers(10, 4, true, true));

        assertEquals(List.of(2, 3, 4), sounds.occupiedSlots);
    }

    @Test
    void acceptedIncrementalMutationAlsoPlaysRosterGrowthWithoutAVisibleScreenOrCard() {
        var store = new RaidLfgStore();
        var sounds = new Sounds();
        new LfgRosterSoundController(store, sounds);
        store.replace(snapshot(1, lobbyWithMembers(11, 1, true, true)));

        store.apply(new LfgProtocol.Mutation(1, 2, lobbyWithMembers(11, 2, true, true)));

        assertEquals(List.of(2), sounds.occupiedSlots);
    }

    @Test
    void viewersOwnJoinUsesOnlyTheSharedJoinResultCue() {
        var store = new RaidLfgStore();
        var sounds = new Sounds();
        new LfgRosterSoundController(store, sounds);
        store.replace(snapshot(1, lobbyWithMembers(12, 1, false, false)));

        store.upsert(2, lobbyWithMembers(12, 2, false, true));

        assertTrue(sounds.occupiedSlots.isEmpty());
    }

    @Test
    void reconnectSnapshotDoesNotReplayOccupiedSlotSounds() {
        var store = new RaidLfgStore();
        var sounds = new Sounds();
        new LfgRosterSoundController(store, sounds);
        store.replace(snapshot(1, lobbyWithMembers(13, 1, true, true)));

        store.replace(snapshot(2, lobbyWithMembers(13, 3, true, true)));

        assertTrue(sounds.occupiedSlots.isEmpty());
    }

    private static LfgProtocol.Snapshot snapshot(long revision, LfgProtocol.Lobby lobby) {
        return new LfgProtocol.Snapshot(
                1, revision, VIEWER, new LfgProtocol.ViewerCapabilities(false, true, Map.of()),
                List.of(lobby));
    }

    private static LfgProtocol.Lobby lobbyWithMembers(
            int id, int count, boolean viewerIsHost, boolean includeViewer
    ) {
        var members = new ArrayList<LfgProtocol.Member>();
        UUID hostId = viewerIsHost ? VIEWER_ID : HOST_ID;
        members.add(member(hostId, viewerIsHost ? "Viewer" : "Host", LfgProtocol.MemberRole.HOST));
        if (includeViewer && !viewerIsHost) {
            members.add(member(VIEWER_ID, "Viewer", LfgProtocol.MemberRole.MEMBER));
        }
        while (members.size() < count) {
            int suffix = 200 + members.size();
            members.add(member(uuid(suffix), "Member" + suffix, LfgProtocol.MemberRole.MEMBER));
        }
        return new LfgProtocol.Lobby(
                uuid(id), LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                members.getFirst().minecraftUuid(), GUILD_ID, Instant.EPOCH, Instant.EPOCH,
                count, 4, List.copyOf(members),
                new LfgProtocol.LobbyCapabilities(count < 4 && !includeViewer, includeViewer, Map.of()));
    }

    private static LfgProtocol.Member member(UUID id, String ign, LfgProtocol.MemberRole role) {
        return new LfgProtocol.Member(
                id, ign, GUILD, role, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }

    private static final class Sounds implements LfgSoundPlayer {
        final ArrayList<Integer> occupiedSlots = new ArrayList<>();

        @Override
        public void playRosterSlotOccupied(int occupiedSlot) {
            occupiedSlots.add(occupiedSlot);
        }
    }
}

package org.kingdomfoxes.ralle.lfg.client;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RaidLfgStoreTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUILD = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID LOBBY = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final LfgProtocol.GuildIdentity GUILD_ID = new LfgProtocol.GuildIdentity(GUILD, "Fox", "FOX", "#FF8200");
    private static final LfgProtocol.PlayerIdentity VIEWER = new LfgProtocol.PlayerIdentity(PLAYER, "Player01", GUILD_ID);

    @Test
    void snapshotReplacesAndEventsAllowRevisionGapsButIgnoreDuplicates() {
        var store = new RaidLfgStore();
        store.replace(new LfgProtocol.Snapshot(1, 5, VIEWER,
                new LfgProtocol.ViewerCapabilities(true, true, Map.of()), List.of()));
        assertTrue(store.upsert(9, lobby(1, true)));
        assertEquals(9, store.state().revision());
        assertFalse(store.state().capabilities().create());
        assertFalse(store.upsert(9, lobby(2, true)));
        assertFalse(store.remove(8, LOBBY));
        assertTrue(store.remove(12, LOBBY));
        assertTrue(store.state().lobbies().isEmpty());
        assertTrue(store.state().capabilities().create());
    }

    @Test
    void mutationThenDuplicateWebsocketEventIsAppliedOnce() {
        var store = new RaidLfgStore();
        store.replace(new LfgProtocol.Snapshot(1, 0, VIEWER,
                new LfgProtocol.ViewerCapabilities(true, true, Map.of()), List.of()));
        assertTrue(store.apply(new LfgProtocol.Mutation(1, 1, lobby(1, true))));
        assertFalse(store.upsert(1, lobby(1, true)));
        assertEquals(1, store.state().lobbies().size());
    }

    private static LfgProtocol.Lobby lobby(long revision, boolean viewerMember) {
        var members = viewerMember ? List.of(new LfgProtocol.Member(PLAYER, "Player01", GUILD_ID,
                LfgProtocol.MemberRole.HOST, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null)) : List.<LfgProtocol.Member>of();
        return new LfgProtocol.Lobby(LOBBY, LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false, PLAYER, GUILD,
                Instant.EPOCH, Instant.EPOCH, revision, 4, members,
                new LfgProtocol.LobbyCapabilities(false, false, Map.of()));
    }
}

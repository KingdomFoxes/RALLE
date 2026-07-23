package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidLfgScreenTest {
    private static final UUID HOST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID GUILD = UUID.fromString("00000000-0000-0000-0000-000000000100");

    @Test
    void collapsedHostActionIsConfirmedDestructiveDisband() {
        var action = RaidLfgScreen.collapsedActionFor(lobby(), HOST);

        assertEquals(RaidLfgScreen.CardActionKind.DISBAND, action.kind());
        assertEquals("Disband", action.label());
        assertTrue(action.destructive());
        assertTrue(action.requiresConfirmation());
    }

    @Test
    void collapsedNonHostActionStillExpandsForRosterReview() {
        var action = RaidLfgScreen.collapsedActionFor(lobby(), MEMBER);

        assertEquals(RaidLfgScreen.CardActionKind.REVIEW_JOIN, action.kind());
        assertEquals("Join", action.label());
        assertFalse(action.destructive());
        assertFalse(action.requiresConfirmation());
    }

    @Test
    void expandedHostActionAlsoRequiresConfirmation() {
        var action = RaidLfgScreen.expandedActionFor(lobby(), HOST);

        assertEquals(RaidLfgScreen.CardActionKind.DISBAND, action.kind());
        assertTrue(action.requiresConfirmation());
    }

    private static LfgProtocol.Lobby lobby() {
        var guild = new LfgProtocol.GuildIdentity(GUILD, "Kingdom of Foxes", "FOX", "#FF8200");
        var host = new LfgProtocol.Member(HOST, "Host", guild, LfgProtocol.MemberRole.HOST,
                LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
        return new LfgProtocol.Lobby(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null, LfgProtocol.Visibility.PUBLIC,
                LfgProtocol.LobbyStatus.OPEN, false, HOST, GUILD, Instant.EPOCH, Instant.EPOCH,
                1, 4, List.of(host), new LfgProtocol.LobbyCapabilities(true, false, Map.of()));
    }
}

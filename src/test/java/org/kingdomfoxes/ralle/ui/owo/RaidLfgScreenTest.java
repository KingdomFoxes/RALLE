package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgStore;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

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
    private static final UUID OTHER_MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000003");
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
    void collapsedNonHostActionStartsJoinCountdown() {
        var action = RaidLfgScreen.collapsedActionFor(lobby(), MEMBER);

        assertEquals(RaidLfgScreen.CardActionKind.JOIN, action.kind());
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

    @Test
    void collapsedMemberActionIsShortDestructiveLeave() {
        var action = RaidLfgScreen.collapsedActionFor(lobbyWithMember(), MEMBER);

        assertEquals(RaidLfgScreen.CardActionKind.LEAVE, action.kind());
        assertEquals("Leave", action.label());
        assertTrue(action.destructive());
        assertFalse(action.requiresConfirmation());
    }

    @Test
    void expandedMemberActionUsesTheSameShortLeaveLabel() {
        var action = RaidLfgScreen.expandedActionFor(lobbyWithMember(), MEMBER);

        assertEquals(RaidLfgScreen.CardActionKind.LEAVE, action.kind());
        assertEquals("Leave", action.label());
        assertTrue(action.destructive());
        assertFalse(action.requiresConfirmation());
    }

    @Test
    void lockedCollapsedCardHidesJoinButKeepsMemberAndHostActions() {
        var locked = lockedLobby();
        assertFalse(RaidLfgScreen.collapsedActionVisible(
                locked, UUID.fromString("00000000-0000-0000-0000-000000000099")));
        assertTrue(RaidLfgScreen.collapsedActionVisible(locked, HOST));
        assertTrue(RaidLfgScreen.collapsedActionVisible(locked, MEMBER));
        assertEquals("Locked", RaidLfgScreen.expandedActionFor(
                locked, UUID.fromString("00000000-0000-0000-0000-000000000099")).label());
    }

    @Test
    void kickConnectorStopsAtFacingBoxEdges() {
        var vertical = RaidLfgScreen.connectorBetween(
                new RaidLfgScreen.SelectionBox(10, 100, 100, 20),
                new RaidLfgScreen.SelectionBox(10, 20, 100, 40));
        assertEquals(new RaidLfgScreen.ConnectorLine(60, 100, 60, 60), vertical);

        var diagonal = RaidLfgScreen.connectorBetween(
                new RaidLfgScreen.SelectionBox(10, 100, 100, 20),
                new RaidLfgScreen.SelectionBox(200, 20, 100, 40));
        assertEquals(new RaidLfgScreen.ConnectorLine(87, 100, 200, 58), diagonal);
    }

    @Test
    void kickOutlineColorInterpolatesAndClamps() {
        assertEquals(0xFFF2B84B, RaidLfgScreen.lerpArgb(0xFFF2B84B, 0xFFFF3333, -1d));
        assertEquals(0xFFF9763F, RaidLfgScreen.lerpArgb(0xFFF2B84B, 0xFFFF3333, 0.5d));
        assertEquals(0xFFFF3333, RaidLfgScreen.lerpArgb(0xFFF2B84B, 0xFFFF3333, 2d));
    }

    @Test
    void mainUiDoesNotDuplicateTheSharedViewerJoinCue() {
        var tracker = new LfgMainUiSoundTracker();
        var sounds = new Sounds();
        tracker.reset(state(lobby()));

        tracker.update(state(lobbyWithMember()), sounds);

        assertEquals(0, sounds.joined);
        assertTrue(sounds.occupiedSlots.isEmpty());
    }

    @Test
    void mainUiKeepsAmethystForMembersWhoJoinAfterTheViewer() {
        var tracker = new LfgMainUiSoundTracker();
        var sounds = new Sounds();
        tracker.reset(state(lobbyWithMember()));

        tracker.update(state(lobbyWithOtherMember()), sounds);

        assertEquals(0, sounds.joined);
        assertEquals(List.of(3), sounds.occupiedSlots);
    }

    @Test
    void onlyASuccessfulExplicitLeaveGetsTheDepleteCue() {
        var tracker = new LfgMainUiSoundTracker();
        var sounds = new Sounds();

        tracker.actionCompleted("join", null, sounds);
        tracker.actionCompleted("leave", new IllegalStateException("rejected"), sounds);
        assertEquals(0, sounds.left);

        tracker.actionCompleted("leave", null, sounds);
        assertEquals(1, sounds.left);
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

    private static LfgProtocol.Lobby lobbyWithMember() {
        var guild = new LfgProtocol.GuildIdentity(GUILD, "Kingdom of Foxes", "FOX", "#FF8200");
        var host = new LfgProtocol.Member(HOST, "Host", guild, LfgProtocol.MemberRole.HOST,
                LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
        var member = new LfgProtocol.Member(MEMBER, "Member", guild, LfgProtocol.MemberRole.MEMBER,
                LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
        return new LfgProtocol.Lobby(UUID.fromString("00000000-0000-0000-0000-000000000010"),
                LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null, LfgProtocol.Visibility.PUBLIC,
                LfgProtocol.LobbyStatus.OPEN, false, HOST, GUILD, Instant.EPOCH, Instant.EPOCH,
                2, 4, List.of(host, member), new LfgProtocol.LobbyCapabilities(false, true, Map.of()));
    }

    private static LfgProtocol.Lobby lobbyWithOtherMember() {
        var lobby = lobbyWithMember();
        var members = new java.util.ArrayList<>(lobby.members());
        members.add(new LfgProtocol.Member(OTHER_MEMBER, "Other", members.getFirst().guild(),
                LfgProtocol.MemberRole.MEMBER, LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        return new LfgProtocol.Lobby(
                lobby.lobbyId(), lobby.raidType(), lobby.region(), lobby.note(), lobby.visibility(),
                lobby.status(), lobby.locked(), lobby.hostMinecraftUuid(), lobby.hostGuildUuid(),
                lobby.createdAt(), lobby.lastActivityAt(), lobby.revision() + 1, lobby.capacity(),
                members, lobby.capabilities());
    }

    private static RaidLfgStore.State state(LfgProtocol.Lobby lobby) {
        var viewer = new LfgProtocol.PlayerIdentity(
                MEMBER, "Member", lobby.members().getFirst().guild());
        return new RaidLfgStore.State(
                lobby.revision(), viewer, new LfgProtocol.ViewerCapabilities(true, true, Map.of()),
                Map.of(lobby.lobbyId(), lobby));
    }

    private static LfgProtocol.Lobby lockedLobby() {
        var lobby = lobbyWithMember();
        return new LfgProtocol.Lobby(
                lobby.lobbyId(), lobby.raidType(), lobby.region(), lobby.note(), lobby.visibility(),
                lobby.status(), true, lobby.hostMinecraftUuid(), lobby.hostGuildUuid(),
                lobby.createdAt(), lobby.lastActivityAt(), lobby.revision(), lobby.capacity(),
                lobby.members(), lobby.capabilities());
    }

    private static final class Sounds implements LfgSoundPlayer {
        int joined;
        int left;
        final java.util.ArrayList<Integer> occupiedSlots = new java.util.ArrayList<>();

        @Override public void playPartyJoined() { joined++; }
        @Override public void playPartyLeft() { left++; }
        @Override public void playRosterSlotOccupied(int occupiedSlot) { occupiedSlots.add(occupiedSlot); }
    }
}

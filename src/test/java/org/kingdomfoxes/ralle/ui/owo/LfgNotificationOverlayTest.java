package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LfgNotificationOverlayTest {
    @Test
    void newestCardOccupiesBottomAnchorAndWholeStackClamps() {
        var anchor = new Rectangle(802, 492, 190, 100);
        var stack = LfgNotificationOverlay.stackBounds(anchor, 3, 1000, 600);

        assertEquals(new Rectangle(802, 492, 190, 100), stack.get(0));
        assertEquals(new Rectangle(802, 386, 190, 100), stack.get(1));
        assertEquals(new Rectangle(802, 280, 190, 100), stack.get(2));
    }

    @Test
    void upperAnchorStacksDownAndClampsAsAUnit() {
        var anchor = new Rectangle(8, -20, 190, 100);
        var stack = LfgNotificationOverlay.stackBounds(anchor, 3, 800, 350);

        assertEquals(new Rectangle(8, 0, 190, 100), stack.get(0));
        assertEquals(new Rectangle(8, 106, 190, 100), stack.get(1));
        assertEquals(new Rectangle(8, 212, 190, 100), stack.get(2));
    }

    @Test
    void rosterSlotsUseTheFullCardWidthWithoutChangingTheirSize() {
        var card = new Rectangle(30, 40, 190, 100);

        assertEquals(38, LfgNotificationOverlay.rosterSlotX(card, 0));
        assertEquals(89, LfgNotificationOverlay.rosterSlotX(card, 1));
        assertEquals(141, LfgNotificationOverlay.rosterSlotX(card, 2));
        assertEquals(192, LfgNotificationOverlay.rosterSlotX(card, 3));
        assertThrows(IllegalArgumentException.class, () -> LfgNotificationOverlay.rosterSlotX(card, 4));
    }

    @Test
    void notificationRegionColorsMatchTheRaidLfgSemanticScale() {
        assertEquals(0xFF00FF55, LfgNotificationOverlay.regionColor(LfgProtocol.Region.EU));
        assertEquals(0xFFFFFF00, LfgNotificationOverlay.regionColor(LfgProtocol.Region.NA));
        assertEquals(0xFFFF3333, LfgNotificationOverlay.regionColor(LfgProtocol.Region.AS));
    }

    @Test
    void rosterHoverIdentifiesGuildAndPlayer() {
        var guild = new LfgProtocol.GuildIdentity(
                UUID.randomUUID(), "Kingdom of Foxes", "FOX", "#FF8200");
        var member = new LfgProtocol.Member(
                UUID.randomUUID(), "RallePlayer", guild,
                LfgProtocol.MemberRole.MEMBER, LfgProtocol.MemberSource.RALLE,
                Instant.EPOCH, null);

        assertEquals("[FOX] RallePlayer",
                LfgNotificationOverlay.rosterTooltip(member).getString());
        assertEquals(0xFFFF8200,
                LfgNotificationOverlay.rosterBorderColor(member));
    }

    @Test
    void partyFilledControlRequiresPersistentFullViewerHostedOffer() {
        var guild = new LfgProtocol.GuildIdentity(
                UUID.randomUUID(), "Kingdom of Foxes", "FOX", "#FF8200");
        var viewerId = UUID.randomUUID();
        var members = new java.util.ArrayList<LfgProtocol.Member>();
        members.add(new LfgProtocol.Member(
                viewerId, "Viewer", guild, LfgProtocol.MemberRole.HOST,
                LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        for (int index = 0; index < 3; index++) {
            members.add(new LfgProtocol.Member(
                    UUID.randomUUID(), "Member" + index, guild, LfgProtocol.MemberRole.MEMBER,
                    LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null));
        }
        var full = new LfgProtocol.Lobby(
                UUID.randomUUID(), LfgProtocol.RaidType.TNA, LfgProtocol.Region.EU, null,
                LfgProtocol.Visibility.PUBLIC, LfgProtocol.LobbyStatus.OPEN, false,
                viewerId, guild.uuid(), Instant.EPOCH, Instant.EPOCH, 2, 4, members,
                new LfgProtocol.LobbyCapabilities(false, true, java.util.Map.of())
        );
        var below = new LfgProtocol.Lobby(
                full.lobbyId(), full.raidType(), full.region(), full.note(), full.visibility(),
                full.status(), full.locked(), full.hostMinecraftUuid(), full.hostGuildUuid(),
                full.createdAt(), full.lastActivityAt(), 3, 4, List.copyOf(members.subList(0, 3)),
                full.capabilities()
        );

        assertEquals(true, LfgNotificationOverlay.showsPartyFilledControl(full, true, viewerId, true));
        assertEquals(false, LfgNotificationOverlay.showsPartyFilledControl(full, false, viewerId, true));
        assertEquals(false, LfgNotificationOverlay.showsPartyFilledControl(below, true, viewerId, true));
        assertEquals(false, LfgNotificationOverlay.showsPartyFilledControl(full, true, UUID.randomUUID(), true));
    }
}

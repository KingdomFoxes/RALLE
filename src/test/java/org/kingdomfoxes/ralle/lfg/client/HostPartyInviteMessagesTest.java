package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPartyInviteMessagesTest {
    private static final UUID LOBBY_ID = uuid(10);

    @Test
    void filledCopyUsesActualNamesAndReusableOpaqueStyledActions() {
        var targets = List.of(member(1, "Alpha"), member(2, "Bravo"), member(3, "Charlie"));

        var message = HostPartyInviteMessages.partyFilled(LOBBY_ID, targets);

        assertEquals(
                "RALLE: Your lobby has filled! Invite all or separately invite Alpha, Bravo, Charlie.",
                message.getString()
        );
        var body = message.getSiblings().get(1);
        var inviteAll = body.getSiblings().get(1);
        assertInteractive(inviteAll);
        var allAction = HostPartyInviteClickActions.parse(inviteAll.getStyle().getClickEvent()).orElseThrow();
        assertTrue(allAction.invitesAll());
        assertEquals(LOBBY_ID, allAction.lobbyId());

        int[] nameIndexes = {3, 5, 7};
        for (int index = 0; index < targets.size(); index++) {
            var clickableName = body.getSiblings().get(nameIndexes[index]);
            assertEquals(targets.get(index).ign(), clickableName.getString());
            assertInteractive(clickableName);
            var action = HostPartyInviteClickActions.parse(clickableName.getStyle().getClickEvent()).orElseThrow();
            assertEquals(LOBBY_ID, action.lobbyId());
            assertEquals(targets.get(index).minecraftUuid(), action.memberId());
        }
    }

    @Test
    void unrelatedOrMalformedCustomActionsAreIgnored() {
        assertTrue(HostPartyInviteClickActions.parse(new ClickEvent.RunCommand("/ralle lfg")).isEmpty());
        assertTrue(HostPartyInviteClickActions.parse(new ClickEvent.Custom(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("ralle", "invite/member/nope"),
                java.util.Optional.empty()
        )).isEmpty());
    }

    private static void assertInteractive(net.minecraft.network.chat.Component component) {
        var style = component.getStyle();
        assertEquals(org.kingdomfoxes.ralle.ui.theme.RallePalette.accent(), style.getColor().getValue());
        assertTrue(style.isBold());
        assertTrue(style.isUnderlined());
        assertInstanceOf(ClickEvent.Custom.class, style.getClickEvent());
        assertInstanceOf(HoverEvent.ShowText.class, style.getHoverEvent());
        assertTrue(((HoverEvent.ShowText) style.getHoverEvent()).value().getString().contains("/pa"));
    }

    private static LfgProtocol.Member member(int id, String ign) {
        var guild = new LfgProtocol.GuildIdentity(uuid(100), "Fox", "FOX", "#FF8200");
        return new LfgProtocol.Member(uuid(id), ign, guild, LfgProtocol.MemberRole.MEMBER,
                LfgProtocol.MemberSource.RALLE, Instant.EPOCH, null);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}

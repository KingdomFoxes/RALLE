package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.List;
import java.util.UUID;

/** Builds the shared-chat presentation for party-filled host invitations. */
public final class HostPartyInviteMessages {
    private HostPartyInviteMessages() {}

    public static Component partyFilled(UUID lobbyId, List<LfgProtocol.Member> targets) {
        return RalleChatMessages.notification(partyFilledBody(lobbyId, targets));
    }

    public static Component partyFilledBody(UUID lobbyId, List<LfgProtocol.Member> targets) {
        var body = Component.empty().append(Component.literal("Your lobby has filled! "));
        body.append(withHover(
                RalleChatMessages.clickable("Invite all", HostPartyInviteClickActions.inviteAll(lobbyId)),
                "Invite current lobby members with /pa"
        ));
        body.append(Component.literal(" or separately invite "));
        for (int index = 0; index < targets.size(); index++) {
            var member = targets.get(index);
            if (index > 0) body.append(Component.literal(", "));
            body.append(withHover(
                    RalleChatMessages.clickable(
                            member.ign(),
                            HostPartyInviteClickActions.inviteMember(lobbyId, member.minecraftUuid())
                    ),
                    "Invite " + member.ign() + " with /pa " + member.ign()
            ));
        }
        body.append(Component.literal("."));
        return body;
    }

    private static Component withHover(Component component, String description) {
        return component.copy().withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal(description))));
    }
}

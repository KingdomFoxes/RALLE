package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.List;
import java.util.UUID;

/** Delivers host invitation prompts and failures as ephemeral local RALLE chat messages. */
public final class MinecraftHostPartyInviteSink implements HostPartyInviteSink {
    private final Minecraft minecraft;

    public MinecraftHostPartyInviteSink(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void partyFilled(UUID lobbyId, List<LfgProtocol.Member> targets) {
        minecraft.execute(() -> RalleChatMessages.post(
                minecraft, HostPartyInviteMessages.partyFilledBody(lobbyId, targets)));
    }

    @Override
    public void error(String message) {
        minecraft.execute(() -> RalleChatMessages.post(minecraft, Component.literal(message)));
    }
}

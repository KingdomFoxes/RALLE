package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import org.kingdomfoxes.ralle.chat.RalleChatMessages;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

/** Delivers ephemeral LFG notifications through RALLE's shared local-chat presentation. */
public final class MinecraftLfgNotificationSink implements LfgNotificationSink {
    private final Minecraft minecraft;
    private final LfgSoundPlayer sounds;

    public MinecraftLfgNotificationSink(Minecraft minecraft, LfgSoundPlayer sounds) {
        this.minecraft = minecraft;
        this.sounds = sounds;
    }

    @Override
    public void partyPing(LfgProtocol.PartyPingFrame ping) {
        minecraft.execute(() -> {
            var message = RalleChatMessages.clickable(
                    ping.hostIgn() + " has pinged you!",
                    new ClickEvent.RunCommand("/ralle lfg")
            );
            RalleChatMessages.post(minecraft, message);
            sounds.playPartyPing();
        });
    }
}

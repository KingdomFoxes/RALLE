package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

/** Delivers ephemeral, clickable LFG notifications through vanilla chat. */
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
            var message = Component.literal(ping.hostIgn() + " has pinged you!")
                    .withStyle(style -> style
                            .withColor(0xF2B84B)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand("/ralle lfg")));
            minecraft.gui.getChat().addMessage(message);
            sounds.playPartyPing();
        });
    }
}

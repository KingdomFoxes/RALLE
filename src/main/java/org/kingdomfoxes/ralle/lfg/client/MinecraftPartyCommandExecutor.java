package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;

import java.util.regex.Pattern;

/** Executes bounded, pre-validated Wynncraft party commands selected by the LFG controllers. */
public final class MinecraftPartyCommandExecutor implements PartyCommandExecutor {
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Minecraft minecraft;

    public MinecraftPartyCommandExecutor(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void kick(String ign) {
        send("party kick ", ign);
    }

    @Override
    public void invite(String ign) {
        send("pa ", ign);
    }

    private void send(String command, String ign) {
        if (ign == null || !IGN.matcher(ign).matches()) return;
        minecraft.execute(() -> {
            var connection = minecraft.getConnection();
            if (connection != null && minecraft.player != null) {
                connection.sendCommand(command + ign);
            }
        });
    }
}

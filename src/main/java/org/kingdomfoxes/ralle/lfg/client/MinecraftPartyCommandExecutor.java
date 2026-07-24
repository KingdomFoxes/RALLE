package org.kingdomfoxes.ralle.lfg.client;

import net.minecraft.client.Minecraft;

import java.util.regex.Pattern;

/** Executes exactly one server-approved Wynncraft party command per accepted kick. */
public final class MinecraftPartyCommandExecutor implements PartyCommandExecutor {
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Minecraft minecraft;

    public MinecraftPartyCommandExecutor(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void kick(String ign) {
        if (ign == null || !IGN.matcher(ign).matches()) return;
        minecraft.execute(() -> {
            var connection = minecraft.getConnection();
            if (connection != null && minecraft.player != null) {
                connection.sendCommand("party kick " + ign);
            }
        });
    }
}

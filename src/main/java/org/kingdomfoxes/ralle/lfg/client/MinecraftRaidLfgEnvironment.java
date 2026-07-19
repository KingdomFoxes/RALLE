package org.kingdomfoxes.ralle.lfg.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.UUID;

/** Reads the current, non-authoritative local connection and identity inputs. */
public final class MinecraftRaidLfgEnvironment implements RaidLfgEnvironment {
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final String modVersion;

    public MinecraftRaidLfgEnvironment(Minecraft minecraft, SettingsRegistry settings) {
        this.minecraft = minecraft;
        this.enabled = settings.setting("raid-lfg-enabled", BooleanSetting.class);
        this.modVersion = FabricLoader.getInstance().getModContainer("ralle")
                .orElseThrow(() -> new IllegalStateException("RALLE mod metadata is unavailable"))
                .getMetadata().getVersion().getFriendlyString();
    }

    @Override public boolean enabled() { return enabled.value(); }

    @Override
    public String serverHost() {
        var server = minecraft.getCurrentServer();
        return server == null ? "" : server.ip;
    }

    @Override public UUID playerId() { return minecraft.getUser().getProfileId(); }
    @Override public String ign() { return minecraft.getUser().getName(); }
    @Override public String modVersion() { return modVersion; }
}

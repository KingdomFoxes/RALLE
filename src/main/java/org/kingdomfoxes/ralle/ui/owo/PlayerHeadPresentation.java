package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.kingdomfoxes.ralle.lfg.client.GuildTerritoryColors;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared asynchronous skin, fallback, hat-layer, and guild-border presentation. */
public final class PlayerHeadPresentation {
    private static final ConcurrentMap<UUID, CompletableFuture<PlayerSkin>> SKINS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PlayerSkin> RESOLVED = new ConcurrentHashMap<>();

    private PlayerHeadPresentation() {}

    public static void draw(GuiGraphics graphics, Minecraft minecraft, LfgProtocol.Member member,
                            int x, int y, int faceSize, int borderColor) {
        graphics.fill(x, y, x + faceSize + 2, y + faceSize + 2, borderColor);
        PlayerFaceRenderer.draw(graphics, resolve(minecraft, member.minecraftUuid(), member.ign()),
                x + 1, y + 1, faceSize);
    }

    public static int guildBorder(LfgProtocol.Member member) {
        return GuildTerritoryColors.forGuild(member.guild().tag(), member.guild().color());
    }

    private static PlayerSkin resolve(Minecraft minecraft, UUID id, String name) {
        var existing = RESOLVED.get(id);
        if (existing != null) return existing;
        var fallback = DefaultPlayerSkin.get(id);
        SKINS.computeIfAbsent(id, ignored -> {
            var partial = new GameProfile(id, name);
            return CompletableFuture.supplyAsync(() -> minecraft.services().profileResolver()
                            .fetchById(id).orElse(partial))
                    .thenCompose(minecraft.getSkinManager()::get)
                    .thenApply(skin -> skin.orElse(fallback))
                    .exceptionally(error -> fallback)
                    .whenComplete((skin, error) -> RESOLVED.put(id, skin));
        });
        return fallback;
    }
}

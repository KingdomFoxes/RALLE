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
import org.kingdomfoxes.ralle.ui.BoundedAsyncCache;

/** Shared asynchronous skin, fallback, hat-layer, and guild-border presentation. */
public final class PlayerHeadPresentation {
    private static final BoundedAsyncCache<UUID, PlayerSkin> SKINS =
            new BoundedAsyncCache<>(256, 8, 30_000, System::currentTimeMillis);

    private PlayerHeadPresentation() {}

    public static void clearSession() { SKINS.clear(); }

    public static void draw(GuiGraphics graphics, Minecraft minecraft, LfgProtocol.Member member,
                            int x, int y, int faceSize, int borderColor) {
        draw(graphics, minecraft, member, x, y, faceSize, borderColor, 1);
    }

    public static void draw(GuiGraphics graphics, Minecraft minecraft, LfgProtocol.Member member,
                            int x, int y, int faceSize, int borderColor, int borderWidth) {
        int outerSize = faceSize + 2;
        graphics.fill(x, y, x + outerSize, y + outerSize, borderColor);
        PlayerFaceRenderer.draw(graphics, resolve(minecraft, member.minecraftUuid(), member.ign()),
                x + borderWidth, y + borderWidth, outerSize - borderWidth * 2);
    }

    public static int guildBorder(LfgProtocol.Member member) {
        return GuildTerritoryColors.forGuild(member.guild().tag(), member.guild().color());
    }

    private static PlayerSkin resolve(Minecraft minecraft, UUID id, String name) {
        var fallback = DefaultPlayerSkin.get(id);
        return SKINS.get(id, fallback, ignored -> {
            var partial = new GameProfile(id, name);
            return CompletableFuture.supplyAsync(() -> minecraft.services().profileResolver()
                            .fetchById(id).orElse(partial))
                    .thenCompose(minecraft.getSkinManager()::get)
                    .thenApply(skin -> skin.orElse(null));
        });
    }
}

package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.kingdomfoxes.ralle.lfg.client.GuildTerritoryColors;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

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
        draw(graphics, resolve(minecraft, member.minecraftUuid(), member.ign()),
                x, y, faceSize + 2, borderColor, null, null);
    }

    public static void draw(GuiGraphics graphics, Minecraft minecraft, LfgProtocol.Member member,
                            int x, int y, int faceSize, int borderColor,
                            NameplateStyle style, CosmeticAppearance appearance) {
        draw(graphics, resolve(minecraft, member.minecraftUuid(), member.ign()),
                x, y, faceSize + 2, borderColor, style, appearance);
    }

    /** Notification, Kick wheel, and preview share a white edge, two-pixel material frame, and guild ring. */
    static void draw(GuiGraphics graphics, PlayerSkin skin, int x, int y, int outerSize,
                     int borderColor, NameplateStyle style, CosmeticAppearance appearance) {
        int materialInset = style != null && appearance != null ? 3 : 0;
        if (materialInset > 0)
            LiquidMaterialPresentation.plate(graphics, style, appearance, x, y, outerSize, outerSize);
        // Cover the center of one full material quad. No scissor strips can bleed or obscure the guild ring.
        graphics.fill(x + materialInset, y + materialInset,
                x + outerSize - materialInset, y + outerSize - materialInset, borderColor);
        int faceInset = materialInset + 1;
        PlayerFaceRenderer.draw(graphics, skin, x + faceInset, y + faceInset, outerSize - faceInset * 2);
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

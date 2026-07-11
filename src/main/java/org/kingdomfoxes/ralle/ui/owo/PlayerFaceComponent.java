package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.authlib.GameProfile;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Draws a player's front-facing skin face and hat layer using Minecraft's native renderer. */
final class PlayerFaceComponent extends BaseUIComponent {
    private static final ConcurrentMap<UUID, CompletableFuture<PlayerSkin>> SKINS = new ConcurrentHashMap<>();

    private volatile PlayerSkin skin;

    PlayerFaceComponent(String uuid, String name, int size) {
        this.sizing(Sizing.fixed(size));
        var profileId = UUID.fromString(uuid);
        this.skin = DefaultPlayerSkin.get(profileId);
        SKINS.computeIfAbsent(profileId, ignored -> loadSkin(profileId, name))
                .thenAccept(resolved -> this.skin = resolved);
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        PlayerFaceRenderer.draw(graphics, skin, x, y, width);
    }

    private static CompletableFuture<PlayerSkin> loadSkin(UUID profileId, String name) {
        var minecraft = Minecraft.getInstance();
        var fallback = DefaultPlayerSkin.get(profileId);
        var partialProfile = new GameProfile(profileId, name);

        return CompletableFuture.supplyAsync(() -> minecraft.services().profileResolver()
                        .fetchById(profileId)
                        .orElse(partialProfile))
                .thenCompose(minecraft.getSkinManager()::get)
                .thenApply(resolved -> resolved.orElse(fallback))
                .exceptionally(error -> fallback);
    }
}

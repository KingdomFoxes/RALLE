package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;

import java.util.UUID;

/** Draws a player's front-facing skin face and hat layer using Minecraft's native renderer. */
final class PlayerFaceComponent extends BaseUIComponent {
    private final int borderColor;
    private final int borderWidth;
    private final UUID profileId;
    private final String name;

    PlayerFaceComponent(String uuid, String name, int faceSize, int borderColor) {
        this(uuid, name, faceSize, borderColor, 1);
    }

    PlayerFaceComponent(String uuid, String name, int faceSize, int borderColor, int borderWidth) {
        this.sizing(Sizing.fixed(faceSize + 2));
        this.borderColor = borderColor;
        this.borderWidth = borderWidth;
        this.profileId = UUID.fromString(uuid);
        this.name = name;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + width, y + height, borderColor);
        PlayerFaceRenderer.draw(graphics, PlayerHeadPresentation.resolve(Minecraft.getInstance(), profileId, name),
                x + borderWidth, y + borderWidth,
                width - borderWidth * 2);
    }
}

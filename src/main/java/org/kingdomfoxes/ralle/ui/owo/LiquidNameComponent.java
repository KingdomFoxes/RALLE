package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.LiquidComponentTint;
import org.kingdomfoxes.ralle.cosmetics.LiquidMaterial;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

/** RALLE-owned LFG name surface; native font and row hit geometry remain authoritative. */
final class LiquidNameComponent extends BaseUIComponent {
    private final NameplateStyle style;
    private final Component star;
    private final Component username;

    LiquidNameComponent(String ign, boolean host, NameplateStyle style) {
        this.style = style;
        this.star = host ? RalleTheme.ui(Component.literal("★ ")) : Component.empty();
        this.username = RalleTheme.ui(Component.literal(ign));
        var font = Minecraft.getInstance().font;
        sizing(Sizing.fixed(font.width(star) + font.width(username) + 4),
                Sizing.fixed(font.lineHeight + 4));
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int textX = x + 2, textY = y + 2;
        long now = System.nanoTime();
        CosmeticAppearance appearance = RalleClient.cosmeticAppearance(style);
        if (appearance == null) {
            graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            graphics.drawString(font, username, textX + font.width(star), textY, 0xffffffff, true);
            return;
        }
        try {
            if (appearance.treatment() == CosmeticAppearance.Treatment.PLATE) {
                var texture = RalleClient.context().cosmeticTextures().texture(style, appearance.resolution(), now);
                int sourceWidth = Math.min(LiquidMaterial.width(appearance.resolution()),
                        (int) Math.ceil(width / appearance.resolution()));
                int sourceHeight = Math.min(LiquidMaterial.height(appearance.resolution()),
                        (int) Math.ceil(height / appearance.resolution()));
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f,
                        width, height, sourceWidth, sourceHeight,
                        LiquidMaterial.width(appearance.resolution()), LiquidMaterial.height(appearance.resolution()));
            }
            if (!star.getString().isEmpty()) graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            int nameX = textX + font.width(star);
            drawUsernameOutline(graphics, nameX, textY, appearance.usernameOutlinePixels());
            var material = RalleClient.context().cosmeticTextures();
            Component rendered = LiquidComponentTint.apply(username, font, (offset, yy) -> {
                int sample = material.sample(style, appearance.resolution(), offset, yy, now);
                if (appearance.treatment() == CosmeticAppearance.Treatment.TEXT) return sample;
                int r = sample >> 16 & 255, g = sample >> 8 & 255, b = sample & 255;
                return (r * 299 + g * 587 + b * 114) / 1000 >= 128 ? 0xff081225 : 0xffffffff;
            });
            graphics.drawString(font, rendered, nameX, textY, 0xffffffff, true);
        } catch (RuntimeException ignored) {
            graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            graphics.drawString(font, username, textX + font.width(star), textY,
                    0xff000000 | style.midtone(), true);
        }
    }

    private void drawUsernameOutline(OwoUIGraphics graphics, int textX, int textY, int radius) {
        if (radius == 0) return;
        var font = Minecraft.getInstance().font;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            if (dx == 0 && dy == 0 || dx * dx + dy * dy > radius * radius + 1) continue;
            graphics.drawString(font, username, textX + dx, textY + dy, 0xffffffff, false);
        }
    }
}

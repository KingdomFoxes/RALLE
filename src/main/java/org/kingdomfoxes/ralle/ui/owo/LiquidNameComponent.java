package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.LiquidComponentTint;
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
        drawName(graphics, star, username, style, RalleClient.cosmeticAppearance(style), x + 2, y + 2);
    }

    static void drawName(GuiGraphics graphics, Component star, Component username, NameplateStyle style,
                         CosmeticAppearance appearance, int textX, int textY) {
        var font = Minecraft.getInstance().font;
        long now = System.nanoTime();
        if (appearance == null) {
            graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            graphics.drawString(font, username, textX + font.width(star), textY, 0xffffffff, true);
            return;
        }
        try {
            if (!star.getString().isEmpty()) graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            int nameX = textX + font.width(star);
            drawUsernameOutline(graphics, username, nameX, textY, appearance.usernameOutlinePixels());
            var material = RalleClient.context().cosmeticTextures();
            Component rendered = LiquidComponentTint.apply(username, font, (offset, yy) -> {
                if (appearance.treatment() == CosmeticAppearance.Treatment.TEXT)
                    return material.sample(style, appearance.resolution(), offset * 256 / Math.max(1, font.width(username)),
                            yy * 64 / font.lineHeight, now);
                return 0xffffffff;
            });
            graphics.drawString(font, rendered, nameX, textY, 0xffffffff, true);
        } catch (RuntimeException ignored) {
            graphics.drawString(font, star, textX, textY, 0xffffffff, true);
            graphics.drawString(font, username, textX + font.width(star), textY,
                    0xffffffff, true);
        }
    }

    private static void drawUsernameOutline(GuiGraphics graphics, Component username, int textX, int textY, int radius) {
        if (radius == 0) return;
        var font = Minecraft.getInstance().font;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            if (dx == 0 && dy == 0 || dx * dx + dy * dy > radius * radius + 1) continue;
            graphics.drawString(font, username, textX + dx, textY + dy, 0xffffffff, false);
        }
    }
}

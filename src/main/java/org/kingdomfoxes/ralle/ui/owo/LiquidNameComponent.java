package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** RALLE-owned LFG name surface; native font and row hit geometry remain authoritative. */
final class LiquidNameComponent extends BaseUIComponent {
    private final Component star;
    private final Component username;

    LiquidNameComponent(String ign, boolean host) {
        this.star = host ? RalleTheme.ui(Component.literal("★ ")) : Component.empty();
        this.username = RalleTheme.ui(Component.literal(ign));
        var font = Minecraft.getInstance().font;
        sizing(Sizing.fixed(font.width(star) + font.width(username) + 4),
                Sizing.fixed(font.lineHeight + 4));
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        drawName(graphics, star, username, x + 2, y + 2);
    }

    static void drawName(GuiGraphics graphics, Component star, Component username, int textX, int textY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, star, textX, textY, 0xffffffff, true);
        graphics.drawString(font, username, textX + font.width(star), textY, 0xffffffff, true);
    }
}

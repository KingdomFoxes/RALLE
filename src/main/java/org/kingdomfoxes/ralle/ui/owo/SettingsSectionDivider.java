package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Interface-font section title centered inside a one-pixel separator. */
final class SettingsSectionDivider extends BaseUIComponent {
    private static final int HEIGHT = 17;
    private static final int TITLE_GAP = 5;
    private final Component title;

    SettingsSectionDivider(Component title) {
        this.title = RalleTheme.ui(title);
        sizing(Sizing.fill(100), Sizing.fixed(HEIGHT));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var font = Minecraft.getInstance().font;
        int titleWidth = font.width(title);
        int titleLeft = x + Math.max(0, (width - titleWidth) / 2);
        int titleRight = titleLeft + titleWidth;
        int lineY = y + height / 2;
        int lineColor = 0xFF000000 | org.kingdomfoxes.ralle.ui.theme.RallePalette.insetAccentArgb();
        if (titleLeft - TITLE_GAP > x) graphics.fill(x, lineY, titleLeft - TITLE_GAP, lineY + 1, lineColor);
        if (titleRight + TITLE_GAP < x + width) {
            graphics.fill(titleRight + TITLE_GAP, lineY, x + width, lineY + 1, lineColor);
        }
        graphics.drawString(font, title, titleLeft, y + Math.max(0, (height - font.lineHeight) / 2),
                org.kingdomfoxes.ralle.ui.theme.RallePalette.accentArgb(), false);
    }
}

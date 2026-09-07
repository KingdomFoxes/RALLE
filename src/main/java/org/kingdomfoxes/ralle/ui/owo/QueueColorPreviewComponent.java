package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.war.queue.QueueAttributionDemo;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;
import java.util.function.Supplier;

/** The local account's sample queue, rendered as one HUD-style line above the color controls. */
final class QueueColorPreviewComponent extends BaseUIComponent {
    private final Supplier<HighlightStyle> style;

    QueueColorPreviewComponent(Supplier<HighlightStyle> style) {
        this.style = style;
        sizing(Sizing.fill(100), Sizing.fixed(10));
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = Minecraft.getInstance();
        var row = QueueAttributionDemo.preview(client.getUser().getName(), style.get(), System.currentTimeMillis());
        graphics.drawString(client.font, row, x, y, 0xFFFFFFFF);
    }
}

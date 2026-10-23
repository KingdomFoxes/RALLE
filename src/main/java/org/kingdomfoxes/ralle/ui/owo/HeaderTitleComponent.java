package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Karla banner lettering with a tight, opaque black half-logical-pixel drop shadow. */
final class HeaderTitleComponent extends LabelComponent {
    private static final float SHADOW_OFFSET = .5f;

    HeaderTitleComponent(Component title) {
        super(RalleTheme.heading(title));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        // Match LabelComponent's framebuffer-pixel baseline adjustment for both passes.
        graphics.push().translate(SHADOW_OFFSET,
                SHADOW_OFFSET + 1f / Minecraft.getInstance().getWindow().getGuiScale());
        try {
            drawText((renderX, renderY, text, shadow, color) -> {
                FormattedCharSequence black = sink -> text.accept((index, style, codePoint) ->
                        sink.accept(index, style.withColor(0x000000), codePoint));
                graphics.drawString(textRenderer, black, renderX, renderY, 0xFF000000, false);
            });
        } finally {
            graphics.pop();
        }
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
    }
}

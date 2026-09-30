package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.container.WrappingParentUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Keeps read-only previews visible while locking only the supporter settings viewport. */
final class SupporterSettingsPane extends WrappingParentUIComponent<UIComponent> {
    private final BooleanSupplier locked;
    private final RalleScrollContainer viewport;

    SupporterSettingsPane(Sizing horizontal, Sizing vertical, UIComponent child,
                          RalleScrollContainer viewport, BooleanSupplier locked) {
        super(horizontal, vertical, child);
        this.viewport = viewport;
        this.locked = locked;
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        drawChildren(graphics, mouseX, mouseY, partialTicks, delta, childView);
        if (!locked.getAsBoolean()) return;

        int top = Math.max(y, viewport.y());
        int bottom = Math.min(y + height, viewport.y() + viewport.height());
        if (bottom <= top) return;
        graphics.nextStratum();
        graphics.enableScissor(x, top, x + width, bottom);
        try {
            RalleSurfaces.SUPPORTER_LOCK.draw(graphics, this);
            var font = Minecraft.getInstance().font;
            int boxWidth = Math.max(1, Math.min(300, width - 20));
            int padding = 9;
            var lines = font.split(RalleTheme.ui(Component.translatable("ralle.cosmetics.supporter.locked")),
                    Math.max(1, boxWidth - padding * 2));
            // Native glyphs occupy lineHeight minus the two trailing spacing pixels.
            int textHeight = (lines.size() - 1) * RalleTheme.BODY_LINE_HEIGHT + Math.max(1, font.lineHeight - 2);
            int boxHeight = textHeight + padding * 2;
            int boxX = x + (width - boxWidth) / 2;
            int boxY = top + (bottom - top - boxHeight) / 2;
            RalleSurfaces.framedNavy(graphics, boxX, boxY, boxWidth, boxHeight);
            int lineY = boxY + (boxHeight - textHeight) / 2;
            for (var line : lines) {
                graphics.drawString(font, line, boxX + (boxWidth - font.width(line)) / 2,
                        lineY, RalleTheme.accentArgb(), false);
                lineY += RalleTheme.BODY_LINE_HEIGHT;
            }
        } finally {
            graphics.disableScissor();
            graphics.nextStratum();
        }
    }

    @Override
    public UIComponent childAt(int x, int y) {
        return locked.getAsBoolean() ? (isInBoundingBox(x, y) ? this : null) : super.childAt(x, y);
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent event, boolean doubled) {
        return locked.getAsBoolean() || super.onMouseDown(event, doubled);
    }
}

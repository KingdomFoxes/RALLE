package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import java.util.Objects;
import java.util.function.Supplier;

/** Shared Minecraft-slot preview with the same production border renderer. */
public final class HighlightedSlotPreviewComponent extends BaseUIComponent {
    private final PreviewItemProvider items;
    private final Supplier<HighlightStyle> style;

    public HighlightedSlotPreviewComponent(PreviewItemProvider items, Supplier<HighlightStyle> style) {
        this.items = Objects.requireNonNull(items, "items");
        this.style = Objects.requireNonNull(style, "style");
        sizing(Sizing.fixed(20));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + 20, y + 20, 0xFF111827);
        graphics.renderItem(items.previewItem(), x + 2, y + 2);
        ConsumableSlotBorder.draw(graphics, x + 2, y + 2, style.get(), System.currentTimeMillis());
    }
}

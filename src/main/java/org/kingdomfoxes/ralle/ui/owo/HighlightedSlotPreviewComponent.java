package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;
import org.lwjgl.glfw.GLFW;

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
        tooltip(RalleTheme.ui(Component.translatable("ralle.consumables.preview.tooltip")));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        graphics.fill(x, y, x + 20, y + 20, 0xFF111827);
        graphics.renderItem(items.previewItem(), x + 2, y + 2);
        ConsumableSlotBorder.draw(graphics, x + 2, y + 2, style.get(), System.currentTimeMillis());
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return super.onMouseDown(click, doubled);
        return items.advance() || super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onKeyPress(KeyEvent key) {
        if ((key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_KP_ENTER
                || key.key() == GLFW.GLFW_KEY_SPACE) && items.advance()) {
            return true;
        }
        return super.onKeyPress(key);
    }

    @Override public boolean canFocus(FocusSource source) { return true; }
    @Override public CursorStyle cursorStyle() { return CursorStyle.HAND; }
}

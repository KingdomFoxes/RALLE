package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import java.util.Objects;
import org.lwjgl.glfw.GLFW;

/** Colored rule text and swatch which animate together for rainbow styles. */
final class DynamicRuleLabelComponent extends BaseUIComponent {
    private final Component text;
    private final HighlightStyle style;
    private final Runnable pressed;
    private final boolean swatch;
    private final boolean interactive;

    DynamicRuleLabelComponent(Component text, HighlightStyle style, int width, Runnable pressed) {
        this(text, style, width, true, pressed);
    }

    DynamicRuleLabelComponent(Component text, HighlightStyle style, int width, boolean swatch, Runnable pressed) {
        this(text, style, width, swatch, true, pressed);
    }

    DynamicRuleLabelComponent(
            Component text,
            HighlightStyle style,
            int width,
            boolean swatch,
            boolean interactive,
            Runnable pressed
    ) {
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
        this.pressed = Objects.requireNonNull(pressed, "pressed");
        this.swatch = swatch;
        this.interactive = interactive;
        sizing(Sizing.fixed(width), Sizing.fixed(18));
    }

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        int color = colorAt(style, x, y, System.currentTimeMillis());
        if (swatch) {
            graphics.fill(x, y + 5, x + 8, y + 13, color);
            graphics.drawRectOutline(x, y + 5, 8, 8, 0xFF080D16);
        }
        graphics.drawString(Minecraft.getInstance().font, RalleTheme.ui(text),
                x + (swatch ? 12 : 0), y + 5, color, false);
    }

    static int colorAt(HighlightStyle style, int x, int y, long timeMillis) {
        return style.rainbow() || style.chroma()
                ? ConsumableSlotBorder.rainbowColor(style.chroma() ? 0f : (x + y) * .01f, timeMillis)
                : 0xFF000000 | style.rgb();
    }

    @Override public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (!interactive) return false;
        pressed.run();
        return true;
    }

    @Override public boolean onKeyPress(KeyEvent key) {
        if (interactive && (key.key() == GLFW.GLFW_KEY_ENTER
                || key.key() == GLFW.GLFW_KEY_KP_ENTER
                || key.key() == GLFW.GLFW_KEY_SPACE)) {
            pressed.run();
            return true;
        }
        return super.onKeyPress(key);
    }

    @Override public boolean canFocus(FocusSource source) { return interactive; }
    @Override public CursorStyle cursorStyle() { return interactive ? CursorStyle.HAND : CursorStyle.NONE; }
}

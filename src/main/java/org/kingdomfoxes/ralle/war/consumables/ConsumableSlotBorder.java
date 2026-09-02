package org.kingdomfoxes.ralle.war.consumables;

import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

/** One-pixel full-opacity slot border geometry and WynnColour-compatible rainbow animation. */
public final class ConsumableSlotBorder {
    public static final float RAINBOW_SPEED = 0.0004f;
    public static final float RAINBOW_SATURATION = 0.85f;

    private ConsumableSlotBorder() {}

    public static Geometry geometry(int slotX, int slotY) {
        return new Geometry(slotX - 1, slotY - 1, slotX + 17, slotY + 17);
    }

    public static void draw(GuiGraphics graphics, int slotX, int slotY, HighlightStyle style, long timeMillis) {
        var box = geometry(slotX, slotY);
        if (!style.rainbow()) {
            int argb = 0xFF000000 | style.rgb();
            graphics.fill(box.left(), box.top(), box.right(), box.top() + 1, argb);
            graphics.fill(box.left(), box.bottom() - 1, box.right(), box.bottom(), argb);
            graphics.fill(box.left(), box.top() + 1, box.left() + 1, box.bottom() - 1, argb);
            graphics.fill(box.right() - 1, box.top() + 1, box.right(), box.bottom() - 1, argb);
            return;
        }
        long phaseTime = Math.floorMod(timeMillis, 10_000L);
        for (int offset = 0; offset < 18; offset++) {
            int horizontal = rainbowColor(offset / 18f, phaseTime);
            graphics.fill(box.left() + offset, box.top(), box.left() + offset + 1, box.top() + 1, horizontal);
            graphics.fill(box.left() + offset, box.bottom() - 1, box.left() + offset + 1, box.bottom(), horizontal);
            int vertical = rainbowColor(offset / 18f + .5f, phaseTime);
            graphics.fill(box.left(), box.top() + offset, box.left() + 1, box.top() + offset + 1, vertical);
            graphics.fill(box.right() - 1, box.top() + offset, box.right(), box.top() + offset + 1, vertical);
        }
    }

    public static int rainbowColor(float positionalOffset, long timeMillis) {
        float hue = (positionalOffset + Math.floorMod(timeMillis, 10_000L) * RAINBOW_SPEED) % 1f;
        if (hue < 0) hue += 1f;
        return 0xFF000000 | (Color.HSBtoRGB(hue, RAINBOW_SATURATION, 1f) & 0xFFFFFF);
    }

    public record Geometry(int left, int top, int right, int bottom) {
        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }
}

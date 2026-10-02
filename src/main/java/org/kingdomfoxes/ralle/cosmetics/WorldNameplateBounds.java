package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/** Fit actual resource-pack glyph geometry as well as advance width, retaining vanilla text centering. */
public record WorldNameplateBounds(float left, float right, float top, float bottom) {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldNameplateBounds.class);
    private static final AtomicBoolean REPORTED_MEASUREMENT_FAILURE = new AtomicBoolean();

    public static WorldNameplateBounds measure(Font font, Component label, boolean extraEars) {
        return measure(font, label, extraEars ? -10 : 0);
    }

    public static WorldNameplateBounds measure(Font font, Component label, int yOffset) {
        int width = font.width(label);
        float x = -width / 2f;
        float y = yOffset;
        ScreenRectangle ink = null;
        try {
            ink = font.prepareText(label.getVisualOrderText(), x, y, 0xffffffff, false, false, 0).bounds();
        } catch (RuntimeException failure) {
            // A font-provider compatibility failure must not suppress the whole material or inventory preview.
            if (REPORTED_MEASUREMENT_FAILURE.compareAndSet(false, true))
                LOGGER.warn("Could not measure nameplate glyph bounds; using padded text advance width", failure);
        }
        float left = x, right = x + width, top = y, bottom = y + 8;
        if (ink != null) {
            left = Math.min(left, ink.left());
            right = Math.max(right, ink.right());
            top = Math.min(top, ink.top());
            bottom = Math.max(bottom, ink.bottom());
        }
        // Two logical pixels of horizontal breathing room inside the separate one-pixel white border.
        return new WorldNameplateBounds(left - 2, right + 2, top - 1, bottom + 1);
    }

    /** Symmetric clipping viewport must contain an asymmetric icon without moving the character. */
    public int centeredOuterWidth() {
        return (int) Math.ceil(2 * (Math.max(Math.abs(left), Math.abs(right)) + 1));
    }
}

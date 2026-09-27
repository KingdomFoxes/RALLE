package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.function.IntBinaryOperator;

/** Preserves text segments, resource-pack font, and click/hover styles while coloring the complete label. */
public final class LiquidComponentTint {
    private LiquidComponentTint() {}

    public static Component apply(Component original, Font font, IntBinaryOperator materialColor) {
        MutableComponent result = Component.empty();
        int[] cursor = {0};
        original.visit((style, text) -> {
            for (int offset = 0; offset < text.length();) {
                int codepoint = text.codePointAt(offset);
                String glyph = new String(Character.toChars(codepoint));
                int color = materialColor.applyAsInt(cursor[0], 4) & 0xffffff;
                result.append(Component.literal(glyph).withStyle(style.withColor(color)));
                cursor[0] += font.width(Component.literal(glyph).withStyle(style));
                offset += Character.charCount(codepoint);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}

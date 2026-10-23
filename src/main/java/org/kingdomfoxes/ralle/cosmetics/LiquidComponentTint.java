package org.kingdomfoxes.ralle.cosmetics;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

/** Preserves text segments, resource-pack font, and click/hover styles while coloring the complete label. */
public final class LiquidComponentTint {
    private LiquidComponentTint() {}

    /** Native glyph shadow survives deferred nametag rendering, which disables the drawShadow flag. */
    public static Component whiteWithVanillaShadow(Component original) {
        MutableComponent result = Component.empty();
        var run = new StringBuilder();
        Style[] runStyle = {null};
        // Decode legacy section-sign codes before splitting glyphs: splitting "§f" would display an extra f.
        StringDecomposer.iterateFormatted(original, Style.EMPTY, (index, style, codepoint) -> {
            Style tinted = style.withColor(0xffffff).withShadowColor(0xff3f3f3f);
            if (!tinted.equals(runStyle[0])) {
                if (!run.isEmpty()) result.append(Component.literal(run.toString()).withStyle(runStyle[0]));
                run.setLength(0);
                runStyle[0] = tinted;
            }
            run.appendCodePoint(codepoint);
            return true;
        });
        if (!run.isEmpty()) result.append(Component.literal(run.toString()).withStyle(runStyle[0]));
        return result;
    }
}

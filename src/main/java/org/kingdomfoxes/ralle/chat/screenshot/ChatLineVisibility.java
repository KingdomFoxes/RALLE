package org.kingdomfoxes.ralle.chat.screenshot;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Classifies frozen lines from their actual prepared glyph/effect bounds, excluding advance-only spaces. */
public final class ChatLineVisibility {
    private ChatLineVisibility() {}

    public static boolean hasVisibleContent(Font font, FormattedCharSequence content) {
        FormattedCharSequence withoutStyleShadow = sink -> content.accept((index, style, codePoint) ->
                sink.accept(index, style.withoutShadow(), codePoint));
        var bounds = font.prepareText(withoutStyleShadow, 0, 0, 0xFFFFFFFF, false, false, 0).bounds();
        // Prepared text made solely from advance-only space providers has no drawable bounds.
        return bounds != null && bounds.width() > 0 && bounds.height() > 0;
    }
}

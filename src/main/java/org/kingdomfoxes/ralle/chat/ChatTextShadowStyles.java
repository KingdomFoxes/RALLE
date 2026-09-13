package org.kingdomfoxes.ralle.chat;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** Shared live/capture policy for Partial-full's opaque offset shadow. */
public final class ChatTextShadowStyles {
    private static final int PARTIAL_FULL_SHADOW = 0xFF000000;
    private static final FontDescription WYNN_PILL = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("minecraft", "banner/pill"));
    private static final FontDescription RALLE_STAR = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("ralle", "guild_rank_star"));

    private ChatTextShadowStyles() {}

    public static Style partialFull(Style style) {
        FontDescription font = style.getFont();
        if (WYNN_PILL.equals(font) || RALLE_STAR.equals(font)) {
            // The colored/background pass owns the badge silhouette's opaque shadow. The
            // overlapping black foreground/star pass must not emit a second displaced copy.
            return style.getColor() != null && style.getColor().getValue() == 0
                    ? style.withoutShadow()
                    : style.withShadowColor(PARTIAL_FULL_SHADOW);
        }
        return style.withShadowColor(PARTIAL_FULL_SHADOW);
    }

    public static FormattedCharSequence partialFull(FormattedCharSequence sequence) {
        return transform(sequence, ChatTextShadowStyles::partialFull);
    }

    public static FormattedCharSequence transform(
            FormattedCharSequence sequence,
            java.util.function.UnaryOperator<Style> styleTransform
    ) {
        return output -> sequence.accept((index, style, codePoint) ->
                output.accept(index, styleTransform.apply(style), codePoint));
    }
}

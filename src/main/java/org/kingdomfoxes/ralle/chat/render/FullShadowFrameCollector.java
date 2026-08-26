package org.kingdomfoxes.ralle.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Objects;

/** Collects all Full-shadow glyphs emitted by one public chat render call. */
public final class FullShadowFrameCollector {
    static final float OPACITY_SCALE = 0.25F;
    static final float MIN_OPACITY = 3.0F / 255.0F;
    /**
     * Wynncraft chat prefixes can begin with a -4 px spacing glyph before their
     * visible bitmap glyph. Keep one additional pixel for the shader halo.
     */
    static final int LEFT_PADDING = 5;
    static final int OTHER_PADDING = 2;
    private static final int MASK_COLOR = 0x000000;

    private final GuiGraphics graphics;
    private final Font font;
    private final int contentWidth;
    private final int localTop;
    private final int localBottom;
    private FullShadowMaskState state;

    public FullShadowFrameCollector(GuiGraphics graphics, Font font, int contentWidth, int localTop, int localBottom) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.font = Objects.requireNonNull(font, "font");
        this.contentWidth = Math.max(1, contentWidth);
        this.localTop = localTop;
        this.localBottom = Math.max(localTop + 1, localBottom);
    }

    /** Returns false only when the batched fallback should render this line. */
    public boolean record(FormattedCharSequence content, int x, int y, float opacity) {
        if (!FullShadowRenderingStrategy.compositorAvailable()) return false;

        float shadowOpacity = opacity * OPACITY_SCALE;
        if (shadowOpacity <= MIN_OPACITY) return true;

        try {
            if (state == null) {
                state = new FullShadowMaskState(
                        font,
                        new Matrix3x2f(graphics.pose()),
                        -LEFT_PADDING,
                        localTop - OTHER_PADDING,
                        contentWidth + OTHER_PADDING,
                        localBottom + OTHER_PADDING
                );
                graphics.guiRenderState.submitPicturesInPictureState(state);
            }
            state.addLine(visualShadow(content), x, y, shadowOpacity);
            return true;
        } catch (Throwable failure) {
            FullShadowRenderingStrategy.disableCompositor("collecting chat glyphs", failure);
            return false;
        }
    }

    static FormattedCharSequence visualShadow(FormattedCharSequence sequence) {
        return output -> sequence.accept((index, style, codePoint) ->
                output.accept(index, visualShadowStyle(style), codePoint)
        );
    }

    static Style visualShadowStyle(Style style) {
        return style.withColor(MASK_COLOR)
                .withoutShadow()
                .withClickEvent(null)
                .withHoverEvent(null)
                .withInsertion(null);
    }
}

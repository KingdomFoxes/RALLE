package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.kingdomfoxes.ralle.chat.render.FullShadowFrameCollector;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class ChatGraphicsTransform implements ChatComponent.ChatGraphicsAccess {
    private static final int WRAPPED_FULL_TEXT_COLOR = 0x000000;
    private static final float WRAPPED_FULL_OPACITY_SCALE = 0.25F;
    private static final float MIN_WRAPPED_FULL_PASS_OPACITY = 3.0F / 255.0F;

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final Font font;
    private final int contentWidth;
    private final ChatBehaviorService.HorizontalAlignment alignment;
    private final ChatBehaviorService.TextShadow shadow;
    private final int messageOffset;
    private final boolean renderVisualShadowPasses;
    private final FullShadowFrameCollector fullShadowCollector;
    private int lastMessageOffset;

    private ChatGraphicsTransform(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            int messageOffset,
            boolean renderVisualShadowPasses,
            FullShadowFrameCollector fullShadowCollector
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.font = Objects.requireNonNull(font, "font");
        this.contentWidth = contentWidth;
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        this.shadow = Objects.requireNonNull(shadow, "shadow");
        this.messageOffset = messageOffset;
        this.renderVisualShadowPasses = renderVisualShadowPasses;
        this.fullShadowCollector = fullShadowCollector;
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow
    ) {
        return wrap(delegate, font, contentWidth, alignment, shadow, 0, true, null);
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            boolean renderVisualShadowPasses
    ) {
        return wrap(delegate, font, contentWidth, alignment, shadow, 0, renderVisualShadowPasses, null);
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            boolean renderVisualShadowPasses,
            FullShadowFrameCollector fullShadowCollector
    ) {
        return wrap(
                delegate,
                font,
                contentWidth,
                alignment,
                shadow,
                0,
                renderVisualShadowPasses,
                fullShadowCollector
        );
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            int messageOffset,
            boolean renderVisualShadowPasses,
            FullShadowFrameCollector fullShadowCollector
    ) {
        if (alignment == ChatBehaviorService.HorizontalAlignment.LEFT
                && shadow == ChatBehaviorService.TextShadow.VANILLA
                && messageOffset == 0) {
            return delegate;
        }
        return new ChatGraphicsTransform(
                delegate,
                font,
                contentWidth,
                alignment,
                shadow,
                messageOffset,
                renderVisualShadowPasses,
                fullShadowCollector
        );
    }

    static FormattedCharSequence applyShadow(
            FormattedCharSequence sequence,
            ChatBehaviorService.TextShadow shadow
    ) {
        if (shadow == ChatBehaviorService.TextShadow.VANILLA) return sequence;
        return output -> sequence.accept((index, style, codePoint) ->
                output.accept(index, shadowStyle(style, shadow), codePoint)
        );
    }

    static Style shadowStyle(Style style, ChatBehaviorService.TextShadow shadow) {
        return switch (shadow) {
            case NONE -> style.withoutShadow();
            case VANILLA -> style;
            case PARTIAL_FULL -> ChatTextShadowStyles.partialFull(style);
            case FULL -> style.withoutShadow();
        };
    }

    static Style wrappedFullShadowStyle(Style style) {
        return style.withColor(WRAPPED_FULL_TEXT_COLOR)
                .withoutShadow()
                .withClickEvent(null)
                .withHoverEvent(null)
                .withInsertion(null);
    }

    static boolean renderWrappedFull(
            ChatComponent.ChatGraphicsAccess graphics,
            int y,
            float opacity,
            FormattedCharSequence content,
            boolean renderVisualShadowPasses
    ) {
        var mainContent = applyShadow(content, ChatBehaviorService.TextShadow.FULL);
        if (renderVisualShadowPasses) {
            float shadowOpacity = opacity * WRAPPED_FULL_OPACITY_SCALE;
            if (shadowOpacity > MIN_WRAPPED_FULL_PASS_OPACITY) {
                var shadowContent = transform(content, ChatGraphicsTransform::wrappedFullShadowStyle);
                graphics.handleMessage(y, shadowOpacity, new BatchedFullShadowSequence(shadowContent));
            }
        }
        return graphics.handleMessage(y, opacity, mainContent);
    }

    private static FormattedCharSequence transform(
            FormattedCharSequence sequence,
            UnaryOperator<Style> styleTransform
    ) {
        return output -> sequence.accept((index, style, codePoint) ->
                output.accept(index, styleTransform.apply(style), codePoint)
        );
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> consumer) {
        delegate.updatePose(consumer);
    }

    @Override
    public void fill(int left, int top, int right, int bottom, int color) {
        delegate.fill(left, top, right, bottom, color);
    }

    @Override
    public boolean handleMessage(int y, float opacity, FormattedCharSequence content) {
        lastMessageOffset = ChatRenderLayout.horizontalOffset(alignment, contentWidth, font.width(content))
                + messageOffset;

        if (shadow == ChatBehaviorService.TextShadow.FULL
                && renderVisualShadowPasses
                && fullShadowCollector != null
                && fullShadowCollector.record(content, lastMessageOffset, y, opacity)) {
            return renderMainText(y, opacity, content);
        }

        return renderAtCurrentOffset(() -> renderMessage(y, opacity, content));
    }

    private boolean renderMainText(int y, float opacity, FormattedCharSequence content) {
        return renderAtCurrentOffset(() -> delegate.handleMessage(
                y,
                opacity,
                applyShadow(content, ChatBehaviorService.TextShadow.FULL)
        ));
    }

    private boolean renderAtCurrentOffset(java.util.function.BooleanSupplier renderer) {
        if (lastMessageOffset == 0) return renderer.getAsBoolean();

        delegate.updatePose(matrix -> matrix.translate(lastMessageOffset, 0));
        try {
            return renderer.getAsBoolean();
        } finally {
            delegate.updatePose(matrix -> matrix.translate(-lastMessageOffset, 0));
        }
    }

    private boolean renderMessage(int y, float opacity, FormattedCharSequence content) {
        if (shadow == ChatBehaviorService.TextShadow.FULL) {
            return renderWrappedFull(delegate, y, opacity, content, renderVisualShadowPasses);
        }
        return delegate.handleMessage(y, opacity, applyShadow(content, shadow));
    }

    @Override
    public void handleTag(int left, int top, int right, int bottom, float opacity, GuiMessageTag tag) {
        delegate.handleTag(left, top, right, bottom, opacity, tag);
    }

    @Override
    public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        if (alignment != ChatBehaviorService.HorizontalAlignment.RIGHT) {
            delegate.handleTagIcon(x, y, hovered, tag, icon);
            return;
        }

        int offset = Math.max(0, contentWidth - icon.width - 2 - x);
        delegate.updatePose(matrix -> matrix.translate(offset, 0));
        try {
            delegate.handleTagIcon(x, y, hovered, tag, icon);
        } finally {
            delegate.updatePose(matrix -> matrix.translate(-offset, 0));
        }
    }
}

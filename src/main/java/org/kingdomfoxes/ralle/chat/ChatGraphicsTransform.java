package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class ChatGraphicsTransform implements ChatComponent.ChatGraphicsAccess {
    private static final int PARTIAL_FULL_SHADOW_COLOR = 0xFF000000;
    private static final int WRAPPED_FULL_TEXT_COLOR = 0x000000;
    private static final float WRAPPED_FULL_OPACITY_SCALE = 0.25F;
    private static final float MIN_WRAPPED_FULL_PASS_OPACITY = 3.0F / 255.0F;
    private static final List<ShadowOffset> WRAPPED_FULL_OFFSETS = createWrappedFullOffsets();

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final Font font;
    private final int contentWidth;
    private final ChatBehaviorService.HorizontalAlignment alignment;
    private final ChatBehaviorService.TextShadow shadow;
    private final boolean renderVisualShadowPasses;
    private int lastMessageOffset;

    private ChatGraphicsTransform(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            boolean renderVisualShadowPasses
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.font = Objects.requireNonNull(font, "font");
        this.contentWidth = contentWidth;
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        this.shadow = Objects.requireNonNull(shadow, "shadow");
        this.renderVisualShadowPasses = renderVisualShadowPasses;
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow
    ) {
        return wrap(delegate, font, contentWidth, alignment, shadow, true);
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow,
            boolean renderVisualShadowPasses
    ) {
        if (alignment == ChatBehaviorService.HorizontalAlignment.LEFT
                && shadow == ChatBehaviorService.TextShadow.VANILLA) {
            return delegate;
        }
        return new ChatGraphicsTransform(
                delegate,
                font,
                contentWidth,
                alignment,
                shadow,
                renderVisualShadowPasses
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
            case PARTIAL_FULL -> style.withShadowColor(PARTIAL_FULL_SHADOW_COLOR);
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
                for (var offset : WRAPPED_FULL_OFFSETS) {
                    graphics.updatePose(matrix -> matrix.translate(offset.x(), offset.y()));
                    try {
                        graphics.handleMessage(y, shadowOpacity, shadowContent);
                    } finally {
                        graphics.updatePose(matrix -> matrix.translate(-offset.x(), -offset.y()));
                    }
                }
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

    private static List<ShadowOffset> createWrappedFullOffsets() {
        var offsets = new ArrayList<ShadowOffset>(16);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                if (x * x == y * y) continue;
                offsets.add(new ShadowOffset(x / 2.0F, y / 2.0F));
            }
        }
        return List.copyOf(offsets);
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
        lastMessageOffset = ChatRenderLayout.horizontalOffset(alignment, contentWidth, font.width(content));
        if (lastMessageOffset == 0) return renderMessage(y, opacity, content);

        delegate.updatePose(matrix -> matrix.translate(lastMessageOffset, 0));
        try {
            return renderMessage(y, opacity, content);
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

    private record ShadowOffset(float x, float y) {}
}

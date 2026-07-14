package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Objects;
import java.util.function.Consumer;

public final class ChatGraphicsTransform implements ChatComponent.ChatGraphicsAccess {
    private static final int FULL_SHADOW_COLOR = 0xFF000000;

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final Font font;
    private final int contentWidth;
    private final ChatBehaviorService.HorizontalAlignment alignment;
    private final ChatBehaviorService.TextShadow shadow;
    private int lastMessageOffset;

    private ChatGraphicsTransform(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.font = Objects.requireNonNull(font, "font");
        this.contentWidth = contentWidth;
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        this.shadow = Objects.requireNonNull(shadow, "shadow");
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            Font font,
            int contentWidth,
            ChatBehaviorService.HorizontalAlignment alignment,
            ChatBehaviorService.TextShadow shadow
    ) {
        if (alignment == ChatBehaviorService.HorizontalAlignment.LEFT
                && shadow == ChatBehaviorService.TextShadow.VANILLA) {
            return delegate;
        }
        return new ChatGraphicsTransform(delegate, font, contentWidth, alignment, shadow);
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
            case FULL -> style.withShadowColor(FULL_SHADOW_COLOR);
        };
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
        var transformed = applyShadow(content, shadow);
        lastMessageOffset = ChatRenderLayout.horizontalOffset(alignment, contentWidth, font.width(content));
        if (lastMessageOffset == 0) return delegate.handleMessage(y, opacity, transformed);

        delegate.updatePose(matrix -> matrix.translate(lastMessageOffset, 0));
        try {
            return delegate.handleMessage(y, opacity, transformed);
        } finally {
            delegate.updatePose(matrix -> matrix.translate(-lastMessageOffset, 0));
        }
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

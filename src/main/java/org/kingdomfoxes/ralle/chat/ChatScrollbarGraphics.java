package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Suppresses only vanilla's focused-chat scrollbar fills. All input and scrolling
 * remain owned by Minecraft's chat component.
 */
public final class ChatScrollbarGraphics implements ChatComponent.ChatGraphicsAccess {
    private static final int VANILLA_SCROLLBAR_LEFT_MARGIN = 4;
    private static final int VANILLA_SCROLLBAR_WIDTH = 2;

    private final ChatComponent.ChatGraphicsAccess delegate;
    private final int scrollbarLeft;

    private ChatScrollbarGraphics(ChatComponent.ChatGraphicsAccess delegate, int contentWidth) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.scrollbarLeft = contentWidth + VANILLA_SCROLLBAR_LEFT_MARGIN;
    }

    public static ChatComponent.ChatGraphicsAccess wrap(
            ChatComponent.ChatGraphicsAccess delegate,
            int contentWidth,
            boolean hidden
    ) {
        return hidden ? new ChatScrollbarGraphics(delegate, contentWidth) : delegate;
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> consumer) {
        delegate.updatePose(consumer);
    }

    @Override
    public void fill(int left, int top, int right, int bottom, int color) {
        int minimumX = Math.min(left, right);
        int maximumX = Math.max(left, right);
        if (minimumX >= scrollbarLeft && maximumX <= scrollbarLeft + VANILLA_SCROLLBAR_WIDTH) return;
        delegate.fill(left, top, right, bottom, color);
    }

    @Override
    public boolean handleMessage(int y, float opacity, FormattedCharSequence content) {
        return delegate.handleMessage(y, opacity, content);
    }

    @Override
    public void handleTag(int left, int top, int right, int bottom, float opacity, GuiMessageTag tag) {
        delegate.handleTag(left, top, right, bottom, opacity, tag);
    }

    @Override
    public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        delegate.handleTagIcon(x, y, hovered, tag, icon);
    }
}

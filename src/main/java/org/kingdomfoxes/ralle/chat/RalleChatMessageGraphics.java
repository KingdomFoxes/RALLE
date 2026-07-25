package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

import java.util.Objects;
import java.util.function.Consumer;

/** Suppresses vanilla's narrow tag indicator for RALLE's full-width message panel. */
public final class RalleChatMessageGraphics implements ChatComponent.ChatGraphicsAccess {
    private final ChatComponent.ChatGraphicsAccess delegate;

    private RalleChatMessageGraphics(ChatComponent.ChatGraphicsAccess delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static ChatComponent.ChatGraphicsAccess wrap(ChatComponent.ChatGraphicsAccess delegate) {
        return new RalleChatMessageGraphics(delegate);
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
        return delegate.handleMessage(y, opacity, content);
    }

    @Override
    public void handleTag(int left, int top, int right, int bottom, float opacity, GuiMessageTag tag) {
        if (!RalleChatMessages.owns(tag)) delegate.handleTag(left, top, right, bottom, opacity, tag);
    }

    @Override
    public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        if (!RalleChatMessages.owns(tag)) delegate.handleTagIcon(x, y, hovered, tag, icon);
    }
}

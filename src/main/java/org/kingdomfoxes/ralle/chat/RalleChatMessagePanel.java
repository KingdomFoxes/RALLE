package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Mth;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotTokens;

import java.util.List;

/** Draws the screenshot-selection fill and solid outline behind tagged sample messages. */
public final class RalleChatMessagePanel {
    private RalleChatMessagePanel() {}

    public static void render(
            ChatComponent.ChatGraphicsAccess graphics,
            List<GuiMessage.Line> lines,
            int scroll,
            int linesPerPage,
            int lineHeight,
            int localBottom,
            int visualWidth,
            double chatScale,
            ChatBehaviorService.MessageDirection direction,
            int guiTick,
            boolean focused
    ) {
        int visibleLineCount = Math.min(Math.max(0, lines.size() - scroll), linesPerPage);
        if (visibleLineCount == 0) return;

        int left = -4;
        int right = Math.max(left + 1, (int) Math.ceil(visualWidth / chatScale) - 4);
        int viewportTop = localBottom - linesPerPage * lineHeight;
        int padding = Math.max(1, (int) Math.ceil(ChatScreenshotTokens.VERTICAL_PADDING / chatScale));
        Panel panel = null;

        for (int offset = 0; offset < visibleLineCount; offset++) {
            var line = lines.get(scroll + offset);
            if (line.endOfEntry() && panel != null) {
                draw(graphics, panel, left, right, viewportTop, localBottom, padding);
                panel = null;
            }

            if (!RalleChatMessages.owns(line.tag())) {
                if (panel != null) {
                    draw(graphics, panel, left, right, viewportTop, localBottom, padding);
                    panel = null;
                }
                continue;
            }

            int renderedIndex = ChatRenderLayout.lineIndex(direction, offset, linesPerPage);
            int bottom = localBottom - renderedIndex * lineHeight;
            int top = bottom - lineHeight;
            float opacity = focused ? 1.0F : messageOpacity(guiTick - line.addedTime());
            panel = panel == null
                    ? new Panel(top, bottom, opacity)
                    : panel.include(top, bottom, opacity);
        }

        if (panel != null) draw(graphics, panel, left, right, viewportTop, localBottom, padding);
    }

    private static float messageOpacity(int age) {
        double fade = 1.0 - age / 200.0;
        fade = Mth.clamp(fade * 10.0, 0.0, 1.0);
        return (float) (fade * fade);
    }

    private static void draw(
            ChatComponent.ChatGraphicsAccess graphics,
            Panel panel,
            int left,
            int right,
            int viewportTop,
            int viewportBottom,
            int padding
    ) {
        if (panel.opacity <= 0.0F) return;
        int top = Math.max(viewportTop, panel.top - padding);
        int bottom = Math.min(viewportBottom, panel.bottom + padding);
        if (bottom <= top) return;

        graphics.fill(left, top, right, bottom,
                ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_FILL, panel.opacity));
        int gold = ChatScreenshotTokens.withOpacity(ChatScreenshotTokens.SELECTION_GOLD, panel.opacity);
        graphics.fill(left, top, right, top + 1, gold);
        graphics.fill(left, bottom - 1, right, bottom, gold);
        graphics.fill(left, top, left + 1, bottom, gold);
        graphics.fill(right - 1, top, right, bottom, gold);
    }

    private record Panel(int top, int bottom, float opacity) {
        Panel include(int nextTop, int nextBottom, float nextOpacity) {
            return new Panel(
                    Math.min(top, nextTop),
                    Math.max(bottom, nextBottom),
                    Math.max(opacity, nextOpacity)
            );
        }
    }
}

package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.kingdomfoxes.ralle.chat.screenshot.ChatScreenshotTokens;

/**
 * Isolated presentation prototype for RALLE-authored chat messages.
 *
 * <p>Only the temporary {@code /ralle text} sample uses this path. Existing
 * notifications deliberately remain unchanged until the presentation is approved.</p>
 */
public final class RalleChatMessages {
    private static final int PREFIX_GOLD = 0xF2B84B;
    private static final GuiMessageTag TAG = new GuiMessageTag(
            ChatScreenshotTokens.SELECTION_GOLD & 0x00FFFFFF,
            null,
            null,
            "RALLE"
    );

    private RalleChatMessages() {}

    public static void postExample(Minecraft minecraft) {
        minecraft.gui.getChat().addMessage(example(), null, TAG);
    }

    public static Component example() {
        var interactive = Component.literal("dolor sit amet")
                .withStyle(style -> style
                        .withColor(ChatScreenshotTokens.SELECTION_GOLD & 0x00FFFFFF)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/ralle settings"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open RALLE settings"))));

        return Component.empty()
                .append(Component.literal("RALLE: ").withStyle(style -> style
                        .withColor(PREFIX_GOLD)
                        .withBold(true)))
                .append(Component.literal("Lorem ipsum "))
                .append(interactive)
                .append(Component.literal(", consectetur.\n"))
                .append(Component.literal("Sed do eiusmod tempor incididunt ut labore et dolore.\n"))
                .append(Component.literal("Ut enim ad minim veniam, quis nostrud exercitation."));
    }

    public static boolean owns(GuiMessageTag tag) {
        return tag == TAG;
    }

    static GuiMessageTag tag() {
        return TAG;
    }
}

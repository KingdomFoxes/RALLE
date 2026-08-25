package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

import java.util.Optional;

/**
 * Produces an indicator-free render view while retaining the authoritative chat
 * message, including its signature and tag, in Minecraft's message history.
 */
public final class ChatSystemIndicators {
    private ChatSystemIndicators() {}

    public static GuiMessage withoutIndicator(GuiMessage message, boolean removeIndicator) {
        if (!removeIndicator || message.tag() == null) return message;
        return new GuiMessage(
                message.addedTime(),
                restoreFirstRunVanillaShadow(message.content()),
                message.signature(),
                null
        );
    }

    private static Component restoreFirstRunVanillaShadow(Component content) {
        Style firstStyle = content.visit(
                (style, text) -> text.isEmpty() ? Optional.empty() : Optional.of(style),
                Style.EMPTY
        ).orElse(null);
        if (firstStyle == null || !Integer.valueOf(Style.NO_SHADOW).equals(firstStyle.getShadowColor())) {
            return content;
        }

        var restored = Component.empty();
        boolean first = true;
        for (Component run : content.toFlatList()) {
            if (first) {
                restored.append(run.copy().setStyle(run.getStyle().withShadowColor(vanillaShadowColor(run.getStyle()))));
                first = false;
            } else {
                restored.append(run);
            }
        }
        return restored;
    }

    private static int vanillaShadowColor(Style style) {
        int textColor = style.getColor() == null ? 0xFFFFFF : style.getColor().getValue();
        return ARGB.scaleRGB(ARGB.opaque(textColor), 0.25F);
    }
}

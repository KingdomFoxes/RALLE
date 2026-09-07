package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;

import java.util.Optional;

/** Fixed local-account example used by the settings color picker. */
public final class QueueAttributionPreview {
    private QueueAttributionPreview() {}

    public static Component preview(String self, HighlightStyle style, long timeMillis) {
        var original = Component.empty()
                .append(Component.literal("Detlas").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" (High)").withStyle(ChatFormatting.RED))
                .append(Component.literal(" 2:32").withStyle(ChatFormatting.AQUA));
        return QueueAttributionFormatter.format(original, Optional.of(self), self,
                Component.translatable("ralle.war.queue.unknown"), style, timeMillis);
    }
}

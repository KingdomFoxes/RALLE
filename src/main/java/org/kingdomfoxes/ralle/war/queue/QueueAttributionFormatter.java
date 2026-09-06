package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Objects;
import java.util.Optional;

/** Adds only the approved name and arrow prefix; the original timer component is appended unchanged. */
public final class QueueAttributionFormatter {
    private QueueAttributionFormatter() {}

    public static Component format(
            Component original,
            Optional<String> attributedIgn,
            String localIgn,
            Component unknownLabel
    ) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(attributedIgn, "attributedIgn");
        Objects.requireNonNull(unknownLabel, "unknownLabel");
        MutableComponent name = attributedIgn
                .<MutableComponent>map(ign -> Component.literal(ign).withStyle(
                        localIgn != null && ign.equalsIgnoreCase(localIgn)
                                ? ChatFormatting.BLUE : ChatFormatting.GRAY))
                .orElseGet(() -> unknownLabel.copy().withStyle(ChatFormatting.GRAY));
        return Component.empty()
                .append(name)
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(original);
    }
}

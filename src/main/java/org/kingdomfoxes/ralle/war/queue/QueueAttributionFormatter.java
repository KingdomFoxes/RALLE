package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.kingdomfoxes.ralle.war.consumables.HighlightStyle;
import org.kingdomfoxes.ralle.war.consumables.ConsumableSlotBorder;

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
        return format(original, attributedIgn, localIgn, unknownLabel, 0x5555FF);
    }

    public static Component format(Component original, Optional<String> attributedIgn, String localIgn,
                                   Component unknownLabel, int selfRgb) {
        return format(original, attributedIgn, localIgn, unknownLabel, new HighlightStyle(selfRgb, false), 0L);
    }

    public static Component format(Component original, Optional<String> attributedIgn, String localIgn,
                                   Component unknownLabel, HighlightStyle selfStyle) {
        return format(original, attributedIgn, localIgn, unknownLabel, selfStyle, System.currentTimeMillis());
    }

    public static Component format(Component original, Optional<String> attributedIgn, String localIgn,
                                   Component unknownLabel, HighlightStyle selfStyle, long timeMillis) {
        return format(original, attributedIgn, localIgn, unknownLabel, selfStyle, timeMillis,
                ign -> KofRankColors.FALLBACK);
    }

    public static Component format(Component original, Optional<String> attributedIgn, String localIgn,
                                   Component unknownLabel, HighlightStyle selfStyle, long timeMillis,
                                   java.util.function.ToIntFunction<String> otherColor) {
        Objects.requireNonNull(selfStyle, "selfStyle");
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(attributedIgn, "attributedIgn");
        Objects.requireNonNull(unknownLabel, "unknownLabel");
        MutableComponent name = attributedIgn
                .<MutableComponent>map(ign -> localIgn != null && ign.equalsIgnoreCase(localIgn)
                        ? selfName(ign, selfStyle, timeMillis)
                        : Component.literal(ign).withStyle(style -> style.withColor(otherColor.applyAsInt(ign))))
                .orElseGet(() -> unknownLabel.copy().withStyle(ChatFormatting.GRAY));
        return Component.empty()
                .append(name)
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(original);
    }
    private static MutableComponent selfName(String ign, HighlightStyle style, long timeMillis) {
        if (!style.rainbow()) return Component.literal(ign).withStyle(s -> s.withColor(style.rgb()));
        var name = Component.empty();
        for (int i = 0; i < ign.length(); i++) {
            int rgb = ConsumableSlotBorder.rainbowColor(i / (float) Math.max(1, ign.length()), timeMillis) & 0xFFFFFF;
            name.append(Component.literal(ign.substring(i, i + 1)).withStyle(s -> s.withColor(rgb)));
        }
        return name;
    }
}

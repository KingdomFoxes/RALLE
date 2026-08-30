package org.kingdomfoxes.ralle.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** RALLE-owned timestamp formatting and formatted-sequence composition. */
public final class ChatTimestamps {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    private ChatTimestamps() {}

    public static Component prefix(LocalDateTime receiveTime) {
        Objects.requireNonNull(receiveTime, "receiveTime");
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(TIME_FORMAT.format(receiveTime)).withStyle(ChatFormatting.GRAY))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static FormattedCharSequence prepend(FormattedCharSequence prefix, FormattedCharSequence content) {
        return FormattedCharSequence.composite(
                Objects.requireNonNull(prefix, "prefix"),
                Objects.requireNonNull(content, "content")
        );
    }
}

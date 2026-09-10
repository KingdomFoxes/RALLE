package org.kingdomfoxes.ralle.chat.identity;

import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Recognizes Wynncraft's private-message envelope before display rewrites. */
public final class DirectMessageIdentityResolver {
    private static final String INDICATOR = "\uDAFF\uDFFC\uE007\uDAFF\uDFFF\uE002\uDAFF\uDFFE ";
    private static final String CONTINUATION = "\uDAFF\uDFFC\uE001\uDB00\uDC06 ";

    private DirectMessageIdentityResolver() {}

    public static Optional<String> incomingSender(Component message, String localIgn) {
        if (message == null || localIgn == null) return Optional.empty();
        var flattened = GuildSpeakerIdentity.flatten(message);
        if (flattened == null || flattened.segments().stream()
                .filter(segment -> segment.start() == 0 && segment.end() > 0)
                .noneMatch(segment -> segment.style().getColor() != null
                        && segment.style().getColor().getValue() == 0xDDCC99)) return Optional.empty();
        String text = flattened.text();
        int start = text.startsWith(INDICATOR) ? INDICATOR.length()
                : text.startsWith(CONTINUATION) ? CONTINUATION.length() : -1;
        if (start < 0) return Optional.empty();
        int arrow = text.indexOf(" \uE003 ", start);
        int colon = arrow < 0 ? -1 : text.indexOf(": ", arrow + 3);
        if (colon < 0) return Optional.empty();
        var sender = identity(message, text, start, arrow);
        var recipient = identity(message, text, arrow + 3, colon);
        return recipient.filter(name -> name.equalsIgnoreCase(localIgn)).isPresent()
                ? sender.filter(name -> !name.equalsIgnoreCase(localIgn)) : Optional.empty();
    }

    private static Optional<String> identity(Component message, String text, int start, int end) {
        // Rank/banner pills end in Wynncraft's positive-two spacing glyph.
        int pillEnd = text.lastIndexOf("\uDB00\uDC02", end - 1);
        if (pillEnd >= start) start = pillEnd + 2;
        while (start < end && text.charAt(start) == ' ') start++;
        if (end <= start || end - start > 64) return Optional.empty();
        return GuildSpeakerIdentity.resolve(message, text.substring(start, end), start, end);
    }
}

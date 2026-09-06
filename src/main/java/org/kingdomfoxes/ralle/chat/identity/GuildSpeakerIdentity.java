package org.kingdomfoxes.ralle.chat.identity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Resolves only hover metadata applying to an already-identified guild speaker span. */
public final class GuildSpeakerIdentity {
    static final int MAX_COMPONENTS = 256;
    static final int MAX_TEXT_LENGTH = 4096;
    private static final Pattern IGN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern NICKNAME_HOVER = Pattern.compile(
            "^(.{1,64})['’]s real name is ([A-Za-z0-9_]{1,16})$"
    );

    private GuildSpeakerIdentity() {}

    public static Optional<String> resolve(
            Component message,
            String displayName,
            int speakerStart,
            int speakerEnd
    ) {
        if (message == null || displayName == null || speakerStart < 0 || speakerEnd <= speakerStart) {
            return Optional.empty();
        }
        var flattened = flatten(message);
        if (flattened == null || speakerEnd > flattened.text().length()
                || !flattened.text().regionMatches(speakerStart, displayName, 0, displayName.length())) {
            return Optional.empty();
        }

        String resolved = null;
        boolean sawSpeakerHover = false;
        for (var segment : flattened.segments()) {
            if (segment.end() <= speakerStart || segment.start() >= speakerEnd) continue;
            if (!(segment.style().getHoverEvent() instanceof HoverEvent.ShowText showText)) continue;
            sawSpeakerHover = true;
            String hoverText = boundedText(showText.value());
            if (hoverText == null) return Optional.empty();
            var match = NICKNAME_HOVER.matcher(hoverText);
            if (!match.matches() || !match.group(1).equals(displayName)) return Optional.empty();
            if (resolved != null && !resolved.equalsIgnoreCase(match.group(2))) return Optional.empty();
            resolved = match.group(2);
        }
        if (sawSpeakerHover) return Optional.ofNullable(resolved);
        return IGN.matcher(displayName).matches() ? Optional.of(displayName) : Optional.empty();
    }

    static Flattened flatten(Component component) {
        var text = new StringBuilder();
        var segments = new ArrayList<Segment>();
        int[] count = {0};
        boolean[] invalid = {false};
        component.visit((style, value) -> {
            if (++count[0] > MAX_COMPONENTS || text.length() + value.length() > MAX_TEXT_LENGTH) {
                invalid[0] = true;
                return Optional.of(false);
            }
            int start = text.length();
            text.append(value);
            segments.add(new Segment(start, text.length(), style));
            return Optional.empty();
        }, Style.EMPTY);
        return invalid[0] ? null : new Flattened(text.toString(), List.copyOf(segments));
    }

    private static String boundedText(Component component) {
        var flattened = flatten(component);
        return flattened == null ? null : flattened.text();
    }

    record Flattened(String text, List<Segment> segments) {}
    record Segment(int start, int end, Style style) {}
}

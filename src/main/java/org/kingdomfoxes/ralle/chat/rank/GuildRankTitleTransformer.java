package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Replaces only Wynncraft's two rank-title glyph passes for the identified guild-chat speaker. */
final class GuildRankTitleTransformer {
    private static final int FOREGROUND_A = 0xE000;
    private static final int BACKGROUND_A = 0xE030;
    private static final int LEFT_CAP = 0xE060;
    private static final int SPACE_FILL = 0xE061;
    private static final int RIGHT_CAP = 0xE062;
    private static final int SPACING_ZERO = 0xD0000;
    private static final int NEGATIVE_ONE = SPACING_ZERO - 1;
    private static final int POSITIVE_TWO = SPACING_ZERO + 2;
    private static final String FOREGROUND_TERMINATOR = Character.toString(POSITIVE_TWO);
    private static final int MAX_HOVER_COMPONENTS = 256;
    private static final Pattern NICKNAME_HOVER = Pattern.compile(
            "^(.{1,64})['’]s real name is ([A-Za-z0-9_]{1,16})$"
    );
    private static final Map<String, GuildRankGlyphs> WYNN_RANK_GLYPHS = Map.of(
            "OWNER", encode("OWNER"),
            "CHIEF", encode("CHIEF"),
            "STRATEGIST", encode("STRATEGIST"),
            "CAPTAIN", encode("CAPTAIN"),
            "RECRUITER", encode("RECRUITER"),
            "RECRUIT", encode("RECRUIT")
    );

    private GuildRankTitleTransformer() {}

    static Component apply(Component message, GuildRankSnapshot snapshot) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.empty()) return message;

        Match match = findMatch(message, snapshot);
        if (match == null) return message;
        return transform(message, match.oldGlyphs(), match.newGlyphs(), match.indicator(),
                match.messageStart(), new int[]{0});
    }

    private static Match findMatch(Component message, GuildRankSnapshot snapshot) {
        String text = message.getString();
        int terminatorIndex = text.indexOf(FOREGROUND_TERMINATOR);
        while (terminatorIndex >= 0) {
            int foregroundEnd = terminatorIndex + FOREGROUND_TERMINATOR.length();
            String oldTitle = decodeForegroundBefore(text, foregroundEnd);
            if (oldTitle != null) {
                GuildRankGlyphs oldGlyphs = WYNN_RANK_GLYPHS.get(oldTitle);
                if (oldGlyphs == null) oldGlyphs = encode(oldTitle);
                String oldForeground = oldGlyphs.foreground();
                int foregroundStart = foregroundEnd - oldForeground.length();
                String oldBackground = oldGlyphs.background();
                int backgroundStart = foregroundStart - oldBackground.length();
                if (backgroundStart >= 0
                        && text.regionMatches(backgroundStart, oldBackground, 0, oldBackground.length())
                        && text.regionMatches(foregroundStart, oldForeground, 0, oldForeground.length())) {
                    Speaker speaker = speakerAfter(text, foregroundEnd);
                    Optional<String> title = speaker == null ? Optional.empty() : snapshot.titleFor(speaker.displayName());
                    if (title.isEmpty() && speaker != null) {
                        String realName = hoveredRealName(message, speaker.displayName(), new int[]{MAX_HOVER_COMPONENTS});
                        if (realName != null) title = snapshot.titleFor(realName);
                    }
                    if (title.isPresent()) {
                        GuildRankGlyphs newGlyphs = snapshot.glyphsFor(title.get());
                        return new Match(oldGlyphs, newGlyphs, indicatorBefore(text, backgroundStart),
                                speaker.messageStart());
                    }
                }
            }
            terminatorIndex = text.indexOf(FOREGROUND_TERMINATOR, foregroundEnd);
        }
        return null;
    }

    private static Speaker speakerAfter(String text, int foregroundEnd) {
        if (foregroundEnd >= text.length() || text.charAt(foregroundEnd) != ' ') return null;
        int nameStart = foregroundEnd + 1;
        int colon = text.indexOf(':', nameStart);
        if (colon < 0 || colon - nameStart > 64) return null;
        String displayName = text.substring(nameStart, colon);
        return displayName.isBlank() || displayName.indexOf('\n') >= 0
                ? null : new Speaker(displayName, colon + 1);
    }

    private static String hoveredRealName(Component component, String displayName, int[] remaining) {
        if (remaining[0]-- <= 0) return null;
        if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText showText) {
            var match = NICKNAME_HOVER.matcher(showText.value().getString());
            if (match.matches() && match.group(1).equals(displayName)) return match.group(2);
        }
        for (var sibling : component.getSiblings()) {
            String realName = hoveredRealName(sibling, displayName, remaining);
            if (realName != null) return realName;
        }
        return null;
    }

    private static String indicatorBefore(String text, int backgroundStart) {
        int cursor = backgroundStart;
        if (cursor > 0 && text.charAt(cursor - 1) == ' ') cursor--;
        int indicatorEnd = cursor;
        while (cursor > 0) {
            int codePoint = text.codePointBefore(cursor);
            if (!isIndicatorCodePoint(codePoint)) break;
            cursor -= Character.charCount(codePoint);
        }
        return cursor == indicatorEnd ? null : text.substring(cursor, indicatorEnd);
    }

    private static boolean isIndicatorCodePoint(int codePoint) {
        return codePoint >= 0xE000 && codePoint <= 0xF8FF
                || codePoint >= 0xC0000 && codePoint <= 0xDFFFF;
    }

    private static String decodeForegroundBefore(String text, int end) {
        if (end <= 0 || text.codePointBefore(end) != POSITIVE_TWO) return null;
        int cursor = end - Character.charCount(POSITIVE_TWO);
        var reversed = new StringBuilder();
        while (cursor > 0) {
            int codePoint = text.codePointBefore(cursor);
            if (codePoint >= FOREGROUND_A && codePoint < FOREGROUND_A + 26) {
                reversed.append((char) ('A' + codePoint - FOREGROUND_A));
            } else if (codePoint == ' ') {
                reversed.append(' ');
            } else {
                break;
            }
            cursor -= Character.charCount(codePoint);
        }
        if (reversed.isEmpty()) return null;
        return GuildRankSnapshot.normalizeTitle(reversed.reverse().toString());
    }

    private static Component transform(
            Component component,
            GuildRankGlyphs oldGlyphs,
            GuildRankGlyphs newGlyphs,
            String indicator,
            int messageStart,
            int[] offset
    ) {
        MutableComponent replacement = null;
        if (component.getContents() instanceof PlainTextContents.LiteralContents literal) {
            int localMessageStart = messageStart <= offset[0]
                    ? 0 : messageStart >= offset[0] + literal.text().length()
                    ? Integer.MAX_VALUE : messageStart - offset[0];
            replacement = replaceRuns(literal.text(), component.getStyle(),
                    oldGlyphs, newGlyphs, indicator, localMessageStart);
            offset[0] += literal.text().length();
        }

        boolean siblingChanged = false;
        var siblings = new Component[component.getSiblings().size()];
        for (int index = 0; index < siblings.length; index++) {
            var sibling = component.getSiblings().get(index);
            siblings[index] = transform(sibling, oldGlyphs, newGlyphs, indicator, messageStart, offset);
            siblingChanged |= siblings[index] != sibling;
        }
        if (replacement == null && !siblingChanged) return component;

        MutableComponent transformed = replacement == null ? component.plainCopy() : replacement;
        for (var sibling : siblings) transformed.append(sibling);
        return transformed;
    }

    private static MutableComponent replaceRuns(
            String text,
            Style style,
            GuildRankGlyphs oldGlyphs,
            GuildRankGlyphs newGlyphs,
            String indicator,
            int messageStart
    ) {
        if (indicator != null && text.equals(indicator)) {
            return Component.literal(indicator).withStyle(style.withColor(ChatFormatting.AQUA));
        }
        if (text.equals(oldGlyphs.background())) {
            return Component.literal(newGlyphs.background()).withStyle(style.withColor(ChatFormatting.AQUA));
        }
        if (text.equals(oldGlyphs.foreground())) {
            return Component.literal(newGlyphs.foreground()).withStyle(style);
        }

        int cursor = 0;
        boolean changed = false;
        // Keep the structural wrapper unstyled. Some Wynncraft messages place the pill inside a
        // larger white literal; retaining that color on the wrapper can win during downstream
        // flattening even though the emitted background child is explicitly aqua.
        var result = Component.empty();
        while (cursor < text.length()) {
            int backgroundIndex = text.indexOf(oldGlyphs.background(), cursor);
            int foregroundIndex = text.indexOf(oldGlyphs.foreground(), cursor);
            int indicatorIndex = indicator == null ? -1 : text.indexOf(indicator, cursor);
            int index = firstIndex(indicatorIndex, backgroundIndex, foregroundIndex);
            if (index < 0) break;
            if (index > cursor) appendOriginal(result, text, cursor, index, style, messageStart);
            if (index == indicatorIndex) {
                result.append(Component.literal(indicator).withStyle(style.withColor(ChatFormatting.AQUA)));
                cursor = index + indicator.length();
            } else if (index == backgroundIndex) {
                result.append(Component.literal(newGlyphs.background()).withStyle(style.withColor(ChatFormatting.AQUA)));
                cursor = index + oldGlyphs.background().length();
            } else {
                result.append(Component.literal(newGlyphs.foreground()).withStyle(style));
                cursor = index + oldGlyphs.foreground().length();
            }
            changed = true;
        }
        if (!changed && messageStart == Integer.MAX_VALUE) return null;
        if (cursor < text.length()) appendOriginal(result, text, cursor, text.length(), style, messageStart);
        return result;
    }

    private static void appendOriginal(
            MutableComponent result, String text, int start, int end, Style style, int messageStart
    ) {
        int split = Math.max(start, Math.min(end, messageStart));
        if (split > start) result.append(Component.literal(text.substring(start, split)).withStyle(style));
        if (split < end) {
            result.append(Component.literal(text.substring(split, end)).withStyle(guildMessageStyle(style)));
        }
    }

    private static Style guildMessageStyle(Style style) {
        var color = style.getColor();
        return color == null || color.getValue() == ChatFormatting.WHITE.getColor()
                ? style.withColor(ChatFormatting.AQUA) : style;
    }

    private static int firstIndex(int... indices) {
        int first = -1;
        for (int index : indices) if (index >= 0 && (first < 0 || index < first)) first = index;
        return first;
    }

    static String background(String title) {
        int foregroundWidth = 0;
        var result = new StringBuilder().appendCodePoint(LEFT_CAP).appendCodePoint(NEGATIVE_ONE);
        for (char character : title.toCharArray()) {
            result.appendCodePoint(character == ' ' ? SPACE_FILL : BACKGROUND_A + character - 'A');
            result.appendCodePoint(NEGATIVE_ONE);
            foregroundWidth += advance(character);
        }
        return result.appendCodePoint(RIGHT_CAP)
                .appendCodePoint(SPACING_ZERO - (foregroundWidth + 2)).toString();
    }

    static String foreground(String title) {
        var result = new StringBuilder();
        for (char character : title.toCharArray()) {
            if (character == ' ') result.append(' ');
            else result.appendCodePoint(FOREGROUND_A + character - 'A');
        }
        return result.appendCodePoint(POSITIVE_TWO).toString();
    }

    static GuildRankGlyphs encode(String title) {
        return new GuildRankGlyphs(background(title), foreground(title));
    }

    private static int advance(char character) {
        return character == 'I' || character == ' ' ? 4 : 6;
    }

    private record Speaker(String displayName, int messageStart) {}

    private record Match(
            GuildRankGlyphs oldGlyphs,
            GuildRankGlyphs newGlyphs,
            String indicator,
            int messageStart
    ) {}
}

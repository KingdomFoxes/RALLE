package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Rewrites only Wynncraft's rank-pill glyph passes for an identified guild-chat speaker. */
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
    private static final String STAR = "\uE100";
    private static final String STAR_TRAILING_SPACER = "\uE101";
    // Minecraft gives the five-pixel bitmap a six-pixel advance.
    private static final int STAR_ADVANCE = 6;
    private static final int FILL_ADVANCE = 4;
    private static final FontDescription RALLE_STAR_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("ralle", "guild_rank_star")
    );
    private static final FontDescription WYNN_PILL_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("minecraft", "banner/pill")
    );
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
    private static final Map<String, Integer> WYNN_RANK_STARS = Map.of(
            "OWNER", 5,
            "CHIEF", 4,
            "STRATEGIST", 3,
            "CAPTAIN", 2,
            "RECRUITER", 1,
            "RECRUIT", 0
    );
    private static final Map<String, String> FOX_RANK_LABELS = Map.ofEntries(
            Map.entry("SIR", "Sir/Madam/Knight"),
            Map.entry("MADAM", "Sir/Madam/Knight"),
            Map.entry("KNIGHT", "Sir/Madam/Knight"),
            Map.entry("LORD", "Lord/Lady/Liege"),
            Map.entry("LADY", "Lord/Lady/Liege"),
            Map.entry("LIEGE", "Lord/Lady/Liege"),
            Map.entry("BARON", "Baron/Baroness/Baronx"),
            Map.entry("BARONESS", "Baron/Baroness/Baronx"),
            Map.entry("BARONX", "Baron/Baroness/Baronx"),
            Map.entry("VISCOUNT", "Viscount/Viscountess/Viscountx"),
            Map.entry("VISCOUNTESS", "Viscount/Viscountess/Viscountx"),
            Map.entry("VISCOUNTX", "Viscount/Viscountess/Viscountx"),
            Map.entry("COUNT", "Count/Countess/Countx"),
            Map.entry("COUNTESS", "Count/Countess/Countx"),
            Map.entry("COUNTX", "Count/Countess/Countx"),
            Map.entry("MARQUIS", "Marquis/Marchioness/Marqix"),
            Map.entry("MARCHIONESS", "Marquis/Marchioness/Marqix"),
            Map.entry("MARQIX", "Marquis/Marchioness/Marqix")
    );
    private static final String TEST_GUILD_INDICATOR = new StringBuilder()
            .appendCodePoint(0xCFFFC)
            .appendCodePoint(0xE006)
            .appendCodePoint(0xCFFFF)
            .appendCodePoint(0xE002)
            .appendCodePoint(0xCFFFE)
            .toString();

    private GuildRankTitleTransformer() {}

    static Component apply(Component message, GuildRankSnapshot snapshot) {
        return apply(message, snapshot, GuildRankStyle.TITLES, true);
    }

    static Component apply(
            Component message,
            GuildRankSnapshot snapshot,
            GuildRankStyle rankStyle,
            boolean useInternalRanks
    ) {
        return apply(message, snapshot, rankStyle, useInternalRanks, true);
    }

    private static Component apply(
            Component message,
            GuildRankSnapshot snapshot,
            GuildRankStyle rankStyle,
            boolean useInternalRanks,
            boolean spaceBetweenStarsAndTitle
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(rankStyle, "rankStyle");

        Match match = findMatch(message);
        if (match == null) return message;

        Optional<String> internalTitle = Optional.empty();
        if (useInternalRanks) {
            internalTitle = snapshot.titleFor(match.speaker().displayName());
            if (internalTitle.isEmpty()) {
                String realName = hoveredRealName(message, match.speaker().displayName(),
                        new int[]{MAX_HOVER_COMPONENTS});
                if (realName != null) internalTitle = snapshot.titleFor(realName);
            }
        }

        String displayedTitle = internalTitle.orElse(match.oldTitle());
        var rankHover = new HoverEvent.ShowText(Component.literal(rankHoverText(match.oldTitle(), internalTitle)));
        GuildRankGlyphs replacement;
        if (rankStyle == GuildRankStyle.TITLES) {
            replacement = internalTitle.isEmpty() ? match.oldGlyphs() : snapshot.glyphsFor(displayedTitle);
        } else {
            Integer stars = WYNN_RANK_STARS.get(match.oldTitle());
            if (stars == null) return message;
            if (rankStyle == GuildRankStyle.STARS && stars == 0) {
                return removeRank(message, match.removeStart(), match.removeEnd(),
                        match.indicator(), match.messageStart(), new int[]{0});
            }
            replacement = rankStyle == GuildRankStyle.STARS
                    ? encodeStars(stars)
                    : encodeStarsAndTitle(stars, displayedTitle, spaceBetweenStarsAndTitle);
        }

        return transform(message, match.oldGlyphs(), replacement, rankHover, match.indicator(),
                match.messageStart(), new int[]{0});
    }

    static String rankHoverText(String guildRank, Optional<String> internalTitle) {
        String normalizedGuildRank = GuildRankSnapshot.normalizeTitle(guildRank);
        if (normalizedGuildRank == null) throw new IllegalArgumentException("Invalid Wynncraft guild rank");
        return internalTitle.map(title -> normalizedGuildRank + " - " + foxRankLabel(title))
                .orElse(normalizedGuildRank);
    }

    private static String foxRankLabel(String title) {
        String normalized = GuildRankSnapshot.normalizeTitle(title);
        if (normalized == null) throw new IllegalArgumentException("Invalid Fox rank");
        String grouped = FOX_RANK_LABELS.get(normalized);
        if (grouped != null) return grouped;
        var words = normalized.toLowerCase(Locale.ROOT).split(" ");
        for (int index = 0; index < words.length; index++) {
            words[index] = Character.toUpperCase(words[index].charAt(0)) + words[index].substring(1);
        }
        return String.join(" ", words);
    }

    private static Match findMatch(Component message) {
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
                    if (speaker == null) {
                        terminatorIndex = text.indexOf(FOREGROUND_TERMINATOR, foregroundEnd);
                        continue;
                    }
                    String indicator = indicatorBefore(text, backgroundStart);
                    int removeStart = backgroundStart;
                    int removeEnd = foregroundEnd;
                    if (indicator != null && backgroundStart > 0 && text.charAt(backgroundStart - 1) == ' ') {
                        removeStart--;
                    } else if (foregroundEnd < text.length() && text.charAt(foregroundEnd) == ' ') {
                        removeEnd++;
                    }
                    return new Match(oldTitle, oldGlyphs, indicator, speaker,
                            speaker.messageStart(), removeStart, removeEnd);
                }
            }
            terminatorIndex = text.indexOf(FOREGROUND_TERMINATOR, foregroundEnd);
        }
        return null;
    }

    static List<Component> testMessages() {
        return List.of(
                testMessage(false, "no space"),
                testMessage(true, "with space")
        );
    }

    private static Component testMessage(boolean gap, String label) {
        var original = Component.empty()
                .append(Component.literal(TEST_GUILD_INDICATOR).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal(background("STRATEGIST"))
                        .withStyle(style -> style.withFont(WYNN_PILL_FONT)))
                .append(Component.literal(foreground("STRATEGIST")).withStyle(style -> style
                        .withColor(ChatFormatting.BLACK)
                        .withFont(WYNN_PILL_FONT)))
                .append(Component.literal(" maxkarson: " + label).withStyle(ChatFormatting.AQUA));
        return apply(original, GuildRankSnapshot.EMPTY, GuildRankStyle.STARS_AND_TITLES, false, gap);
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
            HoverEvent.ShowText rankHover,
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
                    oldGlyphs, newGlyphs, rankHover, indicator, localMessageStart);
            offset[0] += literal.text().length();
        }

        boolean siblingChanged = false;
        var siblings = new Component[component.getSiblings().size()];
        for (int index = 0; index < siblings.length; index++) {
            var sibling = component.getSiblings().get(index);
            siblings[index] = transform(sibling, oldGlyphs, newGlyphs, rankHover,
                    indicator, messageStart, offset);
            siblingChanged |= siblings[index] != sibling;
        }
        if (replacement == null && !siblingChanged) return component;

        MutableComponent transformed = replacement == null ? component.plainCopy() : replacement;
        for (var sibling : siblings) transformed.append(sibling);
        return transformed;
    }

    private static Component removeRank(
            Component component,
            int removeStart,
            int removeEnd,
            String indicator,
            int messageStart,
            int[] offset
    ) {
        MutableComponent replacement = null;
        if (component.getContents() instanceof PlainTextContents.LiteralContents literal) {
            int literalStart = offset[0];
            int literalEnd = literalStart + literal.text().length();
            int localMessageStart = messageStart <= literalStart
                    ? 0 : messageStart >= literalEnd
                    ? Integer.MAX_VALUE : messageStart - literalStart;
            int localRemoveStart = Math.max(0, removeStart - literalStart);
            int localRemoveEnd = Math.min(literal.text().length(), removeEnd - literalStart);
            var rebuilt = Component.empty();
            if (localRemoveStart >= localRemoveEnd) {
                appendOriginal(rebuilt, literal.text(), 0, literal.text().length(),
                        component.getStyle(), localMessageStart);
            } else {
                if (localRemoveStart > 0) {
                    appendOriginal(rebuilt, literal.text(), 0, localRemoveStart,
                            component.getStyle(), localMessageStart);
                }
                if (localRemoveEnd < literal.text().length()) {
                    appendOriginal(rebuilt, literal.text(), localRemoveEnd, literal.text().length(),
                            component.getStyle(), localMessageStart);
                }
            }
            replacement = indicator != null && literal.text().equals(indicator)
                    ? rebuilt.withStyle(component.getStyle().withColor(ChatFormatting.AQUA))
                    : rebuilt;
            offset[0] = literalEnd;
        }

        boolean siblingChanged = false;
        var siblings = new Component[component.getSiblings().size()];
        for (int index = 0; index < siblings.length; index++) {
            var sibling = component.getSiblings().get(index);
            siblings[index] = removeRank(sibling, removeStart, removeEnd, indicator, messageStart, offset);
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
            HoverEvent.ShowText rankHover,
            String indicator,
            int messageStart
    ) {
        if (indicator != null && text.equals(indicator)) {
            return Component.literal(indicator).withStyle(style.withColor(ChatFormatting.AQUA));
        }
        if (text.equals(oldGlyphs.background())) {
            return Component.literal(newGlyphs.background()).withStyle(style
                    .withColor(ChatFormatting.AQUA).withHoverEvent(rankHover));
        }
        if (text.equals(oldGlyphs.foreground())) {
            return replacementForeground(newGlyphs, style.withHoverEvent(rankHover));
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
                result.append(Component.literal(newGlyphs.background()).withStyle(style
                        .withColor(ChatFormatting.AQUA).withHoverEvent(rankHover)));
                cursor = index + oldGlyphs.background().length();
            } else {
                result.append(replacementForeground(newGlyphs, style.withHoverEvent(rankHover)));
                cursor = index + oldGlyphs.foreground().length();
            }
            changed = true;
        }
        if (!changed && messageStart == Integer.MAX_VALUE) return null;
        if (cursor < text.length()) appendOriginal(result, text, cursor, text.length(), style, messageStart);
        return result;
    }

    private static MutableComponent replacementForeground(GuildRankGlyphs glyphs, Style pillStyle) {
        String foreground = glyphs.foreground();
        int starEnd = 0;
        while (foreground.startsWith(STAR, starEnd)) starEnd += STAR.length();
        if (foreground.startsWith(STAR_TRAILING_SPACER, starEnd)) {
            starEnd += STAR_TRAILING_SPACER.length();
        }
        if (starEnd == 0) return Component.literal(foreground).withStyle(pillStyle);

        var result = Component.empty();
        result.append(Component.literal(foreground.substring(0, starEnd))
                .withStyle(pillStyle.withFont(RALLE_STAR_FONT)));
        if (starEnd < foreground.length()) {
            result.append(Component.literal(foreground.substring(starEnd)).withStyle(pillStyle));
        }
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

    static GuildRankGlyphs encodeStars(int stars) {
        if (stars <= 0 || stars > 5) throw new IllegalArgumentException("Stars must be between 1 and 5");
        return encodeComposite(stars, null, false);
    }

    static GuildRankGlyphs encodeStarsAndTitle(int stars, String title, boolean gap) {
        if (stars < 0 || stars > 5) throw new IllegalArgumentException("Stars must be between 0 and 5");
        String normalizedTitle = GuildRankSnapshot.normalizeTitle(title);
        if (normalizedTitle == null) throw new IllegalArgumentException("Invalid guild rank title");
        if (stars == 0) return encode(normalizedTitle);
        return encodeComposite(stars, normalizedTitle, gap);
    }

    private static GuildRankGlyphs encodeComposite(int stars, String title, boolean gap) {
        int starWidth = stars * STAR_ADVANCE;
        int trailingPadding = (FILL_ADVANCE - starWidth % FILL_ADVANCE) % FILL_ADVANCE;
        int foregroundWidth = starWidth + trailingPadding;
        var background = new StringBuilder().appendCodePoint(LEFT_CAP).appendCodePoint(NEGATIVE_ONE);
        for (int index = 0; index < foregroundWidth / FILL_ADVANCE; index++) {
            background.appendCodePoint(SPACE_FILL).appendCodePoint(NEGATIVE_ONE);
        }

        var foreground = new StringBuilder(STAR.repeat(stars));
        if (trailingPadding > 0) foreground.append(STAR_TRAILING_SPACER);
        if (title != null) {
            if (gap) {
                background.appendCodePoint(SPACE_FILL).appendCodePoint(NEGATIVE_ONE);
                foreground.append(' ');
                foregroundWidth += FILL_ADVANCE;
            }
            for (char character : title.toCharArray()) {
                background.appendCodePoint(character == ' ' ? SPACE_FILL : BACKGROUND_A + character - 'A');
                background.appendCodePoint(NEGATIVE_ONE);
                if (character == ' ') foreground.append(' ');
                else foreground.appendCodePoint(FOREGROUND_A + character - 'A');
                foregroundWidth += advance(character);
            }
        }

        background.appendCodePoint(RIGHT_CAP).appendCodePoint(SPACING_ZERO - (foregroundWidth + 2));
        foreground.appendCodePoint(POSITIVE_TWO);
        return new GuildRankGlyphs(background.toString(), foreground.toString());
    }

    private static int advance(char character) {
        return character == 'I' || character == ' ' ? 4 : 6;
    }

    private record Speaker(String displayName, int messageStart) {}

    private record Match(
            String oldTitle,
            GuildRankGlyphs oldGlyphs,
            String indicator,
            Speaker speaker,
            int messageStart,
            int removeStart,
            int removeEnd
    ) {}
}

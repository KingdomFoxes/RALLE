package org.kingdomfoxes.ralle.chat.identity;

import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Fixture-driven validation for Wynncraft's current guild indicator, rank pill, speaker, and body envelope. */
public final class GuildChatIdentityResolver {
    private static final int FOREGROUND_A = 0xE000;
    private static final int BACKGROUND_A = 0xE030;
    private static final int SPACE_FILL = 0xE061;
    private static final int LEFT_CAP = 0xE060;
    private static final int RIGHT_CAP = 0xE062;
    private static final int SPACING_ZERO = 0xD0000;
    private static final int NEGATIVE_ONE = SPACING_ZERO - 1;
    private static final int POSITIVE_TWO = 0xD0002;

    private GuildChatIdentityResolver() {}

    public static Optional<GuildChatMessage> resolve(Component component) {
        if (component == null) return Optional.empty();
        var flattened = GuildSpeakerIdentity.flatten(component);
        if (flattened == null) return Optional.empty();
        String text = flattened.text();

        int terminator = text.indexOf(Character.toString(POSITIVE_TWO));
        while (terminator >= 0) {
            int foregroundEnd = terminator + Character.charCount(POSITIVE_TWO);
            String title = decodeForegroundBefore(text, foregroundEnd);
            if (title != null) {
                String foreground = foreground(title);
                String background = background(title);
                int foregroundStart = foregroundEnd - foreground.length();
                int backgroundStart = foregroundStart - background.length();
                if (backgroundStart >= 0
                        && text.regionMatches(backgroundStart, background, 0, background.length())
                        && text.regionMatches(foregroundStart, foreground, 0, foreground.length())
                        && verifiedIndicatorPrefix(text, backgroundStart)) {
                    int nameStart = foregroundEnd + 1;
                    if (foregroundEnd < text.length() && text.charAt(foregroundEnd) == ' ') {
                        int colon = text.indexOf(':', nameStart);
                        if (colon >= nameStart && colon - nameStart <= 64) {
                            String displayName = text.substring(nameStart, colon);
                            if (!displayName.isBlank() && displayName.indexOf('\n') < 0 && displayName.indexOf('\r') < 0) {
                                int bodyStart = colon + 1;
                                if (bodyStart < text.length() && text.charAt(bodyStart) == ' ') bodyStart++;
                                String body = text.substring(bodyStart);
                                return Optional.of(new GuildChatMessage(
                                        displayName,
                                        GuildSpeakerIdentity.resolve(component, displayName, nameStart, colon),
                                        body
                                ));
                            }
                        }
                    }
                }
            }
            terminator = text.indexOf(Character.toString(POSITIVE_TWO), foregroundEnd);
        }
        return Optional.empty();
    }

    private static boolean verifiedIndicatorPrefix(String text, int backgroundStart) {
        int cursor = backgroundStart;
        if (cursor > 0 && text.charAt(cursor - 1) == ' ') cursor--;
        int indicatorEnd = cursor;
        while (cursor > 0) {
            int codePoint = text.codePointBefore(cursor);
            if (!isIndicatorCodePoint(codePoint)) break;
            cursor -= Character.charCount(codePoint);
        }
        if (cursor == indicatorEnd) return false;
        for (int index = 0; index < cursor; index++) {
            if (!Character.isWhitespace(text.charAt(index))) return false;
        }
        return true;
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
        String title = reversed.reverse().toString();
        return title.length() <= 32 ? title : null;
    }

    static String background(String title) {
        int width = 0;
        var result = new StringBuilder().appendCodePoint(LEFT_CAP).appendCodePoint(NEGATIVE_ONE);
        for (char character : title.toCharArray()) {
            result.appendCodePoint(character == ' ' ? SPACE_FILL : BACKGROUND_A + character - 'A');
            result.appendCodePoint(NEGATIVE_ONE);
            width += character == 'I' || character == ' ' ? 4 : 6;
        }
        return result.appendCodePoint(RIGHT_CAP).appendCodePoint(SPACING_ZERO - width - 2).toString();
    }

    static String foreground(String title) {
        var encoded = new StringBuilder();
        title.codePoints().forEach(codePoint -> encoded.appendCodePoint(
                codePoint == ' ' ? codePoint : FOREGROUND_A + Character.toUpperCase(codePoint) - 'A'));
        encoded.appendCodePoint(POSITIVE_TWO);
        return encoded.toString();
    }
}

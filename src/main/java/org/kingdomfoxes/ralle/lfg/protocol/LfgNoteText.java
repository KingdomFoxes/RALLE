package org.kingdomfoxes.ralle.lfg.protocol;

import java.util.regex.Pattern;

/** Plain-text policy for the only player-authored free-form LFG protocol field. */
public final class LfgNoteText {
    public static final int MAX_LENGTH = 80;

    private static final int LEGACY_FORMATTING_MARKER = '\u00a7';
    private static final Pattern LEGACY_FORMATTING =
            Pattern.compile("(?:\\u00a7|&)[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);

    private LfgNoteText() {}

    /** Cleans local text before it crosses the client/backend boundary. */
    public static String sanitizeForSubmission(String note) {
        if (note == null) return null;
        var cleaned = clean(note);
        if (cleaned.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Note must be at most " + MAX_LENGTH + " characters.");
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Rejects a backend value which is not bounded, single-line, inert plain text. */
    static void requireValidWireValue(String note, String path) {
        if (note == null) return;
        if (note.length() > MAX_LENGTH
                || LEGACY_FORMATTING.matcher(note).find()
                || note.codePoints().anyMatch(LfgNoteText::isForbidden)) {
            throw new LfgProtocolException(path + " is not valid single-line plain text");
        }
    }

    /** Last-line rendering guard for protocol objects supplied outside the normal decoder. */
    public static String sanitizeForDisplay(String note) {
        if (note == null) return "";
        var cleaned = clean(note);
        if (cleaned.length() <= MAX_LENGTH) return cleaned;

        int end = MAX_LENGTH;
        if (Character.isHighSurrogate(cleaned.charAt(end - 1))
                && end < cleaned.length()
                && Character.isLowSurrogate(cleaned.charAt(end))) {
            end--;
        }
        return cleaned.substring(0, end);
    }

    private static String clean(String value) {
        var withoutLegacyFormatting = LEGACY_FORMATTING.matcher(value).replaceAll("");
        var result = new StringBuilder(Math.min(withoutLegacyFormatting.length(), MAX_LENGTH));
        boolean pendingSpace = false;

        for (int offset = 0; offset < withoutLegacyFormatting.length();) {
            int codePoint = withoutLegacyFormatting.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = result.length() > 0;
                continue;
            }
            if (isForbidden(codePoint)) continue;
            if (pendingSpace) result.append(' ');
            result.appendCodePoint(codePoint);
            pendingSpace = false;
        }
        return result.toString();
    }

    private static boolean isForbidden(int codePoint) {
        if (codePoint == LEGACY_FORMATTING_MARKER) return true;
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL, Character.FORMAT, Character.SURROGATE,
                    Character.PRIVATE_USE, Character.UNASSIGNED,
                    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }
}

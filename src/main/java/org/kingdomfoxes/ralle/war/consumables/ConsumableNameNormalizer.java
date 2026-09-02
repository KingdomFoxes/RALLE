package org.kingdomfoxes.ralle.war.consumables;

import java.text.Normalizer;
import java.util.Locale;

/** Unicode-aware matching normalization which retains meaningful word boundaries. */
public final class ConsumableNameNormalizer {
    private ConsumableNameNormalizer() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        var result = new StringBuilder(normalized.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK) {
                if (pendingSpace && !result.isEmpty()) result.append(' ');
                result.appendCodePoint(codePoint);
                pendingSpace = false;
            } else {
                pendingSpace = true;
            }
        }
        return result.toString();
    }

    public static boolean containsPhrase(String normalizedDisplayedName, String normalizedTerm) {
        if (normalizedDisplayedName.isEmpty() || normalizedTerm.isEmpty()) return false;
        int from = 0;
        while (from <= normalizedDisplayedName.length() - normalizedTerm.length()) {
            int found = normalizedDisplayedName.indexOf(normalizedTerm, from);
            if (found < 0) return false;
            int end = found + normalizedTerm.length();
            if ((found == 0 || normalizedDisplayedName.charAt(found - 1) == ' ')
                    && (end == normalizedDisplayedName.length() || normalizedDisplayedName.charAt(end) == ' ')) {
                return true;
            }
            from = found + 1;
        }
        return false;
    }
}

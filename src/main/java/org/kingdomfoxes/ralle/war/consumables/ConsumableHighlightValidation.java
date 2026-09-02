package org.kingdomfoxes.ralle.war.consumables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Central transactional validation and blank-alias filtering. */
public final class ConsumableHighlightValidation {
    public static final int MAX_TERM_LENGTH = 50;

    private ConsumableHighlightValidation() {}

    public static List<ConsumableHighlightRule> validate(List<ConsumableHighlightRule> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        var normalizedTerms = new HashSet<String>();
        var validated = new ArrayList<ConsumableHighlightRule>(candidates.size());
        for (var candidate : candidates) {
            Objects.requireNonNull(candidate, "rule");
            var name = checkedTerm(candidate.name(), "Primary word", false);
            rejectDuplicate(normalizedTerms, name);
            var aliases = new ArrayList<String>();
            for (var rawAlias : candidate.aliases()) {
                var alias = checkedTerm(rawAlias, "Alias", true);
                if (alias == null) continue;
                rejectDuplicate(normalizedTerms, alias);
                aliases.add(alias);
            }
            validated.add(new ConsumableHighlightRule(name, aliases, candidate.style()));
        }
        return List.copyOf(validated);
    }

    public static List<String> parseAliasBatch(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) return List.of();
        return java.util.Arrays.stream(commaSeparated.split(",", -1))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String checkedTerm(String raw, String label, boolean blankAllowed) {
        if (raw == null) throw new IllegalArgumentException(label + " is required");
        var value = raw.strip();
        if (value.isBlank()) {
            if (blankAllowed) return null;
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        if (value.codePointCount(0, value.length()) > MAX_TERM_LENGTH) {
            throw new IllegalArgumentException(label + " is longer than " + MAX_TERM_LENGTH + " characters");
        }
        if (ConsumableNameNormalizer.normalize(value).isBlank()) {
            throw new IllegalArgumentException(label + " must contain a letter or number");
        }
        return value;
    }

    private static void rejectDuplicate(HashSet<String> normalizedTerms, String term) {
        var normalized = ConsumableNameNormalizer.normalize(term);
        if (!normalizedTerms.add(normalized)) {
            throw new IllegalArgumentException("Duplicate word or alias: " + term);
        }
    }
}

package org.kingdomfoxes.ralle.classes;

import java.util.List;
import java.util.regex.Pattern;

/** Latest received position and progression. Mutable continuity evidence, never a server identity. */
public record ClassBookObservation(int slot, List<String> progress) {
    private static final Pattern HOURS = Pattern.compile("- Time Played: ([0-9]{1,9}(?:\\.[0-9]{1,4})?) hours?");
    private static final Pattern LEVEL = Pattern.compile("- Level: ([0-9]{1,9})(?: .*)?");
    public ClassBookObservation {
        if (slot < 0 || slot >= 54) throw new IllegalArgumentException("Invalid character slot");
        progress = List.copyOf(progress);
        if (progress.size() > 32) throw new IllegalArgumentException("Too much character progress");
        progress.forEach(line -> ClassCharacter.text(line, 512));
    }

    /** A class reskin changes the class line, but not the progression it witnesses. */
    public List<String> matchingProgress() { return progress.stream().map(ClassReskins::classLine).toList(); }

    /** A new low-level character reusing an old icon must not steal the old shortcut. */
    public boolean canFollow(ClassBookObservation previous) {
        if (previous == null) return true;
        for (var field : List.of(HOURS, LEVEL)) {
            var old = value(previous.progress, field);
            var next = value(progress, field);
            if (old != null && next != null && next.compareTo(old) < 0) return false;
        }
        return true;
    }

    private static java.math.BigDecimal value(List<String> lines, Pattern field) {
        for (String line : lines) {
            var match = field.matcher(line);
            if (match.matches()) return new java.math.BigDecimal(match.group(1));
        }
        return null;
    }
}

package org.kingdomfoxes.ralle.classes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** One-to-one reconciliation of complete naturally received books. No name/position fallback.
 * Semantic identity gates every match. Progress and artwork are refreshable witnesses, not keys.
 */
final class ClassRosterMatcher {
    private ClassRosterMatcher() {}

    static List<ClassCharacter> match(List<ClassCharacter> incoming, List<ClassCharacter> saved, boolean complete) {
        var assigned = new HashMap<String, ClassCharacter>();
        for (var identity : incoming.stream().map(c -> c.identity().forMatching()).distinct().toList()) {
            var live = incoming.stream().filter(c -> identity.equals(c.identity().forMatching())).toList();
            var old = saved.stream().filter(c -> c.identity() != null && identity.equals(c.identity().forMatching()) && !unresolved(c)).toList();
            var used = new HashSet<String>();
            // An exact, unique progression snapshot can distinguish two identically styled Mages.
            pair(live, old, assigned, used, c -> c.book() == null ? null : c.book().matchingProgress());
            // Artwork can witness continuity without becoming part of the retained reference.
            pair(live, old, assigned, used, ClassCharacter::sprite);
            var remainingLive = live.stream().filter(c -> !assigned.containsKey(c.id())).toList();
            var remainingOld = old.stream().filter(c -> !used.contains(c.id())).toList();
            // One changed entry among otherwise exactly matched peers has only one assignment.
            // Never use this elimination after an addition/removal or an incomplete update.
            if (complete && live.size() == old.size() && remainingLive.size() == 1 && remainingOld.size() == 1
                    && distinguishable(remainingLive.getFirst(), live)
                    && remainingLive.getFirst().book().canFollow(remainingOld.getFirst().book()))
                assigned.put(remainingLive.getFirst().id(), remainingOld.getFirst());
        }
        var result = new ArrayList<ClassCharacter>();
        for (var live : incoming) {
            var old = assigned.get(live.id());
            String id;
            if (old != null) id = old.id();
            else {
                boolean ambiguous = !distinguishable(live, incoming);
                // Birth evidence only allocates a local reference. Subsequent changes keep it.
                String salt = ambiguous ? "" : saved.stream().filter(c -> c.identity() != null && live.identity().forMatching().equals(c.identity().forMatching()))
                        .map(ClassCharacter::id).sorted().reduce("", (a, b) -> a + "\n" + b);
                id = ClassCharacter.OBSERVATION_PREFIX + live.book().slot() + ":"
                        + (ambiguous ? "ambiguous-v3-" : "v3-") + digest(witness(live) + "\n" + live.book().slot() + salt);
            }
            // A v2 reference embeds the original evidence hash. Retain that semantic baseline
            // while refreshing the separately stored presentation and book observation.
            result.add(new ClassCharacter(id, live.type(), live.nickname(), live.sprite(),
                    old == null ? live.identity() : old.identity(), live.book()));
        }
        return List.copyOf(result);
    }

    private static void pair(List<ClassCharacter> live, List<ClassCharacter> old,
                             Map<String, ClassCharacter> assigned, Set<String> used,
                             Function<ClassCharacter, Object> witness) {
        for (var candidate : live) {
            if (assigned.containsKey(candidate.id()) || !distinguishable(candidate, live)) continue;
            var value = witness.apply(candidate);
            if (value == null || live.stream().filter(c -> value.equals(witness.apply(c))).count() != 1) continue;
            var matches = old.stream().filter(c -> value.equals(witness.apply(c))).toList();
            if (matches.size() != 1 || used.contains(matches.getFirst().id())
                    || !candidate.book().canFollow(matches.getFirst().book())) continue;
            assigned.put(candidate.id(), matches.getFirst());
            used.add(matches.getFirst().id());
        }
    }

    static boolean unresolved(ClassCharacter character) { return character.id().contains(":ambiguous-v3-"); }

    static String witness(ClassCharacter character) {
        var value = new StringBuilder(character.identity().forMatching().fingerprint()).append('\n')
                .append(character.sprite().model()).append('\n').append(character.sprite().tint()).append('\n');
        for (String line : character.book().matchingProgress()) value.append(line.length()).append(':').append(line).append('\n');
        return value.toString();
    }

    private static boolean distinguishable(ClassCharacter character, List<ClassCharacter> roster) {
        String evidence = witness(character);
        return roster.stream().filter(c -> evidence.equals(witness(c))).count() == 1;
    }

    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

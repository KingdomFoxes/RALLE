package org.kingdomfoxes.ralle.classes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Normalized book evidence, separate from mutable artwork and the latest menu position.
 * This is an observation, not a server UUID. Unknown non-progress details remain identifying.
 */
public record ClassIdentity(String name, List<String> details) {
    public ClassIdentity {
        name = ClassCharacter.text(name, 128);
        details = List.copyOf(details);
        if (details.size() > 32) throw new IllegalArgumentException("Too much character evidence");
        details.forEach(line -> ClassCharacter.text(line, 512));
    }

    public String fingerprint() {
        try {
            // Length framing avoids ambiguous field boundaries; the tag versions normalization.
            var value = new StringBuilder("ralle-class-identity-v2\n");
            for (String field : java.util.stream.Stream.concat(java.util.stream.Stream.of(name), details.stream()).toList())
                value.append(field.length()).append(':').append(field).append('\n');
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public String reference(int slot) { return ClassCharacter.OBSERVATION_PREFIX + slot + ":v2-" + fingerprint(); }

    /** Normalize the exact class field and its default title, never arbitrary nickname text.
     * Keep the original evidence/fingerprint intact for older saved reference validation.
     */
    public ClassIdentity forMatching() {
        String type = ClassReskins.type(this);
        String family = ClassReskins.family(type);
        if (family.equals(type)) return this;
        return new ClassIdentity(name.equals(type) ? family : name,
                details.stream().map(ClassReskins::classLine).toList());
    }

    public static String fingerprint(String reference) {
        if (reference == null || !reference.matches("observed:[0-9]+:(?:ambiguous-)?v2-[0-9a-f]{64}")) return null;
        return reference.substring(reference.lastIndexOf("v2-") + 3);
    }

    public static boolean ambiguous(String reference) { return reference != null && reference.contains(":ambiguous-v"); }
}

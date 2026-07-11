package org.kingdomfoxes.ralle.api.feature;

import java.util.Objects;
import java.util.regex.Pattern;

public final class IdentifierRules {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+");

    private IdentifierRules() {
    }

    public static String requireValid(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must match " + VALID.pattern());
        }
        return value;
    }
}

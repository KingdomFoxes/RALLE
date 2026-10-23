package org.kingdomfoxes.ralle.classes;

import java.util.Objects;

/** Local display override and keyboard option; removing this never removes a server character. */
public record ClassShortcut(String characterId, String name, String binding) {
    public static final String UNBOUND = "unbound";

    public ClassShortcut {
        characterId = ClassCharacter.text(characterId, 128);
        Objects.requireNonNull(name);
        if (name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Display names must contain at most 64 printable characters");
        Objects.requireNonNull(binding);
        if (!UNBOUND.equals(binding) && !binding.matches("key\\.keyboard\\.[a-z0-9.]+"))
            throw new IllegalArgumentException("Choose a keyboard option key");
    }
}

package org.kingdomfoxes.ralle.classes;

import java.util.Objects;

/** A game-provided identity, never a nickname, class type or inventory position. */
public record ClassCharacter(String id, String type, String nickname) {
    public ClassCharacter {
        id = text(id, 128);
        type = text(type, 64);
        nickname = Objects.requireNonNull(nickname);
        if (nickname.length() > 128 || nickname.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid character nickname");
    }

    public String displayName(String override) {
        return !override.isBlank() ? override : !nickname.isBlank() ? nickname : type;
    }

    static String text(String value, int maximum) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > maximum || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid character data");
        return value;
    }
}

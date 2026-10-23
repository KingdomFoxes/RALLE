package org.kingdomfoxes.ralle.classes;

import java.util.Objects;

/** Game identity or explicitly marked menu observation, requiring exact live-book resolution. */
public record ClassCharacter(String id, String type, String nickname, ClassSprite sprite, ClassIdentity identity, ClassBookObservation book) {
    public ClassCharacter(String id, String type, String nickname, ClassSprite sprite, ClassIdentity identity) { this(id, type, nickname, sprite, identity, null); }
    public ClassCharacter(String id, String type, String nickname, ClassSprite sprite) { this(id, type, nickname, sprite, null); }
    public ClassCharacter(String id, String type, String nickname) { this(id, type, nickname, null); }
    public static final String OBSERVATION_PREFIX = "observed:";

    public boolean hasStableIdentity() { return !id.startsWith(OBSERVATION_PREFIX); }
    public ClassCharacter {
        id = text(id, 128);
        type = text(type, 64);
        nickname = Objects.requireNonNull(nickname);
        if (sprite != null) sprite = new ClassSprite(sprite.model(), sprite.tint());
        if (identity != null) {
            identity = new ClassIdentity(identity.name(), identity.details());
            if (ClassIdentity.fingerprint(id) != null && !identity.fingerprint().equals(ClassIdentity.fingerprint(id)))
                throw new IllegalArgumentException("Character evidence does not match reference");
        }
        if (book != null) {
            book = new ClassBookObservation(book.slot(), book.progress());
            if (identity == null || sprite == null) throw new IllegalArgumentException("Incomplete character observation");
        }
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

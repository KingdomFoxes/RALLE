package org.kingdomfoxes.ralle.classes;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Decoded natural menu observation. Completeness is supplied only by a verified game adapter. */
public record ClassScan(UUID account, List<ClassCharacter> characters, boolean complete) {
    public ClassScan {
        java.util.Objects.requireNonNull(account);
        characters = List.copyOf(characters);
        if (characters.size() > 64) throw new IllegalArgumentException("Too many characters");
        var ids = new HashSet<String>();
        for (var character : characters)
            if (!ids.add(character.id())) throw new IllegalArgumentException("Ambiguous character identity");
    }
}

package org.kingdomfoxes.ralle.cosmetics;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CosmeticPresentationTest {
    @Test
    void canonicalRoleLabelDoesNotChangeWithColorPreference() {
        UUID id = UUID.randomUUID();
        var green = new CosmeticIdentity(id, Set.of("contributor"), "contributor-green", 1);
        var blue = new CosmeticIdentity(id, Set.of("contributor"), "contributor-blue", 2);
        assertEquals("[Fox] Player, RALLE Contributor", CosmeticPresentation.tooltip("Fox", "Player", green));
        assertEquals(CosmeticPresentation.tooltip("Fox", "Player", green),
                CosmeticPresentation.tooltip("Fox", "Player", blue));
        assertEquals("Player, RALLE Admin", CosmeticPresentation.tooltip("", "Player",
                new CosmeticIdentity(id, Set.of("admin"), "supporter-gold", 3)));
        assertEquals("Player", CosmeticPresentation.tooltip("", "Player", CosmeticIdentity.neutral(id, 4)));
    }
}

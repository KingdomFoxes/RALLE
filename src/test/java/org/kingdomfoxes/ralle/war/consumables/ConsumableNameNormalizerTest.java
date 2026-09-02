package org.kingdomfoxes.ralle.war.consumables;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumableNameNormalizerTest {
    @Test void normalizesUnicodeCasePunctuationAndWhitespaceWithoutJoiningWords() {
        assertEquals("healing efficiency", ConsumableNameNormalizer.normalize("  HEALING—Efficiency!! "));
        assertEquals("éclair mana", ConsumableNameNormalizer.normalize("Éclair / Mana"));
        assertEquals("earth dmg", ConsumableNameNormalizer.normalize("earth.dmg"));
    }

    @Test void matchesOnlyCompleteWordsAndPhrases() {
        assertTrue(ConsumableNameNormalizer.containsPhrase("major earth dmg potion", "earth dmg"));
        assertTrue(ConsumableNameNormalizer.containsPhrase("strength potion", "strength"));
        assertFalse(ConsumableNameNormalizer.containsPhrase("string potion", "str"));
        assertFalse(ConsumableNameNormalizer.containsPhrase("manastorm", "mana"));
        assertFalse(ConsumableNameNormalizer.containsPhrase("spells", "spell"));
    }
}

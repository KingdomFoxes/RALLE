package org.kingdomfoxes.ralle.war.consumables;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumableHighlightMatcherTest {
    @Test void primaryAndAliasesMatchCaseInsensitivelyWithFirstRulePriority() {
        var first = new HighlightStyle(0x112233, false);
        var second = new HighlightStyle(0x445566, false);
        var matcher = new ConsumableHighlightMatcher(List.of(
                new ConsumableHighlightRule("Mana", List.of("water dmg"), first),
                new ConsumableHighlightRule("Potion", List.of("water"), second)
        ));
        assertEquals(first, matcher.match("§bGREATER WATER-DMG POTION").orElseThrow());
        assertEquals(first, matcher.match("Mana Elixir").orElseThrow());
    }

    @Test void explicitPluralAndBoundariesAreRequired() {
        var matcher = new ConsumableHighlightMatcher(List.of(
                new ConsumableHighlightRule("spell", List.of(), new HighlightStyle(1, false))));
        assertTrue(matcher.match("spells potion").isEmpty());
        assertTrue(matcher.match("spellbound potion").isEmpty());
        assertTrue(matcher.match("spell potion").isPresent());
    }

    @Test void cacheIsBoundedAndClearedWhenRulesChange() {
        var matcher = new ConsumableHighlightMatcher(List.of(
                new ConsumableHighlightRule("mana", List.of(), new HighlightStyle(1, false))), 2);
        matcher.match("mana one");
        matcher.match("mana two");
        matcher.match("mana three");
        assertEquals(2, matcher.cachedNames());
        matcher.update(List.of());
        assertEquals(0, matcher.cachedNames());
    }
}

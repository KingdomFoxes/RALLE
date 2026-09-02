package org.kingdomfoxes.ralle.war.consumables;

import java.util.List;

/** Shipped semantic defaults. Kept isolated so pack/community terminology can evolve independently. */
public final class ConsumableHighlightDefaults {
    private ConsumableHighlightDefaults() {}

    public static List<ConsumableHighlightRule> rules() {
        return List.of(
                rule("Strength", 0x228B22, "str", "earth dmg"),
                rule("Dexterity", 0xFFFF00, "dex", "thunder dmg"),
                rule("Intelligence", 0x87CEEB, "int", "intel"),
                rule("Defence", 0x8B0000, "def", "defense", "hp"),
                rule("Agility", 0xF0F0F0, "agi"),
                new ConsumableHighlightRule("Rainbow", List.of(), new HighlightStyle(0xFFFFFF, true)),
                rule("Healing Efficiency", 0xFF69B4, "healing eff"),
                rule("Mana and Spell", 0x4169E1, "mana", "spell", "water dmg"),
                rule("JH", 0xFF8C00)
        );
    }

    private static ConsumableHighlightRule rule(String name, int rgb, String... aliases) {
        return new ConsumableHighlightRule(name, List.of(aliases), new HighlightStyle(rgb, false));
    }
}

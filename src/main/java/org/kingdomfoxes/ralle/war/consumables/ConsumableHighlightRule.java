package org.kingdomfoxes.ralle.war.consumables;

import java.util.List;
import java.util.Objects;

/** Immutable ordered displayed-name matching rule. */
public record ConsumableHighlightRule(String name, List<String> aliases, HighlightStyle style) {
    public ConsumableHighlightRule {
        name = Objects.requireNonNull(name, "name");
        aliases = List.copyOf(aliases);
        style = Objects.requireNonNull(style, "style");
    }
}

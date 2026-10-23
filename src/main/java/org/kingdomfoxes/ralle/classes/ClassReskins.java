package org.kingdomfoxes.ralle.classes;

/** Only Wynncraft's known cosmetic pairs share a family; unrelated classes never do. */
final class ClassReskins {
    private static final String CLASS_FIELD = "- Class: ";
    private ClassReskins() {}

    static String family(String type) {
        return switch (type) {
            case "Archer", "Hunter" -> "Archer";
            case "Mage", "Dark Wizard" -> "Mage";
            case "Warrior", "Knight" -> "Warrior";
            case "Assassin", "Ninja" -> "Assassin";
            case "Shaman", "Skyseer" -> "Shaman";
            default -> type;
        };
    }

    static String classLine(String line) {
        return line.startsWith(CLASS_FIELD) ? CLASS_FIELD + family(line.substring(CLASS_FIELD.length())) : line;
    }

    static String type(ClassIdentity identity) {
        var fields = identity.details().stream().filter(line -> line.startsWith(CLASS_FIELD)).toList();
        return fields.size() == 1 ? fields.getFirst().substring(CLASS_FIELD.length()) : "";
    }
}

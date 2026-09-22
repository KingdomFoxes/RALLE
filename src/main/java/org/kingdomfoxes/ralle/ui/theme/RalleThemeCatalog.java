package org.kingdomfoxes.ralle.ui.theme;

import java.util.List;
import java.util.Map;

/** Packaged community palette catalog. IDs are local persistence keys and never depend on display text. */
public final class RalleThemeCatalog {
    public record Theme(String id, String name, String contributor, int background, int outline, int accent) {
        public Theme {
            if (id == null || !id.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid theme id: " + id);
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Theme name is required");
            if ((background | outline | accent) < 0 || background > 0xFFFFFF || outline > 0xFFFFFF || accent > 0xFFFFFF)
                throw new IllegalArgumentException("Theme colors must be RGB values");
        }
    }

    public static final String DEFAULT_ID = "default";
    private static final List<Theme> THEMES = List.of(
            t(DEFAULT_ID, "Default theme", null, "041330", "FFFFFF", "F2B84B"),
            t("royal-dynasty", "Royal Dynasty", "SaltyKing", "6A1B9A", "FFFFFF", "F2B84B"),
            t("unnamed", "???", "SpaseCow", "1A4A00", "FFFFFF", "FFBABA"),
            t("housing-crisis", "Housing crisis", "SpaseCow", "5E0606", "065E32", "001702"),
            t("gloopy-cave", "Gloopy Cave", "SpaseCow", "00D636", "A100D6", "D60036"),
            t("countx-sini", "Countx Sini", "Nothesinistrtype", "6DDFFF", "FFFDAE", "851A1C"),
            t("princes-palette", "Prince's Palette", "Robturne", "9B00E2", "AE0070", "C10000"),
            t("tempest", "Tempest", "Robturne", "DBDBDB", "0094FF", "FFD400"),
            t("scarecrow", "Scarecrow", "Robturne", "CCDEB6", "377141", "000000"),
            t("joker", "Joker", "NeonRider", "660C0C", "020202", "BB8856"),
            t("coral-castle", "Coral Castle", "ToolyTom", "FFB5C2", "FF7B6E", "2F9FBF"),
            t("tidal-tom", "Tidal Tom", "ToolyTom", "36986B", "235D67", "E1C7BA"),
            t("forrest-phil", "Forrest Phil", "ToolyTom", "87A646", "8C613B", "535925"),
            t("cartman", "Cartman", "ToolyTom", "31AAA9", "F8E0A4", "A82020"),
            t("sunset-spase", "Sunset Spase", "ToolyTom", "FFA95A", "FF8B5A", "FF5A5A"),
            t("grassy-green", "Grassy Green", "ToolyTom", "A5D6A7", "1B5E20", "1B5E20"),
            t("mocha-madness", "Mocha Madness", "ToolyTom", "6D3B07", "926441", "E3B7A0"),
            t("radioactive-robert", "Radioactive Robert", "ToolyTom", "8AFF00", "ACFF00", "EBFF00"),
            t("julian-jellyfish", "Julian Jellyfish", "ToolyTom", "DF94CF", "B142C2", "5428A3"),
            t("mad-max", "Mad Max", "ToolyTom", "FEAB5B", "EC8502", "CA5D00"),
            t("false-king", "False King", "ToolyTom", "A8ACB2", "90AEBD", "90AEBD"),
            t("river-styx", "River Styx", "maxkarson", "000000", "0AF539", "0AF539"),
            t("crimson-planet", "Crimson Planet", "maxkarson", "000000", "FF1100", "FF1100"),
            t("cyclic", "Cyclic", "maxkarson", "000000", "00CCFF", "00CCFF"),
            t("hot-chocolate", "Hot Chocolate", "_Hotchocolate", "5D4037", "795548", "A1887F")
    );
    private static final Map<String, Theme> BY_ID = THEMES.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Theme::id, v -> v));

    private RalleThemeCatalog() {}
    public static List<Theme> themes() { return THEMES; }
    public static List<String> ids() { return THEMES.stream().map(Theme::id).toList(); }
    public static Theme get(String id) { return BY_ID.getOrDefault(id, BY_ID.get(DEFAULT_ID)); }
    public static boolean contains(String id) { return BY_ID.containsKey(id); }
    private static Theme t(String id, String name, String contributor, String bg, String outline, String accent) {
        return new Theme(id, name, contributor, Integer.parseInt(bg, 16), Integer.parseInt(outline, 16), Integer.parseInt(accent, 16));
    }
}

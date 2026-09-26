package org.kingdomfoxes.ralle.war.consumables;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Strict versioned JSON codec shared by local persistence, import, and export. */
public final class ConsumableHighlightJson {
    public static final int SCHEMA_VERSION = 1;
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "rules");
    private static final Set<String> RULE_FIELDS = Set.of("name", "aliases", "color", "rainbow");
    private static final Set<String> RULE_FIELDS_WITH_CHROMA = Set.of("name", "aliases", "color", "rainbow", "chroma");

    private ConsumableHighlightJson() {}

    public static List<ConsumableHighlightRule> decode(String json) {
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw invalid("Malformed JSON", exception);
        }
        if (!parsed.isJsonObject()) throw invalid("Root must be an object");
        var root = parsed.getAsJsonObject();
        requireFields(root, ROOT_FIELDS, "root");
        int schemaVersion = integer(root, "schemaVersion", "root");
        if (schemaVersion != SCHEMA_VERSION) {
            throw invalid("Unsupported schemaVersion: " + schemaVersion);
        }
        var rulesElement = root.get("rules");
        if (rulesElement == null || !rulesElement.isJsonArray()) throw invalid("rules must be an array");
        var rules = new ArrayList<ConsumableHighlightRule>();
        int index = 0;
        for (var element : rulesElement.getAsJsonArray()) {
            String path = "rules[" + index++ + "]";
            if (!element.isJsonObject()) throw invalid(path + " must be an object");
            var object = element.getAsJsonObject();
            if (object.has("chroma")) requireFields(object, RULE_FIELDS_WITH_CHROMA, path);
            else requireFields(object, RULE_FIELDS, path);
            var aliasesElement = object.get("aliases");
            if (aliasesElement == null || !aliasesElement.isJsonArray()) throw invalid(path + ".aliases must be an array");
            var aliases = new ArrayList<String>();
            for (var alias : aliasesElement.getAsJsonArray()) {
                if (!alias.isJsonPrimitive() || !alias.getAsJsonPrimitive().isString()) {
                    throw invalid(path + ".aliases must contain strings");
                }
                aliases.add(alias.getAsString());
            }
            rules.add(new ConsumableHighlightRule(
                    string(object, "name", path),
                    aliases,
                    HighlightStyle.parse(string(object, "color", path), bool(object, "rainbow", path),
                            object.has("chroma") && bool(object, "chroma", path))
            ));
        }
        return ConsumableHighlightValidation.validate(rules);
    }

    public static String encode(List<ConsumableHighlightRule> rules) {
        var validated = ConsumableHighlightValidation.validate(rules);
        var root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        var array = new JsonArray();
        for (var rule : validated) {
            var object = new JsonObject();
            object.addProperty("name", rule.name());
            var aliases = new JsonArray();
            rule.aliases().forEach(aliases::add);
            object.add("aliases", aliases);
            object.addProperty("color", rule.style().hex());
            object.addProperty("rainbow", rule.style().rainbow());
            object.addProperty("chroma", rule.style().chroma());
            array.add(object);
        }
        root.add("rules", array);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator();
    }

    private static void requireFields(JsonObject object, Set<String> expected, String path) {
        if (!object.keySet().equals(expected)) {
            var missing = expected.stream().filter(field -> !object.has(field)).toList();
            var extra = object.keySet().stream().filter(field -> !expected.contains(field)).toList();
            throw invalid(path + " has invalid fields (missing=" + missing + ", extra=" + extra + ")");
        }
    }

    private static String string(JsonObject object, String field, String path) {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid(path + "." + field + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject object, String field, String path) {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalid(path + "." + field + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonObject object, String field, String path) {
        var value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(path + "." + field + " must be an integer");
        }
        try {
            int result = value.getAsInt();
            if (value.getAsDouble() != result) throw invalid(path + "." + field + " must be an integer");
            return result;
        } catch (NumberFormatException exception) {
            throw invalid(path + "." + field + " must be an integer", exception);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }
}

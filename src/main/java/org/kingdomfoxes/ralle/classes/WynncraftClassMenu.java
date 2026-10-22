package org.kingdomfoxes.ralle.classes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/** Decoder for the character book observed in the 2026-10-19 game captures.
 * The book has no character UUIDs. References require an exact normalized live evidence match;
 * identical entries in different positions are deliberately not distinguishable.
 */
public final class WynncraftClassMenu {
    public static final int MENU_SIZE = 54;
    public static final String TITLE = new String(Character.toChars(0xCFFD5)) + "\uE01F";
    private static final List<Integer> CHARACTER_SLOTS = List.of(
            9, 10, 11, 18, 19, 20, 27, 28, 29, 36, 37, 38, 45, 46, 47);
    private static final Set<String> TYPES = Set.of("Archer", "Hunter", "Mage", "Dark Wizard",
            "Warrior", "Knight", "Assassin", "Ninja", "Shaman", "Skyseer");
    private static final Pattern LEGACY = Pattern.compile("§.");

    public record Result(List<ClassCharacter> characters, Map<String, Integer> slots,
                         Map<String, String> evidence, boolean complete, Map<String, String> legacyReferences,
                         boolean autoOpenEnabled) {
        public Result { characters = List.copyOf(characters); slots = Map.copyOf(slots); evidence = Map.copyOf(evidence); legacyReferences = Map.copyOf(legacyReferences); }
        public ClassScan scan(UUID account) { return new ClassScan(account, characters, complete); }
        public String canonicalReference(String reference) { return legacyReferences.getOrDefault(reference, reference); }
        /** No fallback by position, nickname or type. Normalized identifying evidence must match,
         * and its position-independent evidence must occur exactly once in this complete live book. */
        public OptionalInt resolve(String reference) {
            reference = canonicalReference(reference);
            String fingerprint = evidence.get(reference);
            Integer slot = slots.get(reference);
            if (!complete || slot == null || fingerprint == null
                    || evidence.values().stream().filter(fingerprint::equals).count() != 1) return OptionalInt.empty();
            return OptionalInt.of(slot);
        }
    }

    private WynncraftClassMenu() {}
    public static boolean matchesTitle(String title) { return TITLE.equals(title); }
    public static int characterNumber(String reference) {
        if (!reference.startsWith(ClassCharacter.OBSERVATION_PREFIX)) return -1;
        try {
            int number = CHARACTER_SLOTS.indexOf(Integer.parseInt(reference.split(":", 3)[1]));
            return number < 0 ? -1 : number + 1;
        } catch (RuntimeException invalid) { return -1; }
    }

    /** Reads only controls, allowing the prerequisite to be checked before character discovery is enabled. */
    public static Optional<Boolean> autoOpenState(String title, List<ItemStack> items) {
        if (!matchesTitle(title) || items.size() != MENU_SIZE
                || !marker(items.get(7), "Cancel Deletion", 559)
                || !marker(items.get(25), "Character Backups", 561)
                || !marker(items.get(51), "Selector Music", 597)
                // Auto Open changes its model when enabled; both states identify the same book.
                || !(marker(items.get(53), "Auto Open", 598)
                || marker(items.get(53), "Auto Open", 599))) return Optional.empty();
        return Optional.of(marker(items.get(53), "Auto Open", 599));
    }

    /** Exact title plus independent control signatures keep housing/inventory/editor menus out. */
    public static Optional<Result> decode(String title, List<ItemStack> items) {
        var autoOpen = autoOpenState(title, items);
        if (autoOpen.isEmpty()) return Optional.empty();
        var characters = new ArrayList<ClassCharacter>();
        var slots = new LinkedHashMap<String, Integer>();
        var evidence = new LinkedHashMap<String, String>();
        var legacyReferences = new LinkedHashMap<String, String>();
        boolean complete = true;
        for (int slot : CHARACTER_SLOTS) {
            var item = items.get(slot);
            if (marker(item, "Create a Character", 562) || marker(item, "Locked Character Slot", 585)) continue;
            var character = character(slot, item);
            if (character == null) { complete = false; continue; }
            characters.add(character);
            slots.put(character.id(), slot);
            evidence.put(character.id(), digest(evidence(item)));
            legacyReferences.put(ClassCharacter.OBSERVATION_PREFIX + slot + ":" + digest(slot + "\n" + rawEvidence(item)), character.id());
        }
        return Optional.of(new Result(characters, slots, evidence, complete, legacyReferences,
                autoOpen.get()));
    }

    private static ClassCharacter character(int slot, ItemStack item) {
        if (item == null || !item.is(Items.POTION)) return null;
        var lore = item.get(DataComponents.LORE);
        if (lore == null || lore.lines().size() > 32) return null;
        var lines = lore.lines().stream().map(line -> clean(line.getString())).toList();
        if (!lines.contains("Left-Click to Select") || !lines.contains("Right-Click to Edit")) return null;
        var classes = lines.stream().filter(line -> line.startsWith("- Class: "))
                .map(line -> line.substring(9)).toList();
        if (classes.size() != 1 || !TYPES.contains(classes.getFirst())) return null;
        // Required observed fields also prevent incomplete per-slot updates from creating entries.
        for (String required : List.of("- Time Played: ", "- Location: ", "- Level: "))
            if (lines.stream().filter(line -> line.startsWith(required) && line.length() > required.length()).count() != 1)
                return null;
        String type = classes.getFirst();
        String name = name(item);
        if (name.isBlank() || name.length() > 128 || lines.stream().anyMatch(line -> line.length() > 512)) return null;
        var model = item.get(DataComponents.CUSTOM_MODEL_DATA);
        if (model == null || model.floats().size() != 1 || !Float.isFinite(model.floats().getFirst())) return null;
        // Progress changes naturally when playing. Keep all identifying evidence and position,
        // but do not mistake play time, location, level or content progress for character identity.
        String reference = ClassCharacter.OBSERVATION_PREFIX + slot + ":" + digest(slot + "\n" + evidence(item));
        var potion = item.get(DataComponents.POTION_CONTENTS);
        int tint = potion == null ? 0xFFFFFF : potion.customColor().orElse(0xFFFFFF);
        return new ClassCharacter(reference, type, name.equals(type) ? "" : name,
                new ClassSprite(model.floats().getFirst(), tint));
    }

    private static String evidence(ItemStack item) {
        var lines = new ArrayList<>(item.get(DataComponents.LORE).lines().stream().map(line -> clean(line.getString())).toList());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String field : List.of("- Time Played: ", "- Location: ", "- Level: "))
                if (line.startsWith(field)) lines.set(i, field + "<progress>");
            if (line.equals("Content Progress") && i + 2 < lines.size()
                    && lines.get(i + 1).matches("\\[[|0-9.% ]+\\]") && lines.get(i + 2).matches("[0-9]+ of [0-9]+")) {
                lines.set(i + 1, "<content progress>");
                lines.set(i + 2, "<content count>");
            }
        }
        var potion = item.get(DataComponents.POTION_CONTENTS);
        int tint = potion == null ? 0xFFFFFF : potion.customColor().orElse(0xFFFFFF);
        return item.get(DataComponents.CUSTOM_MODEL_DATA).floats().getFirst() + "\n" + tint + "\n" + name(item) + "\n" + String.join("\n", lines);
    }

    /** Historical full-item hash, used only to prove migration of an existing old reference. */
    private static String rawEvidence(ItemStack item) {
        return item.get(DataComponents.CUSTOM_MODEL_DATA).floats().getFirst() + "\n" + name(item) + "\n"
                + String.join("\n", item.get(DataComponents.LORE).lines().stream().map(line -> clean(line.getString())).toList());
    }

    private static boolean marker(ItemStack item, String title, float modelNumber) {
        if (item == null || !item.is(Items.POTION) || !name(item).equals(title)) return false;
        var model = item.get(DataComponents.CUSTOM_MODEL_DATA);
        return model != null && model.floats().size() == 1 && model.floats().getFirst() == modelNumber;
    }
    private static String name(ItemStack item) {
        var name = item.get(DataComponents.CUSTOM_NAME);
        return name == null ? "" : clean(name.getString());
    }
    private static String clean(String text) {
        var plain = LEGACY.matcher(text).replaceAll("");
        var result = new StringBuilder();
        plain.codePoints().filter(cp -> Character.getType(cp) != Character.PRIVATE_USE
                && Character.getType(cp) != Character.UNASSIGNED && Character.getType(cp) != Character.SURROGATE
                && !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT)
                .forEach(result::appendCodePoint);
        return result.toString().strip();
    }
    private static String digest(String evidence) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(evidence.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

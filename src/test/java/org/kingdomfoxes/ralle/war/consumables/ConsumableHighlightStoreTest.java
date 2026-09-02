package org.kingdomfoxes.ralle.war.consumables;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumableHighlightStoreTest {
    @TempDir Path directory;

    @Test void missingFileSeedsExactOrderedWynnColourDefaults() throws Exception {
        var path = directory.resolve("ralle-consumable-highlights.json");
        var store = new ConsumableHighlightStore(path);
        assertEquals(List.of("Strength", "Dexterity", "Intelligence", "Defence", "Agility", "Rainbow",
                        "Healing Efficiency", "Mana and Spell", "JH"),
                store.snapshot().stream().map(ConsumableHighlightRule::name).toList());
        assertEquals(List.of("str", "earth dmg"), store.snapshot().getFirst().aliases());
        assertFalse(store.snapshot().getFirst().aliases().contains("strength"));
        assertTrue(store.snapshot().get(5).style().rainbow());
        assertEquals(store.snapshot(), ConsumableHighlightJson.decode(Files.readString(path)));
    }

    @Test void roundTripsEmptyAndStyledRules() {
        assertEquals(List.of(), ConsumableHighlightJson.decode(ConsumableHighlightJson.encode(List.of())));
        var rules = List.of(new ConsumableHighlightRule("Mana", List.of("water dmg"),
                new HighlightStyle(0x123ABC, true)));
        assertEquals(rules, ConsumableHighlightJson.decode(ConsumableHighlightJson.encode(rules)));
    }

    @Test void validationFiltersBlankAliasesAndRejectsBlankLongOrNormalizedDuplicates() {
        var style = new HighlightStyle(1, false);
        assertEquals(List.of("mana"), ConsumableHighlightValidation.validate(List.of(
                new ConsumableHighlightRule("Spell", List.of("", "  ", "mana"), style))).getFirst().aliases());
        assertThrows(IllegalArgumentException.class, () -> ConsumableHighlightValidation.validate(List.of(
                new ConsumableHighlightRule(" ", List.of(), style))));
        assertThrows(IllegalArgumentException.class, () -> ConsumableHighlightValidation.validate(List.of(
                new ConsumableHighlightRule("x".repeat(51), List.of(), style))));
        assertThrows(IllegalArgumentException.class, () -> ConsumableHighlightValidation.validate(List.of(
                new ConsumableHighlightRule("Water-Dmg", List.of(), style),
                new ConsumableHighlightRule("Other", List.of("water dmg"), style))));
    }

    @Test void publishesAndNotifiesOnlyAfterSuccessfulPersistence() throws Exception {
        var store = new ConsumableHighlightStore(directory.resolve("rules.json"));
        var calls = new AtomicInteger();
        store.onChanged(ignored -> calls.incrementAndGet());
        store.replace(List.of());
        assertEquals(List.of(), store.snapshot());
        assertEquals(1, calls.get());

        var blocker = directory.resolve("blocker");
        Files.writeString(blocker, "not a directory");
        var failing = new ConsumableHighlightStore(blocker.resolve("rules.json"));
        var before = failing.snapshot();
        assertThrows(IOException.class, () -> failing.replace(List.of()));
        assertEquals(before, failing.snapshot());
    }

    @Test void corruptFileFallsBackWithoutOverwritingAndResetReplacesEverything() throws Exception {
        var path = directory.resolve("rules.json");
        Files.writeString(path, "not json");
        var store = new ConsumableHighlightStore(path);
        assertEquals("not json", Files.readString(path));
        assertEquals(9, store.snapshot().size());
        store.replace(List.of());
        store.resetDefaults();
        assertEquals(9, store.snapshot().size());
    }

    @Test void importAppendsInOrderAndRejectsConflictsTransactionally() throws Exception {
        var store = new ConsumableHighlightStore(directory.resolve("rules.json"));
        store.replace(List.of(new ConsumableHighlightRule("First", List.of(), new HighlightStyle(1, false))));
        var source = directory.resolve("import.json");
        Files.writeString(source, ConsumableHighlightJson.encode(List.of(
                new ConsumableHighlightRule("Second", List.of("two"), new HighlightStyle(2, false)),
                new ConsumableHighlightRule("Third", List.of(), new HighlightStyle(3, false)))));
        store.importFile(source);
        assertEquals(List.of("First", "Second", "Third"),
                store.snapshot().stream().map(ConsumableHighlightRule::name).toList());

        var before = store.snapshot();
        Files.writeString(source, ConsumableHighlightJson.encode(List.of(
                new ConsumableHighlightRule("Conflict", List.of("two"), new HighlightStyle(4, false)))));
        assertThrows(IllegalArgumentException.class, () -> store.importFile(source));
        assertEquals(before, store.snapshot());
    }

    @Test void importRejectsMalformedUnsupportedInternallyDuplicateAndOversizedFiles() throws Exception {
        var store = new ConsumableHighlightStore(directory.resolve("rules.json"));
        var source = directory.resolve("import.json");
        for (var invalid : List.of(
                "{",
                "{\"schemaVersion\":2,\"rules\":[]}",
                "{\"schemaVersion\":1,\"rules\":[{\"name\":\"A\",\"aliases\":[\"a\"],\"color\":\"#000000\",\"rainbow\":false}]}"
        )) {
            Files.writeString(source, invalid);
            assertThrows(IllegalArgumentException.class, () -> store.importFile(source));
        }
        Files.write(source, new byte[(int) ConsumableHighlightStore.MAX_IMPORT_BYTES + 1]);
        assertThrows(IllegalArgumentException.class, () -> store.importFile(source));
    }
}

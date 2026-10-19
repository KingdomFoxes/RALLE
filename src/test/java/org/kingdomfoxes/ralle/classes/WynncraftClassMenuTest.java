package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Replay actual game components, including their nested names/lore, glyphs and models. */
class WynncraftClassMenuTest {
    private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void capturedBookDetectsFourRealClassesAndExcludesCreateLockedAndUtilitySlots() throws Exception {
        var result = decode(fixture());
        assertTrue(result.complete());
        assertEquals(List.of("Archer", "Mage", "Shaman", "Warrior"), result.characters().stream().map(ClassCharacter::type).toList());
        assertEquals(List.of(9, 10, 11, 18), result.characters().stream().map(c -> result.slots().get(c.id())).toList());
        assertEquals(List.of(1, 2, 3, 4), result.characters().stream().map(c -> WynncraftClassMenu.characterNumber(c.id())).toList());
        assertTrue(result.characters().stream().allMatch(c -> c.nickname().isEmpty()));
        assertTrue(result.characters().stream().noneMatch(ClassCharacter::hasStableIdentity));
        assertEquals(4, result.characters().stream().map(ClassCharacter::id).distinct().count());
        assertEquals(List.of(575F, 580F, 582F, 584F), result.characters().stream().map(c -> c.sprite().model()).toList());
        for (var character : result.characters()) assertEquals(result.slots().get(character.id()).intValue(), result.resolve(character.id()).orElseThrow());
    }

    @Test void incompleteCaptureCannotEraseMageOrItsCustomShortcut() throws Exception {
        var fixture = fixture();
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(decode(fixture).scan(ACCOUNT));
        var mage = store.account(ACCOUNT).characters().get(1);
        store.add(ACCOUNT, mage.id());
        var shortcut = new ClassShortcut(mage.id(), "My local name", "key.keyboard.g");
        store.edit(ACCOUNT, shortcut);
        var slots = fixture.getAsJsonArray("slots");
        for (int i = 0; i < slots.size(); i++) if (slots.get(i).getAsJsonObject().get("slot").getAsInt() == 10) {
            slots.remove(i);
            break;
        }
        var partial = decode(fixture);
        assertFalse(partial.complete());
        assertTrue(partial.resolve(mage.id()).isEmpty());
        assertEquals(3, partial.characters().size());
        store.reconcile(partial.scan(ACCOUNT));
        assertEquals(4, store.account(ACCOUNT).characters().size());
        assertEquals(List.of(shortcut), store.account(ACCOUNT).shortcuts());
        // An explicit empty-slot marker makes a subsequent full scan complete.
        var empty = slot(fixture, 19).deepCopy();
        empty.addProperty("slot", 10);
        slots.add(empty);
        var complete = decode(fixture);
        assertTrue(complete.complete());
        store.reconcile(complete.scan(ACCOUNT));
        assertNull(store.account(ACCOUNT).character(mage.id()));
        assertEquals(List.of(shortcut), store.account(ACCOUNT).shortcuts());
    }

    @Test void duplicateNamesTypesAndRenamedEntriesNeverTurnIntoTrustedIdentities() throws Exception {
        var source = fixture();
        slot(source, 9).add("item", slot(source, 10).get("item").deepCopy());
        var twoMages = decode(source);
        assertEquals(2, twoMages.characters().stream().filter(c -> c.type().equals("Mage")).count());
        assertNotEquals(twoMages.characters().get(0).id(), twoMages.characters().get(1).id());
        assertTrue(twoMages.resolve(twoMages.characters().get(0).id()).isEmpty());
        assertTrue(twoMages.resolve(twoMages.characters().get(1).id()).isEmpty());
        assertTrue(twoMages.resolve(twoMages.characters().get(2).id()).isPresent());
        String original = twoMages.characters().get(1).id();
        var renamed = new JsonObject();
        renamed.addProperty("text", "Renamed Mage");
        slot(source, 10).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", renamed);
        var renamedBook = decode(source);
        var changed = renamedBook.characters().get(1);
        assertEquals("Renamed Mage", changed.nickname());
        assertNotEquals(original, changed.id());
        assertFalse(changed.hasStableIdentity());
        assertTrue(renamedBook.resolve(original).isEmpty());
        assertEquals(10, renamedBook.resolve(changed.id()).orElseThrow());
    }

    @Test void capturedObservationCanSwitchOnlyThroughAnExactCompleteUniqueLiveBook() throws Exception {
        var saved = decode(fixture()).characters().get(1);
        var calls = new ArrayList<String>();
        var controller = new ClassSwitchController(new ClassSwitchController.Actions() {
            public void captureFrame() { calls.add("capture"); }
            public void sendClassCommand() { calls.add("class"); }
            public void rightClickSelectionStage() { calls.add("right"); }
            public void selectCharacter(int containerId, int slot) { calls.add("select:" + containerId + ":" + slot); }
            public void releaseFrame() { calls.add("release"); }
            public void failed(ClassSwitchController.Failure failure) { fail(failure.toString()); }
        });
        assertTrue(controller.begin(ACCOUNT, saved.id(), null, true, 0));
        controller.frameCaptured(); controller.selectionStage(0);
        var live = decode(fixture());
        controller.menu(7, List.of(new ClassSwitchController.Target(saved.id(), live.resolve(saved.id()).orElseThrow())), true);
        controller.tick(ACCOUNT, true, 1600);
        assertTrue(controller.active());
        controller.menuClosed(7, 0);
        assertEquals(ClassSwitchController.State.WAITING_WORLD, controller.state());
        controller.tick(ACCOUNT, true, 1600);
        assertEquals(List.of("capture", "class", "right", "select:7:10", "release"), calls);

        var moved = fixture();
        var from = slot(moved, 10);
        var to = slot(moved, 19);
        from.addProperty("slot", 19); to.addProperty("slot", 10);
        assertTrue(decode(moved).resolve(saved.id()).isEmpty()); // Never follow a similar entry to a new position.
    }

    @Test void unrelatedMenusWrongAccountsCorruptSlotsAndChangedToolbarNeverReconcile() throws Exception {
        var fixture = fixture();
        assertTrue(ClassMenuEvidence.decode(fixture, UUID.randomUUID(), JsonOps.INSTANCE).isEmpty());
        fixture.addProperty("title", "Housing Islands");
        assertTrue(ClassMenuEvidence.decode(fixture, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        fixture = fixture();
        slot(fixture, 7).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(562));
        assertTrue(ClassMenuEvidence.decode(fixture, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        fixture = fixture();
        fixture.getAsJsonArray("slots").add(slot(fixture, 9).deepCopy());
        assertTrue(ClassMenuEvidence.decode(fixture, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        fixture = fixture();
        slot(fixture, 9).addProperty("slot", 9.5);
        assertTrue(ClassMenuEvidence.decode(fixture, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        fixture = fixture();
        slot(fixture, 9).getAsJsonObject("item").addProperty("id", "not:a-real-item");
        assertTrue(ClassMenuEvidence.decode(fixture, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        assertTrue(ClassMenuEvidence.decode(new JsonObject(), ACCOUNT, JsonOps.INSTANCE).isEmpty());
    }

    @Test void incompletePerCharacterDataIsPartialAndCannotInventAnEntry() throws Exception {
        var fixture = fixture();
        slot(fixture, 10).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        var decoded = decode(fixture);
        assertFalse(decoded.complete());
        assertEquals(List.of("Archer", "Shaman", "Warrior"), decoded.characters().stream().map(ClassCharacter::type).toList());
    }

    @Test void aPartialRefreshRetiresOldEvidenceWithoutDuplicatingOrRedirectingShortcuts() throws Exception {
        var fixture = fixture();
        var original = decode(fixture);
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(original.scan(ACCOUNT));
        var oldMage = original.characters().get(1);
        store.add(ACCOUNT, oldMage.id());
        var renamed = new JsonObject(); renamed.addProperty("text", "Changed Mage");
        slot(fixture, 10).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", renamed);
        var incoming = decode(fixture).characters().get(1);
        store.reconcile(new ClassScan(ACCOUNT, List.of(incoming), false));
        assertEquals(4, store.account(ACCOUNT).characters().size());
        assertNull(store.account(ACCOUNT).character(oldMage.id()));
        assertEquals(oldMage.id(), store.account(ACCOUNT).shortcuts().getFirst().characterId());
        assertNotNull(store.account(ACCOUNT).character(incoming.id()));
    }

    @Test void oldFilesCanHydrateSpritesWithoutChangingNamesBindingsOrResurrectingEntries() throws Exception {
        var decoded = decode(fixture());
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        var first = decoded.characters().getFirst();
        store.reconcile(new ClassScan(ACCOUNT, List.of(new ClassCharacter(first.id(), first.type(), first.nickname())), true));
        store.add(ACCOUNT, first.id());
        store.edit(ACCOUNT, new ClassShortcut(first.id(), "Local override", "key.keyboard.g"));
        var sprites = new HashMap<String, ClassSprite>();
        decoded.characters().forEach(c -> sprites.put(c.id(), c.sprite()));
        store.updateSprites(ACCOUNT, sprites);
        var loaded = new ClassSelectorStore(directory.resolve("classes.json")).account(ACCOUNT);
        assertEquals(1, loaded.characters().size());
        assertEquals(first.sprite(), loaded.characters().getFirst().sprite());
        assertEquals(new ClassShortcut(first.id(), "Local override", "key.keyboard.g"), loaded.shortcuts().getFirst());
    }

    @Test void onlyExplicitRetargetingTransfersPreferencesToChangedEvidence() throws Exception {
        var fixture = fixture();
        var original = decode(fixture);
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(original.scan(ACCOUNT));
        var archer = original.characters().get(0);
        var mage = original.characters().get(1);
        store.add(ACCOUNT, archer.id()); store.add(ACCOUNT, mage.id());
        var old = new ClassShortcut(mage.id(), "My mage", "key.keyboard.g");
        store.edit(ACCOUNT, old);
        var renamed = new JsonObject(); renamed.addProperty("text", "Fresh Mage");
        slot(fixture, 10).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", renamed);
        var fresh = decode(fixture).characters().get(1);
        store.reconcile(new ClassScan(ACCOUNT, List.of(fresh), false));
        assertEquals(mage.id(), store.account(ACCOUNT).shortcuts().get(1).characterId());
        assertThrows(IllegalArgumentException.class, () -> store.retarget(ACCOUNT, old, archer.id()));
        store.retarget(ACCOUNT, old, fresh.id());
        assertEquals(List.of(archer.id(), fresh.id()), store.account(ACCOUNT).shortcuts().stream().map(ClassShortcut::characterId).toList());
        assertEquals(new ClassShortcut(fresh.id(), old.name(), old.binding()), store.account(ACCOUNT).shortcuts().get(1));
    }

    @Test void captureRecoveryCanSeedTheEditorAndResetClearsOnlyShortcuts() throws Exception {
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        assertFalse(store.hasAccount(ACCOUNT));
        var discovery = new ClassDiscovery(store);
        discovery.session(ACCOUNT, true);
        var result = decode(fixture());
        assertTrue(discovery.observe(new ClassScan(ACCOUNT, result.characters(), false), discovery.generation()));
        assertTrue(store.hasAccount(ACCOUNT));
        assertEquals(4, store.account(ACCOUNT).characters().size());
        assertTrue(store.account(ACCOUNT).shortcuts().isEmpty());
        store.add(ACCOUNT, result.characters().getFirst().id());
        store.clearShortcuts(ACCOUNT);
        var restored = new ClassSelectorStore(directory.resolve("classes.json"));
        assertTrue(restored.hasAccount(ACCOUNT));
        assertEquals(result.characters(), restored.account(ACCOUNT).characters());
        assertTrue(restored.account(ACCOUNT).shortcuts().isEmpty());
    }

    @Test void playingXThenYDoesNotInvalidateXWhenItsProgressChanges() throws Exception {
        var source = fixture();
        var original = decode(source);
        var x = original.characters().get(0);
        var y = original.characters().get(1);
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(original.scan(ACCOUNT)); store.add(ACCOUNT, x.id()); store.add(ACCOUNT, y.id());
        store.edit(ACCOUNT, new ClassShortcut(x.id(), "My archer", "key.keyboard.g"));
        var lore = slot(source, 9).getAsJsonObject("item").getAsJsonObject("components").getAsJsonArray("minecraft:lore");
        lore.set(2, text("- Time Played: 999.99 hours"));
        lore.set(3, text("- Location: Detlas"));
        lore.set(4, text("- Level: 121 (89.34%)"));
        lore.set(7, text("[||||||||99%||||||||]"));
        lore.set(8, text("1299 of 1400"));
        var afterPlayingX = decode(source);
        store.reconcile(afterPlayingX.scan(ACCOUNT));
        store.recordLoad(ACCOUNT, x.id(), "archer01"); store.recordLoad(ACCOUNT, y.id(), "mage0001");
        var restored = new ClassSelectorStore(path);
        assertEquals(original.characters(), restored.account(ACCOUNT).characters());
        assertEquals(new ClassShortcut(x.id(), "My archer", "key.keyboard.g"), restored.account(ACCOUNT).shortcuts().getFirst());
        assertEquals(9, afterPlayingX.resolve(x.id()).orElseThrow());
        assertEquals(10, afterPlayingX.resolve(y.id()).orElseThrow());
        assertEquals(x.id(), restored.referenceForToken(ACCOUNT, "archer01"));
    }

    @Test void legacyMigrationUsesTheOldFullHashAndPreservesShortcutOrderBindingsAndLoadData() throws Exception {
        var decoded = decode(fixture());
        var current = decoded.characters().getFirst();
        String legacy = decoded.legacyReferences().entrySet().stream().filter(entry -> entry.getValue().equals(current.id()))
                .findFirst().orElseThrow().getKey();
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(new ClassScan(ACCOUNT, List.of(new ClassCharacter(legacy, current.type(), current.nickname(), current.sprite())), true));
        store.add(ACCOUNT, legacy); store.edit(ACCOUNT, new ClassShortcut(legacy, "Custom", "key.keyboard.g"));
        store.recordLoad(ACCOUNT, legacy, "archer01");
        store.migrateReferences(ACCOUNT, decoded.legacyReferences());
        assertEquals(new ClassShortcut(current.id(), "Custom", "key.keyboard.g"), store.account(ACCOUNT).shortcuts().getFirst());
        assertEquals(current, store.account(ACCOUNT).characters().getFirst());
        assertEquals(current.id(), store.lastLoaded(ACCOUNT));
        assertEquals(current.id(), store.referenceForToken(ACCOUNT, "archer01"));
        assertEquals(9, decoded.resolve(legacy).orElseThrow());
    }

    @Test void progressNormalizationStillRejectsRenamesIconChangesAndAmbiguousDuplicates() throws Exception {
        var source = fixture();
        String old = decode(source).characters().getFirst().id();
        slot(source, 9).getAsJsonObject("item").getAsJsonObject("components").getAsJsonObject("minecraft:custom_model_data")
                .getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(580));
        assertTrue(decode(source).resolve(old).isEmpty());
        source = fixture();
        slot(source, 19).add("item", slot(source, 9).get("item").deepCopy());
        var lore = slot(source, 19).getAsJsonObject("item").getAsJsonObject("components").getAsJsonArray("minecraft:lore");
        lore.set(2, text("- Time Played: 1 hour"));
        assertTrue(decode(source).resolve(old).isEmpty()); // Different progress cannot disambiguate identity.
    }

    @Test void migrationRepairsAlreadyRetiredProgressHashesWithoutResurrectingDeletedCharacters() throws Exception {
        var decoded = decode(fixture());
        var archer = decoded.characters().getFirst();
        String legacy = decoded.legacyReferences().entrySet().stream().filter(entry -> entry.getValue().equals(archer.id()))
                .findFirst().orElseThrow().getKey();
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(new ClassScan(ACCOUNT, List.of(new ClassCharacter(legacy, archer.type(), archer.nickname(), archer.sprite())), true));
        store.add(ACCOUNT, legacy);
        store.reconcile(new ClassScan(ACCOUNT, List.of(new ClassCharacter("observed:9:" + "a".repeat(64),
                archer.type(), archer.nickname(), archer.sprite())), true));
        store.migrateReferences(ACCOUNT, decoded.legacyReferences(), decoded.characters());
        assertEquals(archer, store.account(ACCOUNT).characters().getFirst());
        assertEquals(archer.id(), store.account(ACCOUNT).shortcuts().getFirst().characterId());

        store.reconcile(new ClassScan(ACCOUNT, List.of(), true));
        store.migrateReferences(ACCOUNT, decoded.legacyReferences(), decoded.characters());
        assertTrue(store.account(ACCOUNT).characters().isEmpty());
    }

    private static JsonObject text(String value) {
        var text = new JsonObject(); text.addProperty("text", value); return text;
    }

    private static WynncraftClassMenu.Result decode(JsonObject evidence) {
        return ClassMenuEvidence.decode(evidence, ACCOUNT, JsonOps.INSTANCE).orElseThrow();
    }
    private static JsonObject fixture() throws Exception {
        try (var input = WynncraftClassMenuTest.class.getResourceAsStream("/classes/wynncraft-character-book-2026-10-19.json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static JsonObject slot(JsonObject source, int slot) {
        return source.getAsJsonArray("slots").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("slot").getAsInt() == slot).findFirst().orElseThrow();
    }
}

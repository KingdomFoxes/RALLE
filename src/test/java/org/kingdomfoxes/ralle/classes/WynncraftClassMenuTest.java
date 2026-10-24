package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test void latestSixCharacterCaptureReplaysAllSixSavedReferences() throws Exception {
        var source = fixture("wynncraft-character-book-2026-10-23.json");
        var first = decode(source);
        // Reconstruct the exact v2 layout from the failed session, which discarded progression.
        var saved = first.characters().stream().map(c -> new ClassCharacter("observed:" + c.book().slot() + ":"
                + (c.type().equals("Mage") || c.type().equals("Archer") ? "ambiguous-v2-" : "v2-")
                + c.identity().fingerprint(), c.type(), c.nickname(), c.sprite(), c.identity())).toList();
        var result = decode(source, saved);
        assertTrue(result.complete());
        assertEquals(6, result.characters().size());
        assertEquals(saved.stream().map(ClassCharacter::id).toList(), result.characters().stream().map(ClassCharacter::id).toList());
        for (var character : saved)
            assertEquals(WynncraftClassMenu.bookSlot(character), result.resolve(character.id()).orElseThrow());
        assertEquals(11, result.resolve("observed:11:v2-9cac585c8795f5e54e34ef3c4d5c1cc8b62dae6979f28ab82941d58b4224fb0b").orElseThrow());
        assertEquals(18, result.resolve("observed:18:v2-c3e9b04aaff81cfa954612020f2b35db6229079deed14d75ab549dcc92840eec").orElseThrow());
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

    @Test void capturedAutoOpenEnabledBookResolvesTheSameSavedCharacters() throws Exception {
        var saved = decode(fixture());
        var enabled = decode(fixture("wynncraft-character-book-auto-open-2026-10-19.json"), saved.characters());
        assertTrue(enabled.complete());
        assertFalse(saved.autoOpenEnabled());
        assertTrue(enabled.autoOpenEnabled());
        assertEquals(saved.characters().stream().map(ClassCharacter::id).toList(), enabled.characters().stream().map(ClassCharacter::id).toList());
        for (var character : saved.characters())
            assertEquals(saved.resolve(character.id()), enabled.resolve(character.id()));
    }

    @Test void disablingAutoOpenInTheLiveBookDoesNotInvalidateDetectedCharacters() throws Exception {
        var fixture = fixture("wynncraft-character-book-auto-open-2026-10-19.json");
        var enabled = decode(fixture);
        slot(fixture, 53).add("item", slot(fixture(), 53).get("item").deepCopy());
        var disabled = decode(fixture);
        assertFalse(disabled.autoOpenEnabled());
        assertEquals(enabled.characters(), disabled.characters());
        assertTrue(disabled.complete());
    }

    @Test void musicArtworkChangesCannotPreventLiveBookRecognitionOrCharacterSelection() throws Exception {
        var source = fixture("wynncraft-character-book-auto-open-2026-10-19.json");
        var saved = decode(source);
        var music = slot(source, 51).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats");
        music.set(0, new com.google.gson.JsonPrimitive(596));
        var live = decode(source, saved.characters());
        assertTrue(live.complete());
        assertTrue(live.autoOpenEnabled());
        assertEquals(saved.characters(), live.characters());
        for (var character : saved.characters())
            assertEquals(saved.resolve(character.id()), live.resolve(character.id()));
        // Auto Open is read exclusively from its own control, even if music is enabled.
        music.set(0, new com.google.gson.JsonPrimitive(597));
        slot(source, 53).add("item", slot(fixture(), 53).get("item").deepCopy());
        var disabled = decode(source, saved.characters());
        assertFalse(disabled.autoOpenEnabled());
        assertEquals(saved.characters(), disabled.characters());
    }

    @Test void autoOpenControlStillRequiresItsObservedNameItemAndModel() throws Exception {
        var enabled = fixture("wynncraft-character-book-auto-open-2026-10-19.json");
        var control = slot(enabled, 53).getAsJsonObject("item");
        var components = control.getAsJsonObject("components");
        var models = components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats");
        models.set(0, new com.google.gson.JsonPrimitive(600));
        assertTrue(ClassMenuEvidence.decode(enabled, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        models.set(0, new com.google.gson.JsonPrimitive(599));
        components.add("minecraft:custom_name", text("Unrelated control"));
        assertTrue(ClassMenuEvidence.decode(enabled, ACCOUNT, JsonOps.INSTANCE).isEmpty());
        components.add("minecraft:custom_name", text("Auto Open"));
        control.addProperty("id", "minecraft:stone");
        assertTrue(ClassMenuEvidence.decode(enabled, ACCOUNT, JsonOps.INSTANCE).isEmpty());
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
        controller.tick(ACCOUNT, true, 799);
        assertTrue(controller.active());
        controller.menuClosed(7, 800);
        assertEquals(ClassSwitchController.State.WAITING_WORLD, controller.state());
        controller.tick(ACCOUNT, true, 2400);
        assertEquals(List.of("capture", "class", "right", "select:7:10", "release"), calls);

        var moved = fixture();
        var from = slot(moved, 10);
        var to = slot(moved, 19);
        from.addProperty("slot", 19); to.addProperty("slot", 10);
        assertEquals(19, decode(moved, live.characters()).resolve(saved.id()).orElseThrow());
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

    @Test void aPartialRefreshRetainsOldWitnessesUntilACompleteBookCanReconcileThem() throws Exception {
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
        assertEquals(5, store.account(ACCOUNT).characters().size());
        assertNotNull(store.account(ACCOUNT).character(oldMage.id()));
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
        var afterPlayingX = decode(source, store.account(ACCOUNT).characters());
        store.reconcile(afterPlayingX.scan(ACCOUNT));
        store.recordLoad(ACCOUNT, x.id(), "archer01"); store.recordLoad(ACCOUNT, y.id(), "mage0001");
        var restored = new ClassSelectorStore(path);
        assertEquals(original.characters().stream().map(ClassCharacter::id).toList(), restored.account(ACCOUNT).characters().stream().map(ClassCharacter::id).toList());
        assertEquals(new ClassShortcut(x.id(), "My archer", "key.keyboard.g"), restored.account(ACCOUNT).shortcuts().getFirst());
        assertEquals(9, afterPlayingX.resolve(x.id()).orElseThrow());
        assertEquals(10, afterPlayingX.resolve(y.id()).orElseThrow());
        assertEquals(x.id(), restored.referenceForToken(ACCOUNT, "archer01"));
    }

    @Test void legacyMigrationUsesTheOldFullHashAndPreservesShortcutOrderBindingsAndLoadData() throws Exception {
        var decoded = decode(fixture());
        var current = decoded.characters().getFirst();
        String legacy = decoded.legacyReferences().entrySet().stream().filter(entry -> !entry.getKey().contains(":v2-") && entry.getValue().equals(current.id()))
                .findFirst().orElseThrow().getKey();
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(new ClassScan(ACCOUNT, List.of(new ClassCharacter(legacy, current.type(), current.nickname(), current.sprite())), true));
        store.add(ACCOUNT, legacy); store.edit(ACCOUNT, new ClassShortcut(legacy, "Custom", "key.keyboard.g"));
        store.recordLoad(ACCOUNT, legacy, "archer01");
        store.migrateReferences(ACCOUNT, decoded.legacyReferences());
        assertEquals(new ClassShortcut(current.id(), "Custom", "key.keyboard.g"), store.account(ACCOUNT).shortcuts().getFirst());
        assertEquals(current.id(), store.account(ACCOUNT).characters().getFirst().id());
        assertEquals(current.id(), store.lastLoaded(ACCOUNT));
        assertEquals(current.id(), store.referenceForToken(ACCOUNT, "archer01"));
        assertEquals(9, decoded.resolve(legacy).orElseThrow());
    }

    @Test void cosmeticChangesRefreshSpritesWhileAmbiguousDuplicatesRemainUnselectable() throws Exception {
        var source = fixture();
        var saved = decode(source).characters();
        String old = saved.getFirst().id();
        slot(source, 9).getAsJsonObject("item").getAsJsonObject("components").getAsJsonObject("minecraft:custom_model_data")
                .getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(580));
        var changed = decode(source, saved);
        assertEquals(old, changed.characters().getFirst().id());
        assertEquals(580F, changed.characters().getFirst().sprite().model());
        assertEquals(9, changed.resolve(old).orElseThrow());
        source = fixture();
        slot(source, 19).add("item", slot(source, 9).get("item").deepCopy());
        var lore = slot(source, 19).getAsJsonObject("item").getAsJsonObject("components").getAsJsonArray("minecraft:lore");
        lore.set(2, text("- Time Played: 1 hour"));
        var separate = decode(source, saved);
        assertEquals(9, separate.resolve(old).orElseThrow()); // Exact distinct progression witnesses separate repeated types.
        // Truly identical full observations still cannot authorize a choice.
        lore.set(2, slot(fixture(), 9).getAsJsonObject("item").getAsJsonObject("components").getAsJsonArray("minecraft:lore").get(2).deepCopy());
        assertTrue(decode(source, saved).resolve(old).isEmpty());
    }

    @Test void migrationRepairsAlreadyRetiredProgressHashesWithoutResurrectingDeletedCharacters() throws Exception {
        var decoded = decode(fixture());
        var archer = decoded.characters().getFirst();
        String legacy = decoded.legacyReferences().entrySet().stream().filter(entry -> !entry.getKey().contains(":v2-") && entry.getValue().equals(archer.id()))
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

    @Test void iconEditsRefreshPersistedArtworkWithoutLosingShortcutPreferencesOrLoadKnowledge() throws Exception {
        var source = fixture();
        var original = decode(source);
        var warrior = original.characters().getLast();
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(original.scan(ACCOUNT)); store.add(ACCOUNT, warrior.id());
        var shortcut = new ClassShortcut(warrior.id(), "Tank", "key.keyboard.g");
        store.edit(ACCOUNT, shortcut); store.recordLoad(ACCOUNT, warrior.id(), "warrior1");
        store.recordAutoOpen(ACCOUNT, true);
        var components = slot(source, 18).getAsJsonObject("item").getAsJsonObject("components");
        components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x12AB34);
        components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats")
                .set(0, new com.google.gson.JsonPrimitive(583));
        var changed = decode(source, original.characters());
        store.reconcile(changed.scan(ACCOUNT));
        var restored = new ClassSelectorStore(path);
        assertEquals(List.of(shortcut), restored.account(ACCOUNT).shortcuts());
        assertEquals(new ClassSprite(583F, 0x12AB34), restored.account(ACCOUNT).character(warrior.id()).sprite());
        assertEquals(warrior.identity(), restored.account(ACCOUNT).character(warrior.id()).identity());
        assertEquals(warrior.id(), restored.referenceForToken(ACCOUNT, "warrior1"));
        assertTrue(restored.autoOpenEnabled(ACCOUNT));
        assertEquals(18, changed.resolve(warrior.id()).orElseThrow());
        assertEquals(2, JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject().get("version").getAsInt());
    }

    @Test void creatingANeighborCannotInvalidateThePrecedingEntryThroughArtworkOrSpacing() throws Exception {
        var source = fixture();
        var original = decode(source);
        var preceding = original.characters().getLast();
        var added = slot(source, 10).get("item").deepCopy();
        added.getAsJsonObject().getAsJsonObject("components").add("minecraft:custom_name", text("New mage"));
        slot(source, 19).add("item", added);
        var components = slot(source, 18).getAsJsonObject("item").getAsJsonObject("components");
        components.add("minecraft:custom_name", text("\uE000 Warrior \uE001"));
        components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0xFFFFFF);
        components.getAsJsonArray("minecraft:lore").add(text("\uE002   "));
        var changed = decode(source, original.characters());
        assertTrue(changed.complete());
        assertEquals(5, changed.characters().size());
        assertEquals(preceding.identity(), changed.characters().get(3).identity());
        assertEquals(preceding.id(), changed.characters().get(3).id());
        assertEquals(18, changed.resolve(preceding.id()).orElseThrow());
    }

    @Test void uniqueEvidenceFollowsBookInsertionAndPreservesOrderKeysTokensAndLateSaves() throws Exception {
        var source = fixture();
        var original = decode(source);
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(original.scan(ACCOUNT));
        for (var character : original.characters()) {
            store.add(ACCOUNT, character.id());
            store.edit(ACCOUNT, new ClassShortcut(character.id(), "Local " + character.type(), "key.keyboard.g"));
        }
        var mage = original.characters().get(1);
        var now = new java.util.concurrent.atomic.AtomicLong();
        var tracker = new ClassLoadTracker(store, now::get);
        tracker.session(ACCOUNT); tracker.selected(mage.id(), 7); tracker.closed(7, 7);
        tracker.compass("mage0001"); now.set(1600);
        assertTrue(tracker.gameplay(false));
        assertEquals(mage.id(), tracker.current());
        // Insert a new character first and shift every previous character one position.
        var positions = List.of(9, 10, 11, 18, 19);
        for (int i = 4; i > 0; i--) slot(source, positions.get(i)).add("item", slot(source, positions.get(i - 1)).get("item").deepCopy());
        slot(source, 9).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", text("New archer"));
        var moved = decode(source, store.account(ACCOUNT).characters());
        assertEquals(11, moved.resolve(mage.id()).orElseThrow());
        store.migrateReferences(ACCOUNT, moved.legacyReferences(), moved.characters());
        store.reconcile(moved.scan(ACCOUNT));
        store.recordLoadAsync(ACCOUNT, mage.id(), "mage0001"); store.flushWrites();
        var restored = new ClassSelectorStore(path);
        for (int i = 0; i < 4; i++) {
            var shortcut = restored.account(ACCOUNT).shortcuts().get(i);
            assertEquals(moved.characters().get(i + 1).id(), shortcut.characterId());
            assertEquals("Local " + original.characters().get(i).type(), shortcut.name());
            assertEquals("key.keyboard.g", shortcut.binding());
        }
        String movedMage = moved.characters().get(2).id();
        assertEquals(movedMage, restored.lastLoaded(ACCOUNT));
        assertEquals(movedMage, restored.referenceForToken(ACCOUNT, "mage0001"));
        assertTrue(moved.resolve(mage.id()).isPresent());
        // In-session observations and late token writes retain the same local reference.
        assertEquals(mage.id(), movedMage);
        assertEquals(movedMage, tracker.current());
    }

    @Test void oldColorDependentHashMigratesFromSavedArtworkEvenAfterTheLiveColorAndProgressChange() throws Exception {
        var source = fixture();
        var original = decode(source);
        var character = original.characters().getFirst();
        // Pick the v1 normalized hash rather than its older full-progress hash.
        var progress = fixture();
        slot(progress, 9).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonArray("minecraft:lore").set(2, text("- Time Played: 900 hours"));
        var afterProgress = decode(progress);
        String legacy = original.legacyReferences().entrySet().stream().filter(entry -> !entry.getKey().contains("v2-")
                && entry.getValue().equals(character.id()) && afterProgress.legacyReferences().containsKey(entry.getKey()))
                .findFirst().orElseThrow().getKey();
        var path = directory.resolve("legacy.json");
        var oldCharacter = new ClassCharacter(legacy, character.type(), character.nickname(), character.sprite());
        java.nio.file.Files.writeString(path, new com.google.gson.Gson().toJson(Map.of("version", 1,
                "accounts", Map.of(ACCOUNT, new ClassSelectorStore.Account(List.of(oldCharacter),
                        List.of(new ClassShortcut(legacy, "Keep me", "key.keyboard.h")))))));
        var store = new ClassSelectorStore(path);
        slot(progress, 9).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        var live = ClassMenuEvidence.decode(progress, ACCOUNT, JsonOps.INSTANCE, store.account(ACCOUNT).characters()).orElseThrow();
        assertEquals(9, live.resolve(legacy).orElseThrow());
        store.migrateReferences(ACCOUNT, live.legacyReferences(), live.characters());
        store.reconcile(live.scan(ACCOUNT));
        String upgraded = live.characters().getFirst().id();
        assertEquals(new ClassShortcut(upgraded, "Keep me", "key.keyboard.h"),
                new ClassSelectorStore(path).account(ACCOUNT).shortcuts().getFirst());
        assertEquals(0x123456, store.account(ACCOUNT).character(upgraded).sprite().tint());
        // A rename is still an identifying change, even when its old artwork is supplied.
        slot(progress, 9).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", text("Changed identity"));
        var renamed = ClassMenuEvidence.decode(progress, ACCOUNT, JsonOps.INSTANCE, List.of(oldCharacter)).orElseThrow();
        assertTrue(renamed.resolve(legacy).isEmpty());
    }

    @Test void distinctWitnessesSeparateRepeatedTypesAndRemovalCannotRedirectTheRemovedShortcut() throws Exception {
        var source = fixture();
        slot(source, 19).add("item", slot(source, 10).get("item").deepCopy());
        var components = slot(source, 19).getAsJsonObject("item").getAsJsonObject("components");
        components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(579));
        var duplicates = decode(source);
        var first = duplicates.characters().get(1);
        var second = duplicates.characters().getLast();
        assertFalse(ClassIdentity.ambiguous(first.id()));
        assertFalse(ClassIdentity.ambiguous(second.id()));
        assertEquals(10, duplicates.resolve(first.id()).orElseThrow());
        assertEquals(19, duplicates.resolve(second.id()).orElseThrow());
        slot(source, 19).add("item", slot(fixture(), 19).get("item").deepCopy());
        var oneLeft = decode(source, duplicates.characters());
        assertTrue(oneLeft.resolve(second.id()).isEmpty());
        assertEquals(10, oneLeft.resolve(first.id()).orElseThrow());
    }

    @Test void unknownNonProgressLoreRemainsIdentifyingAndPartialBooksNeverAuthorizeAMove() throws Exception {
        var source = fixture();
        var old = decode(source).characters().getFirst();
        slot(source, 9).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonArray("minecraft:lore").add(text("- Gamemode: Hardcore"));
        assertTrue(decode(source).resolve(old.id()).isEmpty());
        source = fixture();
        slot(source, 19).add("item", slot(source, 9).get("item").deepCopy());
        slot(source, 9).add("item", slot(fixture(), 19).get("item").deepCopy());
        slot(source, 10).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        var partial = decode(source);
        assertFalse(partial.complete());
        assertTrue(partial.resolve(old.id()).isEmpty());
        assertFalse(partial.legacyReferences().containsKey(old.id()));
    }

    @Test void partialDuplicateRefreshCannotDiscardItsAmbiguityOrLoseItsShortcut() throws Exception {
        var source = fixture();
        slot(source, 19).add("item", slot(source, 10).get("item").deepCopy());
        var duplicated = decode(source);
        var mage = duplicated.characters().get(1);
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(duplicated.scan(ACCOUNT)); store.add(ACCOUNT, mage.id());
        slot(source, 19).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        slot(source, 10).getAsJsonObject("item").getAsJsonObject("components")
                .getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        var partial = decode(source);
        assertFalse(partial.complete());
        store.migrateReferences(ACCOUNT, partial.legacyReferences(), partial.characters());
        store.reconcile(partial.scan(ACCOUNT));
        assertNotNull(store.account(ACCOUNT).character(mage.id()));
        assertEquals(mage.sprite(), store.account(ACCOUNT).character(mage.id()).sprite());
        assertEquals(mage.id(), store.account(ACCOUNT).shortcuts().getFirst().characterId());
        assertTrue(partial.resolve(mage.id()).isEmpty());
    }

    @Test void repeatedTypesWithTheSameArtworkSurviveProgressRecolorAndRestart() throws Exception {
        var source = fixture("wynncraft-character-book-2026-10-23.json");
        for (int position : List.of(9, 10, 11, 18, 19, 20))
            slot(source, position).getAsJsonObject("item").getAsJsonObject("components")
                    .getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0xFFFFFF);
        var initial = decode(source);
        var path = directory.resolve("progress.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(initial.scan(ACCOUNT));
        for (var character : initial.characters()) store.add(ACCOUNT, character.id());
        var mage = initial.characters().getFirst();
        store.edit(ACCOUNT, new ClassShortcut(mage.id(), "Main Mage", "key.keyboard.g"));
        var components = slot(source, 9).getAsJsonObject("item").getAsJsonObject("components");
        components.getAsJsonArray("minecraft:lore").set(2, text("- Time Played: 218 hours"));
        components.getAsJsonArray("minecraft:lore").set(4, text("- Level: 121 (9%)"));
        components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(579));
        var changed = decode(source, store.account(ACCOUNT).characters());
        assertEquals(initial.characters().stream().map(ClassCharacter::id).toList(), changed.characters().stream().map(ClassCharacter::id).toList());
        for (var character : initial.characters()) assertEquals(character.book().slot(), changed.resolve(character.id()).orElseThrow());
        store.reconcile(changed.scan(ACCOUNT));
        var restarted = new ClassSelectorStore(path);
        var nextBook = decode(source, restarted.account(ACCOUNT).characters());
        for (var shortcut : restarted.account(ACCOUNT).shortcuts()) assertTrue(nextBook.resolve(shortcut.characterId()).isPresent());
        assertEquals("Main Mage", restarted.account(ACCOUNT).shortcuts().getFirst().name());
        assertEquals(0x123456, restarted.account(ACCOUNT).character(mage.id()).sprite().tint());
    }

    @Test void repeatedTypesCanSwapPositionsThroughPartialUpdatesWithoutLosingEitherWitness() throws Exception {
        var source = fixture("wynncraft-character-book-2026-10-23.json");
        var initial = decode(source);
        var path = directory.resolve("swap.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(initial.scan(ACCOUNT));
        for (var character : initial.characters()) store.add(ACCOUNT, character.id());
        var main = initial.characters().getFirst();
        var alt = initial.characters().get(4);
        store.edit(ACCOUNT, new ClassShortcut(main.id(), "Main", "key.keyboard.g"));
        store.recordLoad(ACCOUNT, main.id(), "mainmage");
        var copy = slot(source, 9).get("item").deepCopy();
        slot(source, 9).add("item", slot(source, 19).get("item").deepCopy());
        slot(source, 19).add("item", copy);
        var partialSource = source.deepCopy();
        slot(partialSource, 9).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        var partial = decode(partialSource, store.account(ACCOUNT).characters());
        assertFalse(partial.complete());
        store.reconcile(partial.scan(ACCOUNT));
        assertNotNull(store.account(ACCOUNT).character(main.id()));
        assertNotNull(store.account(ACCOUNT).character(alt.id()));
        var complete = decode(source, store.account(ACCOUNT).characters());
        assertEquals(19, complete.resolve(main.id()).orElseThrow());
        assertEquals(9, complete.resolve(alt.id()).orElseThrow());
        store.reconcile(complete.scan(ACCOUNT));
        store.recordLoadAsync(ACCOUNT, main.id(), "mainmage"); store.flushWrites();
        var restarted = new ClassSelectorStore(path);
        assertEquals(initial.characters().stream().map(ClassCharacter::id).toList(), restarted.account(ACCOUNT).shortcuts().stream().map(ClassShortcut::characterId).toList());
        assertEquals(main.id(), restarted.referenceForToken(ACCOUNT, "mainmage"));
        assertEquals(19, restarted.account(ACCOUNT).character(main.id()).book().slot());
        assertEquals(5, WynncraftClassMenu.characterNumber(restarted.account(ACCOUNT).character(main.id())));
        assertEquals(new ClassShortcut(main.id(), "Main", "key.keyboard.g"), restarted.account(ACCOUNT).shortcuts().getFirst());
    }

    @Test void partialChangedArtworkAndProgressCannotPoisonTheNextCompleteMatch() throws Exception {
        var source = fixture();
        var initial = decode(source);
        var mage = initial.characters().get(1);
        var store = new ClassSelectorStore(directory.resolve("partial.json"));
        store.reconcile(initial.scan(ACCOUNT)); store.add(ACCOUNT, mage.id());
        var components = slot(source, 10).getAsJsonObject("item").getAsJsonObject("components");
        components.getAsJsonArray("minecraft:lore").set(2, text("- Time Played: 230 hours"));
        components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(579));
        var fragment = source.deepCopy();
        slot(fragment, 9).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        var partial = decode(fragment, store.account(ACCOUNT).characters());
        assertFalse(partial.complete());
        store.reconcile(partial.scan(ACCOUNT));
        assertEquals(initial.characters(), store.account(ACCOUNT).characters());
        var complete = decode(source, store.account(ACCOUNT).characters());
        assertEquals(10, complete.resolve(mage.id()).orElseThrow());
        store.reconcile(complete.scan(ACCOUNT));
        assertEquals(mage.id(), store.account(ACCOUNT).shortcuts().getFirst().characterId());
        assertEquals(0x123456, store.account(ACCOUNT).character(mage.id()).sprite().tint());
    }

    @Test void identicalNewDuplicateAndItsRemovalCannotResurrectOrRedirectAnOldShortcut() throws Exception {
        var source = fixture();
        var initial = decode(source);
        var mage = initial.characters().get(1);
        var store = new ClassSelectorStore(directory.resolve("identical.json"));
        store.reconcile(initial.scan(ACCOUNT)); store.add(ACCOUNT, mage.id());
        slot(source, 19).add("item", slot(source, 10).get("item").deepCopy());
        var duplicated = decode(source, store.account(ACCOUNT).characters());
        assertTrue(duplicated.resolve(mage.id()).isEmpty());
        store.reconcile(duplicated.scan(ACCOUNT));
        slot(source, 19).add("item", slot(fixture(), 19).get("item").deepCopy());
        var oneLeft = decode(source, store.account(ACCOUNT).characters());
        assertTrue(oneLeft.resolve(mage.id()).isEmpty());
        store.reconcile(oneLeft.scan(ACCOUNT));
        assertNull(store.account(ACCOUNT).character(mage.id()));
        var fresh = oneLeft.characters().stream().filter(c -> c.type().equals("Mage")).findFirst().orElseThrow();
        assertEquals(10, oneLeft.resolve(fresh.id()).orElseThrow());
    }

    @Test void aNewLowLevelCharacterReusingAnOldIconCannotStealTheOldShortcut() throws Exception {
        var source = fixture("wynncraft-character-book-2026-10-23.json");
        var initial = decode(source);
        var main = initial.characters().getFirst();
        var alt = initial.characters().get(4);
        var mainComponents = slot(source, 9).getAsJsonObject("item").getAsJsonObject("components");
        int oldTint = mainComponents.getAsJsonObject("minecraft:potion_contents").get("custom_color").getAsInt();
        mainComponents.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", 0x123456);
        mainComponents.getAsJsonArray("minecraft:lore").set(2, text("- Time Played: 218 hours"));
        var newcomer = slot(source, 19).get("item").deepCopy();
        newcomer.getAsJsonObject().getAsJsonObject("components").getAsJsonObject("minecraft:potion_contents")
                .addProperty("custom_color", oldTint);
        slot(source, 27).add("item", newcomer);
        var changed = decode(source, initial.characters());
        assertTrue(changed.resolve(main.id()).isEmpty());
        assertEquals(19, changed.resolve(alt.id()).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"Mage,Dark Wizard", "Dark Wizard,Mage", "Archer,Hunter", "Hunter,Archer",
            "Warrior,Knight", "Knight,Warrior", "Assassin,Ninja", "Ninja,Assassin",
            "Shaman,Skyseer", "Skyseer,Shaman"})
    void classReskinsRoundTripWithoutChangingReferencesOrPreferences(String initialType, String reskin) throws Exception {
        var source = fixture();
        setClass(source, 9, initialType, null);
        var initial = decode(source);
        var character = initial.characters().getFirst();
        var path = directory.resolve("reskin.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(initial.scan(ACCOUNT));
        store.add(ACCOUNT, character.id()); store.add(ACCOUNT, initial.characters().getLast().id());
        var preference = new ClassShortcut(character.id(), "My main", "key.keyboard.g");
        store.edit(ACCOUNT, preference); store.recordLoad(ACCOUNT, character.id(), "main0001");
        var order = store.account(ACCOUNT).shortcuts();
        int turn = 0;
        for (String type : List.of(reskin, initialType)) {
            setClass(source, 9, type, null);
            var components = slot(source, 9).getAsJsonObject("item").getAsJsonObject("components");
            float model = 590F + turn;
            int tint = 0x123456 + turn++;
            components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(model));
            components.getAsJsonObject("minecraft:potion_contents").addProperty("custom_color", tint);
            var live = decode(source, store.account(ACCOUNT).characters());
            assertEquals(9, live.resolve(character.id()).orElseThrow());
            store.reconcile(live.scan(ACCOUNT));
            store = new ClassSelectorStore(path);
            var updated = store.account(ACCOUNT).character(character.id());
            assertNotNull(updated);
            assertEquals(type, updated.type());
            assertEquals("", updated.nickname());
            assertEquals(type, updated.displayName(""));
            assertEquals(new ClassSprite(model, tint), updated.sprite());
            assertEquals(order, store.account(ACCOUNT).shortcuts());
            assertEquals(character.id(), store.referenceForToken(ACCOUNT, "main0001"));
        }
    }

    @ParameterizedTest
    @CsvSource({"Mage,Dark Wizard", "Dark Wizard,Mage"})
    void reskinWithDuplicateFamilyAndBookReorderPreservesAnExistingV2Reference(String initialType, String nextType) throws Exception {
        var source = fixture("wynncraft-character-book-2026-10-23.json");
        setClass(source, 9, initialType, null);
        var initial = decode(source);
        var main = initial.characters().getFirst();
        String legacyId = main.identity().reference(main.book().slot());
        var legacy = new ClassCharacter(legacyId, main.type(), main.nickname(), main.sprite(), main.identity(), main.book());
        var saved = new ArrayList<>(initial.characters()); saved.set(0, legacy);
        var path = directory.resolve("legacy-reskin.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(new ClassScan(ACCOUNT, saved, true)); store.add(ACCOUNT, legacyId);
        setClass(source, 9, nextType, null);
        var copy = slot(source, 9).get("item").deepCopy();
        slot(source, 9).add("item", slot(source, 19).get("item").deepCopy());
        slot(source, 19).add("item", copy);
        var live = decode(source, saved);
        assertEquals(19, live.resolve(legacyId).orElseThrow());
        assertEquals(9, live.resolve(initial.characters().get(4).id()).orElseThrow());
        store.reconcile(live.scan(ACCOUNT));
        var restarted = new ClassSelectorStore(path);
        assertEquals(nextType, restarted.account(ACCOUNT).character(legacyId).type());
        assertEquals(legacyId, restarted.account(ACCOUNT).shortcuts().getFirst().characterId());
        assertEquals(19, decode(source, restarted.account(ACCOUNT).characters()).resolve(legacyId).orElseThrow());
    }

    @Test void reskinKeepsCustomCharacterNamesButDoesNotNormalizeRealRenamesOrOtherClasses() throws Exception {
        var source = fixture();
        setClass(source, 10, "Mage", "Aether");
        var initial = decode(source);
        var mage = initial.characters().get(1);
        setClass(source, 10, "Dark Wizard", "Aether");
        var reskinned = decode(source, initial.characters());
        assertEquals(10, reskinned.resolve(mage.id()).orElseThrow());
        assertEquals("Aether", reskinned.characters().get(1).nickname());
        setClass(source, 10, "Dark Wizard", "Different nickname");
        assertTrue(decode(source, initial.characters()).resolve(mage.id()).isEmpty());
        setClass(source, 10, "Knight", "Aether");
        assertTrue(decode(source, initial.characters()).resolve(mage.id()).isEmpty());
    }

    @Test void aPartialReskinAndProgressUpdateCannotReplaceTheSavedCharacterBeforeTheBookCompletes() throws Exception {
        var source = fixture();
        var initial = decode(source);
        var mage = initial.characters().get(1);
        var store = new ClassSelectorStore(directory.resolve("partial-reskin.json"));
        store.reconcile(initial.scan(ACCOUNT)); store.add(ACCOUNT, mage.id());
        setClass(source, 10, "Dark Wizard", null);
        var components = slot(source, 10).getAsJsonObject("item").getAsJsonObject("components");
        components.getAsJsonArray("minecraft:lore").set(2, text("- Time Played: 220 hours"));
        components.getAsJsonObject("minecraft:custom_model_data").getAsJsonArray("floats").set(0, new com.google.gson.JsonPrimitive(591));
        var fragment = source.deepCopy();
        slot(fragment, 9).getAsJsonObject("item").getAsJsonObject("components").remove("minecraft:lore");
        var partial = decode(fragment, store.account(ACCOUNT).characters());
        assertFalse(partial.complete());
        store.reconcile(partial.scan(ACCOUNT));
        assertEquals(initial.characters(), store.account(ACCOUNT).characters());
        var complete = decode(source, store.account(ACCOUNT).characters());
        assertEquals(10, complete.resolve(mage.id()).orElseThrow());
        store.reconcile(complete.scan(ACCOUNT));
        assertEquals("Dark Wizard", store.account(ACCOUNT).character(mage.id()).type());
        assertEquals(mage.id(), store.account(ACCOUNT).shortcuts().getFirst().characterId());
    }

    @Test void reskinLabelsAloneCannotDistinguishOtherwiseIdenticalCharacters() throws Exception {
        var source = fixture();
        slot(source, 19).add("item", slot(source, 10).get("item").deepCopy());
        setClass(source, 19, "Dark Wizard", null);
        var live = decode(source);
        for (var character : live.characters().stream().filter(c -> c.type().equals("Mage") || c.type().equals("Dark Wizard")).toList())
            assertTrue(live.resolve(character.id()).isEmpty());
    }

    private static void setClass(JsonObject source, int position, String type, String nickname) {
        var components = slot(source, position).getAsJsonObject("item").getAsJsonObject("components");
        components.add("minecraft:custom_name", text(nickname == null ? type : nickname));
        components.getAsJsonArray("minecraft:lore").set(1, text("- Class: " + type));
    }

    private static JsonObject text(String value) {
        var text = new JsonObject(); text.addProperty("text", value); return text;
    }

    private static WynncraftClassMenu.Result decode(JsonObject evidence) {
        return ClassMenuEvidence.decode(evidence, ACCOUNT, JsonOps.INSTANCE).orElseThrow();
    }
    private static WynncraftClassMenu.Result decode(JsonObject evidence, List<ClassCharacter> saved) {
        return ClassMenuEvidence.decode(evidence, ACCOUNT, JsonOps.INSTANCE, saved).orElseThrow();
    }
    private static JsonObject fixture() throws Exception {
        return fixture("wynncraft-character-book-2026-10-19.json");
    }
    private static JsonObject fixture(String name) throws Exception {
        try (var input = WynncraftClassMenuTest.class.getResourceAsStream("/classes/" + name)) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static JsonObject slot(JsonObject source, int slot) {
        return source.getAsJsonArray("slots").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> value.get("slot").getAsInt() == slot).findFirst().orElseThrow();
    }
}

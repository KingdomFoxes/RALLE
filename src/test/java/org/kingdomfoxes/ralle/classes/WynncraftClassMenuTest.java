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
        controller.frameCaptured(); controller.selectionStage();
        var live = decode(fixture());
        controller.menu(7, List.of(new ClassSwitchController.Target(saved.id(), live.resolve(saved.id()).orElseThrow())), true);
        controller.transition(saved.id());
        assertTrue(controller.active());
        controller.menuClosed(7);
        controller.transition(saved.id());
        assertEquals(ClassSwitchController.State.WAITING_WORLD, controller.state());
        controller.worldReady();
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

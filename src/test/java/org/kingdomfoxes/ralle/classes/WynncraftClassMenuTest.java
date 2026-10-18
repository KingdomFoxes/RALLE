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
        String original = twoMages.characters().get(1).id();
        var renamed = new JsonObject();
        renamed.addProperty("text", "Renamed Mage");
        slot(source, 10).getAsJsonObject("item").getAsJsonObject("components").add("minecraft:custom_name", renamed);
        var changed = decode(source).characters().get(1);
        assertEquals("Renamed Mage", changed.nickname());
        assertNotEquals(original, changed.id());
        assertFalse(changed.hasStableIdentity());
        var controller = new ClassSwitchController(new ClassSwitchController.Actions() {
            public void captureFrame() { fail("An observation cannot begin automation"); }
            public void sendClassCommand() { fail(); }
            public void rightClickSelectionStage() { fail(); }
            public void selectCharacter(int containerId, int slot) { fail(); }
            public void releaseFrame() { fail(); }
            public void failed(ClassSwitchController.Failure failure) { fail(); }
        });
        assertFalse(controller.begin(ACCOUNT, changed.id(), null, true, 0));
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

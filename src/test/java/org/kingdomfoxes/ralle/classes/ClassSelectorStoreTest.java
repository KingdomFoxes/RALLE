package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSelectorStoreTest {
    @TempDir Path directory;
    private final UUID account = UUID.randomUUID();
    private final ClassCharacter mage = new ClassCharacter("server-id-a", "Mage", "Same name");
    private final ClassCharacter other = new ClassCharacter("server-id-b", "Mage", "Same name");

    @Test void completeAndPartialScansPreservePreferencesAndOrderAcrossRestarts() throws Exception {
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        store.reconcile(new ClassScan(account, List.of(mage, other), true));
        assertTrue(store.account(account).shortcuts().isEmpty());
        store.add(account, other.id());
        store.add(account, mage.id());
        store.edit(account, new ClassShortcut(other.id(), "Local name", "key.keyboard.g"));
        store.reconcile(new ClassScan(account, List.of(new ClassCharacter(mage.id(), "Mage", "Renamed")), false));
        assertEquals(2, store.account(account).characters().size());
        store.reconcile(new ClassScan(account, List.of(mage), true));
        assertNull(store.account(account).character(other.id()));
        var restored = new ClassSelectorStore(path);
        assertEquals(store.account(account), restored.account(account));
        assertEquals(List.of(other.id(), mage.id()), restored.account(account).shortcuts().stream().map(ClassShortcut::characterId).toList());
        assertEquals("Local name", restored.account(account).shortcuts().getFirst().name());
        assertEquals("key.keyboard.g", restored.account(account).shortcuts().getFirst().binding());
        restored.reconcile(new ClassScan(account, List.of(mage, other), true));
        restored.remove(account, mage.id());
        assertNotNull(restored.account(account).character(mage.id()));
        assertEquals(1, restored.account(account).shortcuts().size());
    }

    @Test void duplicateNamesAreDistinctButDuplicateIdentitiesAreRejected() throws Exception {
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(new ClassScan(account, List.of(mage, other), true));
        assertEquals(2, store.account(account).characters().size());
        assertThrows(IllegalArgumentException.class, () -> new ClassScan(account, List.of(mage, mage), true));
        assertThrows(IllegalArgumentException.class, () -> store.add(account, "unknown"));
        store.add(account, mage.id());
        store.add(account, mage.id());
        assertEquals(1, store.account(account).shortcuts().size());
        assertTrue(store.account(UUID.randomUUID()).characters().isEmpty());
    }

    @Test void failedSaveDoesNotPublishAndCorruptFilesArePreserved() throws Exception {
        var parent = directory.resolve("blocked");
        Files.writeString(parent, "file blocks a directory");
        var store = new ClassSelectorStore(parent.resolve("classes.json"));
        assertThrows(java.io.IOException.class, () -> store.reconcile(new ClassScan(account, List.of(mage), true)));
        assertTrue(store.account(account).characters().isEmpty());
        assertEquals(0, store.revision());
        var corrupt = directory.resolve("corrupt.json");
        Files.writeString(corrupt, "{broken");
        var unavailable = ClassSelectorStore.open(corrupt);
        assertFalse(unavailable.available());
        assertThrows(java.io.IOException.class, () -> unavailable.reconcile(new ClassScan(account, List.of(mage), true)));
        assertEquals("{broken", Files.readString(corrupt));
    }

    @Test void localNamePriorityAndValidation() {
        assertEquals("Local", mage.displayName("Local"));
        assertEquals("Same name", mage.displayName(""));
        assertEquals("Mage", new ClassCharacter("id", "Mage", "").displayName(""));
        assertThrows(IllegalArgumentException.class, () -> new ClassCharacter("", "Mage", "name"));
        assertThrows(IllegalArgumentException.class, () -> new ClassShortcut("id", "bad\nname", "unbound"));
        assertThrows(IllegalArgumentException.class, () -> new ClassShortcut("id", "", "key.mouse.left"));
    }

    @Test void discoveryRejectsDisabledAndStaleAccountOrConnectionScans() throws Exception {
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        var discovery = new ClassDiscovery(store);
        discovery.session(account, false);
        var scan = new ClassScan(account, List.of(mage), true);
        assertFalse(discovery.observe(scan, discovery.generation()));
        discovery.session(account, true);
        long generation = discovery.generation();
        assertTrue(discovery.observe(scan, generation));
        discovery.selected(account, mage.id(), generation);
        assertEquals(mage.id(), discovery.current());
        discovery.session(UUID.randomUUID(), true);
        assertNull(discovery.current());
        assertFalse(discovery.observe(scan, discovery.generation()));
        discovery.session(account, true);
        assertFalse(discovery.observe(scan, generation));
        discovery.disconnect();
        assertFalse(discovery.observe(scan, generation));
        assertEquals(List.of(mage), store.account(account).characters());
    }
}

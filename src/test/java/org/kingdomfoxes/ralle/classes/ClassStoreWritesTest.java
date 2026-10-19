package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ClassStoreWritesTest {
    @TempDir Path directory;

    @Test void stalledDiskDoesNotBlockSwitchCompletionOrReadsAndShutdownFlushSavesBothAccounts() throws Exception {
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        var now = new AtomicLong();
        var tracker = new ClassLoadTracker(store, now::get, store::recordLoadAsync);
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var blocked = ClassStoreWrites.submit(() -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Test writer not released"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.IOException(e); }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                tracker.session(first); tracker.selected("mage", 7); tracker.closed(7, 7);
                tracker.compass("abc12345"); now.set(1600);
                assertTrue(tracker.gameplay(false)); assertEquals("mage", tracker.current());
                assertNull(store.lastLoaded(first)); // Save-before-publish still holds.
                assertTrue(store.account(first).shortcuts().isEmpty());
                tracker.session(second); tracker.selected("archer", 8); tracker.closed(8, 8);
                tracker.compass("def12345"); now.set(3200); assertTrue(tracker.gameplay(false));
                tracker.reset(); // Queued writes own their original account and identity.
            });
        } finally { release.countDown(); }
        blocked.get(2, TimeUnit.SECONDS); store.flushWrites();
        var restored = new ClassSelectorStore(path);
        assertEquals("mage", restored.referenceForToken(first, "abc12345"));
        assertEquals("archer", restored.referenceForToken(second, "def12345"));
    }

    @Test void queuedLoadsEditsAndEvidenceMigrationPersistInOrderWithoutCancellingSelectorRevision() throws Exception {
        var path = directory.resolve("classes.json"); var account = UUID.randomUUID();
        var store = new ClassSelectorStore(path);
        store.reconcile(new ClassScan(account, List.of(new ClassCharacter("old", "Mage", "")), true));
        store.add(account, "old");
        long revision = store.revision();
        store.recordLoadAsync(account, "old", "abc12345").get(2, TimeUnit.SECONDS);
        assertEquals(revision, store.revision());
        store.recordLoadAsync(account, "old", "def12345");
        store.migrateReferences(account, Map.of("old", "new"));
        store.edit(account, new ClassShortcut("new", "Local name", "key.keyboard.g"));
        store.recordLoadAsync(account, "new", "abc12345");
        store.flushWrites();
        var restored = new ClassSelectorStore(path);
        assertEquals("new", restored.lastLoaded(account));
        assertEquals("new", restored.referenceForToken(account, "abc12345"));
        assertEquals("new", restored.referenceForToken(account, "def12345"));
        assertEquals("Local name", restored.account(account).shortcuts().getFirst().name());
    }

    @Test void asynchronousFailurePreservesDamagedFileAndDoesNotBlockLaterWrites() throws Exception {
        var broken = directory.resolve("broken.json"); Files.writeString(broken, "{broken");
        var account = UUID.randomUUID();
        var failed = ClassSelectorStore.open(broken).recordLoadAsync(account, "mage", "abc12345");
        assertThrows(ExecutionException.class, () -> failed.get(2, TimeUnit.SECONDS));
        assertEquals("{broken", Files.readString(broken));
        var store = new ClassSelectorStore(directory.resolve("healthy.json"));
        store.recordLoadAsync(account, "mage", "abc12345"); store.flushWrites();
        assertEquals("mage", new ClassSelectorStore(directory.resolve("healthy.json")).lastLoaded(account));
    }
}

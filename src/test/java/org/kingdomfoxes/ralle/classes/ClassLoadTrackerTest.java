package org.kingdomfoxes.ralle.classes;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassLoadTrackerTest {
    @TempDir Path directory;
    private final UUID account = UUID.randomUUID();
    private final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();

    @Test void manualSelectionsAreSavedAfterBookCloseAndDoorTimer() throws Exception {
        var path = directory.resolve("classes.json");
        var store = new ClassSelectorStore(path);
        var tracker = new ClassLoadTracker(store, now::get); tracker.session(account);
        tracker.compass("old12345");
        tracker.selected("mage", 7); // Discards stale inventory evidence from the outgoing class.
        assertFalse(tracker.gameplay(true));
        tracker.welcome(); assertFalse(tracker.gameplay(true));
        tracker.closed(0, 7); assertFalse(tracker.gameplay(false));
        assertNull(store.lastLoaded(account));
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true));
        assertEquals("mage", tracker.current());
        assertEquals("mage", new ClassSelectorStore(path).lastLoaded(account));
        assertNull(store.referenceForToken(account, "old12345"));
        tracker.compass("new12345"); // Late compass refresh is still associated with the confirmed load.
        assertEquals("mage", new ClassSelectorStore(path).referenceForToken(account, "new12345"));
    }

    @Test void reconnectDoesNotBlindlyReuseLastClassButCanRecoverAnObservedCompassAssociation() throws Exception {
        var path = directory.resolve("classes.json"); var store = new ClassSelectorStore(path);
        store.recordLoad(account, "mage", "abc12345");
        var tracker = new ClassLoadTracker(new ClassSelectorStore(path), now::get); tracker.session(account);
        assertNull(tracker.current());
        tracker.welcome(); assertFalse(tracker.gameplay(true)); // Wait for natural inventory data.
        tracker.compass("abc12345"); now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true));
        assertEquals("mage", tracker.current());
        tracker.session(UUID.randomUUID()); tracker.compass("abc12345"); tracker.welcome();
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true)); assertNull(tracker.current());
        tracker.reset(); assertNull(tracker.current()); assertFalse(tracker.gameplay(true));
    }

    @Test void partialTokenCollisionsNeverRecoverAGuessedClassAndPreferenceEditsKeepLoadData() throws Exception {
        var path = directory.resolve("classes.json"); var store = new ClassSelectorStore(path);
        store.reconcile(new ClassScan(account, List.of(new ClassCharacter("mage", "Mage", "")), true));
        store.recordLoad(account, "mage", "abc12345"); store.add(account, "mage");
        assertEquals("mage", store.referenceForToken(account, "abc12345"));
        store.recordLoad(account, "archer", "abc12345");
        assertNull(store.referenceForToken(account, "abc12345"));
        store.recordLoad(account, "mage", "abc12345");
        assertNull(new ClassSelectorStore(path).referenceForToken(account, "abc12345"));
        assertEquals("mage", store.lastLoaded(account));
        assertEquals(1, store.account(account).shortcuts().size());
    }

    @Test void presentationCancellationDoesNotEraseASelectionThatLaterActuallyLoads() throws Exception {
        var tracker = new ClassLoadTracker(new ClassSelectorStore(directory.resolve("classes.json")), now::get);
        tracker.session(account); tracker.selected("mage", 7);
        // Escape cancels frame/input/audio ownership, not this independent natural-load observer.
        tracker.closed(7, 0); tracker.welcome();
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true)); assertEquals("mage", tracker.current());
        tracker.selectionStage(); assertNull(tracker.current());
        tracker.welcome(); tracker.compass("unknown1");
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true)); assertNull(tracker.current());
    }

    @Test void damagedStorageCannotCrashOrEraseKnowledgeOfTheCurrentSession() throws Exception {
        var path = directory.resolve("broken.json"); Files.writeString(path, "{broken");
        var tracker = new ClassLoadTracker(ClassSelectorStore.open(path), now::get);
        tracker.session(account); tracker.selected("mage", 7); tracker.closed(7, 7); tracker.welcome();
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true)); assertEquals("mage", tracker.current());
        assertEquals("{broken", Files.readString(path));
    }

    @Test void rejectedOrAbandonedSelectionCannotBeAssignedToAnUnrelatedLaterLoad() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong();
        var tracker = new ClassLoadTracker(new ClassSelectorStore(directory.resolve("classes.json")), now::get);
        tracker.session(account); tracker.selected("mage", 7); tracker.closed(7, 7);
        now.set(15_000);
        assertFalse(tracker.hasSelection());
        tracker.compass("unknown1"); tracker.welcome();
        now.set(Math.max(now.get(), 1600)); assertTrue(tracker.gameplay(true)); assertNull(tracker.current());
    }

    @Test void confirmedBookSelectionBecomesCurrentAfter1600MillisWithoutAnyWelcome() throws Exception {
        var tracker = new ClassLoadTracker(new ClassSelectorStore(directory.resolve("classes.json")), now::get);
        tracker.session(account); tracker.selected("mage", 7); tracker.closed(7, 7);
        now.set(1599); assertFalse(tracker.gameplay(false));
        tracker.closed(0, 0); // A duplicate close must not restart the door timer.
        now.set(1600); assertTrue(tracker.gameplay(false));
        assertEquals("mage", tracker.current());
    }

    @Test void loadSignalsRequireTheReceivedWelcomeShapeAndStrictCompassEvidence() {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        assertTrue(ClassLoadSignals.welcome("\n\uE001§6§lWelcome to Wynncraft!§r\n\uE002play.wynncraft.com -/- wynncraft.com\n"));
        assertFalse(ClassLoadSignals.welcome("Welcome to Wynncraft!"));
        assertFalse(ClassLoadSignals.welcome("Player: Welcome to Wynncraft! play.wynncraft.com wynncraft.com"));
        assertFalse(ClassLoadSignals.welcome("Left-Click to play   Right-Click to switch"));
        var compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("abc12345"))));
        assertEquals("abc12345", ClassLoadSignals.compassToken(compass));
        compass.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Class: Mage"))));
        assertNull(ClassLoadSignals.compassToken(compass));
        var unrelated = new ItemStack(Items.PAPER);
        unrelated.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("abc12345"))));
        assertNull(ClassLoadSignals.compassToken(unrelated));
    }
}

package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.settings.RalleSettings;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsNavigationStateTest {
    @TempDir Path directory;

    @Test
    void longScrollStaysInMemoryUntilIdleAndIdenticalSnapshotsDoNotWrite() throws Exception {
        var path = directory.resolve("navigation.properties");
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new SettingsNavigationState(path, registry(), clock::get);
        state.showCategory("chat", 0);
        var initial = Files.readString(path);
        for (int step = 1; step <= 100; step++) {
            clock.addAndGet(50);
            state.remember(SettingsNavigationState.Snapshot.categoryPage("chat", step / 100d));
            state.flushIfIdle();
            assertEquals(initial, Files.readString(path));
            assertEquals(step / 100d, state.snapshot().scrollProgress());
        }
        clock.addAndGet(SettingsNavigationState.SAVE_IDLE_MILLIS - 1);
        state.flushIfIdle();
        assertEquals(initial, Files.readString(path));
        clock.incrementAndGet();
        state.flushIfIdle();
        assertEquals(1, new SettingsNavigationState(path, registry()).snapshot().scrollProgress());

        var marker = java.nio.file.attribute.FileTime.fromMillis(1000);
        Files.setLastModifiedTime(path, marker);
        state.showCategory("chat", 1);
        state.flush();
        assertEquals(marker, Files.getLastModifiedTime(path));
    }

    @Test
    void closeFlushesFinalSmallScrollAndPageChangesFlushImmediately() {
        var path = directory.resolve("navigation.properties");
        var state = new SettingsNavigationState(path, registry(), () -> 0);
        state.showSubcategory("chat", "screenshots", .5);
        state.remember(SettingsNavigationState.Snapshot.subcategoryPage("chat", "screenshots", .501));
        state.flush(); // Screen.removed flushes even before the debounce expires.
        assertEquals(.501, new SettingsNavigationState(path, registry()).snapshot().scrollProgress());
        state.remember(SettingsNavigationState.Snapshot.subcategoryPage("chat", "screenshots", .7));
        state.showAbout();
        assertTrue(new SettingsNavigationState(path, registry()).snapshot().about());
    }

    @Test
    void failedSaveKeepsMemoryAndRetriesAfterIdle() throws Exception {
        var parent = directory.resolve("blocked");
        Files.writeString(parent, "not a directory");
        var path = parent.resolve("navigation.properties");
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var state = new SettingsNavigationState(path, registry(), clock::get);
        state.showCategory("chat", .75);
        assertEquals(.75, state.snapshot().scrollProgress());
        Files.delete(parent);
        state.flushIfIdle();
        assertFalse(Files.exists(path));
        clock.set(SettingsNavigationState.SAVE_IDLE_MILLIS);
        state.flushIfIdle();
        assertEquals(.75, new SettingsNavigationState(path, registry()).snapshot().scrollProgress());
    }

    @Test
    void firstVisitUsesAboutAndLaterVisitsRestoreEveryPageKind() {
        var registry = registry();
        var path = directory.resolve("ralle-settings-ui.properties");
        var first = new SettingsNavigationState(path, registry);
        assertTrue(first.snapshot().about());

        first.showCategory("chat", .25);
        var restored = new SettingsNavigationState(path, registry).snapshot();
        assertFalse(restored.about());
        assertTrue(restored.categoryPage());
        assertEquals("chat", restored.categoryId());
        assertEquals(.25, restored.scrollProgress());

        first.showSubcategory("chat", "screenshots", .625);
        restored = new SettingsNavigationState(path, registry).snapshot();
        assertTrue(restored.subcategoryPage());
        assertEquals("screenshots", restored.subcategoryId());
        assertEquals(.625, restored.scrollProgress());
    }

    @Test
    void readsLegacySubcategoryStateCompatibly() throws Exception {
        var path = directory.resolve("ralle-settings-ui.properties");
        Files.writeString(path, "about=false\ncategory=chat\nsubcategory=screenshots\nscroll=.5\n");

        var restored = new SettingsNavigationState(path, registry()).snapshot();
        assertTrue(restored.subcategoryPage());
        assertEquals("screenshots", restored.subcategoryId());
        assertEquals(.5, restored.scrollProgress());
    }

    @Test
    void invalidPersistedNavigationFallsBackToAbout() throws Exception {
        var path = directory.resolve("ralle-settings-ui.properties");
        Files.writeString(path, "about=false\ncategory=removed\nsubcategory=missing\nscroll=2\n");
        assertTrue(new SettingsNavigationState(path, registry()).snapshot().about());
    }

    private SettingsRegistry registry() {
        var registry = new SettingsRegistry(directory.resolve("ralle.properties"));
        RalleSettings.register(registry);
        return registry;
    }
}

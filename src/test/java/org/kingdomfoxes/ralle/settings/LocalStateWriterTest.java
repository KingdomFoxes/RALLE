package org.kingdomfoxes.ralle.settings;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.platform.AtomicFiles;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.ui.owo.SettingsNavigationState;
import net.minecraft.network.chat.Component;
import java.util.List;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LocalStateWriterTest {
    @TempDir Path directory;

    @Test void slowDiskDoesNotBlockUiSavesOrReadsAndBurstsCoalesceWithoutLosingOtherSections() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writes = new AtomicInteger();
        var path = directory.resolve("ralle.json");
        Files.writeString(path, "{\"schemaVersion\":1,\"classes\":{\"largeRoster\":\"" + "x".repeat(900 * 1024) + "\"}}");
        try (var state = new LocalStateFile(path, (destination, json) -> {
            writes.incrementAndGet();
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test writer timed out"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
            AtomicFiles.write(destination, json);
        })) {
            var first = state.section("settings").writePropertiesAsync(properties("revision", "first"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var pending = new ArrayList<CompletableFuture<Void>>();
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                    assertNull(state.section("settings").read(), "Uncommitted state is never published");
                    for (int i = 0; i < 200; i++) {
                        pending.add(state.section("settings").writePropertiesAsync(properties("revision", "" + i)));
                        pending.add(state.section("hud").writePropertiesAsync(properties("position", "" + i)));
                    }
                });
                assertEquals(2, pending.stream().distinct().count(), "Only one pending future/value per section");
                assertFalse(first.isDone());
            } finally { release.countDown(); }
            state.flush();
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
            assertEquals(2, writes.get());
            assertEquals("199", state.section("settings").readProperties().getProperty("revision"));
            assertEquals("199", state.section("hud").readProperties().getProperty("position"));
        }
        var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("199", root.getAsJsonObject("settings").get("revision").getAsString());
        assertEquals("199", root.getAsJsonObject("hud").get("position").getAsString());
        assertEquals(900 * 1024, root.getAsJsonObject("classes").get("largeRoster").getAsString().length());
    }

    @Test void failedAsyncSavesReportErrorsAndDoNotPublishOrPreventLaterRecovery() throws Exception {
        var failures = new AtomicInteger(1);
        var path = directory.resolve("ralle.json");
        try (var state = new LocalStateFile(path, (destination, json) -> {
            if (failures.getAndDecrement() > 0) throw new IOException("Simulated I/O failure");
            AtomicFiles.write(destination, json);
        })) {
            var save = state.section("settings").writePropertiesAsync(properties("value", "lost"));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> save.get(2, TimeUnit.SECONDS));
            assertNull(state.section("settings").read());
            state.section("settings").writeProperties(properties("value", "recovered"));
            assertEquals("recovered", state.section("settings").readProperties().getProperty("value"));
        }
    }

    @Test void shutdownFlushesPendingSavesAndRejectsNewWork() throws Exception {
        var path = directory.resolve("ralle.json");
        var state = new LocalStateFile(path);
        var saved = state.section("navigation").writePropertiesAsync(properties("page", "about"));
        state.close();
        assertTrue(saved.isDone());
        assertFalse(saved.isCompletedExceptionally());
        assertTrue(Files.readString(path).contains("about"));
        assertTrue(state.section("settings").writePropertiesAsync(properties("value", "late")).isCompletedExceptionally());
    }

    @Test void rapidUiUpdatesKeepOnlyOneObserverAndNavigationAcknowledgesOnlyTheLatestSnapshot() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var path = directory.resolve("ralle.json");
        try (var state = new LocalStateFile(path, (destination, json) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test writer timed out"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
            AtomicFiles.write(destination, json);
        })) {
            state.section("classes").writePropertiesAsync(properties("hold", "disk"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var registry = new SettingsRegistry(state.section("settings"));
            registry.registerCategory(new SettingsCategory("chat", Component.literal("Chat"), Component.empty(),
                    List.of(new BooleanSetting("enabled", Component.empty(), Component.empty(), false)), List.of()));
            registry.seal();
            var settingsSave = state.section("settings").writePropertiesAsync(properties("chat.enabled", "false"));
            var navigation = new SettingsNavigationState(state.section("navigation"), registry);
            try {
                for (int i = 0; i < 200; i++) {
                    registry.setting("enabled", BooleanSetting.class).set(i % 2 == 0);
                    navigation.showCategory("chat", i / 200d);
                }
                navigation.showAbout(); // Return to the original saved value while another value is pending.
                var navigationSave = state.section("navigation").writePropertiesAsync(properties("page", "about"));
                assertEquals(1, settingsSave.getNumberOfDependents());
                assertEquals(1, navigationSave.getNumberOfDependents());
            } finally { release.countDown(); }
            state.flush();
            assertTrue(new SettingsNavigationState(state.section("navigation"), registry).snapshot().about());
            // An older coalesced completion must not suppress this later change as already saved.
            navigation.showCategory("chat", .5);
            state.flush();
            var restored = new SettingsNavigationState(state.section("navigation"), registry).snapshot();
            assertEquals("chat", restored.categoryId());
            assertEquals(.5, restored.scrollProgress());
        }
    }

    private static Properties properties(String name, String value) {
        var result = new Properties();
        result.setProperty(name, value);
        return result;
    }
}

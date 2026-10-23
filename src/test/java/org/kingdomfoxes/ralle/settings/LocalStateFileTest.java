package org.kingdomfoxes.ralle.settings;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsCategory;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;
import org.kingdomfoxes.ralle.chat.RalleOnboardingNotice;
import org.kingdomfoxes.ralle.classes.ClassSelectorStore;
import org.kingdomfoxes.ralle.requeue.AutoRaidRequeueStore;
import org.kingdomfoxes.ralle.requeue.WynnRaid;
import org.kingdomfoxes.ralle.ui.owo.SettingsNavigationState;
import org.kingdomfoxes.ralle.war.consumables.ConsumableHighlightStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class LocalStateFileTest {
    @TempDir Path directory;

    @Test void migratesEveryLegacyFileAndUsesOnlyTheUnifiedFileAfterwards() throws Exception {
        var legacy = List.of("ralle.properties", "ralle-hud-layout.properties", "ralle-settings-ui.properties",
                "ralle-onboarding.properties", "ralle-auto-requeue.properties", "ralle-class-shortcuts.json",
                "ralle-consumable-highlights.json", "ralle-ranks.json");
        for (var name : legacy) Files.writeString(directory.resolve(name), name.endsWith(".properties") ? "value=kept\n" : "{\"value\":\"kept\"}");
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        var state = new LocalStateFile(path);
        var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(9, root.size());
        for (var name : List.of("settings", "hud", "navigation", "onboarding", "requeue", "classes", "consumables", "guildRanks"))
            assertEquals("kept", root.getAsJsonObject(name).get("value").getAsString());
        state.section("classes").write("{\"value\":\"updated\"}");
        Files.writeString(directory.resolve("ralle-class-shortcuts.json"), "{\"value\":\"stale\"}");
        assertTrue(new LocalStateFile(path).section("classes").read().contains("updated"));
        for (var name : legacy) assertTrue(Files.exists(directory.resolve(name)), "Retain migration recovery copies");
    }

    @Test void everyLiveStoreSharesOneFileWithoutLosingOtherSectionsAcrossRestart() throws Exception {
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        var state = new LocalStateFile(path);
        var onboarding = new RalleOnboardingNotice(state.section("onboarding"), false);
        var settings = registry(state);
        var hud = new HudPlacementRegistry(state.section("hud"));
        hud.register(new HudPlacementRegistry.ElementDefinition("chat", 10, 10));
        hud.seal();
        hud.setPixels("chat", new HudPlacementRegistry.Rectangle(20, 30, 80, 40), 200, 100);
        var navigation = new SettingsNavigationState(state.section("navigation"), settings);
        navigation.showCategory("chat", .5);
        var account = UUID.randomUUID();
        new ClassSelectorStore(state.section("classes")).recordAutoOpen(account, true);
        var rules = new ConsumableHighlightStore(state.section("consumables"));
        new AutoRaidRequeueStore(state.section("requeue")).remember(WynnRaid.values()[0]);
        state.section("guildRanks").write("{\"saved\":true}");
        settings.setting("enabled", BooleanSetting.class).set(true);
        assertTrue(onboarding.postIfNeeded(ignored -> {}));
        state.flush();
        var restored = new LocalStateFile(path);
        assertTrue(registry(restored).setting("enabled", BooleanSetting.class).value());
        assertTrue(new ClassSelectorStore(restored.section("classes")).autoOpenEnabled(account));
        assertEquals(rules.snapshot(), new ConsumableHighlightStore(restored.section("consumables")).snapshot());
        assertEquals(WynnRaid.values()[0], new AutoRaidRequeueStore(restored.section("requeue")).lastRaid().orElseThrow());
        assertFalse(new RalleOnboardingNotice(restored.section("onboarding"), true).postIfNeeded(ignored -> fail("Repeated welcome")));
        assertEquals(.5, new SettingsNavigationState(restored.section("navigation"), settings).snapshot().scrollProgress());
        assertTrue(restored.section("hud").readProperties().containsKey("chat.x"));
        assertTrue(restored.section("guildRanks").read().contains("saved"));
        try (var files = Files.list(directory)) { assertEquals(List.of(path), files.toList()); }
    }

    @Test void concurrentSectionWritersDoNotLoseEachOthersChanges() {
        var state = new LocalStateFile(directory.resolve(LocalStateFile.FILE_NAME));
        var tasks = List.of("settings", "hud", "navigation", "classes").stream().map(name ->
                CompletableFuture.runAsync(() -> {
                    try { for (int i = 0; i < 10; i++) state.section(name).write("{\"revision\":" + i + "}"); }
                    catch (IOException failure) { throw new RuntimeException(failure); }
                })).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(tasks).join();
        var restored = new LocalStateFile(directory.resolve(LocalStateFile.FILE_NAME));
        for (var name : List.of("settings", "hud", "navigation", "classes"))
            assertDoesNotThrow(() -> assertEquals(9, JsonParser.parseString(restored.section(name).read()).getAsJsonObject().get("revision").getAsInt()));
    }

    @Test void damagedOrFutureUnifiedFilesAreNeverOverwritten() throws Exception {
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        for (String text : List.of("{broken", "{\"schemaVersion\":2}", "[]", "null")) {
            Files.writeString(path, text);
            var state = new LocalStateFile(path);
            assertThrows(IOException.class, () -> state.section("settings").write("{}"));
            assertThrows(IOException.class, () -> state.section("classes").read());
            assertEquals(text, Files.readString(path));
        }
    }

    @Test void unreadableLegacySectionDoesNotDisableUnrelatedSettingsOrDestroyTheOriginal() throws Exception {
        Files.writeString(directory.resolve("ralle-class-shortcuts.json"), "{broken");
        Files.writeString(directory.resolve("ralle.properties"), "chat.enabled=true\n");
        var state = new LocalStateFile(directory.resolve(LocalStateFile.FILE_NAME));
        assertTrue(registry(state).setting("enabled", BooleanSetting.class).value());
        assertFalse(ClassSelectorStore.open(state.section("classes")).available());
        state.section("settings").writeProperties(new Properties());
        assertThrows(IOException.class, () -> state.section("classes").write("{}"));
        assertEquals("{broken", Files.readString(directory.resolve("ralle-class-shortcuts.json")));
        Files.writeString(directory.resolve("ralle-class-shortcuts.json"), "{\"version\":2,\"accounts\":{}}");
        assertTrue(ClassSelectorStore.open(new LocalStateFile(directory.resolve(LocalStateFile.FILE_NAME))
                .section("classes")).available());
    }

    @Test void failedAtomicSaveKeepsThePreviouslyPublishedState() throws Exception {
        var path = directory.resolve(LocalStateFile.FILE_NAME);
        var state = new LocalStateFile(path);
        state.section("classes").write("{\"before\":true}");
        Files.delete(path);
        Files.createDirectory(path);
        Files.writeString(path.resolve("prevent-replacement"), "test");
        assertThrows(IOException.class, () -> state.section("classes").write("{\"after\":true}"));
        assertTrue(state.section("classes").read().contains("before"));
        try (var files = Files.list(directory)) { assertEquals(List.of(path), files.toList()); }
    }

    private SettingsRegistry registry(LocalStateFile state) {
        var registry = new SettingsRegistry(state.section("settings"));
        registry.registerCategory(new SettingsCategory("chat", Component.literal("Chat"), Component.empty(),
                List.of(new BooleanSetting("enabled", Component.literal("Enabled"), Component.empty(), false)), List.of()));
        registry.seal();
        return registry;
    }
}

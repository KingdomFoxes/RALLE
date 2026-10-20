package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchReplayTest {
    @TempDir Path directory;

    @Test void rapidRecoveredSwitchesRecordOnlyAcknowledgedSelectionsFromFreshCapturedBooks() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var account = UUID.fromString("00000000-0000-0000-0000-000000000001");
        WynncraftClassMenu.Result book;
        try (var input = getClass().getResourceAsStream("/classes/wynncraft-character-book-2026-10-19.json")) {
            book = ClassMenuEvidence.decode(JsonParser.parseReader(new java.io.InputStreamReader(input))
                    .getAsJsonObject(), account, JsonOps.INSTANCE).orElseThrow();
        }
        var store = new ClassSelectorStore(directory.resolve("recovered.json"));
        store.reconcile(book.scan(account));
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var loads = new ClassLoadTracker(store, clock::get); loads.session(account);
        var effects = new ArrayList<String>();
        var target = new java.util.concurrent.atomic.AtomicReference<String>();
        var controller = new ClassSwitchController(new ClassSwitchController.Actions() {
            public void captureFrame() { effects.add("capture"); }
            public void releaseFrame() { effects.add("release"); }
            public void sendClassCommand() { effects.add("class"); loads.selectionStage(); }
            public boolean canRetryClassCommand() { return true; }
            public void rightClickSelectionStage() { effects.add("right-click"); }
            public void selectCharacter(int container, int slot) {
                assertEquals(book.resolve(target.get()).orElseThrow(), slot);
                loads.selected(target.get(), container);
            }
            public boolean closeCharacterBook(int container) {
                loads.selectionStage(); // The native adapter does this before vanilla closeContainer.
                loads.closed(container, container);
                effects.add("close:" + container);
                return true;
            }
            public void failed(ClassSwitchController.Failure failure) { fail(failure.toString()); }
        });
        for (int operation = 0; operation < 2; operation++) {
            long start = operation * 4000L;
            clock.set(start); target.set(book.characters().get(operation).id());
            assertTrue(controller.begin(account, target.get(), loads.current(), true, start));
            controller.frameCaptured();
            clock.set(start + 750); controller.tick(account, true, clock.get()); // Lost initial command.
            int partial = 10 + operation * 3, ignored = partial + 1, accepted = partial + 2;
            controller.bookOpened(partial, start + 800);
            clock.set(start + 1400); controller.tick(account, true, clock.get());
            assertFalse(loads.hasSelection());
            controller.bookOpened(ignored, start + 1450);
            var targets = List.of(new ClassSwitchController.Target(target.get(), book.resolve(target.get()).orElseThrow()));
            controller.menu(ignored, targets, true);
            clock.set(start + 2250); controller.tick(account, true, clock.get()); // Ignored target click.
            assertFalse(loads.hasSelection());
            assertFalse(loads.gameplay(false));
            controller.bookOpened(accepted, start + 2300); controller.menu(accepted, targets, true);
            assertTrue(controller.retiredBook(ignored)); // Adapter filters the old close before loads.closed.
            controller.serverClosed(ignored, accepted, start + 2310);
            clock.set(start + 2350); loads.closed(accepted, accepted);
            controller.serverClosed(accepted, accepted, clock.get());
            clock.set(start + 3949); assertFalse(loads.gameplay(false));
            clock.set(start + 3950); assertTrue(loads.gameplay(false));
            controller.tick(account, true, clock.get());
            assertEquals(target.get(), loads.current());
            assertEquals(target.get(), new ClassSelectorStore(directory.resolve("recovered.json")).lastLoaded(account));
        }
        assertEquals(2, Collections.frequency(effects, "capture"));
        assertEquals(2, Collections.frequency(effects, "release"));
        assertEquals(4, Collections.frequency(effects, "class"));
    }
    @ParameterizedTest(name = "real character book with Auto Open = {0}")
    @ValueSource(booleans = {false, true})
    void realBookDoorAnimationAndWorldLoadKeepFrameUntilTheActualLoadedClassIsRecorded(boolean autoOpen) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var account = UUID.fromString("00000000-0000-0000-0000-000000000001");
        WynncraftClassMenu.Result book;
        String capture = autoOpen ? "wynncraft-character-book-auto-open-2026-10-19.json"
                : "wynncraft-character-book-2026-10-19.json";
        try (var input = getClass().getResourceAsStream("/classes/" + capture)) {
            assertNotNull(input);
            book = ClassMenuEvidence.decode(JsonParser.parseReader(new java.io.InputStreamReader(input)).getAsJsonObject(), account, JsonOps.INSTANCE).orElseThrow();
        }
        String target = book.characters().get(1).id();
        var store = new ClassSelectorStore(directory.resolve("classes.json"));
        store.reconcile(book.scan(account));
        var now = new java.util.concurrent.atomic.AtomicLong(250);
        var loads = new ClassLoadTracker(store, now::get); loads.session(account);
        var effects = new ArrayList<String>();
        var audio = new ClassSwitchAudio(() -> {});
        var controller = new ClassSwitchController(new ClassSwitchController.Actions() {
            public void muteAudio() { audio.mute(); }
            public void restoreAudio() { audio.close(); }
            public void captureFrame() { effects.add("capture"); }
            public void sendClassCommand() { effects.add("/class"); loads.selectionStage(); }
            public void rightClickSelectionStage() { effects.add("right-click"); }
            public void selectCharacter(int menu, int slot) { effects.add("select:" + menu + ":" + slot); loads.selected(target, menu); }
            public void releaseFrame() { effects.add("release"); }
            public void failed(ClassSwitchController.Failure failure) { fail(failure.toString()); }
        });
        try {
            controller.begin(account, target, null, true, 0); controller.frameCaptured();
            if (!autoOpen) {
                controller.selectionStage(100); controller.selectionStage(200);
            }
            assertTrue(controller.bookOpened());
            assertFalse(controller.acceptingStageClicks());
            var targets = List.of(new ClassSwitchController.Target(target, book.resolve(target).orElseThrow()));
            controller.menu(7, targets, false);
            controller.selectionStage(250); // A partial Auto Open book must never trigger fallback clicks.
            assertEquals(ClassSwitchController.State.WAITING_MENU, controller.state());
            assertTrue(effects.stream().noneMatch(effect -> effect.startsWith("select:")));
            controller.menu(7, targets, true); // Select immediately, without another controller tick.
            assertEquals(ClassSwitchController.State.WAITING_TRANSITION, controller.state());
            controller.menu(7, targets, true); // Later contents/tick callbacks cannot repeat the click.
            loads.closed(0, 7); controller.menuClosed(7, 250); // Native close ID 0 still closes live book 7.
            for (int milliseconds = 250; milliseconds < 1850; milliseconds += 50) {
                now.set(milliseconds);
                controller.tick(account, true, milliseconds);
                assertFalse(loads.gameplay(false)); // Includes dark frames, bright door frames and detached camera.
                assertFalse(effects.contains("release"));
                assertEquals(0f, ClassSwitchAudio.outputVolume(.7f));
            }
            now.set(1850);
            assertTrue(loads.gameplay(false)); // No welcome/camera signal can hold the frame beyond 1600 ms.
            controller.tick(account, true, 1850);
            loads.compass("abc12345");
            assertEquals(target, loads.current()); assertEquals(target, store.lastLoaded(account));
            assertEquals(.7f, ClassSwitchAudio.outputVolume(.7f));
            assertEquals(autoOpen
                    ? List.of("capture", "/class", "select:7:10", "release")
                    : List.of("capture", "/class", "right-click", "right-click", "select:7:10", "release"), effects);
            assertEquals(target, new ClassSelectorStore(directory.resolve("classes.json")).lastLoaded(account));
        } finally { audio.close(); }
    }
}

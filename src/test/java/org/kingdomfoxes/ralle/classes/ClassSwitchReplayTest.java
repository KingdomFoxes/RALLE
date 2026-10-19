package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchReplayTest {
    @TempDir Path directory;
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

package org.kingdomfoxes.ralle.classes;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Replay the book and video observations together, including the actual output-mute gate. */
class ClassSwitchReplayTest {
    @Test void opensBookSelectsExactlyOnceRevealsNativeBlackAndUnmutesOnlyAfterWorldReturn() throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var account = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var book = ClassMenuEvidence.decode(fixture("wynncraft-character-book-2026-10-19.json"), account, JsonOps.INSTANCE).orElseThrow();
        String target = book.characters().get(1).id();
        var effects = new ArrayList<String>();
        var audio = new ClassSwitchAudio(() -> effects.add("refresh-audio"));
        var controller = new ClassSwitchController(new ClassSwitchController.Actions() {
            public void muteAudio() { audio.mute(); }
            public void restoreAudio() { audio.close(); }
            public void captureFrame() { effects.add("capture"); }
            public void sendClassCommand() { effects.add("/class"); }
            public void rightClickSelectionStage() { effects.add("right-click"); }
            public void selectCharacter(int menu, int slot) { effects.add("select:" + menu + ":" + slot); }
            public void releaseFrame() { effects.add("release"); }
            public void failed(ClassSwitchController.Failure failure) { fail(failure.toString()); }
            public void completed(String reference) { assertEquals(target, reference); effects.add("completed"); }
        });
        var stage = new ClassStageReadiness();
        try {
            assertTrue(controller.begin(account, target, null, true, 0));
            assertEquals(0f, ClassSwitchAudio.outputVolume(.7f));
            assertFalse(effects.contains("/class"));
            controller.frameCaptured();
            for (var entry : fixture("transition-video-samples.json").getAsJsonArray("samples")) {
                var sample = entry.getAsJsonObject();
                double seconds = sample.get("timeSeconds").getAsDouble();
                controller.tick(account, true, (long) (seconds * 1000));
                var pixels = ByteBuffer.allocate(64 * 36 * 4);
                byte[] mask = Base64.getDecoder().decode(sample.get("darkMask").getAsString());
                int bit = 0;
                for (int y = 9; y < 27; y++) for (int x = 16; x < 48; x++, bit++) {
                    byte value = (byte) ((mask[bit / 8] & 1 << bit % 8) == 0 ? 255 : 0);
                    int offset = (y * 64 + x) * 4;
                    pixels.put(offset, value).put(offset + 1, value).put(offset + 2, value);
                }
                boolean black = ClassTransitionPixels.black(pixels, 64, 36);
                if (controller.state() == ClassSwitchController.State.WAITING_STAGE) {
                    if (seconds >= 2.9) stage.prompt(); // Prompt may precede a usable scene.
                    stage.frame(black, seconds >= 4.1);
                    if (stage.ready()) controller.selectionStage();
                }
                if (seconds >= 4.5) controller.menu(7, List.of(new ClassSwitchController.Target(target, book.resolve(target).orElseThrow())), true);
                if (seconds >= 6 && controller.state() == ClassSwitchController.State.WAITING_TRANSITION) controller.menuClosed(7);
                if (black) controller.transition(target);
                else if (controller.state() == ClassSwitchController.State.WAITING_WORLD) controller.worldReady();
                if (seconds < 8.3) assertFalse(effects.contains("release"), "Early release at " + seconds);
                if (seconds <= 8.8) assertEquals(0f, ClassSwitchAudio.outputVolume(.7f), "Early audio at " + seconds);
            }
            assertFalse(controller.active());
            assertEquals(.7f, ClassSwitchAudio.outputVolume(.7f));
            assertEquals(List.of("refresh-audio", "capture", "/class", "right-click", "select:7:10", "release", "refresh-audio", "completed"), effects);
        } finally { audio.close(); }
    }
    private static com.google.gson.JsonObject fixture(String name) throws Exception {
        try (var input = ClassSwitchReplayTest.class.getResourceAsStream("/classes/" + name)) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}

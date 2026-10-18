package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchControllerTest {
    private final UUID account = UUID.randomUUID();
    private static final class Actions implements ClassSwitchController.Actions {
        final List<String> calls = new ArrayList<>();
        ClassSwitchController.Failure failure;
        boolean captureThrows, commandThrows;
        public void captureFrame() { calls.add("capture"); if (captureThrows) throw new IllegalStateException(); }
        public void sendClassCommand() { calls.add("class"); if (commandThrows) throw new IllegalStateException(); }
        public void rightClickSelectionStage() { calls.add("right-click"); }
        public void selectCharacter(int containerId, int slot) { calls.add("select:" + containerId + ":" + slot); }
        public void releaseFrame() { calls.add("release"); }
        public void failed(ClassSwitchController.Failure failure) { this.failure = failure; }
    }
    private static List<ClassSwitchController.Target> targets() {
        return List.of(new ClassSwitchController.Target("a", 10), new ClassSwitchController.Target("b", 11));
    }

    @Test void observationsAdvanceExactlyOnceAndReleaseOnlyAtActualTransition() {
        var actions = new Actions();
        var controller = new ClassSwitchController(actions);
        assertFalse(controller.begin(account, "a", "a", true, 0));
        assertFalse(controller.begin(account, "b", "a", false, 0));
        assertTrue(actions.calls.isEmpty());
        assertTrue(controller.begin(account, "b", "a", true, 0));
        assertFalse(controller.begin(account, "b", "a", true, 1));
        controller.menu(1, targets(), true); // Out of order: cannot click before the stage.
        controller.transition("b");
        controller.tick(account, true, 8_000); // Delayed response is allowed, without additional commands.
        assertEquals(List.of("capture", "class"), actions.calls);
        controller.selectionStage(); controller.selectionStage();
        controller.menu(1, targets(), false);
        assertEquals(ClassSwitchController.State.WAITING_MENU, controller.state());
        controller.menu(1, targets(), true); controller.menu(1, targets(), true);
        assertEquals(ClassSwitchController.State.WAITING_TRANSITION, controller.state());
        controller.transition("b"); controller.transition("b"); controller.cancel();
        assertFalse(controller.active());
        assertNull(actions.failure);
        assertEquals(List.of("capture", "class", "right-click", "select:1:11", "release"), actions.calls);
    }

    @Test void everyStageSupportsCancellationTimeoutDisconnectAndUnexpectedMenus() {
        for (int stage = 0; stage < 3; stage++) for (int exit = 0; exit < 5; exit++) {
            var actions = new Actions();
            var controller = new ClassSwitchController(actions);
            assertTrue(controller.begin(account, "b", "a", true, 0));
            if (stage >= 1) controller.selectionStage();
            if (stage >= 2) controller.menu(1, targets(), true);
            switch (exit) {
                case 0 -> controller.cancel();
                case 1 -> controller.tick(account, true, 15_000);
                case 2 -> controller.tick(account, false, 2);
                case 3 -> controller.unexpectedMenu();
                case 4 -> controller.tick(UUID.randomUUID(), true, 2);
            }
            assertFalse(controller.active());
            assertNotNull(actions.failure);
            controller.selectionStage(); controller.menu(2, targets(), true); controller.cancel();
            assertEquals(1, Collections.frequency(actions.calls, "release"));
            assertEquals(1, Collections.frequency(actions.calls, "class"));
        }
    }

    @Test void ambiguousMissingOrChangedTargetsNeverClickAndWrongTransitionFails() {
        var invalid = List.of(List.<ClassSwitchController.Target>of(),
                List.of(new ClassSwitchController.Target("a", 1)),
                List.of(new ClassSwitchController.Target("b", 1), new ClassSwitchController.Target("b", 2)),
                List.of(new ClassSwitchController.Target("a", 1), new ClassSwitchController.Target("b", 1)));
        for (var targets : invalid) {
            var actions = new Actions();
            var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0);
            controller.selectionStage(); controller.menu(1, targets, true);
            assertEquals(ClassSwitchController.Failure.AMBIGUOUS_TARGET, actions.failure);
            assertEquals(List.of("capture", "class", "right-click", "release"), actions.calls);
        }
        var actions = new Actions();
        var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0);
        controller.selectionStage(); controller.menu(1, targets(), true);
        controller.transition("a");
        assertEquals(ClassSwitchController.Failure.AMBIGUOUS_TARGET, actions.failure);
        assertFalse(controller.active());
    }

    @Test void captureAndCommandFailuresCleanUpFrameExactlyOnce() {
        for (boolean captureFailure : List.of(true, false)) {
            var actions = new Actions();
            actions.captureThrows = captureFailure;
            actions.commandThrows = !captureFailure;
            var controller = new ClassSwitchController(actions);
            assertFalse(controller.begin(account, "b", null, true, 0));
            controller.cancel();
            assertEquals(1, Collections.frequency(actions.calls, "release"));
            assertEquals(ClassSwitchController.Failure.UNAVAILABLE, actions.failure);
        }
    }

    @Test void selectionPromptRequiresBothServerInstructions() {
        assertTrue(ClassMenuCapture.selectionPrompt(" Left-Click to play     Right-Click to switch"));
        assertFalse(ClassMenuCapture.selectionPrompt("Left-Click to play"));
        assertFalse(ClassMenuCapture.selectionPrompt("Right-Click to switch"));
    }
}

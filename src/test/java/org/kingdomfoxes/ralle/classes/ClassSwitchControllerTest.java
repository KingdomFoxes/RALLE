package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchControllerTest {
    private final UUID account = UUID.randomUUID();
    private static final class Actions implements ClassSwitchController.Actions {
        final List<String> calls = new ArrayList<>();
        ClassSwitchController.Failure failure;
        boolean captureThrows, commandThrows, muted;
        String completed;
        int muteCalls, restoreCalls;
        public void captureFrame() { calls.add("capture"); if (captureThrows) throw new IllegalStateException(); }
        public void sendClassCommand() { calls.add("class"); if (commandThrows) throw new IllegalStateException(); }
        public void rightClickSelectionStage() { calls.add("right-click"); }
        public void selectCharacter(int container, int slot) { calls.add("select:" + container + ":" + slot); }
        public void releaseFrame() { calls.add("release"); }
        public void failed(ClassSwitchController.Failure failure) { this.failure = failure; }
        public void completed(String reference) { completed = reference; }
        public void muteAudio() { muted = true; muteCalls++; }
        public void restoreAudio() { muted = false; restoreCalls++; }
    }
    private static List<ClassSwitchController.Target> targets() {
        return List.of(new ClassSwitchController.Target("a", 10), new ClassSwitchController.Target("b", 11));
    }

    @Test void capturesBeforeCommandAndRetainsFrameAndAudioThroughTheEntireDoorAnimation() {
        var actions = new Actions();
        var controller = new ClassSwitchController(actions);
        assertFalse(controller.begin(account, "a", "a", true, 0));
        assertFalse(controller.begin(account, "b", "a", false, 0));
        assertTrue(controller.begin(account, "b", "a", true, 0));
        assertFalse(controller.begin(account, "b", "a", true, 0));
        controller.selectionStage(0); controller.tick(account, true, 0); controller.menu(7, targets(), true);
        assertEquals(List.of("capture"), actions.calls);
        controller.frameCaptured(); controller.frameCaptured();
        controller.selectionStage(0); controller.selectionStage(99);
        controller.menu(7, targets(), false);
        assertEquals(ClassSwitchController.State.WAITING_MENU, controller.state());
        controller.bookOpened(); controller.menu(7, targets(), true); controller.menu(7, targets(), true);
        controller.tick(account, true, 0);
        assertEquals(ClassSwitchController.State.WAITING_TRANSITION, controller.state());
        controller.menuClosed(7, 0); controller.menuClosed(7, 0); controller.menuClosed(0, 0);
        assertEquals(ClassSwitchController.State.WAITING_WORLD, controller.state());
        controller.tick(account, true, 1599);
        assertFalse(actions.calls.contains("release"));
        assertTrue(actions.muted);
        controller.tick(account, true, 1600); controller.tick(account, true, 1600); controller.cancel();
        assertFalse(controller.active()); assertFalse(actions.muted); assertNull(actions.failure);
        assertEquals("b", actions.completed);
        assertEquals(1, actions.muteCalls); assertEquals(1, actions.restoreCalls);
        assertEquals(List.of("capture", "class", "right-click", "select:7:11", "release"), actions.calls);
    }

    @Test void everyStageSupportsCancellationTimeoutDisconnectAccountChangesAndUnexpectedMenus() {
        for (int stage = 0; stage < 5; stage++) for (int exit = 0; exit < 5; exit++) {
            var actions = new Actions();
            var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0);
            if (stage >= 1) controller.frameCaptured();
            if (stage >= 2) controller.selectionStage(0);
            if (stage >= 3) controller.menu(7, targets(), true);
            if (stage >= 4) controller.menuClosed(7, 0);
            switch (exit) {
                case 0 -> controller.cancel();
                case 1 -> controller.tick(account, true, 15_000);
                case 2 -> controller.tick(account, false, 2);
                case 3 -> controller.unexpectedMenu();
                case 4 -> controller.tick(UUID.randomUUID(), true, 2);
            }
            assertFalse(controller.active()); assertNotNull(actions.failure);
            controller.selectionStage(5_000); controller.menu(7, targets(), true); controller.tick(account, true, 1600); controller.cancel();
            assertEquals(1, Collections.frequency(actions.calls, "release"));
            assertEquals(stage >= 1 ? 1 : 0, Collections.frequency(actions.calls, "class"));
            assertNull(actions.completed); assertFalse(actions.muted); assertEquals(1, actions.restoreCalls);
        }
    }

    @Test void ambiguousMissingOrChangedTargetsNeverClick() {
        var invalid = List.of(List.<ClassSwitchController.Target>of(),
                List.of(new ClassSwitchController.Target("a", 1)),
                List.of(new ClassSwitchController.Target("b", 1), new ClassSwitchController.Target("b", 2)),
                List.of(new ClassSwitchController.Target("a", 1), new ClassSwitchController.Target("b", 1)));
        for (var targets : invalid) {
            var actions = new Actions();
            var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0);
            controller.frameCaptured(); controller.selectionStage(0); controller.menu(7, targets, true);
            assertEquals(ClassSwitchController.Failure.AMBIGUOUS_TARGET, actions.failure);
            assertEquals(List.of("capture", "class", "right-click", "release"), actions.calls);
        }
    }

    @Test void captureAndCommandFailuresReleaseEverythingExactlyOnce() {
        for (boolean captureFailure : List.of(true, false)) {
            var actions = new Actions(); actions.captureThrows = captureFailure; actions.commandThrows = !captureFailure;
            var controller = new ClassSwitchController(actions);
            assertEquals(!captureFailure, controller.begin(account, "b", null, true, 0));
            controller.frameCaptured(); controller.cancel();
            assertFalse(controller.active()); assertEquals(1, Collections.frequency(actions.calls, "release"));
            assertEquals(ClassSwitchController.Failure.UNAVAILABLE, actions.failure);
            assertFalse(actions.muted); assertEquals(1, actions.restoreCalls);
        }
    }

    @Test void unrelatedClosesFailAndInitialInventoryCloseCannotArmCompletion() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0);
        controller.frameCaptured(); controller.menuClosed(0, 0); controller.tick(account, true, 1600);
        assertEquals(ClassSwitchController.State.WAITING_STAGE, controller.state());
        controller.selectionStage(0); controller.menu(7, targets(), true); controller.menuClosed(8, 0);
        assertEquals(ClassSwitchController.Failure.UNEXPECTED_MENU, actions.failure); assertNull(actions.completed);
    }

    @Test void nativeZeroIdCloseMatchesTheActualBookEvenIfAnotherModAlreadyDismissedIt() {
        for (int liveContainer : new int[] {7, 0}) {
            var actions = new Actions(); var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0); controller.frameCaptured(); controller.selectionStage(0);
            controller.bookOpened(); controller.menu(7, targets(), true);
            controller.serverClosed(0, liveContainer, 0);
            assertEquals(ClassSwitchController.State.WAITING_WORLD, controller.state());
            assertTrue(actions.muted); assertFalse(actions.calls.contains("release")); assertNull(actions.failure);
            controller.tick(account, true, 1600); assertEquals("b", actions.completed);
        }
    }

    @Test void autoOpenSkipsStageClicksEvenWithPartialContents() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); assertFalse(controller.bookOpened());
        controller.frameCaptured(); assertTrue(controller.bookOpened()); controller.menu(7, targets(), false);
        for (int i = 0; i < 10; i++) controller.selectionStage(i * 100);
        assertFalse(controller.acceptingStageClicks());
        controller.menu(7, targets(), true); controller.menuClosed(7, 0); controller.tick(account, true, 1600);
        assertEquals(List.of("capture", "class", "select:7:11", "release"), actions.calls);
    }

    @Test void retriesAreImmediateThen100MillisApartAndStopBeforeBookContentsAreComplete() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 100); controller.frameCaptured();
        controller.selectionStage(100); controller.selectionStage(199);
        assertEquals(1, Collections.frequency(actions.calls, "right-click"));
        controller.selectionStage(200); controller.selectionStage(300);
        assertEquals(3, Collections.frequency(actions.calls, "right-click"));
        assertTrue(controller.bookOpened()); controller.menu(7, targets(), false); controller.selectionStage(400);
        assertEquals(3, Collections.frequency(actions.calls, "right-click"));
        controller.menu(7, targets(), true);
        assertEquals(1, Collections.frequency(actions.calls, "class"));
        assertEquals(1, Collections.frequency(actions.calls, "select:7:11"));
    }

    @Test void attemptCapTimeoutAndSlowTicksPreventUnboundedClicksAndCatchupBursts() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        for (int i = 0; i < 150; i++) controller.selectionStage(i * 100L);
        controller.selectionStage(15_000); controller.selectionStage(15_000);
        assertEquals(ClassSwitchController.Failure.TIMEOUT, actions.failure);
        assertEquals(150, Collections.frequency(actions.calls, "right-click")); assertFalse(actions.muted);
        assertTrue(controller.begin(account, "b", null, true, 20_000)); controller.frameCaptured();
        controller.selectionStage(20_000); controller.selectionStage(25_000); controller.selectionStage(25_000); controller.selectionStage(25_099);
        assertEquals(152, Collections.frequency(actions.calls, "right-click"));
        controller.cancel(); controller.selectionStage(25_100);
        assertEquals(152, Collections.frequency(actions.calls, "right-click"));
    }

    @Test void releaseUsesFirstBookClosePlus1600MillisWithoutWelcomeOrCameraSignals() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured(); controller.selectionStage(0);
        controller.bookOpened(); controller.menu(7, targets(), true);
        controller.tick(account, true, 1_000);
        assertTrue(controller.active()); // Time since click alone cannot start the timer.
        controller.serverClosed(7, 7, 1_000);
        controller.serverClosed(0, 0, 1_500); controller.menuClosed(99, 1_700);
        controller.tick(account, true, 2_599);
        assertTrue(controller.active()); assertTrue(actions.muted);
        controller.tick(account, true, 2_600);
        assertFalse(controller.active()); assertFalse(actions.muted); assertEquals("b", actions.completed);
        assertEquals(1, Collections.frequency(actions.calls, "release"));
    }
}

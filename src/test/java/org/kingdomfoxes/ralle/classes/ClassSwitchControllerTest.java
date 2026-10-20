package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassSwitchControllerTest {
    private final UUID account = UUID.randomUUID();
    private static final class Actions implements ClassSwitchController.Actions {
        final List<String> calls = new ArrayList<>();
        ClassSwitchController.Failure failure;
        boolean captureThrows, commandThrows, muted, commandRetry, bookClose = true;
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
        public boolean canRetryClassCommand() { return commandRetry; }
        public boolean closeCharacterBook(int container) { calls.add("close:" + container); return bookClose; }
    }
    private static List<ClassSwitchController.Target> targets() {
        return List.of(new ClassSwitchController.Target("a", 10), new ClassSwitchController.Target("b", 11));
    }

    @Test void preRenderCommandOverlapsDrawingButStageInputWaitsForTheCapturedFrame() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        assertTrue(controller.begin(account, "b", "a", true, 0));
        controller.frameRendering(); controller.frameRendering();
        assertEquals(List.of("capture", "class"), actions.calls);
        assertEquals(ClassSwitchController.State.CAPTURING, controller.state());
        controller.selectionStage(0);
        assertFalse(controller.acceptingStageClicks());
        controller.frameCaptured(); controller.frameCaptured(); controller.frameRendering();
        assertTrue(controller.bookOpened());
        controller.menu(7, targets(), true);
        controller.menuClosed(7, 100);
        controller.tick(account, true, 1699);
        assertTrue(actions.muted);
        controller.tick(account, true, 1700);
        assertEquals(List.of("capture", "class", "select:7:11", "release"), actions.calls);
    }

    @Test void cancellationAndFailureBetweenEarlyCommandAndCaptureCannotResendOrHoldAudio() {
        for (boolean fail : List.of(false, true)) {
            var actions = new Actions(); var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0);
            controller.frameRendering();
            if (fail) controller.unavailable(); else controller.cancel();
            controller.frameCaptured(); controller.frameRendering();
            assertEquals(List.of("capture", "class", "release"), actions.calls);
            assertFalse(actions.muted);
            assertEquals(1, actions.restoreCalls);
        }
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

    @Test void captureFailureIsImmediateAndCommandFailureGetsOnlyBoundedRetries() {
        for (boolean captureFailure : List.of(true, false)) {
            var actions = new Actions(); actions.captureThrows = captureFailure; actions.commandThrows = !captureFailure;
            actions.commandRetry = true;
            var controller = new ClassSwitchController(actions);
            assertEquals(!captureFailure, controller.begin(account, "b", null, true, 0));
            controller.frameCaptured();
            if (!captureFailure) {
                assertTrue(controller.active());
                controller.tick(account, true, 750);
                controller.tick(account, true, 1500);
                assertEquals(3, Collections.frequency(actions.calls, "class"));
            }
            controller.cancel();
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
        controller.tick(account, true, 799);
        assertTrue(controller.active()); // Time since click alone cannot start the timer.
        controller.serverClosed(7, 7, 1_000);
        controller.serverClosed(0, 0, 1_500); controller.menuClosed(99, 1_700);
        controller.tick(account, true, 2_599);
        assertTrue(controller.active()); assertTrue(actions.muted);
        controller.tick(account, true, 2_600);
        assertFalse(controller.active()); assertFalse(actions.muted); assertEquals("b", actions.completed);
        assertEquals(1, Collections.frequency(actions.calls, "release"));
    }

    @Test void commandRetriesRequireUsableGameplayAndStopOnTheFirstStageSignal() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        controller.tick(account, true, 750); // No live gameplay permission.
        assertEquals(1, Collections.frequency(actions.calls, "class"));
        actions.commandRetry = true;
        controller.tick(account, true, 751); controller.tick(account, true, 751);
        controller.tick(account, true, 1500);
        assertEquals(2, Collections.frequency(actions.calls, "class"));
        controller.observedSelectionStage();
        controller.tick(account, true, 3000);
        assertEquals(2, Collections.frequency(actions.calls, "class"));
        controller.cancel(); controller.tick(account, true, 4000);
        assertFalse(actions.muted);
    }

    @Test void silentCommandIsCappedWithoutCatchupBurstsAndNextSwitchStartsFresh() {
        var actions = new Actions(); actions.commandRetry = true;
        var controller = new ClassSwitchController(actions);
        for (long start : new long[] {0, 20_000}) {
            assertTrue(controller.begin(account, "b", null, true, start)); controller.frameCaptured();
            controller.tick(account, true, start + 749);
            controller.tick(account, true, start + 5000); controller.tick(account, true, start + 5000);
            controller.tick(account, true, start + 5750); controller.tick(account, true, start + 10_000);
            controller.tick(account, true, start + 15_000);
            assertEquals(ClassSwitchController.Failure.TIMEOUT, actions.failure);
            assertFalse(actions.muted);
        }
        assertEquals(6, Collections.frequency(actions.calls, "class"));
        assertEquals(2, actions.restoreCalls);
    }

    @Test void incompleteBookClosesOnceAndFreshBookSelectsImmediatelyWithoutAnotherClassCommand() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        controller.bookOpened(7, 100);
        controller.menu(7, targets(), false);
        controller.tick(account, true, 699);
        assertFalse(actions.calls.contains("close:7"));
        controller.tick(account, true, 700); controller.tick(account, true, 700);
        assertEquals(1, Collections.frequency(actions.calls, "close:7"));
        controller.serverClosed(7, 0, 701); // Delayed close of the retired book cannot fail a retry.
        controller.selectionStage(701);
        controller.bookOpened(8, 720); controller.menu(8, targets(), true);
        controller.serverClosed(7, 8, 730); // Nor can it acknowledge a new selection.
        assertEquals(ClassSwitchController.State.WAITING_TRANSITION, controller.state());
        controller.serverClosed(8, 8, 750); controller.tick(account, true, 2350);
        assertEquals("b", actions.completed);
        assertEquals(1, Collections.frequency(actions.calls, "capture"));
        assertEquals(1, Collections.frequency(actions.calls, "class"));
        assertEquals(1, Collections.frequency(actions.calls, "select:8:11"));
        assertEquals(1, actions.restoreCalls);
    }

    @Test void ignoredTargetClickRecoversOnlyWhileThatBookRemainsLiveAndNeverClicksItTwice() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        controller.bookOpened(7, 0); controller.menu(7, targets(), true);
        controller.tick(account, true, 799);
        assertFalse(actions.calls.contains("close:7"));
        controller.tick(account, true, 800);
        assertEquals(ClassSwitchController.State.WAITING_STAGE, controller.state());
        controller.bookOpened(8, 820); controller.menu(8, targets(), true);
        controller.serverClosed(8, 8, 850); controller.tick(account, true, 2450);
        assertEquals(1, Collections.frequency(actions.calls, "select:7:11"));
        assertEquals(1, Collections.frequency(actions.calls, "select:8:11"));
        assertEquals("b", actions.completed);
    }

    @Test void bookRecoveryIsCappedAndStillUsesTheOriginalDeadline() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        for (int i = 0; i < 3; i++) {
            controller.bookOpened(7 + i, i * 600L);
            controller.tick(account, true, (i + 1) * 600L);
        }
        assertEquals(ClassSwitchController.Failure.TIMEOUT, actions.failure);
        assertEquals(2, actions.calls.stream().filter(s -> s.startsWith("close:")).count());
        assertFalse(actions.muted);
        assertTrue(controller.begin(account, "b", null, true, 20_000)); controller.frameCaptured();
        controller.bookOpened(10, 34_500); controller.tick(account, true, 35_000);
        assertEquals(ClassSwitchController.Failure.TIMEOUT, actions.failure);
        assertFalse(actions.calls.contains("close:10"));
    }

    @Test void unrelatedLiveMenuDuringRecoveryFailsWithoutFurtherAutomation() {
        var actions = new Actions(); actions.bookClose = false;
        var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        controller.bookOpened(7, 0); controller.tick(account, true, 600);
        assertEquals(ClassSwitchController.Failure.UNEXPECTED_MENU, actions.failure);
        controller.selectionStage(700); controller.tick(account, true, 1000);
        assertFalse(actions.calls.contains("right-click"));
        assertFalse(actions.muted);
    }

    @Test void rapidNextSwitchIgnoresPreviousBookClosesAndAllowsFreshContainerIdReuse() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", "a", true, 0); controller.frameCaptured();
        controller.bookOpened(7, 10); controller.menu(7, targets(), true);
        controller.serverClosed(7, 7, 20); controller.tick(account, true, 1620);
        assertTrue(controller.begin(account, "a", "b", true, 1621)); controller.frameCaptured();
        controller.serverClosed(7, 0, 1622);
        assertEquals(ClassSwitchController.State.WAITING_STAGE, controller.state());
        controller.bookOpened(8, 1630); controller.menu(8, targets(), true);
        controller.serverClosed(7, 8, 1631);
        assertEquals(ClassSwitchController.State.WAITING_TRANSITION, controller.state());
        controller.serverClosed(8, 8, 1640); controller.tick(account, true, 3240);
        assertEquals("a", actions.completed);
        assertTrue(controller.begin(account, "b", "a", true, 3241)); controller.frameCaptured();
        assertTrue(controller.bookOpened(7, 3250)); // A fresh server open reuses this ID.
        assertFalse(controller.retiredBook(7));
        controller.menu(7, targets(), true); controller.serverClosed(7, 7, 3260);
        controller.tick(account, true, 4860);
        assertEquals("b", actions.completed);
        assertEquals(3, actions.restoreCalls);
        assertEquals(3, Collections.frequency(actions.calls, "class"));
    }

    @Test void rapidNextSwitchIgnoresLocallyDismissedWorldQueryClosesBeforeItsOwnBookArrives() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", "a", true, 0); controller.frameCaptured();
        controller.bookOpened(6, 10); controller.menu(6, targets(), true);
        controller.serverClosed(6, 6, 20); controller.tick(account, true, 1620);
        assertTrue(controller.begin(account, "a", "b", true, 1621));
        // Actual October 20 log: close 9/live 0 after selecting book 6 on the last switch.
        // This is a query container, so retaining only previous character-book IDs is insufficient.
        controller.serverClosed(9, 0, 1622);
        assertEquals(ClassSwitchController.State.CAPTURING, controller.state());
        controller.frameCaptured(); controller.serverClosed(9, 0, 1623);
        assertEquals(ClassSwitchController.State.WAITING_STAGE, controller.state());
        controller.selectionStage(1624); controller.serverClosed(15, 0, 1625);
        assertEquals(ClassSwitchController.State.WAITING_MENU, controller.state());
        assertTrue(actions.muted); assertNull(actions.failure); assertNull(controller.retry());
        assertTrue(controller.bookOpened(9, 1630)); // Fresh book may reuse the query's ID.
        controller.menu(9, targets(), true); controller.serverClosed(9, 9, 1640);
        controller.tick(account, true, 3239); assertTrue(controller.active());
        controller.tick(account, true, 3240); assertFalse(controller.active());
        assertEquals("a", actions.completed); assertEquals(2, actions.restoreCalls);
        assertEquals(2, Collections.frequency(actions.calls, "class"));
        assertEquals(1, Collections.frequency(actions.calls, "select:9:10"));
    }

    @Test void unrelatedClosesWithALiveMenuOrAfterSelectingStillFailClosed() {
        for (boolean selected : new boolean[] {false, true}) {
            var actions = new Actions(); var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0); controller.frameCaptured();
            if (selected) {
                controller.bookOpened(7, 10); controller.menu(7, targets(), true);
            }
            controller.serverClosed(9, selected ? 0 : 9, 20);
            assertEquals(ClassSwitchController.Failure.UNEXPECTED_MENU, actions.failure);
            assertNull(actions.completed); assertFalse(actions.muted);
        }
    }

    @Test void prematurelyClosedPartialBookRecoversWithoutClosingAnotherMenu() {
        var actions = new Actions(); var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        controller.bookOpened(7, 10); controller.serverClosed(0, 7, 20);
        assertEquals(ClassSwitchController.State.WAITING_STAGE, controller.state());
        assertTrue(actions.calls.stream().noneMatch(s -> s.startsWith("close:")));
        controller.bookOpened(8, 30);
        controller.menu(7, targets(), true); // Old contents cannot select in the replacement book.
        assertTrue(actions.calls.stream().noneMatch(s -> s.startsWith("select:")));
        controller.menu(8, targets(), true); controller.serverClosed(8, 8, 40);
        controller.tick(account, true, 1640);
        assertEquals("b", actions.completed);
    }

    @Test void recoveryCancellationDisconnectAndAccountChangesReleaseAndStopRetries() {
        for (int exit = 0; exit < 3; exit++) {
            var actions = new Actions(); actions.commandRetry = true;
            var controller = new ClassSwitchController(actions);
            controller.begin(account, "b", null, true, 0); controller.frameCaptured();
            controller.bookOpened(7, 0); controller.tick(account, true, 600);
            switch (exit) {
                case 0 -> controller.cancel();
                case 1 -> controller.tick(account, false, 601);
                case 2 -> controller.tick(UUID.randomUUID(), true, 601);
            }
            controller.selectionStage(700); controller.bookOpened(8, 700); controller.tick(account, true, 2000);
            assertFalse(controller.active()); assertFalse(actions.muted);
            assertEquals(1, actions.restoreCalls);
            assertEquals(1, Collections.frequency(actions.calls, "class"));
            assertFalse(actions.calls.contains("close:8"));
        }
    }

    @Test void retryFeedbackCountsActualRetriesAndClearsOnAcknowledgementOrTheNextOperation() {
        var actions = new Actions(); actions.commandRetry = true;
        var controller = new ClassSwitchController(actions);
        controller.begin(account, "b", null, true, 0); controller.frameCaptured();
        assertNull(controller.retry()); // Initial submission is not a retry.
        controller.tick(account, true, 750);
        assertEquals(new ClassSwitchController.Retry(ClassSwitchController.RetryKind.COMMAND, 1, 2), controller.retry());
        controller.tick(account, true, 1499);
        assertEquals(1, controller.retry().number());
        controller.tick(account, true, 1500);
        assertEquals(2, controller.retry().number());
        controller.bookOpened(7, 1600); controller.tick(account, true, 2200);
        assertEquals(new ClassSwitchController.Retry(ClassSwitchController.RetryKind.BOOK, 1, 2), controller.retry());
        controller.bookOpened(8, 2300); controller.tick(account, true, 2900);
        assertEquals(new ClassSwitchController.Retry(ClassSwitchController.RetryKind.BOOK, 2, 2), controller.retry());
        controller.bookOpened(9, 3000); controller.menu(9, targets(), true);
        controller.serverClosed(9, 9, 3010);
        assertNull(controller.retry()); // Recovery succeeded: restore the ordinary modifier status.
        controller.tick(account, true, 4610);
        assertTrue(controller.begin(account, "a", "b", true, 4611)); controller.frameCaptured();
        assertNull(controller.retry());
        controller.tick(account, true, 5361); assertNotNull(controller.retry());
        controller.cancel(); assertNull(controller.retry());
    }
}

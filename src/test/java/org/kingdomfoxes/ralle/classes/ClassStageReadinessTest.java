package org.kingdomfoxes.ralle.classes;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassStageReadinessTest {
    @Test void earlyPromptAndBrightOutgoingWorldCannotTriggerTheClickDuringInitialBlack() {
        var stage = new ClassStageReadiness();
        stage.prompt();
        stage.frame(false, true); // Outgoing gameplay before /class has faded.
        assertFalse(stage.ready());
        stage.frame(true, true); // The exact failure: prompt is already present during black.
        assertFalse(stage.ready());
        stage.frame(false, true); // The actual selection scene is finally rendered.
        assertTrue(stage.ready());
        stage.frame(true, true);
        assertFalse(stage.ready());
    }
    @Test void promptWorldAndUsableCameraAreAllRequiredWithoutAClockDelay() {
        var stage = new ClassStageReadiness();
        stage.frame(true, false);
        stage.frame(false, true);
        assertFalse(stage.ready());
        stage.prompt();
        assertTrue(stage.ready());
        stage.frame(false, false); // Container/overlay, missing player, or hands still busy.
        assertFalse(stage.ready());
        stage.frame(false, true);
        assertTrue(stage.ready());
    }
    @Test void aNewOperationCannotReuseTheOldPromptOrScene() {
        var stage = new ClassStageReadiness();
        stage.prompt(); stage.frame(true, true); stage.frame(false, true);
        assertTrue(stage.ready());
        stage.reset();
        stage.frame(false, true); stage.prompt();
        assertFalse(stage.ready());
    }
    @Test void aReplacedPromptCannotAuthorizeAnInteraction() {
        var stage = new ClassStageReadiness();
        stage.prompt(); stage.frame(true, true); stage.frame(false, true);
        assertTrue(stage.ready());
        stage.prompt(false);
        assertFalse(stage.ready());
        stage.prompt();
        assertTrue(stage.ready()); // Keep observed frames, but require the current stage prompt.
    }
}

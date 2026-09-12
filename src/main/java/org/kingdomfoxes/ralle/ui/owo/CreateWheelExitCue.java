package org.kingdomfoxes.ralle.ui.owo;

/** Arms the normal creation cue only after acceptance and consumes it on the real wheel exit. */
final class CreateWheelExitCue {
    private boolean pending;

    void creationAccepted() { pending = true; }

    boolean wheelRemoved(boolean temporaryPartyPromptRemoval) {
        if (temporaryPartyPromptRemoval || !pending) return false;
        pending = false;
        return true;
    }
}

package org.kingdomfoxes.ralle.sound;

/** Client-only feedback used by chat screenshot selection and copying. */
public interface ChatSelectionSoundPlayer {
    void playSelectionCount(int logicalMessageCount);

    void playCopySuccess();

    /** Flushes a coalesced selection hit when its rate-limit window has elapsed. */
    void tick();

    /** Drops coalesced feedback when the current selection is no longer active. */
    void resetSelection();
}

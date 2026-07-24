package org.kingdomfoxes.ralle.sound;

/** Client-only feedback for Raid LFG host controls and incoming party pings. */
public interface LfgSoundPlayer {
    LfgSoundPlayer SILENT = new LfgSoundPlayer() {};

    default void playKickTargetHover() {}
    default void playKickHoldStart() {}
    default void playKickHoldCancelled() {}
    default void playKickSucceeded() {}
    default void playPartyPing() {}
}

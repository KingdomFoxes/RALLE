package org.kingdomfoxes.ralle.sound;

/** Client-only feedback for Raid LFG controls, membership changes, pings, and discovery cards. */
public interface LfgSoundPlayer {
    LfgSoundPlayer SILENT = new LfgSoundPlayer() {};

    default void playKickTargetHover() {}
    default void playKickHoldStart() {}
    default void playKickHoldCancelled() {}
    default void playKickSucceeded() {}
    default void playPartyPing() {}
    default void playNotificationIn() {}
    default void playNotificationOut() {}
    default void playNewPartyReady() {}
    default void playPartyJoined() {}
    default void playPartyLeft() {}
    default void playRosterSlotOccupied(int occupiedSlot) {}
    default void playPartyFilledRaceLost() {}
}

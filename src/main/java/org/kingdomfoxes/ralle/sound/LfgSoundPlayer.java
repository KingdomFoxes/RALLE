package org.kingdomfoxes.ralle.sound;

/** Client-only feedback for Raid LFG controls, membership changes, pings, and discovery cards. */
public interface LfgSoundPlayer {
    LfgSoundPlayer SILENT = new LfgSoundPlayer() {};

    default void playKickTargetHover() {}
    default void playKickHoldStart() {}
    default void playKickHoldCancelled() {}
    default void playKickSucceeded() {}
    default void playPartyLocked() {}
    default void playPartyUnlocked() {}
    default void playLockToggle(boolean locked) {
        if (locked) playPartyLocked();
        else playPartyUnlocked();
    }
    default void playPartyPing() {}
    default void playLocalPartyPing() {}
    default void playNotificationIn() {}
    default void playNotificationOut() {}
    default void playNewPartyReady() {}
    default void playPartyCreated() {}
    default void playPartyJoined() {}
    default void playPartyLeft() {}
    default void playRosterSlotOccupied(int occupiedSlot) {}
    default void playPartyFilledRaceLost() {}
}

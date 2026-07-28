package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

/** Main-screen-only sound feedback for confirmed explicit actions. */
final class LfgMainUiSoundTracker {
    void actionCompleted(String action, Throwable failure, LfgSoundPlayer sounds) {
        if (failure != null) return;
        if ("create".equals(action)) sounds.playPartyCreated();
        if ("leave".equals(action)) sounds.playPartyLeft();
    }
}

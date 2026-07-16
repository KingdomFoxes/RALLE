package org.kingdomfoxes.ralle.chat.screenshot;

import org.kingdomfoxes.ralle.sound.ChatSelectionSoundPlayer;

/** Converts logical selection-state changes into optional client sound feedback. */
final class ChatSelectionSoundFeedback {
    private final ChatSelectionSoundPlayer player;
    private int lastLogicalMessageCount;

    ChatSelectionSoundFeedback(ChatSelectionSoundPlayer player) {
        this.player = player;
    }

    void selectionChanged(int anchorMessage, int currentMessage) {
        int count = Math.abs(anchorMessage - currentMessage) + 1;
        if (count == lastLogicalMessageCount) return;
        lastLogicalMessageCount = count;
        player.playSelectionCount(count);
    }

    void copySucceeded() {
        player.playCopySuccess();
    }

    void tick() {
        player.tick();
    }

    void reset() {
        lastLogicalMessageCount = 0;
        player.resetSelection();
    }
}

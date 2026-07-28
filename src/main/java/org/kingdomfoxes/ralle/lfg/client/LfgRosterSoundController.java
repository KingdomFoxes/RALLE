package org.kingdomfoxes.ralle.lfg.client;

import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.Objects;

/**
 * Always-on sound feedback for authoritative roster growth in the viewer's current lobby.
 *
 * <p>Snapshot and clear changes are deliberately silent so reconnect synchronization cannot
 * replay membership cues. The viewer must already belong on both sides of the incremental change,
 * leaving the shared join-result presentation as the sole owner of the viewer's own join cue.</p>
 */
public final class LfgRosterSoundController {
    private final RaidLfgStore store;
    private final LfgSoundPlayer sounds;

    public LfgRosterSoundController(RaidLfgStore store, LfgSoundPlayer sounds) {
        this.store = Objects.requireNonNull(store, "store");
        this.sounds = Objects.requireNonNull(sounds, "sounds");
        store.observeLobbyChanges(this::onLobbyChange);
    }

    private void onLobbyChange(RaidLfgStore.LobbyChange change) {
        if (change.origin() == RaidLfgStore.UpdateOrigin.SNAPSHOT
                || change.origin() == RaidLfgStore.UpdateOrigin.CLEAR
                || change.previous() == null
                || change.current() == null) {
            return;
        }

        var viewer = store.state().viewer();
        if (viewer == null
                || !change.previous().contains(viewer.minecraftUuid())
                || !change.current().contains(viewer.minecraftUuid())) {
            return;
        }

        int previousSize = change.previous().members().size();
        int currentSize = change.current().members().size();
        for (int occupied = previousSize + 1; occupied <= currentSize; occupied++) {
            sounds.playRosterSlotOccupied(Math.min(change.current().capacity(), occupied));
        }
    }
}

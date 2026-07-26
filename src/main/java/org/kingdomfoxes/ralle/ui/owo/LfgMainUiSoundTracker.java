package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.lfg.client.RaidLfgStore;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Main-screen-only sound feedback for confirmed Raid LFG membership changes. */
final class LfgMainUiSoundTracker {
    private UUID viewerId;
    private Map<UUID, LfgProtocol.Lobby> lobbies = Map.of();

    void reset(RaidLfgStore.State state) {
        viewerId = state.viewer() == null ? null : state.viewer().minecraftUuid();
        lobbies = Map.copyOf(state.lobbies());
    }

    void update(RaidLfgStore.State state, LfgSoundPlayer sounds) {
        var currentViewerId = state.viewer() == null ? null : state.viewer().minecraftUuid();
        if (currentViewerId == null || !Objects.equals(viewerId, currentViewerId)) {
            reset(state);
            return;
        }

        for (var current : state.lobbies().values()) {
            var previous = lobbies.get(current.lobbyId());
            if (previous == null) continue;

            boolean viewerWasMember = previous.contains(currentViewerId);
            boolean viewerIsMember = current.contains(currentViewerId);
            if (viewerWasMember && viewerIsMember
                    && current.members().size() > previous.members().size()) {
                for (int occupied = previous.members().size() + 1;
                     occupied <= current.members().size(); occupied++) {
                    sounds.playRosterSlotOccupied(Math.min(current.capacity(), occupied));
                }
            }
        }

        reset(state);
    }

    void actionCompleted(String action, Throwable failure, LfgSoundPlayer sounds) {
        if (failure == null && "leave".equals(action)) sounds.playPartyLeft();
    }
}

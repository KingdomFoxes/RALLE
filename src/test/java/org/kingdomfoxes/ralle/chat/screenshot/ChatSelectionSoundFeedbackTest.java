package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;
import org.kingdomfoxes.ralle.sound.ChatSelectionSoundPlayer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatSelectionSoundFeedbackTest {
    @Test
    void reportsOnlyChangedLogicalMessageCountsInEitherDirection() {
        var player = new RecordingPlayer();
        var feedback = new ChatSelectionSoundFeedback(player);

        feedback.selectionChanged(5, 5);
        feedback.selectionChanged(5, 6);
        feedback.selectionChanged(5, 4);
        feedback.selectionChanged(5, 3);
        feedback.selectionChanged(5, 4);

        assertEquals(List.of(1, 2, 3, 2), player.selectionCounts);
    }

    @Test
    void resetAndFailurePathsProduceNoSuccessSound() {
        var player = new RecordingPlayer();
        var feedback = new ChatSelectionSoundFeedback(player);

        feedback.selectionChanged(2, 4);
        feedback.reset();
        feedback.tick();

        assertEquals(0, player.copySuccesses);
        assertEquals(1, player.resets);
        assertEquals(1, player.ticks);
    }

    @Test
    void copySuccessDelegatesExactlyOnce() {
        var player = new RecordingPlayer();
        var feedback = new ChatSelectionSoundFeedback(player);

        feedback.copySucceeded();

        assertEquals(1, player.copySuccesses);
    }

    private static final class RecordingPlayer implements ChatSelectionSoundPlayer {
        final List<Integer> selectionCounts = new ArrayList<>();
        int copySuccesses;
        int ticks;
        int resets;

        @Override public void playSelectionCount(int logicalMessageCount) {
            selectionCounts.add(logicalMessageCount);
        }

        @Override public void playCopySuccess() {
            copySuccesses++;
        }

        @Override public void tick() {
            ticks++;
        }

        @Override public void resetSelection() {
            resets++;
        }
    }
}

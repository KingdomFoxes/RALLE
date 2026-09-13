package org.kingdomfoxes.ralle.chat.input;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatInputRequestTrackerTest {
    private final ChatTypeTabService tabs = new ChatTypeTabService();
    private final ChatInputRequestTracker requests = new ChatInputRequestTracker(tabs);

    private void click(long now) {
        tabs.rememberPrefix("/p ");
        requests.menuAction(7, "Kingdom Foxes: Diplomacy", "Add Ally", "", now);
    }

    @Test
    void clickAloneDoesNothingAndMatchingServerCloseSwitchesOnce() {
        click(100);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
        requests.serverClosed(7, 200);
        assertEquals(Optional.of(""), tabs.prefixForNewChat());
        assertTrue(tabs.inputSelectedAll());
        long revision = tabs.inputRequestRevision();
        tabs.rememberPrefix("/g ");
        requests.serverClosed(7, 201);
        assertEquals(revision, tabs.inputRequestRevision());
        assertEquals(Optional.of("/g "), tabs.prefixForNewChat());
    }

    @Test
    void wrongContainerTimeoutManualCloseReplacementAndNewClickDiscardCandidate() {
        click(100);
        requests.serverClosed(8, 200);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
        click(100);
        requests.serverClosed(7, 5101);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
        click(100);
        requests.reset(); // Manual close, menu replacement, sent reply, disabled feature, or disconnect.
        requests.serverClosed(7, 200);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
        click(100);
        requests.menuAction(7, "Kingdom Foxes: Diplomacy", "Back", "", 150);
        requests.serverClosed(7, 200);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
    }

    @Test
    void promptClearsCandidateAndPlainReplyKeepsAllForRetries() {
        click(100);
        requests.observePrompt("Please write in chat the username of the player you want to recruit, or cancel.");
        assertEquals(Optional.of(""), tabs.prefixForNewChat());
        tabs.observeSentChat("FriendFox");
        assertEquals(Optional.of(""), tabs.prefixForNewChat());
        long revision = tabs.inputRequestRevision();
        requests.serverClosed(7, 200);
        assertEquals(revision, tabs.inputRequestRevision());
        assertEquals("/help", ChatTypeTabService.outgoingMessage("/help", ""));
    }

    @Test
    void cancellationAndDisconnectCannotLeavePendingInputOrSelectAnotherChannel() {
        click(100);
        requests.observePrompt("You moved and your chat input was canceled.");
        requests.serverClosed(7, 200);
        assertEquals(Optional.of("/p "), tabs.prefixForNewChat());
        click(100);
        requests.reset();
        tabs.resetSession();
        requests.serverClosed(7, 200);
        assertEquals(Optional.empty(), tabs.prefixForNewChat());
    }
}

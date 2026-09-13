package org.kingdomfoxes.ralle.chat.input;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTypeTabServiceTest {
    @Test
    void serverInputSelectionOverridesPriorChannelButAllowsManualChoiceAndCommands() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg FriendFox hello");
        long previousRevision = service.inputRequestRevision();
        service.selectAllForInput();
        assertEquals(previousRevision + 1, service.inputRequestRevision());
        assertEquals(Optional.of(""), service.prefixForNewChat());
        service.observeIncomingSender("OtherFox");
        assertEquals(Optional.of(""), service.prefixForNewChat());
        assertEquals("unchanged draft", ChatTypeTabService.outgoingMessage("unchanged draft", ""));
        assertEquals("/help", ChatTypeTabService.outgoingMessage("/help", ""));
        service.rememberPrefix("/g ");
        assertEquals(false, service.inputSelectedAll());
        assertEquals(Optional.of("/g "), service.prefixForNewChat());
        service.selectAllForInput();
        service.resetSession();
        assertEquals(false, service.inputSelectedAll());
        assertEquals(Optional.empty(), service.prefixForNewChat());
    }

    @Test
    void incomingMessageAddsAndUpdatesTabWithoutChangingSelectedTypeOrDraftDestination() {
        var service = new ChatTypeTabService();
        service.rememberPrefix("/p ");
        service.observeIncomingSender("FirstFox");
        assertEquals(Optional.of("/msg FirstFox "), service.nextPrefix("/p ", "/p "));
        assertEquals(Optional.of("/p "), service.prefixForNewChat());
        service.rememberPrefix("/msg FirstFox ");
        service.observeIncomingSender("NewFox");
        assertEquals(Optional.of("/msg NewFox "), service.prefixForNewChat());
        assertEquals("/msg NewFox ", service.refreshEmptyChannel("/msg FirstFox ", ""));
        assertEquals("/msg FirstFox ", service.refreshEmptyChannel("/msg FirstFox ", "draft"));
        assertEquals("/p ", service.refreshEmptyChannel("/p ", ""));
        assertEquals(Optional.of(""), service.nextPrefix("/msg FirstFox ", "/msg FirstFox "));
        service.resetSession();
        assertEquals(Optional.of(""), service.nextPrefix("/p ", "/p "));
    }

    @Test
    void labelsNeverBecomePartOfTheOutgoingMessage() {
        assertEquals("[Guild]", ChatTypeTabService.channelLabel("/g "));
        assertEquals("[Party]", ChatTypeTabService.channelLabel("/p "));
        assertEquals("[FoxFriend]", ChatTypeTabService.channelLabel("/msg FoxFriend "));
        assertEquals("[All]", ChatTypeTabService.channelLabel(""));
        assertEquals("/g hello", ChatTypeTabService.outgoingMessage("hello", "/g "));
        assertEquals("/p hello", ChatTypeTabService.outgoingMessage("hello", "/p "));
        assertEquals("/msg FoxFriend hello", ChatTypeTabService.outgoingMessage("hello", "/msg FoxFriend "));
        assertEquals("hello", ChatTypeTabService.outgoingMessage("hello", ""));
    }

    @Test
    void emptyBodyDoesNotSendACommandAndExplicitCommandsBypassTheChannel() {
        assertEquals("", ChatTypeTabService.outgoingMessage("", "/g "));
        assertEquals("  ", ChatTypeTabService.outgoingMessage("  ", "/p "));
        assertEquals("/ralle settings", ChatTypeTabService.outgoingMessage("/ralle settings", "/g "));
        assertEquals("  /help", ChatTypeTabService.outgoingMessage("  /help", "/g "));
        assertEquals("[Guild] hello", ChatTypeTabService.outgoingMessage("[Guild] hello", ""));
    }

    @Test
    void explicitCommandsFromTypingPasteHistoryAndDraftsRecoverTheirChannel() {
        assertEquals(Optional.of("/g "), ChatTypeTabService.explicitPrefix("/G hello"));
        assertEquals(Optional.of("/p "), ChatTypeTabService.explicitPrefix("/p "));
        assertEquals(Optional.of("/msg FoxFriend "), ChatTypeTabService.explicitPrefix("/MSG FoxFriend hello"));
        assertEquals(Optional.empty(), ChatTypeTabService.explicitPrefix("/msg incomplete"));
        assertEquals(Optional.empty(), ChatTypeTabService.explicitPrefix("/guild other command"));
        assertEquals(Optional.empty(), ChatTypeTabService.explicitPrefix("/msg invalid-name hello"));
    }

    @Test
    void cyclesGuildPartyAndAllChatWithoutADirectMessageRecipient() {
        var service = new ChatTypeTabService();

        assertEquals(Optional.of("/g "), service.nextPrefix("", null));
        assertEquals(Optional.of("/p "), service.nextPrefix("/g ", "/g "));
        assertEquals(Optional.of(""), service.nextPrefix("/p ", "/p "));
        assertEquals(Optional.of("/g "), service.nextPrefix("", ""));
    }

    @Test
    void completeDirectMessageAddsRecipientInCycleAndPreservesCapitalization() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("MsG FoxFriend hello there");

        assertEquals(Optional.of("/msg FoxFriend "), service.nextPrefix("/p ", "/p "));
        assertEquals(Optional.of(""),
                service.nextPrefix("/msg FoxFriend ", "/msg FoxFriend "));
        assertEquals(Optional.of("/g "), service.nextPrefix("", ""));
    }

    @Test
    void latestCompleteDirectMessageReplacesThePreviousRecipient() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg FirstFox hello");
        service.observeSentCommand("/msg NewFox_123 another message");

        assertEquals(Optional.of("/msg NewFox_123 "), service.nextPrefix("/p ", "/p "));
    }

    @Test
    void incompleteInvalidAndUnrelatedCommandsDoNotUpdateRecipient() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg ValidFox initial");
        service.observeSentCommand("msg MissingMessage");
        service.observeSentCommand("msg ValidFox    ");
        service.observeSentCommand("msg ab hello");
        service.observeSentCommand("msg name-with-dash hello");
        service.observeSentCommand("tell OtherFox hello");
        service.observeSentCommand("w OtherFox hello");
        service.observeSentCommand("r hello");

        assertEquals(Optional.of("/msg ValidFox "), service.nextPrefix("/p ", "/p "));
    }

    @Test
    void minecraftUsernameLengthBoundariesAreEnforced() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg abc hello");
        assertEquals(Optional.of("/msg abc "), service.nextPrefix("/p ", "/p "));

        service.observeSentCommand("msg abcdefghijklmnop hello");
        assertEquals(Optional.of("/msg abcdefghijklmnop "), service.nextPrefix("/p ", "/p "));

        service.observeSentCommand("msg abcdefghijklmnopq hello");
        assertEquals(Optional.of("/msg abcdefghijklmnop "), service.nextPrefix("/p ", "/p "));
    }

    @Test
    void editedPrefixAndMessageDraftRemainVanillaOwned() {
        var service = new ChatTypeTabService();

        assertEquals(Optional.empty(), service.nextPrefix("/guild ", "/g "));
        assertEquals(Optional.empty(), service.nextPrefix("/g hello", "/g "));
        assertEquals(Optional.empty(), service.nextPrefix("/g ", null));
    }

    @Test
    void selectedChatTypeIsRestoredForTheNextChatScreen() {
        var service = new ChatTypeTabService();

        assertEquals(Optional.empty(), service.prefixForNewChat());
        service.rememberPrefix("/p ");
        assertEquals(Optional.of("/p "), service.prefixForNewChat());
        service.rememberPrefix("");
        assertEquals(Optional.of(""), service.prefixForNewChat());
    }

    @Test
    void selectedDirectMessageTypeUsesTheLatestRecipient() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg FirstFox hello");
        service.rememberPrefix("/msg FirstFox ");
        service.observeSentCommand("msg NewFox hello");

        assertEquals(Optional.of("/msg NewFox "), service.prefixForNewChat());
    }

    @Test
    void successfullySentChatAndSupportedCommandsBecomeTheLastUsedType() {
        var service = new ChatTypeTabService();

        service.observeSentCommand("g guild message");
        assertEquals(Optional.of("/g "), service.prefixForNewChat());
        service.observeSentCommand("P party message");
        assertEquals(Optional.of("/p "), service.prefixForNewChat());
        service.observeSentCommand("msg FriendFox direct message");
        assertEquals(Optional.of("/msg FriendFox "), service.prefixForNewChat());
        service.observeSentChat("all chat message");
        assertEquals(Optional.of(""), service.prefixForNewChat());
    }

    @Test
    void incompleteOrUnrelatedCommandsDoNotReplaceTheLastUsedType() {
        var service = new ChatTypeTabService();
        service.rememberPrefix("/p ");

        service.observeSentCommand("g    ");
        service.observeSentCommand("help");
        service.observeSentChat("   ");

        assertEquals(Optional.of("/p "), service.prefixForNewChat());
    }

    @Test
    void disconnectResetClearsSelectedTypeAndDirectMessageRecipient() {
        var service = new ChatTypeTabService();
        service.observeSentCommand("msg SessionFox hello");
        assertEquals(Optional.of("/msg SessionFox "), service.prefixForNewChat());

        service.resetSession();

        assertEquals(Optional.empty(), service.prefixForNewChat());
        assertEquals(Optional.of(""), service.nextPrefix("/p ", "/p "));
    }
}

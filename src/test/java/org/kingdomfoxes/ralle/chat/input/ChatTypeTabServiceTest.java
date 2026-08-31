package org.kingdomfoxes.ralle.chat.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTypeTabServiceTest {
    @TempDir
    Path temporaryDirectory;

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
    void lastDirectMessageRecipientPersistsAcrossServiceInstances() throws Exception {
        var path = temporaryDirectory.resolve("ralle-chat-input.properties");
        var service = new ChatTypeTabService(path);
        service.observeSentCommand("msg PersistedFox hello");

        assertEquals(Optional.of("/msg PersistedFox "),
                new ChatTypeTabService(path).nextPrefix("/p ", "/p "));
        assertEquals(true, Files.readString(path).contains("last-direct-message-recipient=PersistedFox"));
    }

    @Test
    void invalidPersistedRecipientIsIgnored() throws Exception {
        var path = temporaryDirectory.resolve("ralle-chat-input.properties");
        Files.writeString(path, "last-direct-message-recipient=not-valid!\n");

        assertEquals(Optional.of(""), new ChatTypeTabService(path).nextPrefix("/p ", "/p "));
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
    void selectedChatTypePersistsAcrossServiceInstances() throws Exception {
        var path = temporaryDirectory.resolve("ralle-chat-input.properties");
        var service = new ChatTypeTabService(path);
        service.rememberPrefix("/p ");

        assertEquals(Optional.of("/p "), new ChatTypeTabService(path).prefixForNewChat());
        assertEquals(true, Files.readString(path).contains("last-chat-type=party"));
    }

    @Test
    void invalidOrRecipientlessPersistedChatTypeIsIgnored() throws Exception {
        var path = temporaryDirectory.resolve("ralle-chat-input.properties");
        Files.writeString(path, "last-chat-type=direct_message\n");
        assertEquals(Optional.empty(), new ChatTypeTabService(path).prefixForNewChat());

        Files.writeString(path, "last-chat-type=unknown\n");
        assertEquals(Optional.empty(), new ChatTypeTabService(path).prefixForNewChat());
    }
}

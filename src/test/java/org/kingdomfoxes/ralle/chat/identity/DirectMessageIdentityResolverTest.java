package org.kingdomfoxes.ralle.chat.identity;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectMessageIdentityResolverTest {
    private static final String PREFIX = "\uDAFF\uDFFC\uE007\uDAFF\uDFFF\uE002\uDAFF\uDFFE ";

    private Component message(Component sender, String recipient) {
        return Component.literal(PREFIX).withColor(0xDDCC99).append(sender)
                .append(" \uE003 ").append(recipient).append(": hello");
    }

    @Test
    void incomingOnlyAndVerifiedEnvelope() {
        var incoming = message(Component.literal("FriendFox"), "LocalFox");
        assertEquals(Optional.of("FriendFox"), DirectMessageIdentityResolver.incomingSender(incoming, "localfox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(incoming, "FriendFox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(
                Component.literal(incoming.getString()), "LocalFox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(
                Component.literal("FriendFox -> LocalFox: hi").withColor(0xDDCC99), "LocalFox"));
    }

    @Test
    void resolvesNicknameMetadataAndRecipientBanner() {
        var nick = Component.literal("Fancy Fox").withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal("Fancy Fox's real name is RealFox"))));
        assertEquals(Optional.of("RealFox"), DirectMessageIdentityResolver.incomingSender(
                message(nick, "\uE060\uDAFF\uDFFF\uE062\uDB00\uDC02 LocalFox"), "LocalFox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(
                message(Component.literal("Fancy Fox"), "LocalFox"), "LocalFox"));
    }

    @Test
    void compactIndicatorMultilineBodyAndNicknamedLocalRecipient() {
        var incoming = Component.literal("\uDAFF\uDFFC\uE001\uDB00\uDC06 ").withColor(0xDDCC99)
                .append("FriendFox \uE003 ")
                .append(Component.literal("Local Nick").withStyle(style -> style.withHoverEvent(
                        new HoverEvent.ShowText(Component.literal("Local Nick's real name is LocalFox")))))
                .append(": first line\nsecond line");
        assertEquals(Optional.of("FriendFox"), DirectMessageIdentityResolver.incomingSender(incoming, "LocalFox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(
                message(Component.literal("LocalFox"), "LocalFox"), "LocalFox"));
        assertEquals(Optional.empty(), DirectMessageIdentityResolver.incomingSender(
                message(Component.literal("FriendFox"), "OtherFox"), "LocalFox"));
    }
}

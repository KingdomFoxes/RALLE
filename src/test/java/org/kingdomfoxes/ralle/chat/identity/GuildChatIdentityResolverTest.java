package org.kingdomfoxes.ralle.chat.identity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildChatIdentityResolverTest {
    @Test
    void resolvesDirectIgnFromVerifiedGuildEnvelope() {
        var resolved = GuildChatIdentityResolver.resolve(message("MaxKarson", null, "Detlas defense is High"));

        assertEquals("MaxKarson", resolved.orElseThrow().displayName());
        assertEquals(Optional.of("MaxKarson"), resolved.orElseThrow().resolvedIgn());
        assertEquals("Detlas defense is High", resolved.orElseThrow().body());
    }

    @Test
    void prefersRealNameHoverForNicknameWithSpacesOrIgnShape() {
        assertEquals(Optional.of("_Leoh_"), GuildChatIdentityResolver.resolve(
                message("A Fancy Class", "A Fancy Class's real name is _Leoh_", "Nemract defense is Very High"))
                .orElseThrow().resolvedIgn());
        assertEquals(Optional.of("Real_Player"), GuildChatIdentityResolver.resolve(
                message("LooksLikeIGN", "LooksLikeIGN’s real name is Real_Player", "Ragni defense is Low"))
                .orElseThrow().resolvedIgn());
    }

    @Test
    void wrappedDefenseAnnouncementKeepsNicknameIdentityThroughQueueParsing() {
        var component = message("Speeddealer", "Speeddealer's real name is ToolyTom",
                "Thesead Suburbs defense is Very\n\uDAFF\uDFFC\uE001\uDB00\uDC06 Low");
        var resolved = GuildChatIdentityResolver.resolve(component).orElseThrow();
        var announcement = new org.kingdomfoxes.ralle.war.queue.QueueAnnouncementParser()
                .parse(resolved, java.util.List.of("Thesead Suburbs")).orElseThrow();

        assertEquals("ToolyTom", announcement.senderIgn());
        assertEquals("Thesead Suburbs", announcement.territoryName());
    }

    @Test
    void acceptsBothPossessiveFormsForNamesEndingInSWithoutRelaxingIdentityChecks() {
        for (String alias : java.util.List.of("Hephaestus", "FOXES")) {
            for (String possessive : java.util.List.of("'", "’", "'s", "’s")) {
                assertEquals(Optional.of("Robturne"), GuildChatIdentityResolver.resolve(
                        message(alias, alias + possessive + " real name is Robturne", "hello"))
                        .orElseThrow().resolvedIgn());
            }
        }
        for (String hover : java.util.List.of("SomeoneElse' real name is Robturne",
                "Hephaestus' real name is Robturne extra", "Hephaestus real name is Robturne")) {
            assertTrue(GuildChatIdentityResolver.resolve(message("Hephaestus", hover, "hello"))
                    .orElseThrow().resolvedIgn().isEmpty());
        }
        assertTrue(GuildChatIdentityResolver.resolve(message("OtherName", "OtherName' real name is Robturne", "hello"))
                .orElseThrow().resolvedIgn().isEmpty());
    }

    @Test
    void rejectsContradictoryOrMalformedSpeakerHoverWithoutFallingBackToAlias() {
        assertTrue(GuildChatIdentityResolver.resolve(
                message("LooksLikeIGN", "SomeoneElse's real name is Real_Player", "Ragni defense is Low"))
                .orElseThrow().resolvedIgn().isEmpty());
        assertTrue(GuildChatIdentityResolver.resolve(
                message("LooksLikeIGN", "not identity metadata", "Ragni defense is Low"))
                .orElseThrow().resolvedIgn().isEmpty());
    }

    @Test
    void ignoresHoverMetadataAttachedOnlyToTheMessageBody() {
        var body = Component.literal("Ragni defense is Low").withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal("LooksLikeIGN's real name is WrongPlayer"))));
        var resolved = GuildChatIdentityResolver.resolve(message("LooksLikeIGN", null, body));

        assertEquals(Optional.of("LooksLikeIGN"), resolved.orElseThrow().resolvedIgn());
    }

    @Test
    void rejectsMessagesWithoutTheGuildIndicatorAndRankEnvelope() {
        assertTrue(GuildChatIdentityResolver.resolve(Component.literal("MaxKarson: Detlas defense is High")).isEmpty());
        assertTrue(GuildChatIdentityResolver.resolve(Component.literal(
                GuildChatIdentityResolver.background("CAPTAIN")
                        + GuildChatIdentityResolver.foreground("CAPTAIN")
                        + " MaxKarson: Detlas defense is High")).isEmpty());
    }

    @Test
    void boundsStyledComponentTraversal() {
        var message = Component.empty();
        for (int index = 0; index < 300; index++) message.append(Component.literal("x"));

        assertTrue(GuildChatIdentityResolver.resolve(message).isEmpty());
    }

    private static Component message(String speaker, String hover, String body) {
        return message(speaker, hover, Component.literal(body).withStyle(ChatFormatting.AQUA));
    }

    private static Component message(String speaker, String hover, Component body) {
        var speakerComponent = Component.literal(" " + speaker + ": ").withStyle(ChatFormatting.AQUA);
        if (hover != null) {
            speakerComponent = speakerComponent.withStyle(style -> style.withHoverEvent(
                    new HoverEvent.ShowText(Component.literal(hover))));
        }
        return Component.empty()
                .append(Component.literal(indicator()).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(GuildChatIdentityResolver.background("CAPTAIN"))
                .append(GuildChatIdentityResolver.foreground("CAPTAIN"))
                .append(speakerComponent)
                .append(body);
    }

    private static String indicator() {
        return new StringBuilder()
                .appendCodePoint(0xCFFFC).appendCodePoint(0xE006)
                .appendCodePoint(0xCFFFF).appendCodePoint(0xE002)
                .appendCodePoint(0xCFFFE).toString();
    }
}

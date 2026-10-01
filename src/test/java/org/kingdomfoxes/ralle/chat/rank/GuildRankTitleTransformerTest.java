package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildRankTitleTransformerTest {
    @Test
    void resolvesApostropheOnlyPossessivesForNamesEndingInS() {
        var snapshot = new GuildRankSnapshot(1, Map.of("MailOrderGF", "Madam", "Robturne", "Prince"));
        var accounts = Map.of("MailOrderShurikens", "MailOrderGF", "Hephaestus", "Robturne",
                "aurascrollslavehelps", "MailOrderGF", "aurascrollslavehelp", "MailOrderGF");
        accounts.forEach((alias, ign) -> {
            for (String apostrophe : List.of("'", "’")) {
                String possessive = apostrophe + (alias.endsWith("s") ? "" : "s");
                var hover = new HoverEvent.ShowText(Component.literal(alias + possessive + " real name is " + ign));
                var original = Component.empty()
                        .append(GuildRankTitleTransformer.background("STRATEGIST"))
                        .append(GuildRankTitleTransformer.foreground("STRATEGIST"))
                        .append(Component.literal(" " + alias + ": hello")
                                .withStyle(style -> style.withHoverEvent(hover)));
                for (var style : List.of(GuildRankStyle.TITLES, GuildRankStyle.STARS_AND_TITLES)) {
                    var transformed = GuildRankTitleTransformer.apply(original, snapshot, style, true);
                    String title = snapshot.titleFor(ign).orElseThrow();
                    String expected = style == GuildRankStyle.TITLES
                            ? GuildRankTitleTransformer.foreground(title)
                            : GuildRankTitleTransformer.encodeStarsAndTitle(3, title, true).foreground();
                    assertTrue(transformed.getString().contains(expected), alias + possessive + " / " + style);
                    assertTrue(renderedSegments(transformed).stream().anyMatch(segment ->
                            segment.text().contains(alias) && hover.equals(segment.style().getHoverEvent())));
                }
            }
        });
    }

    @Test
    void resolvesCanonicalRankAcrossClassNamesAndWrappedInheritedHover() {
        var snapshot = new GuildRankSnapshot(1, Map.of("MailOrderGF", "Madam", "OtherMember", "Lord"));
        for (String alias : List.of("MailOrderGF", "MailOrderShurikens", "OtherMember")) {
            var original = guildMessage("STRATEGIST", alias).copy();
            if (!alias.equals("MailOrderGF")) {
                original.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("\n" + alias + "'s real name is\nMailOrderGF\n"))));
            }
            for (var style : List.of(GuildRankStyle.TITLES, GuildRankStyle.STARS_AND_TITLES)) {
                var transformed = GuildRankTitleTransformer.apply(original, snapshot, style, true);
                String expected = style == GuildRankStyle.TITLES
                        ? GuildRankTitleTransformer.foreground("MADAM")
                        : GuildRankTitleTransformer.encodeStarsAndTitle(3, "MADAM", true).foreground();
                assertTrue(transformed.getString().contains(expected), alias + " / " + style);
            }
        }
    }

    @Test
    void nicknameCannotBorrowCachedRankWhenHoverIdentifiesAnotherOrInvalidPlayer() {
        var snapshot = new GuildRankSnapshot(1, Map.of("OtherMember", "Lord"));
        for (String hover : List.of("OtherMember's real name is UnlistedPlayer",
                "WrongAlias's real name is OtherMember")) {
            var original = guildMessage("STRATEGIST", "OtherMember").copy()
                    .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
            assertTrue(GuildRankTitleTransformer.apply(original, snapshot).getString()
                    .contains(GuildRankTitleTransformer.foreground("STRATEGIST")));
        }
    }

    @Test
    void primeMinisterUsesCompactPillButRetainsFullHoverTitle() {
        var snapshot = new GuildRankSnapshot(1, Map.of("maxkarson", "Prime Minister"));
        assertEquals("PRIME MINISTER", snapshot.titleFor("maxkarson").orElseThrow());
        assertEquals(GuildRankTitleTransformer.encode("PM"), snapshot.glyphsFor("PRIME MINISTER"));
        for (int stars = 0; stars <= 5; stars++) {
            assertEquals(GuildRankTitleTransformer.encodeStarsAndTitle(stars, "PM", true),
                    GuildRankTitleTransformer.encodeStarsAndTitle(stars, "PRIME MINISTER", true));
        }
        assertEquals("CHIEF - Prime Minister",
                GuildRankTitleTransformer.rankHoverText("CHIEF", Optional.of("PRIME MINISTER")).getString());
    }

    @Test
    void replacesTheTitleAndRestoresAquaToThePillAndGuildIndicator() {
        var indicator = Component.literal(strategistIndicator()).withStyle(ChatFormatting.WHITE);
        var background = Component.literal(GuildRankTitleTransformer.background("STRATEGIST"));
        var foreground = Component.literal(GuildRankTitleTransformer.foreground("STRATEGIST"))
                .withStyle(ChatFormatting.BLACK);
        var speakerAndMessage = Component.literal(" maxkarson: hello").withStyle(ChatFormatting.AQUA);
        var original = Component.empty()
                .append(indicator)
                .append(" ")
                .append(background)
                .append(foreground)
                .append(speakerAndMessage);

        var transformed = GuildRankTitleTransformer.apply(
                original,
                new GuildRankSnapshot(1L, Map.of("MaxKarson", "Sir"))
        );

        assertEquals(
                strategistIndicator() + " " + GuildRankTitleTransformer.background("SIR")
                        + GuildRankTitleTransformer.foreground("SIR") + " maxkarson: hello",
                transformed.getString()
        );
        assertEquals(ChatFormatting.AQUA.getColor(),
                transformed.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.AQUA.getColor(),
                transformed.getSiblings().get(2).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.BLACK.getColor(),
                transformed.getSiblings().get(3).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.AQUA.getColor(),
                renderedSegments(transformed).stream()
                        .filter(segment -> segment.text().equals(" hello"))
                        .findFirst().orElseThrow().style().getColor().getValue());
    }

    @Test
    void resolvesNicknamedCharactersFromExistingHoverMetadataWithOneCacheLookup() {
        var hover = new HoverEvent.ShowText(Component.literal("Vlou Gremlin's real name is _Leoh_"));
        var nickname = Component.literal(" Vlou Gremlin: hello")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withHoverEvent(hover));
        var original = Component.empty()
                .append(Component.literal(strategistIndicator()).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal(GuildRankTitleTransformer.background("STRATEGIST")))
                .append(Component.literal(GuildRankTitleTransformer.foreground("STRATEGIST"))
                        .withStyle(ChatFormatting.BLACK))
                .append(nickname);

        var transformed = GuildRankTitleTransformer.apply(
                original,
                new GuildRankSnapshot(1L, Map.of("_Leoh_", "Sir"))
        );

        assertEquals(
                strategistIndicator() + " " + GuildRankTitleTransformer.background("SIR")
                        + GuildRankTitleTransformer.foreground("SIR") + " Vlou Gremlin: hello",
                transformed.getString()
        );
        assertEquals(ChatFormatting.AQUA.getColor(),
                transformed.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.AQUA.getColor(),
                transformed.getSiblings().get(2).getStyle().getColor().getValue());
        var nicknameSegment = renderedSegments(transformed).stream()
                .filter(segment -> segment.text().equals(" Vlou Gremlin:"))
                .findFirst().orElseThrow();
        assertSame(hover, nicknameSegment.style().getHoverEvent());
        assertEquals(ChatFormatting.AQUA.getColor(),
                renderedSegments(transformed).stream()
                        .filter(segment -> segment.text().equals(" hello"))
                        .findFirst().orElseThrow().style().getColor().getValue());
    }

    @Test
    void combinedWhiteLiteralStillFlattensTheReplacementBackgroundAsAqua() {
        String originalText = strategistIndicator() + " "
                + GuildRankTitleTransformer.background("STRATEGIST")
                + GuildRankTitleTransformer.foreground("STRATEGIST")
                + " maxkarson: hello";
        var original = Component.literal(originalText).withStyle(ChatFormatting.WHITE);

        var transformed = GuildRankTitleTransformer.apply(
                original,
                new GuildRankSnapshot(1L, Map.of("maxkarson", "Sir"))
        );
        var renderedSegments = new ArrayList<RenderedSegment>();
        transformed.visit((style, text) -> {
            renderedSegments.add(new RenderedSegment(text, style));
            return Optional.empty();
        }, Style.EMPTY);

        var background = renderedSegments.stream()
                .filter(segment -> segment.text().equals(GuildRankTitleTransformer.background("SIR")))
                .findFirst().orElseThrow();
        assertEquals(ChatFormatting.AQUA.getColor(), background.style().getColor().getValue());
        var messageBody = renderedSegments.stream()
                .filter(segment -> segment.text().equals(" hello"))
                .findFirst().orElseThrow();
        assertEquals(ChatFormatting.AQUA.getColor(), messageBody.style().getColor().getValue());
    }

    @Test
    void restoresOnlyAnUncoloredGuildMessageBodyToAqua() {
        var speaker = Component.literal(" maxkarson:").withStyle(ChatFormatting.AQUA);
        var messageBody = Component.literal(" hello guild").withStyle(ChatFormatting.WHITE);
        var original = Component.empty()
                .append(Component.literal(strategistIndicator()).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal(GuildRankTitleTransformer.background("STRATEGIST")))
                .append(Component.literal(GuildRankTitleTransformer.foreground("STRATEGIST"))
                        .withStyle(ChatFormatting.BLACK))
                .append(speaker)
                .append(messageBody);

        var transformed = GuildRankTitleTransformer.apply(
                original,
                new GuildRankSnapshot(1L, Map.of("maxkarson", "Sir"))
        );
        var segments = renderedSegments(transformed);

        assertSame(speaker, transformed.getSiblings().get(4));
        assertEquals(ChatFormatting.AQUA.getColor(), segments.stream()
                .filter(segment -> segment.text().equals(" hello guild"))
                .findFirst().orElseThrow().style().getColor().getValue());
    }

    @Test
    void preservesAnExplicitMessageBodyColor() {
        var original = Component.empty()
                .append(Component.literal(GuildRankTitleTransformer.background("CAPTAIN")))
                .append(Component.literal(GuildRankTitleTransformer.foreground("CAPTAIN")))
                .append(Component.literal(" maxkarson:").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" warning").withStyle(ChatFormatting.GOLD));

        var transformed = GuildRankTitleTransformer.apply(
                original,
                new GuildRankSnapshot(1L, Map.of("maxkarson", "Sir"))
        );

        assertEquals(ChatFormatting.GOLD.getColor(), renderedSegments(transformed).stream()
                .filter(segment -> segment.text().equals(" warning"))
                .findFirst().orElseThrow().style().getColor().getValue());
    }

    @Test
    void precomputesOneGlyphPairPerUniqueApiTitle() {
        var snapshot = new GuildRankSnapshot(1L, Map.of(
                "maxkarson", "Sir",
                "_Leoh_", "Sir"
        ));

        assertSame(snapshot.glyphsFor("SIR"), snapshot.glyphsFor("SIR"));
        assertEquals(GuildRankTitleTransformer.background("SIR"),
                snapshot.glyphsFor("SIR").background());
    }

    @Test
    void mapsEveryWynncraftGuildRankToItsClassicStarCount() {
        var ranks = Map.of(
                "RECRUITER", 1,
                "CAPTAIN", 2,
                "STRATEGIST", 3,
                "CHIEF", 4,
                "OWNER", 5
        );

        ranks.forEach((rank, count) -> {
            var transformed = GuildRankTitleTransformer.apply(
                    guildMessage(rank, "maxkarson"),
                    GuildRankSnapshot.EMPTY,
                    GuildRankStyle.STARS,
                    false
            );
            String trailingSpacer = count % 2 == 0 ? "" : "\uE101";
            assertEquals("\uE100".repeat(count) + trailingSpacer,
                    foregroundContentBeforeTerminator(transformed));
        });
    }

    @Test
    void compactStarsStayTightAndUseOnlyOneTrailingAlignmentSpacer() {
        var encoded = GuildRankTitleTransformer.encodeStars(3);
        int[] background = encoded.background().codePoints().toArray();

        assertEquals("\uE100\uE100\uE100\uE101" + Character.toString(0xD0002), encoded.foreground());
        assertEquals(5, encoded.background().codePoints().filter(codepoint -> codepoint == 0xE061).count());
        assertEquals(0xE062, background[background.length - 2]);
    }

    @Test
    void packagesTheNamespacedCompactStarFont() {
        assertNotNull(getClass().getResource("/assets/ralle/font/guild_rank_star.json"));
        assertNotNull(getClass().getResource("/assets/ralle/textures/font/guild_rank_star.png"));
    }

    @Test
    void recruitStarStyleRemovesTheWholeRankPillWithoutLeavingDoubleSpacing() {
        var transformed = GuildRankTitleTransformer.apply(
                guildMessage("RECRUIT", "maxkarson"),
                GuildRankSnapshot.EMPTY,
                GuildRankStyle.STARS,
                false
        );

        assertEquals(strategistIndicator() + " maxkarson: hello", transformed.getString());
        assertEquals(ChatFormatting.AQUA.getColor(), transformed.getSiblings().get(0).getStyle().getColor().getValue());
    }

    @Test
    void combinesStarsAndThePublicTitleInsideOneBackgroundAndOneForegroundPass() {
        var transformed = GuildRankTitleTransformer.apply(
                guildMessage("STRATEGIST", "maxkarson"),
                GuildRankSnapshot.EMPTY,
                GuildRankStyle.STARS_AND_TITLES,
                false
        );
        var combined = GuildRankTitleTransformer.encodeStarsAndTitle(3, "STRATEGIST", true);

        assertEquals(strategistIndicator() + " " + combined.background() + combined.foreground()
                + " maxkarson: hello", transformed.getString());
        assertEquals("\uE100\uE100\uE100\uE101 "
                        + GuildRankTitleTransformer.foreground("STRATEGIST")
                        .substring(0, GuildRankTitleTransformer.foreground("STRATEGIST").length() - 2),
                combined.foreground().substring(0, combined.foreground().length() - 2));
    }

    @Test
    void combinedPillUsesRalleFontForStarsAndKeepsTheWynnFontForItsTitle() {
        FontDescription pillFont = new FontDescription.Resource(
                Identifier.fromNamespaceAndPath("minecraft", "banner/pill"));
        FontDescription starFont = new FontDescription.Resource(
                Identifier.fromNamespaceAndPath("ralle", "guild_rank_star"));
        var original = Component.empty()
                .append(Component.literal(strategistIndicator()).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal(GuildRankTitleTransformer.background("STRATEGIST"))
                        .withStyle(style -> style.withFont(pillFont)))
                .append(Component.literal(GuildRankTitleTransformer.foreground("STRATEGIST"))
                        .withStyle(style -> style.withColor(ChatFormatting.BLACK).withFont(pillFont)))
                .append(Component.literal(" maxkarson: hello").withStyle(ChatFormatting.AQUA));

        var transformed = GuildRankTitleTransformer.apply(
                original, GuildRankSnapshot.EMPTY, GuildRankStyle.STARS_AND_TITLES, false);
        var segments = renderedSegments(transformed);

        assertEquals(starFont, segments.stream()
                .filter(segment -> segment.text().equals("\uE100\uE100\uE100\uE101"))
                .findFirst().orElseThrow().style().getFont());
        assertEquals(pillFont, segments.stream()
                .filter(segment -> segment.text().startsWith(" ")
                        && segment.text().contains(Character.toString(0xE012)))
                .findFirst().orElseThrow().style().getFont());
    }

    @Test
    void combinesWynnStarsWithTheResolvedInternalFoxTitleInTheSamePill() {
        var transformed = GuildRankTitleTransformer.apply(
                guildMessage("CAPTAIN", "maxkarson"),
                new GuildRankSnapshot(1L, Map.of("maxkarson", "Sir")),
                GuildRankStyle.STARS_AND_TITLES,
                true
        );
        var combined = GuildRankTitleTransformer.encodeStarsAndTitle(2, "SIR", true);

        assertEquals(strategistIndicator() + " " + combined.background() + combined.foreground()
                + " maxkarson: hello", transformed.getString());
    }

    @Test
    void prunedApiResponseUsesTheChatPillForWynncraftRankHover() {
        var snapshot = new GuildRankSnapshot(1L, StrictGuildRankJson.decodeApi("""
                {"total_members":3,"members":[
                  {"uuid":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","name":"maxkarson",
                   "fox_rank":"Liege","prime_minister":false},
                  {"uuid":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","name":"ToolyTom",
                   "fox_rank":"Lord","prime_minister":true},
                  {"uuid":"cccccccc-cccc-cccc-cccc-cccccccccccc","name":"NoTitle",
                   "fox_rank":null,"prime_minister":false}
                ]}
                """));
        var suffixes = Map.of("maxkarson", " - Lord/Lady/Liege",
                "ToolyTom", " - Prime Minister", "NoTitle", "");

        for (var style : GuildRankStyle.values()) {
            for (String rank : List.of("STRATEGIST", "CAPTAIN")) {
                suffixes.forEach((player, suffix) -> {
                    var transformed = GuildRankTitleTransformer.apply(
                            guildMessage(rank, player), snapshot, style, true);
                    var pillHovers = renderedSegments(transformed).stream()
                            .map(segment -> segment.style().getHoverEvent())
                            .filter(HoverEvent.ShowText.class::isInstance)
                            .map(HoverEvent.ShowText.class::cast)
                            .map(hover -> hover.value().getString())
                            .toList();

                    assertTrue(pillHovers.size() >= 2, style + " / " + rank + " / " + player);
                    assertTrue(pillHovers.stream().allMatch((rank + suffix)::equals),
                            style + " / " + rank + " / " + player);
                });
            }
        }
    }

    @Test
    void addsExpandedGuildAndFoxRanksToTheWholePillHover() {
        var transformed = GuildRankTitleTransformer.apply(
                guildMessage("STRATEGIST", "maxkarson"),
                new GuildRankSnapshot(1L, Map.of("maxkarson", "Liege")),
                GuildRankStyle.STARS_AND_TITLES,
                true
        );

        var pillSegments = renderedSegments(transformed).stream()
                .filter(segment -> segment.style().getHoverEvent() != null)
                .toList();
        assertEquals(3, pillSegments.size());
        assertTrue(pillSegments.stream().allMatch(segment ->
                segment.style().getHoverEvent() instanceof HoverEvent.ShowText showText
                        && showText.value().getString().equals("STRATEGIST - Lord/Lady/Liege")));
    }

    @Test
    void enabledInternalRankPathAddsGuildOnlyHoverWhenNoFoxRankResolves() {
        var original = guildMessage("CAPTAIN", "maxkarson");

        var transformed = GuildRankTitleTransformer.apply(
                original, GuildRankSnapshot.EMPTY, GuildRankStyle.TITLES, true);

        assertEquals(original.getString(), transformed.getString());
        var pillSegments = renderedSegments(transformed).stream()
                .filter(segment -> segment.text().equals(GuildRankTitleTransformer.background("CAPTAIN"))
                        || segment.text().equals(GuildRankTitleTransformer.foreground("CAPTAIN")))
                .toList();
        assertEquals(2, pillSegments.size());
        assertTrue(pillSegments.stream().allMatch(segment ->
                segment.style().getHoverEvent() instanceof HoverEvent.ShowText showText
                        && showText.value().getString().equals("CAPTAIN")));
    }

    @Test
    void expandsEveryGroupedFoxRankVariantToItsCanonicalLabel() {
        assertEquals("CHIEF - Sir/Madam/Knight",
                GuildRankTitleTransformer.rankHoverText("chief", Optional.of("MADAM")).getString());
        assertEquals("STRATEGIST - Lord/Lady/Liege",
                GuildRankTitleTransformer.rankHoverText("strategist", Optional.of("LIEGE")).getString());
        assertEquals("CHIEF - Baron/Baroness/Baronx",
                GuildRankTitleTransformer.rankHoverText("chief", Optional.of("BARONESS")).getString());
        assertEquals("CHIEF - Viscount/Viscountess/Viscountx",
                GuildRankTitleTransformer.rankHoverText("chief", Optional.of("VISCOUNTX")).getString());
        assertEquals("CHIEF - Count/Countess/Countx",
                GuildRankTitleTransformer.rankHoverText("chief", Optional.of("COUNT")).getString());
        assertEquals("CHIEF - Marquis/Marchioness/Marqix",
                GuildRankTitleTransformer.rankHoverText("chief", Optional.of("MARCHIONESS")).getString());
        assertEquals("RECRUIT - Page",
                GuildRankTitleTransformer.rankHoverText("recruit", Optional.of("PAGE")).getString());
    }

    @Test
    void doesNotUseAnOrdinaryMentionAsTheSpeakersFoxRank() {
        var guildMessage = Component.literal(
                GuildRankTitleTransformer.background("CAPTAIN")
                        + GuildRankTitleTransformer.foreground("CAPTAIN")
                        + " OtherPlayer: maxkarson: hello"
        );
        var snapshot = new GuildRankSnapshot(1L, Map.of("maxkarson", "Knight"));

        var transformed = GuildRankTitleTransformer.apply(guildMessage, snapshot);

        assertEquals(guildMessage.getString(), transformed.getString());
        var pillHovers = renderedSegments(transformed).stream()
                .map(RenderedSegment::style)
                .map(Style::getHoverEvent)
                .filter(HoverEvent.ShowText.class::isInstance)
                .map(HoverEvent.ShowText.class::cast)
                .map(hover -> hover.value().getString())
                .toList();
        assertEquals(List.of("CAPTAIN", "CAPTAIN"), pillHovers);
    }

    @Test
    void ignoresUnsupportedApiTitlesInsteadOfProducingBrokenGlyphs() {
        var snapshot = new GuildRankSnapshot(1L, Map.of("maxkarson", "Lord/Lady"));
        assertEquals(Map.of(), snapshot.titlesByPlayer());
    }

    private static String strategistIndicator() {
        return new StringBuilder()
                .appendCodePoint(0xCFFFC)
                .appendCodePoint(0xE006)
                .appendCodePoint(0xCFFFF)
                .appendCodePoint(0xE002)
                .appendCodePoint(0xCFFFE)
                .toString();
    }

    private static Component guildMessage(String rank, String speaker) {
        return Component.empty()
                .append(Component.literal(strategistIndicator()).withStyle(ChatFormatting.WHITE))
                .append(" ")
                .append(Component.literal(GuildRankTitleTransformer.background(rank)))
                .append(Component.literal(GuildRankTitleTransformer.foreground(rank)).withStyle(ChatFormatting.BLACK))
                .append(Component.literal(" " + speaker + ": hello").withStyle(ChatFormatting.AQUA));
    }

    private static String foregroundContentBeforeTerminator(Component component) {
        String text = component.getString();
        String terminator = Character.toString(0xD0002);
        int end = text.indexOf(terminator);
        int star = text.indexOf("\uE100");
        return text.substring(star, end);
    }

    private static ArrayList<RenderedSegment> renderedSegments(Component component) {
        var renderedSegments = new ArrayList<RenderedSegment>();
        component.visit((style, text) -> {
            renderedSegments.add(new RenderedSegment(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return renderedSegments;
    }

    private record RenderedSegment(String text, Style style) {}
}

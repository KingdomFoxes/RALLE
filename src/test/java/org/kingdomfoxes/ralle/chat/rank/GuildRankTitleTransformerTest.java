package org.kingdomfoxes.ralle.chat.rank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GuildRankTitleTransformerTest {
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
                new GuildRankSnapshot(1L, Map.of("MaxKarson", "King Cricket"))
        );

        assertEquals(
                strategistIndicator() + " " + GuildRankTitleTransformer.background("KING CRICKET")
                        + GuildRankTitleTransformer.foreground("KING CRICKET") + " maxkarson: hello",
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
                "maxkarson", "King Cricket",
                "_Leoh_", "King Cricket"
        ));

        assertSame(snapshot.glyphsFor("KING CRICKET"), snapshot.glyphsFor("KING CRICKET"));
        assertEquals(GuildRankTitleTransformer.background("KING CRICKET"),
                snapshot.glyphsFor("KING CRICKET").background());
    }

    @Test
    void ignoresUnknownSpeakersAndOrdinaryMentions() {
        var guildMessage = Component.literal(
                GuildRankTitleTransformer.background("CAPTAIN")
                        + GuildRankTitleTransformer.foreground("CAPTAIN")
                        + " OtherPlayer: maxkarson: hello"
        );
        var snapshot = new GuildRankSnapshot(1L, Map.of("maxkarson", "Knight"));

        assertSame(guildMessage, GuildRankTitleTransformer.apply(guildMessage, snapshot));
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

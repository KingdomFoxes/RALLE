package org.kingdomfoxes.ralle.war.queue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueAttributionFormatterTest {
    @Test
    void customColorAffectsOnlySelfAndPreviewUsesDemoQueues() {
        var original = Component.literal("Detlas").withStyle(ChatFormatting.GOLD);
        var self = segments(QueueAttributionFormatter.format(original, Optional.of("Player"), "player",
                Component.literal("Unknown"), 0x123456));
        assertEquals(0x123456, self.get(0).style().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(), self.get(1).style().getColor().getValue());
        assertEquals(ChatFormatting.GOLD.getColor(), self.get(2).style().getColor().getValue());
        var row = QueueAttributionPreview.preview("Player",
                new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x123456, false), 0L);
        assertEquals("Player → Detlas (High) 2:32", row.getString());
        assertEquals(0x123456, segments(row).get(0).style().getColor().getValue());
    }

    @Test
    void previewUsesTheSuppliedAccountIncludingMaximumLengthNames() {
        var style = new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x123456, false);
        for (String account : new String[]{"DifferentPlayer", "WWWWWWWWWWWWWWWW"}) {
            assertEquals(account + " → Detlas (High) 2:32",
                    QueueAttributionPreview.preview(account, style, 0L).getString());
        }
    }

    @Test
    void rainbowAnimatesOnlyTheLocalNameAndMatchesPreview() {
        var style = new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x123456, true);
        var original = Component.literal("Detlas").withStyle(ChatFormatting.GOLD);
        var first = segments(QueueAttributionFormatter.format(original, Optional.of("Player"), "player",
                Component.literal("Unknown"), style, 0L));
        var later = segments(QueueAttributionFormatter.format(original, Optional.of("Player"), "player",
                Component.literal("Unknown"), style, 250L));
        org.junit.jupiter.api.Assertions.assertNotEquals(first.get(0).style().getColor(), first.get(1).style().getColor());
        org.junit.jupiter.api.Assertions.assertNotEquals(first.get(0).style().getColor(), later.get(0).style().getColor());
        assertEquals(ChatFormatting.GRAY.getColor(), first.get(6).style().getColor().getValue());
        assertEquals(ChatFormatting.GOLD.getColor(), first.get(7).style().getColor().getValue());
        var remote = segments(QueueAttributionFormatter.format(original, Optional.of("Remote"), "player",
                Component.literal("Unknown"), style, 0L));
        assertEquals(ChatFormatting.GRAY.getColor(), remote.get(0).style().getColor().getValue());
        var unknown = segments(QueueAttributionFormatter.format(original, Optional.empty(), "player",
                Component.literal("Unknown"), style, 0L));
        assertEquals(ChatFormatting.GRAY.getColor(), unknown.get(0).style().getColor().getValue());
        var preview = segments(QueueAttributionPreview.preview("Player", style, 250L));
        for (int i = 0; i < 6; i++) assertEquals(later.get(i), preview.get(i));
    }

    @Test
    void usesBlueForSelfGrayForOthersAndPreservesOriginalStyles() {
        var original = Component.empty()
                .append(Component.literal("Detlas").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" 02:31").withStyle(ChatFormatting.RED));

        var self = QueueAttributionFormatter.format(
                original, Optional.of("MaxKarson"), "maxkarson", Component.literal("Unknown"));
        var other = QueueAttributionFormatter.format(
                original, Optional.of("_Leoh_"), "MaxKarson", Component.literal("Unknown"));
        var unknown = QueueAttributionFormatter.format(
                original, Optional.empty(), "MaxKarson", Component.literal("Unknown"));

        assertEquals("MaxKarson → Detlas 02:31", self.getString());
        assertEquals(ChatFormatting.BLUE.getColor(), segments(self).get(0).style().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(), segments(other).get(0).style().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(), segments(unknown).get(0).style().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(), segments(self).get(1).style().getColor().getValue());
        assertEquals(ChatFormatting.GOLD.getColor(), segments(self).get(2).style().getColor().getValue());
        assertEquals(true, segments(self).get(2).style().isBold());
        assertEquals(ChatFormatting.RED.getColor(), segments(self).get(3).style().getColor().getValue());
    }

    @Test
    void rankColorsChangeOnlyOtherNames() {
        var original = Component.literal("Detlas 2:32").withStyle(ChatFormatting.GOLD);
        var style = new org.kingdomfoxes.ralle.war.consumables.HighlightStyle(0x123456, false);
        var remote = segments(QueueAttributionFormatter.format(original, Optional.of("Remote"), "Player",
                Component.literal("Unknown"), style, 0L, ign -> KofRankColors.forTitle("King")));
        assertEquals(0xFFCC00, remote.get(0).style().getColor().getValue());
        assertEquals(ChatFormatting.GRAY.getColor(), remote.get(1).style().getColor().getValue());
        assertEquals(ChatFormatting.GOLD.getColor(), remote.get(2).style().getColor().getValue());
        for (var ign : java.util.List.of(Optional.of("PLAYER"), Optional.<String>empty())) {
            var row = segments(QueueAttributionFormatter.format(original, ign, "Player",
                    Component.literal("Unknown"), style, 0L, name -> { throw new AssertionError("Unexpected lookup"); }));
            assertEquals(ign.isPresent() ? 0x123456 : 0xAAAAAA, row.get(0).style().getColor().getValue());
        }
    }

    @Test
    void allRankTitleVariantsUseTheRequestedPalette() {
        var groups = java.util.Map.of(
                0x5A84D4, "page,squire,sir,madam,knight,lord,lady,liege",
                0x8E77CC, "prime minister",
                0x0EACB4, "baron,baroness,baronx,viscount,viscountess,viscountx",
                0x4DD6EC, "count,countess,countx,marquis,marchioness,marqix",
                0xFF9A19, "viceroy", 0xFFCC00, "archduke,prince,king");
        groups.forEach((rgb, titles) -> {
            for (String title : titles.split(",")) assertEquals(rgb.intValue(), KofRankColors.forTitle(title));
        });
        assertEquals(0xAAAAAA, KofRankColors.forTitle("unknown"));
        assertEquals(0xAAAAAA, KofRankColors.forTitle(null));
    }

    private static ArrayList<Segment> segments(Component component) {
        var result = new ArrayList<Segment>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) result.add(new Segment(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private record Segment(String text, Style style) {}
}

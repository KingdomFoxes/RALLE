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

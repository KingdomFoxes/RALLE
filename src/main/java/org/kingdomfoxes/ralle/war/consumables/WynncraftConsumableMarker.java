package org.kingdomfoxes.ralle.war.consumables;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** Recognizes the exact Wynncraft tooltip emblems for supported consumable families. */
final class WynncraftConsumableMarker {
    private static final FontDescription FRAME_FONT = new FontDescription.Resource(
            Identifier.withDefaultNamespace("tooltip/emblem/sprite"));
    private static final Set<String> SUPPORTED_FRAMES = Set.of(
            "\uE027", // Potion
            "\uE033", // Food
            "\uE032"  // Scroll
    );

    private WynncraftConsumableMarker() {}

    static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;

        for (var line : lore.lines()) {
            for (var part : line.toFlatList()) {
                if (FRAME_FONT.equals(part.getStyle().getFont())
                        && SUPPORTED_FRAMES.contains(part.getString())) {
                    return true;
                }
            }
        }
        return false;
    }
}

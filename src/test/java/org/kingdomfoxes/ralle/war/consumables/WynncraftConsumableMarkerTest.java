package org.kingdomfoxes.ralle.war.consumables;

import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynncraftConsumableMarkerTest {
    private static final FontDescription FRAME_FONT = new FontDescription.Resource(
            Identifier.withDefaultNamespace("tooltip/emblem/sprite"));

    @BeforeAll static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void recognizesOnlyPotionFoodAndScrollFrames() {
        assertTrue(WynncraftConsumableMarker.matches(marked("\uE027")));
        assertTrue(WynncraftConsumableMarker.matches(marked("\uE033")));
        assertTrue(WynncraftConsumableMarker.matches(marked("\uE032")));
        assertFalse(WynncraftConsumableMarker.matches(marked("\uE026")));
    }

    @Test void vanillaItemAndMatchingNameDoNotProveConsumableType() {
        var unmarkedPotion = new ItemStack(Items.POTION);
        unmarkedPotion.set(DataComponents.CUSTOM_NAME, Component.literal("Earth Damage Potion"));
        assertFalse(WynncraftConsumableMarker.matches(unmarkedPotion));

        var wrongFont = new ItemStack(Items.POTION);
        wrongFont.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("\uE027"))));
        assertFalse(WynncraftConsumableMarker.matches(wrongFont));
    }

    private static ItemStack marked(String frame) {
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(frame).withStyle(style -> style.withFont(FRAME_FONT)))));
        return stack;
    }
}

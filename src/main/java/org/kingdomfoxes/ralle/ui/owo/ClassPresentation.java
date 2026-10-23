package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomModelData;
import org.kingdomfoxes.ralle.classes.ClassCharacter;

import java.util.List;
import java.util.Optional;

/** Uses Minecraft's normal item renderer so the active Wynncraft pack supplies its own class sprite. */
public final class ClassPresentation {
    private ClassPresentation() {}

    public static ItemStack item(ClassCharacter character) {
        if (character == null || character.sprite() == null) return new ItemStack(Items.PAPER);
        var sprite = character.sprite();
        var item = new ItemStack(Items.POTION);
        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(sprite.model()), List.of(), List.of(), List.of()));
        item.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(sprite.tint()), List.of(), Optional.empty()));
        return item;
    }
}

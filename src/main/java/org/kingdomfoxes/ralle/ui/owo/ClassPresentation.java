package org.kingdomfoxes.ralle.ui.owo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.PackResources;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomModelData;
import org.kingdomfoxes.ralle.classes.ClassCharacter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Uses Minecraft's normal item renderer so the active Wynncraft pack supplies its own class sprite. */
public final class ClassPresentation {
    private static final String ICON_PATH = "item/wynn/gui/character_select/icon/";
    // The cached packs use this order at 575–584 and 585–594, respectively.
    private static final List<String> ICONS = List.of("archer", "assassin", "dark_wizard", "hunter", "knight",
            "mage", "ninja", "shaman", "skyseer", "warrior");
    private static PackResources iconPack;
    private static Map<String, Float> iconSelectors = Map.of();

    private ClassPresentation() {}

    public static ItemStack item(ClassCharacter character) {
        if (character == null || character.sprite() == null) return new ItemStack(Items.PAPER);
        return item(character, activeIconSelectors());
    }

    static ItemStack item(ClassCharacter character, Map<String, Float> selectors) {
        if (character == null || character.sprite() == null) return new ItemStack(Items.PAPER);
        var sprite = character.sprite();
        Float model = sprite.model();
        int index = model.intValue() - 575;
        if (model == model.intValue() && index >= 0 && index < 2 * ICONS.size()) {
            model = selectors.get(ICONS.get(index % ICONS.size()));
            if (model == null) return new ItemStack(Items.PAPER);
        }
        var item = new ItemStack(Items.POTION);
        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(model), List.of(), List.of(), List.of()));
        item.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(sprite.tint()), List.of(), Optional.empty()));
        return item;
    }

    private static Map<String, Float> activeIconSelectors() {
        var resources = Minecraft.getInstance().getResourceManager();
        var definition = resources.getResource(WynncraftScrollPreviewItemProvider.POTION_ITEM_DEFINITION).orElse(null);
        if (definition == null) {
            iconPack = null;
            iconSelectors = Map.of();
        } else if (definition.source() != iconPack) {
            iconPack = definition.source();
            try (var reader = definition.openAsReader()) {
                iconSelectors = iconSelectors(JsonParser.parseReader(reader), model ->
                        resources.getResource(WynncraftScrollPreviewItemProvider.modelResource(model)).isPresent());
            } catch (IOException | JsonParseException failure) {
                iconSelectors = Map.of();
            }
        }
        return iconSelectors;
    }

    static Map<String, Float> iconSelectors(JsonElement definition, java.util.function.Predicate<String> hasModel) {
        var selectors = new HashMap<String, Float>();
        for (String icon : ICONS) {
            String path = ICON_PATH + icon;
            if (!hasModel.test(path)) continue;
            var selector = WynncraftScrollPreviewItemProvider.selectorForModel(definition, path);
            if (selector != null && Float.isFinite(selector)) selectors.put(icon, selector);
        }
        return Map.copyOf(selectors);
    }
}

package org.kingdomfoxes.ralle.ui.owo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/** Uses Wynncraft's potion selector 1495 only when a non-vanilla potion model resource is active. */
public final class WynncraftScrollPreviewItemProvider implements PreviewItemProvider {
    private static final Identifier POTION_ITEM_DEFINITION =
            Identifier.fromNamespaceAndPath("minecraft", "items/potion.json");
    private static final Identifier TELEPORT_SCROLL_MODEL =
            Identifier.fromNamespaceAndPath("minecraft", "models/item/wynn/scroll/scroll_teleport.json");
    private static final String TELEPORT_SCROLL_MODEL_ID = "item/wynn/scroll/scroll_teleport";
    private final ItemStack preview = resolvePreviewItem();

    @Override
    public ItemStack previewItem() {
        return preview;
    }

    private static ItemStack resolvePreviewItem() {
        var fallback = new ItemStack(Items.PAPER);
        var resources = Minecraft.getInstance().getResourceManager();
        var itemDefinition = resources.getResource(POTION_ITEM_DEFINITION).orElse(null);
        if (itemDefinition == null || "vanilla".equals(itemDefinition.sourcePackId())
                || "fabric".equals(itemDefinition.sourcePackId())
                || resources.getResource(TELEPORT_SCROLL_MODEL).isEmpty()) {
            return fallback;
        }
        try (var reader = itemDefinition.openAsReader()) {
            if (!usesTeleportScroll(JsonParser.parseReader(reader))) return fallback;
        } catch (Exception ignored) {
            return fallback;
        }
        var stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(1495f), List.of(), List.of(), List.of()
        ));
        return stack;
    }

    static boolean usesTeleportScroll(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                if (usesTeleportScroll(child)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        var object = element.getAsJsonObject();
        var threshold = object.get("threshold");
        if (threshold != null && threshold.isJsonPrimitive() && threshold.getAsJsonPrimitive().isNumber()
                && threshold.getAsDouble() == 1495d && containsTeleportScrollModel(object.get("model"))) {
            return true;
        }
        for (var child : object.entrySet()) {
            if (usesTeleportScroll(child.getValue())) return true;
        }
        return false;
    }

    private static boolean containsTeleportScrollModel(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            var value = element.getAsString();
            return TELEPORT_SCROLL_MODEL_ID.equals(value)
                    || ("minecraft:" + TELEPORT_SCROLL_MODEL_ID).equals(value);
        }
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                if (containsTeleportScrollModel(child)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        for (var child : element.getAsJsonObject().entrySet()) {
            if (containsTeleportScrollModel(child.getValue())) return true;
        }
        return false;
    }
}

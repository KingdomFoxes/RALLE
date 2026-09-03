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

/** Cycles through representative models discovered from the active Wynncraft resource pack. */
public final class WynncraftScrollPreviewItemProvider implements PreviewItemProvider {
    private static final Identifier POTION_ITEM_DEFINITION =
            Identifier.fromNamespaceAndPath("minecraft", "items/potion.json");
    private static final PreviewKind[] PREVIEWS = {
            new PreviewKind("item/wynn/economy/woodcutting/scroll_white",
                    "item/wynn/economy/woodcutting/scroll_", Fallback.PAPER),
            new PreviewKind("item/wynn/potion/healing_full", "item/wynn/potion/", Fallback.POTION),
            new PreviewKind("item/wynn/economy/meals/generic_item", "item/wynn/economy/meals/", Fallback.FOOD)
    };
    private int selected;
    private final ItemStack[] previews = resolvePreviewItems();

    @Override
    public ItemStack previewItem() {
        return previews[selected];
    }

    @Override
    public boolean advance() {
        selected = (selected + 1) % previews.length;
        return true;
    }

    private static ItemStack[] resolvePreviewItems() {
        var resolved = new ItemStack[PREVIEWS.length];
        var resources = Minecraft.getInstance().getResourceManager();
        var itemDefinition = resources.getResource(POTION_ITEM_DEFINITION).orElse(null);
        if (itemDefinition == null) return fallbacks();
        JsonElement definition;
        try (var reader = itemDefinition.openAsReader()) {
            definition = JsonParser.parseReader(reader);
        } catch (Exception ignored) {
            return fallbacks();
        }
        for (int index = 0; index < PREVIEWS.length; index++) {
            var kind = PREVIEWS[index];
            var selection = findSelector(definition, kind.preferredModel(), kind.modelPathPrefix());
            resolved[index] = selection != null && resources.getResource(modelResource(selection.modelId())).isPresent()
                    ? modeledPotion(selection.selector())
                    : fallbackStack(kind.fallback());
        }
        return resolved;
    }

    static boolean usesConsumableScroll(JsonElement element) {
        return usesSelector(element, 1459, "item/wynn/economy/woodcutting/scroll_white");
    }

    static boolean usesSelector(JsonElement element, int selector, String modelId) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                if (usesSelector(child, selector, modelId)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        var object = element.getAsJsonObject();
        var threshold = object.get("threshold");
        if (threshold != null && threshold.isJsonPrimitive() && threshold.getAsJsonPrimitive().isNumber()
                && threshold.getAsDouble() == selector && containsModel(object.get("model"), modelId)) {
            return true;
        }
        for (var child : object.entrySet()) {
            if (usesSelector(child.getValue(), selector, modelId)) return true;
        }
        return false;
    }

    static Float selectorForModel(JsonElement element, String modelId) {
        var selection = findSelector(element, normalizeModelId(modelId), null);
        return selection == null ? null : selection.selector();
    }

    private static ModelSelector findSelector(JsonElement element, String preferredModel, String modelPathPrefix) {
        var preferred = findSelector(element, modelId -> preferredModel.equals(normalizeModelId(modelId)));
        return preferred != null || modelPathPrefix == null
                ? preferred
                : findSelector(element, modelId -> normalizeModelId(modelId).startsWith(modelPathPrefix));
    }

    private static ModelSelector findSelector(JsonElement element, java.util.function.Predicate<String> acceptsModel) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                var selection = findSelector(child, acceptsModel);
                if (selection != null) return selection;
            }
            return null;
        }
        if (!element.isJsonObject()) return null;
        var object = element.getAsJsonObject();
        var threshold = object.get("threshold");
        if (threshold != null && threshold.isJsonPrimitive() && threshold.getAsJsonPrimitive().isNumber()) {
            var modelId = findModelId(object.get("model"), acceptsModel);
            if (modelId != null) return new ModelSelector(threshold.getAsFloat(), normalizeModelId(modelId));
        }
        for (var child : object.entrySet()) {
            var selection = findSelector(child.getValue(), acceptsModel);
            if (selection != null) return selection;
        }
        return null;
    }

    private static String findModelId(JsonElement element, java.util.function.Predicate<String> acceptsModel) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            var value = element.getAsString();
            return acceptsModel.test(value) ? value : null;
        }
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                var modelId = findModelId(child, acceptsModel);
                if (modelId != null) return modelId;
            }
            return null;
        }
        if (!element.isJsonObject()) return null;
        for (var child : element.getAsJsonObject().entrySet()) {
            var modelId = findModelId(child.getValue(), acceptsModel);
            if (modelId != null) return modelId;
        }
        return null;
    }

    private static boolean containsModel(JsonElement element, String modelId) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            var value = element.getAsString();
            return modelId.equals(value) || ("minecraft:" + modelId).equals(value);
        }
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                if (containsModel(child, modelId)) return true;
            }
            return false;
        }
        if (!element.isJsonObject()) return false;
        for (var child : element.getAsJsonObject().entrySet()) {
            if (containsModel(child.getValue(), modelId)) return true;
        }
        return false;
    }

    private static ItemStack modeledPotion(float selector) {
        var stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(selector), List.of(), List.of(), List.of()
        ));
        return stack;
    }

    private static Identifier modelResource(String modelId) {
        var normalized = normalizeModelId(modelId);
        int separator = normalized.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : normalized.substring(0, separator);
        String path = separator < 0 ? normalized : normalized.substring(separator + 1);
        return Identifier.fromNamespaceAndPath(namespace, "models/" + path + ".json");
    }

    private static String normalizeModelId(String modelId) {
        return modelId.startsWith("minecraft:") ? modelId.substring("minecraft:".length()) : modelId;
    }

    private static ItemStack[] fallbacks() {
        var fallbacks = new ItemStack[PREVIEWS.length];
        for (int index = 0; index < PREVIEWS.length; index++) {
            fallbacks[index] = fallbackStack(PREVIEWS[index].fallback());
        }
        return fallbacks;
    }

    private static ItemStack fallbackStack(Fallback fallback) {
        return new ItemStack(switch (fallback) {
            case PAPER -> Items.PAPER;
            case POTION -> Items.POTION;
            case FOOD -> Items.COOKED_BEEF;
        });
    }

    private record PreviewKind(String preferredModel, String modelPathPrefix, Fallback fallback) {}
    private record ModelSelector(float selector, String modelId) {}
    private enum Fallback { PAPER, POTION, FOOD }
}

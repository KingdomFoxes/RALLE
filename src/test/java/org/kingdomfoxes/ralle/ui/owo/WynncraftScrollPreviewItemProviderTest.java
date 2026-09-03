package org.kingdomfoxes.ralle.ui.owo;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynncraftScrollPreviewItemProviderTest {
    @Test void requiresTheExactWhiteConsumableScrollSelectorAt1459() {
        assertTrue(WynncraftScrollPreviewItemProvider.usesConsumableScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1459,"model":{"model":"item/wynn/economy/woodcutting/scroll_white"}}]}}
                """)));
        assertFalse(WynncraftScrollPreviewItemProvider.usesConsumableScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1458,"model":{"model":"item/wynn/economy/woodcutting/scroll_white"}}]}}
                """)));
        assertFalse(WynncraftScrollPreviewItemProvider.usesConsumableScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1459,"model":{"model":"item/potion"}}]}}
                """)));
    }


    @Test void recognizesTheWynncraftPotionAndFoodSelectors() {
        var definition = JsonParser.parseString("""
                {"entries":[
                  {"threshold":1500,"model":{"model":"item/wynn/potion/healing_full"}},
                  {"threshold":1461,"model":{"model":"minecraft:item/wynn/economy/meals/generic_item"}}
                ]}
                """);
        assertTrue(WynncraftScrollPreviewItemProvider.usesSelector(
                definition, 1500, "item/wynn/potion/healing_full"));
        assertTrue(WynncraftScrollPreviewItemProvider.usesSelector(
                definition, 1461, "item/wynn/economy/meals/generic_item"));
        assertFalse(WynncraftScrollPreviewItemProvider.usesSelector(
                definition, 1459, "item/wynn/economy/woodcutting/scroll_white"));
    }

    @Test void discoversTheActivePackSelectorInsteadOfAssumingItsNumber() {
        var definition = JsonParser.parseString("""
                {"entries":[
                  {"threshold":742.5,"model":{"model":"minecraft:item/wynn/potion/healing_full"}}
                ]}
                """);
        assertEquals(742.5f, WynncraftScrollPreviewItemProvider.selectorForModel(
                definition, "item/wynn/potion/healing_full"));
    }
}

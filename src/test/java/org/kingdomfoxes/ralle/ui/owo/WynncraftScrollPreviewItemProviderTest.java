package org.kingdomfoxes.ralle.ui.owo;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WynncraftScrollPreviewItemProviderTest {
    @Test void requiresTheExactTeleportScrollSelectorAt1495() {
        assertTrue(WynncraftScrollPreviewItemProvider.usesTeleportScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1495,"model":{"model":"item/wynn/scroll/scroll_teleport"}}]}}
                """)));
        assertFalse(WynncraftScrollPreviewItemProvider.usesTeleportScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1494,"model":{"model":"item/wynn/scroll/scroll_teleport"}}]}}
                """)));
        assertFalse(WynncraftScrollPreviewItemProvider.usesTeleportScroll(JsonParser.parseString("""
                {"model":{"entries":[{"threshold":1495,"model":{"model":"item/potion"}}]}}
                """)));
    }
}

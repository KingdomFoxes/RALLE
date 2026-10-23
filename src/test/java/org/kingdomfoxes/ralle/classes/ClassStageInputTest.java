package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClassStageInputTest {
    @Test void nativeUseIsQueuedOnceWithoutHeldRepeatsOrOtherActionsOnASharedBinding() {
        var use = mapping("use", InputConstants.Type.MOUSE, 1);
        var other = mapping("shared", InputConstants.Type.MOUSE, 1);
        var unrelated = mapping("old", InputConstants.Type.KEYSYM, 348);
        KeyMapping.click(InputConstants.getKey(unrelated.saveString()));
        use.setDown(true); other.setDown(true);
        ClassStageInput.queueUse(use, new KeyMapping[] {use, other, unrelated});
        // This is exactly what vanilla's handleKeybinds consumes, not an invented use-item packet.
        assertTrue(use.consumeClick());
        assertFalse(use.consumeClick());
        assertFalse(use.isDown());
        assertFalse(other.consumeClick());
        assertFalse(other.isDown());
        assertFalse(unrelated.consumeClick());
    }
    @Test void rebindingUseStillQueuesThatActionAndClearsStaleClicks() {
        var use = mapping("rebound", InputConstants.Type.KEYSYM, 347);
        KeyMapping.click(InputConstants.getKey(use.saveString()));
        KeyMapping.click(InputConstants.getKey(use.saveString()));
        ClassStageInput.queueUse(use, new KeyMapping[] {use});
        assertTrue(use.consumeClick());
        assertFalse(use.consumeClick());
        assertFalse(use.isDown());
    }
    private static KeyMapping mapping(String id, InputConstants.Type type, int key) {
        return new KeyMapping("key.ralle.test.native." + id, type, key, KeyMapping.Category.MISC);
    }
}

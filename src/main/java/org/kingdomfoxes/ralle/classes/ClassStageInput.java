package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Queue exactly one normal Use click for vanilla's input pass, with no held repeat.
 * Shared physical bindings must not queue unrelated gameplay actions. */
public final class ClassStageInput {
    private ClassStageInput() { }
    public static void queueUse(KeyMapping use, KeyMapping[] mappings) {
        for (var mapping : mappings) clear(mapping);
        KeyMapping.click(InputConstants.getKey(use.saveString()));
        for (var mapping : mappings) if (mapping != use) clear(mapping);
        use.setDown(false);
    }
    private static void clear(KeyMapping mapping) {
        mapping.setDown(false);
        while (mapping.consumeClick()) { }
    }
}

package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.world.item.ItemStack;

/** Isolates resource-pack-specific preview selection from reusable slot UI. */
public interface PreviewItemProvider {
    ItemStack previewItem();

    /** Advances an interactive preview, returning whether its item changed. */
    default boolean advance() {
        return false;
    }
}

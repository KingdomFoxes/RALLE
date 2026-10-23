package org.kingdomfoxes.ralle.classes;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/** Passive work cache only. Automated target selection always re-decodes the live menu. */
final class ClassMenuSnapshot {
    private AbstractContainerMenu menu;
    private String title;
    private long rosterRevision;
    private List<ItemStack> items = List.of();

    boolean unchanged(AbstractContainerMenu menu, String title, long rosterRevision) {
        if (this.menu != menu || !Objects.equals(this.title, title) || this.rosterRevision != rosterRevision
                || items.size() != WynncraftClassMenu.MENU_SIZE || menu.slots.size() < items.size()) return false;
        // Copies catch in-place component/count changes even if another mod did not advance
        // the native state ID. Presentation, lore and Auto Open are all observed inputs.
        for (int i = 0; i < items.size(); i++)
            if (!ItemStack.matches(items.get(i), menu.getSlot(i).getItem())) return false;
        return true;
    }

    List<ItemStack> capture(AbstractContainerMenu menu, String title, long rosterRevision) {
        this.menu = menu;
        this.title = title;
        this.rosterRevision = rosterRevision;
        items = menu.slots.stream().limit(WynncraftClassMenu.MENU_SIZE).map(slot -> slot.getItem().copy()).toList();
        return items;
    }

    void clear() { menu = null; title = null; items = List.of(); }
    void rosterRevision(long revision) { rosterRevision = revision; }
}

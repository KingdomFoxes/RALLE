package org.kingdomfoxes.ralle.classes;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class ClassMenuSnapshotTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void unchangedBooksSkipPassiveWorkButEveryComponentCountAndOwnerChangeInvalidatesIt() {
        var menu = new Book();
        menu.getSlot(10).set(new ItemStack(Items.POTION));
        var snapshot = new ClassMenuSnapshot();
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 1));
        snapshot.capture(menu, WynncraftClassMenu.TITLE, 1);
        assertTrue(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 1));
        // The same live ItemStack can be mutated without advancing a native menu state ID.
        menu.getSlot(10).getItem().set(DataComponents.CUSTOM_NAME, Component.literal("Changed class"));
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 1));
        snapshot.capture(menu, WynncraftClassMenu.TITLE, 1);
        menu.getSlot(10).getItem().setCount(2);
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 1));
        snapshot.capture(menu, WynncraftClassMenu.TITLE, 1);
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 2));
        snapshot.rosterRevision(2);
        assertTrue(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 2));
        assertFalse(snapshot.unchanged(menu, "Different book", 2));
        assertFalse(snapshot.unchanged(new Book(), WynncraftClassMenu.TITLE, 2));
        snapshot.clear();
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 2));
    }

    @Test void autoOpenControlChangesAreObservedEvenWhileCharacterDiscoveryIsDisabled() {
        var menu = new Book();
        var snapshot = new ClassMenuSnapshot();
        snapshot.capture(menu, WynncraftClassMenu.TITLE, 0);
        menu.getSlot(53).set(new ItemStack(Items.POTION));
        assertFalse(snapshot.unchanged(menu, WynncraftClassMenu.TITLE, 0));
    }

    @Test void automatedSelectionStillReDecodesLiveItemsInsteadOfReadingPassiveSnapshots() throws Exception {
        var node = new ClassNode();
        try (var input = ClassMenuScanner.class.getResourceAsStream("ClassMenuScanner.class")) {
            new ClassReader(input).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("liveMenu")).findFirst().orElseThrow();
        boolean decoded = false;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            assertNotEquals("org/kingdomfoxes/ralle/classes/ClassMenuSnapshot", call.owner);
            if (call.owner.equals("org/kingdomfoxes/ralle/classes/WynncraftClassMenu") && call.name.equals("decode")) decoded = true;
        }
        assertTrue(decoded, "Every target selection must still use fresh live evidence");
    }

    private static final class Book extends AbstractContainerMenu {
        Book() {
            super(MenuType.GENERIC_9x6, 1);
            var items = new SimpleContainer(WynncraftClassMenu.MENU_SIZE);
            for (int i = 0; i < WynncraftClassMenu.MENU_SIZE; i++) addSlot(new Slot(items, i, 0, 0));
        }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
}

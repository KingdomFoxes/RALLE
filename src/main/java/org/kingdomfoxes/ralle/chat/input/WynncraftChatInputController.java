package org.kingdomfoxes.ralle.chat.input;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import org.kingdomfoxes.ralle.client.WynncraftHost;

/** Minecraft boundary for passive next-message input detection, independent of Wynntils. */
public final class WynncraftChatInputController {
    private final Minecraft client;
    private final BooleanSupplier enabled;
    private final ChatInputRequestTracker requests;

    public WynncraftChatInputController(Minecraft client, BooleanSupplier enabled, ChatTypeTabService tabs) {
        this.client = client;
        this.enabled = enabled;
        requests = new ChatInputRequestTracker(tabs);
    }

    public void observePrompt(Component message) {
        if (active()) requests.observePrompt(message.getString());
    }

    public void menuAction(int containerId, int slotId, int button, ClickType type) {
        requests.reset();
        if (!active() || type != ClickType.PICKUP || button < 0 || button > 1
                || !(client.screen instanceof AbstractContainerScreen<?> screen)) return;
        var menu = screen.getMenu();
        if (menu.containerId != containerId || slotId < 0 || slotId >= menu.slots.size()) return;
        var slot = menu.getSlot(slotId);
        if (slot.container == client.player.getInventory() || !slot.hasItem()) return;
        var stack = slot.getItem();
        var lore = stack.get(DataComponents.LORE);
        String help = lore == null ? "" : String.join(" ", lore.lines().stream().map(Component::getString).toList());
        requests.menuAction(containerId, screen.getTitle().getString(), stack.getHoverName().getString(), help, now());
    }

    public void serverClosed(int containerId) {
        if (active()) requests.serverClosed(containerId, now());
    }

    public void tick() {
        if (active()) requests.tick(now());
    }

    public void reset() {
        requests.reset();
    }

    private boolean active() {
        var server = client.getCurrentServer();
        boolean active = enabled.getAsBoolean() && client.player != null
                && server != null && WynncraftHost.matches(server.ip);
        if (!active) requests.reset();
        return active;
    }

    private static long now() {
        return System.nanoTime() / 1_000_000;
    }
}

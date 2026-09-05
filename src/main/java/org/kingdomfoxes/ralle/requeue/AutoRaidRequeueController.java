package org.kingdomfoxes.ralle.requeue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.kingdomfoxes.ralle.api.settings.KeybindSetting;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarState;
import org.kingdomfoxes.ralle.ui.owo.LfgActionGlyph;

import java.util.Locale;

/** Bounded, headless Wynncraft Party Finder navigation for the last locally observed raid. */
public final class AutoRaidRequeueController {
    public static final String KEYBIND_ID = "automatic-raid-requeue-keybind";

    private static final int MENU_TIMEOUT_TICKS = 60;
    private static final int SETTLE_TICKS = 2;
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int MAIN_RAID_START_SLOT = 10;
    private static final int PARTY_QUEUE_SLOT = inventorySlot(6, 5);
    private static final int READY_START_SLOT = 33;
    private static final int READY_END_SLOT = 35;

    private final Minecraft minecraft;
    private final KeybindSetting binding;
    private final AutoRaidRequeueStore store;
    private final LfgActionBarState actionBar;

    private State state = State.IDLE;
    private WynnRaid target;
    private AbstractContainerMenu menu;
    private int deadlineTick;
    private int settledTick;
    private final RaidReadyTracker readyTracker = new RaidReadyTracker();

    public AutoRaidRequeueController(Minecraft minecraft, KeybindSetting binding,
                                     AutoRaidRequeueStore store, LfgActionBarState actionBar) {
        this.minecraft = minecraft;
        this.binding = binding;
        this.store = store;
        this.actionBar = actionBar;
    }

    public void observeChat(Component message) {
        if (!listening()) {
            clearPendingPrompt();
            return;
        }
        readyTracker.observe(message.getString(), tickNow()).ifPresent(store::remember);
    }

    public void start() {
        if (!normalWynncraftGameplay()) return;
        if (state != State.IDLE) {
            actionBar.show("Raid requeue already running", LfgActionBarState.Tone.MUTED);
            return;
        }
        target = store.lastRaid().orElse(null);
        if (target == null) {
            actionBar.show("No previous raid remembered", LfgActionBarState.Tone.DANGER);
            return;
        }
        var connection = minecraft.getConnection();
        if (connection == null) return;
        state = State.WAIT_MAIN;
        deadlineTick = tickNow() + MENU_TIMEOUT_TICKS;
        actionBar.showRaid("Requeue ", target.lfgType(), target.displayName(),
                LfgActionBarState.Tone.NORMAL, LfgActionGlyph.REQUEUE);
        connection.sendCommand("pf");
    }

    /** Called by the narrow setScreen mixin after vanilla has installed the server menu on the player. */
    public boolean suppressScreen(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?>) || !(screen instanceof MenuAccess<?> access)) return false;
        if (state != State.WAIT_MAIN && state != State.WAIT_QUEUE && state != State.WAIT_READY) return false;
        menu = access.getMenu();
        state = switch (state) {
            case WAIT_MAIN -> State.SCAN_MAIN;
            case WAIT_QUEUE -> State.SCAN_QUEUE;
            case WAIT_READY -> State.SCAN_READY;
            default -> state;
        };
        settledTick = tickNow() + SETTLE_TICKS;
        return true;
    }

    public void tick() {
        if (state == State.IDLE) return;
        if (!normalWynncraftConnection()) {
            fail("Raid requeue cancelled");
            return;
        }
        int now = tickNow();
        if (state == State.FINISH) {
            if (minecraft.player.containerMenu != menu || now > deadlineTick) finish();
            return;
        }
        if (now > deadlineTick) {
            fail("Raid requeue timed out");
            return;
        }
        if (state == State.SCAN_MAIN || state == State.SCAN_QUEUE || state == State.SCAN_READY) {
            if (now < settledTick || menu == null || minecraft.player.containerMenu != menu) return;
            switch (state) {
                case SCAN_MAIN -> scanMain();
                case SCAN_QUEUE -> scanQueue();
                case SCAN_READY -> scanReady();
                default -> { }
            }
        }
    }

    public void cancel() {
        if (state != State.IDLE) closeOwnedMenu();
        reset();
        clearPendingPrompt();
    }

    public boolean running() {
        return state != State.IDLE;
    }

    private void scanMain() {
        int containerSlots = containerSlots(menu);
        for (int slot = MAIN_RAID_START_SLOT; slot < containerSlots; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.getItem() == Items.PLAYER_HEAD) break;
            if (named(stack, target.displayName())) {
                clickAndWait(slot, State.WAIT_READY);
                return;
            }
        }
        if (PARTY_QUEUE_SLOT < containerSlots) clickAndWait(PARTY_QUEUE_SLOT, State.WAIT_QUEUE);
    }

    private void scanQueue() {
        int slot = findNamedSlot(menu, target.displayName(), 0, containerSlots(menu) - 1);
        if (slot >= 0) clickAndWait(slot, State.WAIT_READY);
    }

    private void scanReady() {
        int last = Math.min(READY_END_SLOT, containerSlots(menu) - 1);
        int slot = findNamedSlot(menu, "Ready Up!", READY_START_SLOT, last);
        if (slot < 0) slot = findNamedSlot(menu, "Ready Up!", 0, containerSlots(menu) - 1);
        if (slot < 0) return;
        click(slot);
        state = State.FINISH;
        deadlineTick = tickNow() + MENU_TIMEOUT_TICKS;
        actionBar.showRaid("Requeued ", target.lfgType(), target.displayName(),
                LfgActionBarState.Tone.ACCENT, LfgActionGlyph.REQUEUE);
    }

    private void clickAndWait(int slot, State waitingState) {
        click(slot);
        state = waitingState;
        menu = null;
        deadlineTick = tickNow() + MENU_TIMEOUT_TICKS;
    }

    private void click(int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, minecraft.player);
    }

    private void finish() {
        closeOwnedMenu();
        reset();
    }

    private void fail(String text) {
        closeOwnedMenu();
        actionBar.show(text, LfgActionBarState.Tone.DANGER);
        reset();
    }

    private void closeOwnedMenu() {
        if (menu != null && minecraft.player != null && minecraft.player.containerMenu == menu) {
            minecraft.player.closeContainer();
        }
    }

    private void reset() {
        state = State.IDLE;
        target = null;
        menu = null;
        deadlineTick = 0;
        settledTick = 0;
    }

    private void clearPendingPrompt() {
        readyTracker.clear();
    }

    private boolean listening() {
        return !KeybindSetting.UNBOUND.equals(binding.value()) && normalWynncraftConnection();
    }

    private boolean normalWynncraftGameplay() {
        return normalWynncraftConnection() && minecraft.screen == null;
    }

    private boolean normalWynncraftConnection() {
        var server = minecraft.getCurrentServer();
        return server != null
                && RaidLfgService.isWynncraft(server.ip.strip().toLowerCase(Locale.ROOT).split(":", 2)[0])
                && minecraft.level != null
                && minecraft.player != null;
    }

    static int containerSlots(AbstractContainerMenu menu) {
        return Math.max(0, menu.slots.size() - PLAYER_INVENTORY_SLOTS);
    }

    static int inventorySlot(int oneBasedRow, int oneBasedColumn) {
        if (oneBasedRow < 1 || oneBasedColumn < 1 || oneBasedColumn > 9) {
            throw new IllegalArgumentException("Inventory rows and columns are one-based; columns end at 9");
        }
        return (oneBasedRow - 1) * 9 + oneBasedColumn - 1;
    }

    private static int findNamedSlot(AbstractContainerMenu menu, String wanted, int first, int last) {
        if (last < first) return -1;
        for (int slot = Math.max(0, first); slot <= last && slot < menu.slots.size(); slot++) {
            if (named(menu.getSlot(slot).getItem(), wanted)) return slot;
        }
        return -1;
    }

    static boolean named(ItemStack stack, String wanted) {
        if (stack == null || stack.isEmpty()) return false;
        String actual = WynnRaid.normalize(stack.getHoverName().getString());
        String expected = WynnRaid.normalize(wanted);
        return actual.equals(expected) || actual.endsWith(" " + expected);
    }

    private int tickNow() {
        return minecraft.player == null ? 0 : minecraft.player.tickCount;
    }

    private enum State {
        IDLE, WAIT_MAIN, SCAN_MAIN, WAIT_QUEUE, SCAN_QUEUE, WAIT_READY, SCAN_READY, FINISH
    }
}

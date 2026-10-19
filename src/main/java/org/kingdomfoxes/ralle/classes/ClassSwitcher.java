package org.kingdomfoxes.ralle.classes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickType;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.client.WynncraftHost;
import org.kingdomfoxes.ralle.ui.owo.ClassSelectorWheelScreen;
import org.kingdomfoxes.ralle.ui.owo.LfgActionBarState;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Standalone native adapter with bounded stage clicks and no initial timed delay. */
public final class ClassSwitcher implements ClassSwitchController.Actions, AutoCloseable {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ClassSwitcher.class);
    private final Minecraft minecraft;
    private final BooleanSetting enabled;
    private final ClassMenuScanner scanner;
    private final LfgActionBarState feedback;
    private final java.util.function.BooleanSupplier otherAutomation;
    private final ClassSwitchFrame frame = new ClassSwitchFrame();
    private final ClassSwitchController controller = new ClassSwitchController(this);
    private final ClassSwitchAudio audio;
    private final ClassLoadTracker loads;
    private final ClassSelectorStore store;
    private String target;
    private int book = -1;
    private boolean selectionScene;
    private int interactionBudget;
    private ClassSwitchController.State lastStage = ClassSwitchController.State.IDLE;
    private String bookStatus = "not received";

    public ClassSwitcher(Minecraft minecraft, BooleanSetting enabled, ClassMenuScanner scanner, ClassSelectorStore store, LfgActionBarState feedback,
                         java.util.function.BooleanSupplier otherAutomation) {
        this.minecraft = minecraft;
        this.enabled = enabled;
        this.scanner = scanner;
        this.feedback = feedback;
        this.otherAutomation = otherAutomation;
        this.store = store;
        loads = new ClassLoadTracker(store, store::recordLoadAsync);
        audio = new ClassSwitchAudio(() -> minecraft.getSoundManager().refreshCategoryVolume(SoundSource.MASTER));
    }
    public boolean active() { return controller.active(); }
    public boolean isCurrent(String reference) {
        loads.session(minecraft.getUser().getProfileId());
        return reference.equals(loads.current());
    }
    private boolean connected() {
        return minecraft.getConnection() != null && minecraft.getCurrentServer() != null
                && WynncraftHost.matches(minecraft.getCurrentServer().ip);
    }
    private static long now() { return System.nanoTime() / 1_000_000; }

    public boolean begin(String reference) {
        if (!enabled.value() || !connected() || minecraft.player == null || minecraft.level == null
                || minecraft.gameMode == null || minecraft.player.containerMenu != minecraft.player.inventoryMenu
                || minecraft.getOverlay() != null || otherAutomation.getAsBoolean() || active() || isCurrent(reference)) return false;
        target = reference;
        book = -1;
        bookStatus = "not received";
        lastStage = ClassSwitchController.State.CAPTURING;
        LOGGER.info("Class switch: capturing selected frame");
        feedback.clear();
        return controller.begin(minecraft.getUser().getProfileId(), reference, loads.current(), true, now());
    }
    public void tick() {
        if (!enabled.value() || !connected()) {
            controller.tick(minecraft.getUser().getProfileId(), connected(), now());
            if (active()) controller.cancel();
            loads.reset();
            return;
        }
        loads.session(minecraft.getUser().getProfileId());
        if (!active()) return;
        beforeTick();
        if (!active()) return;
        // Screens may be replaced by a native/Wynntils character book. Keep it ticking underneath.
        if (minecraft.screen != null && !(minecraft.screen instanceof ClassSelectorWheelScreen)
                && controller.state() != ClassSwitchController.State.WAITING_WORLD
                && controller.state() != ClassSwitchController.State.WAITING_TRANSITION
                && (minecraft.player == null || minecraft.player.containerMenu == minecraft.player.inventoryMenu)) {
            controller.unexpectedMenu(); return;
        }
        blockHeldInputs();
        traceState();
        menuUpdated();
    }
    /** Packet-tail fast path: validate and select as soon as complete live contents are applied. */
    public void menuUpdated() {
        if (controller.state() == ClassSwitchController.State.WAITING_MENU) {
            beforeTick();
            if (!active()) return;
            scanner.liveMenu().ifPresent(menu -> {
                bookStatus = (menu.result().complete() ? "complete" : "partial") + " (" + menu.result().characters().size() + " characters)";
                if (menu.result().complete()) {
                    var slot = menu.result().resolve(target);
                    controller.menu(menu.containerId(), slot.isPresent()
                            ? List.of(new ClassSwitchController.Target(target, slot.getAsInt())) : List.of(), true);
                }
            });
        }
    }
    /** Revalidate before vanilla input, including after a delayed tick. */
    public void beforeTick() {
        if (!active()) return;
        controller.tick(minecraft.getUser().getProfileId(), connected(), now());
        if (active() && (!enabled.value() || minecraft.getOverlay() != null
                || GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE)) controller.cancel();
    }
    public void beforeInput() {
        if (!active()) return;
        beforeTick();
        if (!active() || !controller.acceptingStageClicks()
                || !selectionScene || !usableScene()) return;
        controller.selectionStage(now());
    }
    private boolean usableScene() {
        return minecraft.player != null && minecraft.level != null && minecraft.screen == null
                && minecraft.getCameraEntity() != null && minecraft.getOverlay() == null
                && !minecraft.player.isUsingItem() && !minecraft.player.isHandsBusy()
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu;
    }
    private void blockHeldInputs() {
        for (var key : minecraft.options.keyMappings) {
            key.setDown(false);
            while (key.consumeClick()) { }
        }
        if (minecraft.gameMode != null) minecraft.gameMode.stopDestroyBlock();
    }
    public boolean handleKeyboard(KeyEvent event, int action) {
        if (!active()) return false;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) controller.cancel();
        return true;
    }
    public void actionBar(String text) {
        controller.tick(minecraft.getUser().getProfileId(), connected(), now());
        if (active() && !enabled.value()) controller.cancel();
        boolean prompt = ClassMenuCapture.selectionPrompt(text);
        loads.session(minecraft.getUser().getProfileId());
        if (prompt && loads.hasSelection() && !controller.acceptingStageClicks()) return;
        if (prompt && !selectionScene) loads.selectionStage();
        if (prompt && !selectionScene) interactionBudget = 8;
        selectionScene = prompt;
    }

    public void systemChat(String text) {
        if (!enabled.value() || !connected() || !ClassLoadSignals.welcome(text)) return;
        loads.session(minecraft.getUser().getProfileId());
        loads.welcome();
        selectionScene = false;
        LOGGER.info("Class load: received Wynncraft world welcome");
    }
    public void compassUpdated(net.minecraft.world.item.ItemStack item) {
        if (!enabled.value() || !connected()) return;
        loads.session(minecraft.getUser().getProfileId());
        loads.compass(ClassLoadSignals.compassToken(item));
    }
    /** Observe original received inventory evidence even when another mod replaces a menu. */
    public void inventorySlot(int containerId, int slot, net.minecraft.world.item.ItemStack item) {
        if (!enabled.value() || !connected() || minecraft.player == null) return;
        if (ClassLoadSignals.compassSlot(minecraft.player.containerMenu, minecraft.player.getInventory(), containerId, slot))
            compassUpdated(item);
    }
    public void inventoryContents(int containerId, List<net.minecraft.world.item.ItemStack> items) {
        if (!enabled.value() || !connected() || minecraft.player == null) return;
        for (int slot = 0; slot < items.size(); slot++) {
            if (ClassLoadSignals.compassSlot(minecraft.player.containerMenu, minecraft.player.getInventory(), containerId, slot)) {
                compassUpdated(items.get(slot));
                return;
            }
        }
    }
    /** Records only packet types, never payloads/IDs; permits comparing a manual click after Escape. */
    public void outgoing(Packet<?> packet) {
        if (!selectionScene || interactionBudget <= 0 || !enabled.value() || !connected()) return;
        if (packet instanceof ServerboundUseItemPacket || packet instanceof ServerboundUseItemOnPacket
                || packet instanceof ServerboundInteractPacket || packet instanceof ServerboundSwingPacket) {
            interactionBudget--;
            LOGGER.info("Class selection {} interaction: {}", active() ? "automated" : "manual", packet.type().id());
        }
    }
    public void opened(int containerId, String title) {
        selectionScene = false;
        if (!active()) return;
        blockHeldInputs(); // Stop stage input on book-open, even before contents arrive.
        // Wynntils may query native menus as soon as the server announces WORLD.
        // No further automation is permitted; keep the frame while those menus finish normally.
        if (controller.state() == ClassSwitchController.State.WAITING_WORLD) return;
        if (!WynncraftClassMenu.matchesTitle(title) || !controller.bookOpened())
            controller.unexpectedMenu();
        else {
            book = containerId;
            bookStatus = "opened; waiting for recognized contents";
            LOGGER.info("Class switch: character book opened ({})", containerId);
            traceState();
        }
    }
    public void closed(int packetContainer, int liveContainer) {
        if (!enabled.value() || !connected()) return;
        loads.session(minecraft.getUser().getProfileId());
        loads.closed(packetContainer, liveContainer);
        if (active()) {
            if (controller.state() == ClassSwitchController.State.WAITING_WORLD) return;
            LOGGER.info("Class switch: server close packet {}, live container {}", packetContainer, liveContainer);
            controller.serverClosed(packetContainer, liveContainer, now());
            traceState();
        }
    }
    /** Record manual selection now, confirm it only after the book closes and gameplay loads. */
    public void inventoryClick(int containerId, int slot, int button, ClickType type) {
        if (active()) return;
        if (!enabled.value() || !connected() || button != 0 || type != ClickType.PICKUP) return;
        loads.session(minecraft.getUser().getProfileId());
        scanner.liveMenu().filter(menu -> menu.containerId() == containerId && menu.result().complete()).ifPresent(menu ->
                menu.result().slots().entrySet().stream().filter(e -> e.getValue() == slot && menu.result().resolve(e.getKey()).isPresent())
                        .findFirst().ifPresent(entry -> loads.selected(entry.getKey(), containerId)));
    }

    /** Overlap the outbound command with rendering; keep the selected wheel visible for capture. */
    public void beforeRender() {
        if (!active() || !enabled.value() || !connected() || minecraft.getWindow().isMinimized()) return;
        beforeTick();
        controller.frameRendering();
    }

    /** Called after all world and UI rendering, immediately before vanilla presents its frame. */
    public void beforePresent() {
        if (!enabled.value() || !connected()) return;
        try {
            beforeTick();
            loads.session(minecraft.getUser().getProfileId());
            if (loads.gameplay(worldVisible())) {
                LOGGER.info("Class load: gameplay confirmed; current class {}", loads.current() == null ? "unknown" : "recorded");
            }
            if (!active()) return;
            controller.tick(minecraft.getUser().getProfileId(), connected(), now());
            if (!active()) return;
            if (!enabled.value() || minecraft.getOverlay() != null) { controller.cancel(); return; }
            var live = minecraft.getMainRenderTarget();
            if (controller.state() == ClassSwitchController.State.CAPTURING) {
                frame.capture(live);
                controller.frameCaptured();
                if (active() && minecraft.screen instanceof ClassSelectorWheelScreen) minecraft.setScreen(null);
                traceState();
            }
            if (!active()) return;
            if (!frame.fits(live)) { controller.unavailable(); return; }
            if (active()) frame.present(live);
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(ClassSwitcher.class).warn("Class switch rendering failed", error);
            controller.unavailable();
        }
    }
    @Override public void captureFrame() { blockHeldInputs(); }
    @Override public void muteAudio() { audio.mute(); }
    @Override public void restoreAudio() {
        try { audio.close(); }
        catch (RuntimeException error) { LOGGER.warn("Could not refresh sound channels after class switch", error); }
    }
    @Override public void sendClassCommand() {
        if (!connected()) throw new IllegalStateException("No Wynncraft connection");
        // This render pass still needs the selected wheel; dismiss it only after the GPU copy.
        loads.selectionStage();
        selectionScene = false; interactionBudget = 0;
        minecraft.getConnection().sendCommand("class");
        traceState();
    }
    @Override public void rightClickSelectionStage() {
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.level == null
                || minecraft.player.containerMenu != minecraft.player.inventoryMenu)
            throw new IllegalStateException("Selection stage unavailable");
        // Called only inside vanilla's input pass; rate limits measure actual queued clicks.
        ClassStageInput.queueUse(minecraft.options.keyUse, minecraft.options.keyMappings);
        LOGGER.info("Class switch: queued native Use click (100 ms retry limit until book opens)");
        traceState();
    }
    @Override public void selectCharacter(int containerId, int slot) {
        var menu = scanner.liveMenu().orElseThrow();
        if (containerId != book || menu.containerId() != containerId || menu.result().resolve(target).orElse(-1) != slot)
            throw new IllegalStateException("Character book changed before selection");
        loads.selected(menu.result().canonicalReference(target), containerId);
        minecraft.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, minecraft.player);
        LOGGER.info("Class switch: selected validated character slot {} in book {}", slot, containerId);
        traceState();
    }
    @Override public void releaseFrame() {
        frame.close();
        // Close only our selector. Escape/failure exposes any real server screen as it stands.
        if (minecraft.screen instanceof ClassSelectorWheelScreen) minecraft.setScreen(null);
        blockHeldInputs();
        minecraft.mouseHandler.setIgnoreFirstMove();
    }
    @Override public void completed(String reference) {
        loads.gameplay(false);
        LOGGER.info("Class switch: 1600 ms door timer finished; frame and audio released");
        clearOperation();
    }
    @Override public void failed(ClassSwitchController.Failure failure) {
        LOGGER.info("Class switch stopped at {}: {}; book {}", lastStage, failure, bookStatus);
        clearOperation();
        String key = "ralle.classes.switch." + failure.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        feedback.show(Component.translatable(key).getString(), failure == ClassSwitchController.Failure.CANCELLED
                ? LfgActionBarState.Tone.MUTED : LfgActionBarState.Tone.DANGER, 3500);
    }
    public void reset() {
        controller.cancel();
        frame.close(); restoreAudio(); clearOperation();
        loads.reset();
        selectionScene = false; interactionBudget = 0;
    }
    private boolean worldVisible() {
        return enabled.value() && connected() && minecraft.player != null && minecraft.level != null
                && (minecraft.screen == null || minecraft.screen instanceof ClassSelectorWheelScreen) && minecraft.getOverlay() == null
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu
                && minecraft.getCameraEntity() == minecraft.player
                && minecraft.gameMode != null && !minecraft.gameMode.isSpectator()
                && !minecraft.player.isHandsBusy() && !selectionScene;
    }
    private void clearOperation() {
        frame.close();
        target = null; book = -1; lastStage = ClassSwitchController.State.IDLE;
    }
    private void traceState() {
        var stage = controller.state();
        if (stage != ClassSwitchController.State.IDLE && stage != lastStage) {
            lastStage = stage;
            LOGGER.info("Class switch: {}", stage);
        }
    }
    @Override public void close() {
        reset();
        try { store.flushWrites(); }
        catch (java.io.IOException failure) { LOGGER.warn("Could not finish saving class data at shutdown", failure); }
    }
}

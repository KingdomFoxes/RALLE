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

/** Standalone native adapter. No LFG/service authority, web lookup, retries or fixed stage delay. */
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
    private String target, current;
    private java.util.UUID currentAccount;
    private int book = -1;
    private String manuallySelected;
    private int manualBook = -1;
    private final ClassStageReadiness stageReadiness = new ClassStageReadiness();
    private boolean selectionScene;
    private boolean nativeUsePending;
    private int interactionBudget;
    private ClassSwitchController.State lastStage = ClassSwitchController.State.IDLE;
    private String bookStatus = "not received";

    public ClassSwitcher(Minecraft minecraft, BooleanSetting enabled, ClassMenuScanner scanner, LfgActionBarState feedback,
                         java.util.function.BooleanSupplier otherAutomation) {
        this.minecraft = minecraft;
        this.enabled = enabled;
        this.scanner = scanner;
        this.feedback = feedback;
        this.otherAutomation = otherAutomation;
        audio = new ClassSwitchAudio(() -> minecraft.getSoundManager().refreshCategoryVolume(SoundSource.MASTER));
    }
    public boolean active() { return controller.active(); }
    public boolean isCurrent(String reference) {
        return reference.equals(current) && minecraft.getUser().getProfileId().equals(currentAccount);
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
        stageReadiness.reset();
        bookStatus = "not received";
        lastStage = ClassSwitchController.State.CAPTURING;
        LOGGER.info("Class switch: capturing selected frame");
        feedback.clear();
        return controller.begin(minecraft.getUser().getProfileId(), reference, current, true, now());
    }
    public void tick() {
        if (!enabled.value() || !connected()) {
            controller.tick(minecraft.getUser().getProfileId(), connected(), now());
            if (active()) controller.cancel();
            current = null; currentAccount = null; manuallySelected = null; manualBook = -1;
            return;
        }
        if (!active()) return;
        beforeTick();
        if (!active()) return;
        // Screens may be replaced by a native/Wynntils character book. Keep it ticking underneath.
        if (minecraft.screen != null && !(minecraft.screen instanceof ClassSelectorWheelScreen)
                && (minecraft.player == null || minecraft.player.containerMenu == minecraft.player.inventoryMenu)) {
            controller.unexpectedMenu(); return;
        }
        blockHeldInputs();
        if (controller.state() == ClassSwitchController.State.WAITING_STAGE && stageReadiness.ready() && usableScene()) {
            controller.selectionStage();
        }
        traceState();
        if (controller.state() == ClassSwitchController.State.WAITING_MENU) {
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
    /** Runs before vanilla can consume the queued Use click, including after a delayed tick. */
    public void beforeTick() {
        if (!active()) return;
        controller.tick(minecraft.getUser().getProfileId(), connected(), now());
        if (active() && (!enabled.value() || minecraft.getOverlay() != null
                || GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE)) controller.cancel();
    }
    public void beforeInput() {
        if (!nativeUsePending) return;
        beforeTick();
        if (!active() || controller.state() != ClassSwitchController.State.WAITING_MENU
                || !selectionScene || !usableScene()) return;
        nativeUsePending = false;
        ClassStageInput.queueUse(minecraft.options.keyUse, minecraft.options.keyMappings);
        LOGGER.info("Class switch: queued one Use click for vanilla input handling");
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
        if (prompt && !selectionScene) interactionBudget = 8;
        selectionScene = prompt;
        if (controller.state() == ClassSwitchController.State.WAITING_STAGE) {
            stageReadiness.prompt(prompt);
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
        manuallySelected = null; manualBook = -1;
        selectionScene = false;
        nativeUsePending = false;
        if (!active()) return;
        blockHeldInputs(); // Auto Open may beat the queued Use click; do not send it into a book.
        if (!WynncraftClassMenu.matchesTitle(title) || !controller.bookOpened())
            controller.unexpectedMenu();
        else {
            book = containerId;
            stageReadiness.reset();
            LOGGER.info("Class switch: character book opened ({})", containerId);
            traceState();
        }
    }
    public void closed(int containerId) {
        // Servers may close inventory 0 while processing /class, before opening the book.
        if (active() && !(containerId == 0 && book < 0)) controller.menuClosed(containerId);
        else if (enabled.value() && containerId == manualBook) {
            current = manuallySelected;
            currentAccount = minecraft.getUser().getProfileId();
        }
        manuallySelected = null; manualBook = -1;
    }
    /** Manual selections establish the current reference only after a matching server close. */
    public void inventoryClick(int containerId, int slot, int button, ClickType type) {
        if (active()) return;
        manuallySelected = null; manualBook = -1;
        if (!enabled.value() || button != 0 || type != ClickType.PICKUP) return;
        scanner.liveMenu().filter(menu -> menu.containerId() == containerId && menu.result().complete()).ifPresent(menu ->
                menu.result().slots().entrySet().stream().filter(e -> e.getValue() == slot && menu.result().resolve(e.getKey()).isPresent())
                        .findFirst().ifPresent(entry -> { manuallySelected = entry.getKey(); manualBook = containerId; }));
    }

    /** Called after all world and UI rendering, immediately before vanilla presents its frame. */
    public void beforePresent() {
        if (!active()) return;
        try {
            controller.tick(minecraft.getUser().getProfileId(), connected(), now());
            if (!active()) return;
            if (!enabled.value() || minecraft.getOverlay() != null) { controller.cancel(); return; }
            var live = minecraft.getMainRenderTarget();
            if (controller.state() == ClassSwitchController.State.CAPTURING) {
                frame.capture(live);
                controller.frameCaptured();
            }
            if (!active()) return;
            if (controller.state() == ClassSwitchController.State.WAITING_STAGE) {
                frame.sample(live, black -> {
                    if (controller.state() == ClassSwitchController.State.WAITING_STAGE)
                        stageReadiness.frame(black, usableScene());
                }, controller::unavailable);
            }
            if (controller.state() == ClassSwitchController.State.WAITING_WORLD) {
                frame.sample(live, black -> {
                    controller.tick(minecraft.getUser().getProfileId(), connected(), now());
                    if (!black && worldVisible()) controller.worldReady();
                }, controller::unavailable);
                return; // Wynncraft draws its own black-to-world transition; no frozen overlay.
            }
            if (!frame.fits(live)) { controller.unavailable(); return; }
            if (controller.waitingForBlack()) frame.sample(live, black -> {
                controller.tick(minecraft.getUser().getProfileId(), connected(), now());
                if (black && active() && controller.waitingForBlack()) {
                    controller.transition(target);
                    traceState();
                }
            }, controller::unavailable);
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
        // The GPU copy retains the wheel. The live screen must no longer intercept selection.
        if (minecraft.screen instanceof ClassSelectorWheelScreen) minecraft.setScreen(null);
        stageReadiness.reset();
        selectionScene = false; interactionBudget = 0;
        minecraft.getConnection().sendCommand("class");
        traceState();
    }
    @Override public void rightClickSelectionStage() {
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.level == null
                || minecraft.player.containerMenu != minecraft.player.inventoryMenu)
            throw new IllegalStateException("Selection stage unavailable");
        nativeUsePending = true;
        LOGGER.info("Class switch: selection scene visible; requesting one native Use click");
        traceState();
    }
    @Override public void selectCharacter(int containerId, int slot) {
        var menu = scanner.liveMenu().orElseThrow();
        if (containerId != book || menu.containerId() != containerId || menu.result().resolve(target).orElse(-1) != slot)
            throw new IllegalStateException("Character book changed before selection");
        current = null; // An accepted click may already change the character even if later cancelled.
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
        current = reference;
        currentAccount = minecraft.getUser().getProfileId();
        LOGGER.info("Class switch: gameplay restored; audio unmuted");
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
        current = null; currentAccount = null; manuallySelected = null; manualBook = -1;
        selectionScene = false; interactionBudget = 0;
    }
    private boolean worldVisible() {
        return enabled.value() && connected() && minecraft.player != null && minecraft.level != null
                && minecraft.screen == null && minecraft.getOverlay() == null
                && minecraft.player.containerMenu == minecraft.player.inventoryMenu
                && minecraft.getCameraEntity() == minecraft.player;
    }
    private void clearOperation() {
        frame.close(); // Also invalidate a post-black readback after cancellation or completion.
        target = null; book = -1; nativeUsePending = false; stageReadiness.reset(); lastStage = ClassSwitchController.State.IDLE;
    }
    private void traceState() {
        var stage = controller.state();
        if (stage != ClassSwitchController.State.IDLE && stage != lastStage) {
            lastStage = stage;
            LOGGER.info("Class switch: {}", stage);
        }
    }
    @Override public void close() { reset(); }
}

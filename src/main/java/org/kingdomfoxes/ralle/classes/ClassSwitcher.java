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
import org.kingdomfoxes.ralle.ui.owo.ClassWheelSnapshot;
import org.kingdomfoxes.ralle.ui.owo.ClassIcePreviewScreen;
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
    private final ClassIceRenderer ice = new ClassIceRenderer();
    private final ClassIceTimeline iceTimeline = new ClassIceTimeline();
    private ClassWheelSnapshot foreground;
    private ClassWheelSnapshot modifierStatus;
    private ClassSelectorWheelScreen frozenWheel;
    private ClassSwitchController.Retry shownRetry;
    private boolean wheelCapture;
    private ClassIcePreviewScreen preview;
    private long previewStarted;
    private int previewStyle = 1, previewPixels = 1;
    private int pixelSize = 1, capturedScale;
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
    public boolean autoOpenEnabled() { return scanner.autoOpenEnabled(); }
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
                || minecraft.getOverlay() != null || otherAutomation.getAsBoolean() || active() || isCurrent(reference)
                || !autoOpenEnabled()) return false;
        target = reference;
        book = -1;
        bookStatus = "not received";
        lastStage = ClassSwitchController.State.CAPTURING;
        LOGGER.info("Class switch: capturing selected frame; target={}", reference);
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
                if (!menu.result().autoOpenEnabled()) { controller.autoOpenRequired(); return; }
                bookStatus = (menu.result().complete() ? "complete" : "partial") + " (" + menu.result().characters().size() + " characters)";
                if (menu.result().complete()) {
                    var slot = menu.result().resolve(target);
                    if (slot.isEmpty()) LOGGER.warn("Class match rejected: target={}; canonical={}; reason={}; roster={}",
                            target, menu.result().canonicalReference(target), menu.result().matchStatus(target),
                            menu.result().characters().stream().map(c -> c.type() + "@" + c.book().slot() + "=" + c.id()).toList());
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
        if (prompt) controller.observedSelectionStage();
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
        if (!WynncraftClassMenu.matchesTitle(title) || !controller.bookOpened(containerId, now()))
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
        if (active() && controller.retiredBook(packetContainer)) return;
        loads.session(minecraft.getUser().getProfileId());
        loads.closed(packetContainer, liveContainer);
        if (active()) {
            if (controller.state() == ClassSwitchController.State.WAITING_WORLD) return;
            LOGGER.info("Class switch: server close packet {}, live container {}", packetContainer, liveContainer);
            controller.serverClosed(packetContainer, liveContainer, now());
            if (controller.state() == ClassSwitchController.State.WAITING_WORLD) iceTimeline.bookClosed(now());
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
        if (preview != null) {
            if (minecraft.screen != preview || minecraft.getOverlay() != null || !minecraft.isWindowActive()
                    || now() - previewStarted >= 800 + ClassSwitchController.ANIMATION_MILLIS) stopPreview();
            return;
        }
        if (!active() || !enabled.value() || !connected() || minecraft.getWindow().isMinimized()) return;
        beforeTick();
        controller.frameRendering();
    }

    /** Called after all world and UI rendering, immediately before vanilla presents its frame. */
    public void beforePresent() {
        if (preview != null) {
            try {
                var live = minecraft.getMainRenderTarget();
                if (!frame.captured()) return;
                if (!frame.fits(live) || capturedScale != minecraft.getWindow().getGuiScale()) { stopPreview(); return; }
                if (foreground == null) foreground = preview.captureForeground();
                composite(live);
            } catch (RuntimeException error) { LOGGER.warn("Ice preview failed", error); stopPreview(); }
            return;
        }
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
                if (wheelCapture) {
                    if (!frame.captured() || !(minecraft.screen instanceof ClassSelectorWheelScreen wheel)) {
                        controller.unavailable(); return;
                    }
                    foreground = wheel.captureForeground();
                    frozenWheel = wheel;
                } else frame.capture(live);
                controller.frameCaptured();
                if (active() && minecraft.screen instanceof ClassSelectorWheelScreen) minecraft.setScreen(null);
                traceState();
            }
            if (!active()) return;
            if (!frame.fits(live) || capturedScale != minecraft.getWindow().getGuiScale()) { controller.unavailable(); return; }
            if (active()) composite(live);
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(ClassSwitcher.class).warn("Class switch rendering failed", error);
            controller.unavailable();
        }
    }
    @Override public void captureFrame() {
        blockHeldInputs();
        iceTimeline.start(now());
        wheelCapture = minecraft.screen instanceof ClassSelectorWheelScreen;
        pixelSize = 1;
        capturedScale = minecraft.getWindow().getGuiScale();
    }

    /** Native GUI boundary: HUD has rendered; menu blur and foreground have not. */
    public void captureBackground() {
        if (frame.captured()) return;
        if (preview != null || controller.state() == ClassSwitchController.State.CAPTURING && wheelCapture) {
            try { frame.capture(minecraft.getMainRenderTarget()); }
            catch (RuntimeException failure) {
                if (preview != null) stopPreview(); else controller.unavailable();
            }
        }
    }
    private void composite(com.mojang.blaze3d.pipeline.RenderTarget live) {
        if (preview != null && now() >= previewStarted + 800) iceTimeline.bookClosed(previewStarted + 800);
        ice.draw(frame.texture(), live, iceTimeline.progress(now()), pixelSize,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                preview == null ? 1 : previewStyle);
        if (foreground != null) {
            minecraft.gameRenderer.processBlurEffect();
            foreground.present(live);
        }
        if (preview == null) {
            updateModifierStatus();
            if (modifierStatus != null) modifierStatus.present(live);
        }
    }
    private void updateModifierStatus() {
        var retry = controller.retry();
        if (java.util.Objects.equals(retry, shownRetry) && (modifierStatus != null || frozenWheel == null)) return;
        Component message = retry == null ? null : Component.translatable(
                retry.kind() == ClassSwitchController.RetryKind.COMMAND
                        ? "ralle.classes.switch.retry-command" : "ralle.classes.switch.retry-book", retry.number(), retry.total());
        ClassWheelSnapshot replacement = null;
        if (frozenWheel != null) replacement = frozenWheel.captureStatus(message);
        else if (message != null) {
            var status = new LfgActionBarState();
            status.hold(message.getString(), LfgActionBarState.Tone.DANGER);
            replacement = new ClassWheelSnapshot(minecraft, graphics ->
                    org.kingdomfoxes.ralle.ui.owo.LfgActionBarOverlay.render(minecraft, graphics, status.snapshot()));
        }
        var old = modifierStatus;
        modifierStatus = replacement;
        shownRetry = retry;
        if (old != null) old.close();
    }
    public void previewIce(int pixels) {
        previewIce(pixels, previewStyle);
    }
    public void previewIceStyle(int style) {
        previewIce(previewPixels, style);
    }
    private void previewIce(int pixels, int style) {
        if (active()) return;
        if (pixels != 1 && pixels != 2) throw new IllegalArgumentException("Expected one or two logical pixels");
        if (style < 1 || style > 4) throw new IllegalArgumentException("Expected style 1 through 4");
        stopPreview();
        ClassIceRenderer.warm(minecraft, style);
        previewStyle = style;
        previewPixels = pixels;
        pixelSize = pixels;
        capturedScale = minecraft.getWindow().getGuiScale();
        previewStarted = now();
        iceTimeline.start(previewStarted);
        // Both previews replay the same 800 ms book arrival and 1600 ms finishing interval.
        preview = new ClassIcePreviewScreen(this, pixels, style);
        minecraft.setScreen(preview);
    }
    public void stopPreview() {
        var old = preview;
        preview = null;
        if (old == null) return;
        closeComposition();
        if (minecraft.screen == old) minecraft.setScreen(null);
    }
    public void warmIce() { ClassIceRenderer.warm(minecraft); }
    private void closeComposition() {
        frame.close();
        try { ice.close(); }
        finally {
            try {
                if (modifierStatus != null) { var old = modifierStatus; modifierStatus = null; old.close(); }
            } finally {
                frozenWheel = null;
                shownRetry = null;
                if (foreground != null) { var old = foreground; foreground = null; old.close(); }
            }
        }
    }
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
        LOGGER.info("Class switch: sent /class (bounded command recovery enabled)");
        traceState();
    }
    @Override public boolean canRetryClassCommand() {
        return worldVisible() && !loads.hasSelection();
    }
    @Override public boolean closeCharacterBook(int containerId) {
        if (containerId != book || !scanner.ownsLiveBook(containerId)) return false;
        // Discard the unacknowledged selection before our own close can be observed.
        loads.selectionStage();
        selectionScene = false;
        book = -1;
        bookStatus = "recovering stalled book; waiting for fresh selection prompt";
        blockHeldInputs();
        minecraft.player.closeContainer();
        LOGGER.info("Class switch: closed stalled character book {}; awaiting verified stage to reopen", containerId);
        return true;
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
        if (!menu.result().autoOpenEnabled()) { controller.autoOpenRequired(); return; }
        if (containerId != book || menu.containerId() != containerId || menu.result().resolve(target).orElse(-1) != slot)
            throw new IllegalStateException("Character book changed before selection");
        loads.selected(menu.result().canonicalReference(target), containerId);
        minecraft.gameMode.handleInventoryMouseClick(containerId, slot, 0, ClickType.PICKUP, minecraft.player);
        LOGGER.info("Class switch: selected validated character slot {} in book {}", slot, containerId);
        traceState();
    }
    @Override public void releaseFrame() {
        closeComposition();
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
        stopPreview();
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
        closeComposition();
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

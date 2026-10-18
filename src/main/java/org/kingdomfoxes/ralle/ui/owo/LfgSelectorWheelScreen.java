package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.diagnostics.DiagnosticProfiler;
import org.kingdomfoxes.ralle.RalleClient;

import org.kingdomfoxes.ralle.lfg.client.LfgCreationFeedback;
import org.kingdomfoxes.ralle.lfg.client.LfgChordState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgKeybinds;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.sound.CreateWheelSounds;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Transparent Create/Kick selector. Both graphical styles share actions, input and effect lifecycle. */
public final class LfgSelectorWheelScreen extends Screen {
    public enum Mode { CREATE, KICK }

    private static final int DISABLED = 0xFF697487;
    private static final LfgProtocol.RaidType[] RAIDS = LfgProtocol.RaidType.values();

    private final Minecraft minecraft;
    private final RaidLfgKeybinds controller;
    private final RaidLfgService service;
    private final Mode mode;
    private final int initiatingKey;
    private final boolean bars;
    private final LfgChordState<Entry> keyboardChord = new LfgChordState<>();
    private boolean keyboardSelection;
    private boolean pointerArmed;
    private boolean mouseReady;
    private int lastMouseX, lastMouseY;
    private final UUID lobbyId;
    private final boolean hostedAtOpen;
    private List<Entry> entries;
    private final SelectorWheelModel model = new SelectorWheelModel();
    private List<SelectorWheelLayout.Face> geometry = List.of();
    private double wheelScale = 1;
    private double outerRadius;
    private double originY, scroll, maxScroll;
    private Component status;
    private boolean statusFailure;
    private boolean partyPromptOpen;
    private CreateWheelAnimation creationAnimation;
    private final CreateWheelExitCue exitCue = new CreateWheelExitCue();
    private final CreateWheelSnapshot[] snapshots = new CreateWheelSnapshot[RAIDS.length];
    private final ItemStack[] raidItems = new ItemStack[RAIDS.length];
    private boolean snapshotFailed;
    private UUID pendingKickId;
    private int kickX;
    private int kickY;
    private float kickScale;
    private long explosionStarted = -1;
    private static final Identifier EXPLOSION = Identifier.fromNamespaceAndPath("ralle", "textures/gui/wheel/kick_explosion.png");

    public LfgSelectorWheelScreen(Minecraft minecraft, RaidLfgKeybinds controller,
                                  RaidLfgService service, Mode mode, int initiatingKey, String style) {
        super(Component.translatable(mode == Mode.CREATE
                ? "ralle.settings.option.raid-lfg-create-selector-wheel"
                : "ralle.settings.option.raid-lfg-kick-selector-wheel"));
        this.minecraft = minecraft;
        this.controller = controller;
        this.service = service;
        this.mode = mode;
        this.initiatingKey = initiatingKey;
        this.bars = "bars".equals(style);
        keyboardChord.begin();
        var lobby = mode == Mode.KICK ? controller.currentLobbyForWheel() : null;
        this.lobbyId = lobby == null ? null : lobby.lobbyId();
        var hostLobby = mode == Mode.KICK ? controller.currentHostLobbyForWheel() : null;
        this.hostedAtOpen = lobby != null && hostLobby != null && hostLobby.lobbyId().equals(lobby.lobbyId());
        this.entries = mode == Mode.CREATE ? createEntries() : kickEntries(lobby);
        if (mode == Mode.KICK) {
            if (lobby == null) status = Component.translatable("ralle.lfg.wheel.no-party");
            else if (hostLobby == null || !hostLobby.lobbyId().equals(lobby.lobbyId())) {
                status = Component.translatable("ralle.lfg.wheel.host-only");
            } else if (entries.isEmpty()) status = Component.translatable("ralle.lfg.wheel.no-players");
        }
        model.open(System.currentTimeMillis());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        super.init();
        clearSnapshots();
        snapshotFailed = false;
        for (var key : minecraft.options.keyMappings) key.setDown(false);
        rebuildGeometry();
        GLFW.glfwSetCursorPos(minecraft.getWindow().handle(),
                minecraft.getWindow().getScreenWidth() / 2d, minecraft.getWindow().getScreenHeight() / 2d);
        lastMouseX = width / 2;
        lastMouseY = height / 2;
        pointerArmed = false;
        mouseReady = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS;
    }

    private void rebuildGeometry() {
        double labelWidth = entries.stream().mapToInt(entry -> font.width(RalleTypography.body(entry.label())))
                .max().orElse(0);
        if (bars) {
            int side = mode == Mode.CREATE ? (int) Math.max(160, (labelWidth + 12) * 5) : 80;
            // Round S down to a multiple of five so its 20% minimum remains pixel-aligned.
            side = Math.max(5, Math.min(side, (int) Math.floor(width * .64)) / 5 * 5);
            geometry = SelectorWheelLayout.bars(entries.size(), side, width);
            wheelScale = 1;
            double top = geometry.stream().mapToInt(SelectorWheelLayout.Face::top).min().orElse(-side / 2);
            double bottom = geometry.stream().mapToInt(SelectorWheelLayout.Face::bottom).max().orElse(side / 2);
            maxScroll = Math.max(0, bottom - top - Math.max(1, height - 64));
            scroll = Math.clamp(scroll, 0, maxScroll);
            originY = maxScroll > 0 ? 12 - top - scroll : height / 2d;
            outerRadius = bottom;
            return;
        }
        outerRadius = mode == Mode.CREATE ? Math.max(76, labelWidth + 32) : 40;
        int envelope = mode == Mode.CREATE ? 18 : 7;
        wheelScale = Math.max(.01, Math.min(1, Math.min((width / 2d - 8) / (outerRadius + envelope),
                (height / 2d - 24) / (outerRadius + envelope))));
        geometry = SelectorWheelLayout.circles(mode == Mode.CREATE ? SelectorWheelModel.ring(entries.size(), 24, outerRadius, true)
                : SelectorWheelModel.kickGeometry(entries.size()));
        originY = height / 2d;
    }

    @Override public void tick() {
        if (minecraft.screen != this) return;
        if (mode == Mode.CREATE) {
            if (createFailureUntil > 0 && System.currentTimeMillis() >= createFailureUntil) {
                cancelAndClose();
                return;
            }
            if (service.pendingCreate() && model.state() == SelectorWheelModel.State.SUBMITTED)
                status = Component.translatable(LfgCreationFeedback.progress(service));
            else if (model.state() == SelectorWheelModel.State.SELECTING) {
                var reason = LfgCreationFeedback.unavailable(service);
                status = reason == null ? null : Component.translatable(reason);
            }
        }
        if (mode == Mode.KICK) {
            refreshKickRoster();
            if (explosionStarted >= 0 && KickWheelAnimation.finished(explosionStarted, System.currentTimeMillis())) {
                explosionStarted = -1;
                pendingKickId = null;
                model.resumeSelection();
                keyboardChord.begin();
                status = entries.isEmpty() ? Component.translatable("ralle.lfg.wheel.no-players") : null;
            }
        } else if (model.hovered() >= 0) {
            prepareSnapshot(model.hovered());
        }
        if (creationAnimation != null && creationAnimation.finished(System.currentTimeMillis())) {
            cancelAndClose();
            return;
        }
        long window = minecraft.getWindow().handle();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE
                || !retainsCreatePresentation() && !physicalKeyDown(window, initiatingKey)
                || !controller.wheelStillValid(this, mode, lobbyId, hostedAtOpen)) {
            cancelAndClose();
        }
    }

    private static boolean physicalKeyDown(long window, int key) {
        return key >= 0 && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try (var diagnosticScope = DiagnosticProfiler.measure(DiagnosticProfiler.Section.SELECTOR_RENDER)) {
            long now = System.currentTimeMillis();
            if (mouseX != lastMouseX || mouseY != lastMouseY) {
                pointerArmed = true;
                keyboardSelection = false;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
            if (!keyboardSelection && pointerArmed && model.state() == SelectorWheelModel.State.SELECTING)
                selectHover(hit(mouseX, mouseY), now);
            if (bars) graphics.enableScissor(0, 6, width, Math.max(7, height - 42));
            graphics.pose().pushMatrix();
            graphics.pose().translate(width / 2f, (float) originY);
            graphics.pose().scale((float) wheelScale, (float) wheelScale);
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                var bounds = geometry.get(index);
                double offset = model.offset(index, now);
                boolean dissolving = creationAnimation != null && index == model.hovered();
                if (dissolving) offset += creationAnimation.extension(now);
                int dx = (int) Math.round(bounds.motionX() * offset);
                int dy = (int) Math.round(bounds.motionY() * offset);
                double progress = dissolving ? creationAnimation.progress(now) : 0;
                if (dissolving && snapshots[index] != null) {
                    snapshots[index].draw(graphics, dx, dy, progress);
                    continue;
                }
                // If GPU capture failed, keep the ordinary bounded renderer, then remove the segment.
                if (dissolving && progress >= .5) continue;
                SelectorWheelRenderer.drawFace(graphics, bounds, dx, dy, index == model.hovered());
                int x = (int) Math.round(bounds.centerX()) + dx;
                int y = (int) Math.round(bounds.centerY()) + dy;
                if (mode == Mode.CREATE) drawRaid(graphics, entry, bounds, x, y, index == model.hovered());
                else drawPlayer(graphics, entry, x, y, index == model.hovered());
            }
            graphics.pose().popMatrix();
            if (bars) graphics.disableScissor();
            if (explosionStarted >= 0) {
                int frame = KickWheelAnimation.frame(explosionStarted, now);
                if (frame >= 0) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(kickX, kickY);
                    graphics.pose().scale(kickScale, kickScale);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, EXPLOSION,
                            -35, -55, frame * 71f, 0, 71, 100, 71 * 17, 100);
                    graphics.pose().popMatrix();
                }
            }
            Component below = status != null ? status : model.hovered() >= 0 && mode == Mode.KICK
                    ? entries.get(model.hovered()).label()
                    : Component.translatable(mode == Mode.CREATE ? "ralle.lfg.wheel.create.hint" : "ralle.lfg.wheel.kick.hint");
            int color = statusFailure ? 0xFFFF6B6B : model.hovered() >= 0 && mode == Mode.KICK
                    ? org.kingdomfoxes.ralle.ui.theme.RallePalette.accentArgb()
                    : org.kingdomfoxes.ralle.ui.theme.RallePalette.secondaryText();
            var lines = font.split(RalleTypography.body(below), Math.max(1, width - 24));
            int statusY = Math.min(height - lines.size() * (font.lineHeight + 2) - 4,
                    (int) Math.ceil(originY + (outerRadius + 12) * wheelScale));
            for (var line : lines) {
                graphics.drawString(font, line, (width - font.width(line)) / 2, statusY, color, true);
                statusY += font.lineHeight + 2;
            }
        }
    }

    private void drawRaid(GuiGraphics graphics, Entry entry, SelectorWheelLayout.Face face, int x, int y, boolean hovered) {
        graphics.renderItem(raidItem(entry), x - 8, y - 14);
        var label = RalleTypography.body(entry.label());
        int color = enabled(entry) || retainsCreatePresentation() ? hovered
                        ? org.kingdomfoxes.ralle.ui.theme.RallePalette.accentArgb()
                        : org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText() : DISABLED;
        if (!bars) graphics.drawString(font, label, x - font.width(label) / 2, y + 5, color, false);
        else {
            int lineY = y + 5;
            for (var line : font.split(label, Math.max(1, face.right() - face.left() - 6))) {
                graphics.drawString(font, line, x - font.width(line) / 2, lineY, color, false);
                lineY += font.lineHeight + 1;
            }
        }
    }

    private void prepareSnapshot(int index) {
        if (snapshotFailed || snapshots[index] != null) return;
        var bounds = geometry.get(index);
        var entry = entries.get(index);
        try {
            snapshots[index] = CreateWheelSnapshot.capture(minecraft, bounds.left() - 8, bounds.top() - 8,
                    bounds.right() + 8, bounds.bottom() + 8, graphics -> {
                SelectorWheelRenderer.drawFace(graphics, bounds, 0, 0, true);
                int x = (int) Math.round(bounds.centerX());
                int y = (int) Math.round(bounds.centerY());
                drawRaid(graphics, entry, bounds, x, y, true);
            });
        } catch (RuntimeException | LinkageError failure) {
            snapshotFailed = true;
            org.slf4j.LoggerFactory.getLogger(LfgSelectorWheelScreen.class)
                    .warn("Create wheel capture unavailable; using bounded segment fallback", failure);
        }
    }

    private void clearSnapshots() {
        for (int i = 0; i < snapshots.length; i++) {
            if (snapshots[i] != null) snapshots[i].close();
            snapshots[i] = null;
        }
    }

    private ItemStack raidItem(Entry entry) {
        int index = entry.raid().ordinal();
        if (raidItems[index] == null) raidItems[index] = new ItemStack(RaidPresentation.item(entry.raid()));
        return raidItems[index];
    }

    private void refreshKickRoster() {
        var lobby = controller.currentLobbyForWheel();
        if (lobby == null || !lobby.lobbyId().equals(lobbyId)) return;
        var next = kickEntries(lobby);
        if (next.equals(entries)) return;
        entries = next;
        model.rosterChanged();
        pointerArmed = false;
        rebuildGeometry();
        if (pendingKickId == null && hostedAtOpen) {
            status = entries.isEmpty() ? Component.translatable("ralle.lfg.wheel.no-players") : null;
        }
    }

    private long createFailureUntil;

    private boolean retainsCreatePresentation() {
        return mode == Mode.CREATE && model.state() == SelectorWheelModel.State.SUBMITTED
                && (!statusFailure || System.currentTimeMillis() < createFailureUntil);
    }

    public boolean retainsSubmittedCreate() { return retainsCreatePresentation(); }

    public void suspendForPartyPrompt() { partyPromptOpen = true; }
    public void resumeFromPartyPrompt() { partyPromptOpen = false; }
    public void abandonPartyPrompt() {
        partyPromptOpen = false;
        model.cancel();
        controller.wheelClosed(this);
    }

    private void drawPlayer(GuiGraphics graphics, Entry entry, int x, int y, boolean hovered) {
        var member = currentMember(entry.memberId());
        int border = member == null ? DISABLED : PlayerHeadPresentation.guildBorder(member);
        if (member != null) {
            var cosmetic = RalleClient.cosmetic(member.minecraftUuid());
            var style = cosmetic == null ? null : cosmetic.selectedStyle();
            PlayerHeadPresentation.draw(graphics, minecraft, member, x - 9, y - 9, 16, border,
                    style, style == null ? null : RalleClient.cosmeticAppearance(style));
        } else graphics.fill(x - 8, y - 8, x + 8, y + 8, 0xFF27344A);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0 || !mouseReady) return true;
        mouseReady = false;
        if ((int) event.x() != lastMouseX || (int) event.y() != lastMouseY) pointerArmed = true;
        keyboardSelection = false;
        if (model.state() == SelectorWheelModel.State.SELECTING) {
            int target = hit(event.x(), event.y());
            selectHover(pointerArmed ? target : -1, System.currentTimeMillis());
        }
        submitSelection();
        return true;
    }

    private boolean submitSelection() {
        if (model.state() != SelectorWheelModel.State.SELECTING || model.hovered() < 0) return false;
        var entry = entries.get(model.hovered());
        if (!enabled(entry) || !physicalKeyDown(minecraft.getWindow().handle(), initiatingKey)) return false;
        if (!model.submit()) return false;
        keyboardChord.finish();
        statusFailure = false;
        status = mode == Mode.CREATE ? null : Component.translatable("ralle.lfg.wheel.kicking");
        if (mode == Mode.CREATE) controller.submitWheelCreate(entry.raid(), this::feedback);
        else {
            pendingKickId = entry.memberId();
            var sector = geometry.get(model.hovered());
            double offset = model.offset(model.hovered(), System.currentTimeMillis());
            kickX = (int) Math.round(width / 2d + (sector.centerX() + sector.motionX() * offset) * wheelScale);
            kickY = (int) Math.round(originY + (sector.centerY() + sector.motionY() * offset) * wheelScale);
            kickScale = (float) wheelScale;
            controller.submitWheelKick(lobbyId, entry.memberId(), this::feedback);
        }
        return true;
    }

    private void selectHover(int index, long now) {
        if (index >= 0 && !enabled(entries.get(index))) index = -1;
        if (mode == Mode.CREATE && index >= 0 && index != model.hovered()) CreateWheelSounds.hover(minecraft);
        model.hover(index, now);
    }

    /** Raw keyboard events have one owner; the screen callback cannot dispatch the same release again. */
    public boolean handleKeyboard(KeyEvent event, int action) {
        if (action == GLFW.GLFW_PRESS && (event.key() == GLFW.GLFW_KEY_ESCAPE
                || controller.isOtherWheelModifier(mode, event))) {
            cancelAndClose();
            return true;
        }
        if (event.key() == initiatingKey) {
            if (action == GLFW.GLFW_RELEASE && !retainsCreatePresentation()) cancelAndClose();
            return true;
        }
        int digit = RaidLfgKeybinds.topRowDigit(event.key());
        if (digit == 0 || mode == Mode.KICK && (digit < 2 || digit > 4)) return true;
        if (model.state() != SelectorWheelModel.State.SELECTING) return true;
        if (action == GLFW.GLFW_PRESS && !keyboardChord.completed()) {
            Entry selected = keyboardEntry(digit);
            // Empty slots replace the previous selection too; releasing them must not submit the old target.
            var press = keyboardChord.press(digit, () -> selected != null ? selected : EMPTY_KEYBOARD_SLOT);
            keyboardSelection = true;
            selectHover(entries.indexOf(press.selection()), System.currentTimeMillis());
            revealSelection();
        } else if (action == GLFW.GLFW_RELEASE) {
            var release = keyboardChord.release(digit, physicalKeyDown(minecraft.getWindow().handle(), initiatingKey));
            if (release.execution() != null) {
                selectHover(entries.indexOf(release.execution()), System.currentTimeMillis());
                if (!submitSelection()) keyboardChord.begin();
            }
        }
        return true;
    }

    private Entry keyboardEntry(int digit) {
        if (mode == Mode.CREATE) return digit <= entries.size() ? entries.get(digit - 1) : null;
        var lobby = controller.currentHostLobbyForWheel();
        if (lobby == null || !lobby.lobbyId().equals(lobbyId) || digit > lobby.members().size()) return null;
        var id = lobby.members().get(digit - 1).minecraftUuid();
        return entries.stream().filter(entry -> id.equals(entry.memberId())).findFirst().orElse(null);
    }

    private void revealSelection() {
        if (!bars || maxScroll == 0 || model.hovered() < 0) return;
        var face = geometry.get(model.hovered());
        double top = originY + face.top() - SelectorWheelModel.EXTENSION;
        double bottom = originY + face.bottom();
        if (top < 6) scroll -= 6 - top;
        else if (bottom > height - 42) scroll += bottom - (height - 42);
        rebuildGeometry();
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return handleKeyboard(event, GLFW.GLFW_PRESS);
    }

    @Override public boolean keyReleased(KeyEvent event) {
        return handleKeyboard(event, GLFW.GLFW_RELEASE);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) mouseReady = true;
        return true;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (bars && maxScroll > 0 && model.state() == SelectorWheelModel.State.SELECTING) {
            scroll = Math.clamp(scroll - verticalAmount * 24, 0, maxScroll);
            rebuildGeometry();
            model.hover(-1, System.currentTimeMillis());
            keyboardSelection = false;
        }
        return true;
    }

    @Override public void onClose() { cancelAndClose(); }

    @Override public void removed() {
        clearSnapshots();
        if (exitCue.wheelRemoved(partyPromptOpen)) controller.playSuccessfulWheelCreateExit();
        if (!partyPromptOpen) {
            controller.wheelClosed(this);
        }
        super.removed();
    }

    private int hit(double x, double y) {
        if (bars && (y < 6 || y >= height - 42)) return -1;
        return SelectorWheelLayout.hit(geometry, (x - width / 2d) / wheelScale, (y - originY) / wheelScale);
    }

    private void cancelAndClose() {
        if (minecraft.screen != this) return;
        model.cancel();
        controller.wheelClosed(this);
        minecraft.setScreen(null);
    }

    private void feedback(boolean success, Component message) {
        if (minecraft.screen != this) return;
        statusFailure = !success;
        status = message;
        if (!success && mode == Mode.CREATE) createFailureUntil = System.currentTimeMillis() + 3500;
        if (success && mode == Mode.CREATE && creationAnimation == null && minecraft.screen == this) {
            prepareSnapshot(model.hovered());
            long duration = CreateWheelSounds.created(minecraft);
            creationAnimation = new CreateWheelAnimation(System.currentTimeMillis(), duration);
            exitCue.creationAccepted();
        }
        if (mode == Mode.KICK) {
            refreshKickRoster();
            if (success) explosionStarted = System.currentTimeMillis();
            else {
                pendingKickId = null;
                model.resumeSelection();
                keyboardChord.begin();
            }
        }
    }

    private boolean enabled(Entry entry) {
        if (model.state() != SelectorWheelModel.State.SELECTING) return false;
        if (service.lifecycle() != RaidLfgService.LifecycleState.ONLINE) return false;
        if (mode == Mode.CREATE) {
            var capabilities = service.store().state().capabilities();
            return capabilities != null && capabilities.create() && !service.pendingCreate();
        }
        return explosionStarted < 0 && currentMember(entry.memberId()) != null && !service.pending(lobbyId, "kick");
    }

    private LfgProtocol.Member currentMember(UUID id) {
        if (id == null || lobbyId == null) return null;
        var lobby = controller.currentHostLobbyForWheel();
        if (lobby == null || !lobbyId.equals(lobby.lobbyId())) return null;
        return lobby.members().stream().filter(member -> member.minecraftUuid().equals(id))
                .filter(member -> member.role() != LfgProtocol.MemberRole.HOST).findFirst().orElse(null);
    }

    private static List<Entry> createEntries() {
        var result = new ArrayList<Entry>();
        for (var raid : RAIDS) result.add(new Entry(raid, null, Component.literal(RaidPresentation.shortName(raid))));
        return List.copyOf(result);
    }

    private static List<Entry> kickEntries(LfgProtocol.Lobby lobby) {
        if (lobby == null) return List.of();
        return lobby.members().stream().filter(member -> member.role() != LfgProtocol.MemberRole.HOST)
                .limit(3).map(member -> new Entry(null, member.minecraftUuid(), Component.literal(member.ign()))).toList();
    }

    private record Entry(LfgProtocol.RaidType raid, UUID memberId, Component label) {}
    private static final Entry EMPTY_KEYBOARD_SLOT = new Entry(null, null, Component.empty());
}

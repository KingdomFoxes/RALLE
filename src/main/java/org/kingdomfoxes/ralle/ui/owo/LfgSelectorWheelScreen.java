package org.kingdomfoxes.ralle.ui.owo;

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

/** Transparent, non-pausing Create/Kick radial selector which owns input for one modifier hold. */
public final class LfgSelectorWheelScreen extends Screen {
    public enum Mode { CREATE, KICK }

    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFA9B0BE;
    private static final int ACCENT = 0xFFF2B84B;
    private static final int DISABLED = 0xFF697487;
    private static final int SURFACE = 0xEE0A1830;
    private static final int OUTLINE = 0xFF586985;
    private static final LfgProtocol.RaidType[] RAIDS = LfgProtocol.RaidType.values();

    private final Minecraft minecraft;
    private final RaidLfgKeybinds controller;
    private final RaidLfgService service;
    private final Mode mode;
    private final int initiatingKey;
    private final UUID lobbyId;
    private final boolean hostedAtOpen;
    private List<Entry> entries;
    private final SelectorWheelModel model = new SelectorWheelModel();
    private List<SelectorWheelModel.Sector> geometry = List.of();
    private double wheelScale = 1;
    private double outerRadius;
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
    private long explosionStarted = -1;
    private static final Identifier EXPLOSION = Identifier.fromNamespaceAndPath("ralle", "textures/gui/wheel/kick_explosion.png");

    public LfgSelectorWheelScreen(Minecraft minecraft, RaidLfgKeybinds controller,
                                  RaidLfgService service, Mode mode, int initiatingKey) {
        super(Component.translatable(mode == Mode.CREATE
                ? "ralle.settings.option.raid-lfg-create-selector-wheel"
                : "ralle.settings.option.raid-lfg-kick-selector-wheel"));
        this.minecraft = minecraft;
        this.controller = controller;
        this.service = service;
        this.mode = mode;
        this.initiatingKey = initiatingKey;
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
    }

    private void rebuildGeometry() {
        double labelWidth = entries.stream().mapToInt(entry -> font.width(RalleTypography.body(entry.label())))
                .max().orElse(0);
        outerRadius = mode == Mode.CREATE ? Math.max(76, labelWidth + 32) : 40;
        int envelope = mode == Mode.CREATE ? 18 : 7;
        wheelScale = Math.max(.01, Math.min(1, Math.min((width / 2d - 8) / (outerRadius + envelope),
                (height / 2d - 24) / (outerRadius + envelope))));
        geometry = mode == Mode.CREATE ? SelectorWheelModel.ring(entries.size(), 24, outerRadius, true)
                : SelectorWheelModel.kickGeometry(entries.size());
    }

    @Override public void tick() {
        if (minecraft.screen != this) return;
        if (mode == Mode.KICK) {
            refreshKickRoster();
            if (explosionStarted >= 0 && KickWheelAnimation.finished(explosionStarted, System.currentTimeMillis())) {
                explosionStarted = -1;
                pendingKickId = null;
                model.resumeSelection();
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
        long now = System.currentTimeMillis();
        int hovered = model.state() == SelectorWheelModel.State.SELECTING
                ? hit(mouseX, mouseY) : -1;
        if (hovered >= 0 && !enabled(entries.get(hovered))) hovered = -1;
        if (mode == Mode.CREATE && hovered >= 0 && hovered != model.hovered()) CreateWheelSounds.hover(minecraft);
        model.hover(hovered, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, height / 2f);
        graphics.pose().scale((float) wheelScale, (float) wheelScale);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var bounds = geometry.get(index);
            double offset = model.offset(index, now);
            boolean dissolving = creationAnimation != null && index == model.hovered();
            if (dissolving) offset += creationAnimation.extension(now);
            int dx = (int) Math.round(Math.cos(bounds.angle()) * offset);
            int dy = (int) Math.round(Math.sin(bounds.angle()) * offset);
            double progress = dissolving ? creationAnimation.progress(now) : 0;
            if (dissolving && snapshots[index] != null) {
                snapshots[index].draw(graphics, dx, dy, progress);
                continue;
            }
            // If GPU capture failed, keep the ordinary bounded renderer, then remove the segment.
            if (dissolving && progress >= .5) continue;
            if (mode == Mode.CREATE) {
                SelectorWheelRenderer.drawCreate(graphics, bounds, dx, dy, index == model.hovered());
            }
            else {
                SelectorWheelRenderer.draw(graphics, bounds, dx, dy, index == model.hovered());
            }
            int x = (int) Math.round(bounds.centerX()) + dx;
            int y = (int) Math.round(bounds.centerY()) + dy;
            if (mode == Mode.CREATE) drawRaid(graphics, entry, x, y, index == model.hovered());
            else drawPlayer(graphics, entry, x, y, index == model.hovered());
        }
        if (explosionStarted >= 0) {
            int frame = KickWheelAnimation.frame(explosionStarted, now);
            if (frame >= 0) graphics.blit(RenderPipelines.GUI_TEXTURED, EXPLOSION,
                    kickX - 35, kickY - 55, frame * 71f, 0, 71, 100, 71 * 17, 100);
        }
        graphics.pose().popMatrix();
        Component below = status != null ? status : model.hovered() >= 0 && mode == Mode.KICK
                ? entries.get(model.hovered()).label()
                : Component.translatable(mode == Mode.CREATE ? "ralle.lfg.wheel.create.hint" : "ralle.lfg.wheel.kick.hint");
        int color = statusFailure ? 0xFFFF6B6B : model.hovered() >= 0 && mode == Mode.KICK ? ACCENT : MUTED;
        graphics.drawCenteredString(font, RalleTypography.body(below), width / 2,
                Math.min(height - 12, height / 2 + (int) Math.ceil((outerRadius + 12) * wheelScale)), color);
    }

    private void drawRaid(GuiGraphics graphics, Entry entry, int x, int y, boolean hovered) {
        graphics.renderItem(raidItem(entry), x - 8, y - 14);
        var label = RalleTypography.body(entry.label());
        graphics.drawString(font, label, x - font.width(label) / 2, y + 5,
                enabled(entry) || retainsCreatePresentation() ? hovered ? ACCENT : TEXT : DISABLED, false);
    }

    private void prepareSnapshot(int index) {
        if (snapshotFailed || snapshots[index] != null) return;
        var bounds = geometry.get(index);
        var entry = entries.get(index);
        try {
            snapshots[index] = CreateWheelSnapshot.capture(minecraft, (int) Math.ceil(outerRadius) + 4, graphics -> {
                SelectorWheelRenderer.drawCreate(graphics, bounds, 0, 0, true);
                int x = (int) Math.round(bounds.centerX());
                int y = (int) Math.round(bounds.centerY());
                graphics.renderItem(raidItem(entry), x - 8, y - 14);
                var label = RalleTypography.body(entry.label());
                graphics.drawString(font, label, x - font.width(label) / 2, y + 5, ACCENT, false);
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
        rebuildGeometry();
        if (pendingKickId == null && hostedAtOpen) {
            status = entries.isEmpty() ? Component.translatable("ralle.lfg.wheel.no-players") : null;
        }
    }

    private boolean retainsCreatePresentation() {
        return mode == Mode.CREATE && model.state() == SelectorWheelModel.State.SUBMITTED && !statusFailure;
    }

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
        if (member != null) PlayerHeadPresentation.draw(graphics, minecraft, member, x - 9, y - 9, 16, border);
        else graphics.fill(x - 8, y - 8, x + 8, y + 8, 0xFF27344A);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return true;
        if (model.state() == SelectorWheelModel.State.SELECTING) {
            int target = hit(event.x(), event.y());
            model.hover(target >= 0 && enabled(entries.get(target)) ? target : -1, System.currentTimeMillis());
        }
        if (model.state() != SelectorWheelModel.State.SELECTING || model.hovered() < 0) return true;
        var entry = entries.get(model.hovered());
        if (!enabled(entry) || !physicalKeyDown(minecraft.getWindow().handle(), initiatingKey)) return true;
        if (!model.submit()) return true;
        statusFailure = false;
        status = Component.translatable(mode == Mode.CREATE ? "ralle.lfg.wheel.creating" : "ralle.lfg.wheel.kicking");
        if (mode == Mode.CREATE) controller.submitWheelCreate(entry.raid(), this::feedback);
        else {
            pendingKickId = entry.memberId();
            var sector = geometry.get(model.hovered());
            double offset = model.offset(model.hovered(), System.currentTimeMillis());
            kickX = (int) Math.round(sector.centerX()) + (int) Math.round(Math.cos(sector.angle()) * offset);
            kickY = (int) Math.round(sector.centerY()) + (int) Math.round(Math.sin(sector.angle()) * offset);
            controller.submitWheelKick(lobbyId, entry.memberId(), this::feedback);
        }
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || controller.isOtherWheelModifier(mode, event)) {
            cancelAndClose();
        }
        return true;
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (event.key() == initiatingKey && !retainsCreatePresentation()) cancelAndClose();
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { return true; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return true; }

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
        return SelectorWheelModel.hit(geometry, (x - width / 2d) / wheelScale, (y - height / 2d) / wheelScale);
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
}

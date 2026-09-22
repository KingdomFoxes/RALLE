package org.kingdomfoxes.ralle.ui.owo;

import org.kingdomfoxes.ralle.diagnostics.DiagnosticProfiler;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.SideAnchor;
import org.kingdomfoxes.ralle.api.hud.RalleHudElements;
import org.kingdomfoxes.ralle.lfg.client.GuildTerritoryColors;
import org.kingdomfoxes.ralle.lfg.client.HostPartyInviteController;
import org.kingdomfoxes.ralle.lfg.client.LfgNotificationManager;
import org.kingdomfoxes.ralle.lfg.client.LfgDisbandConfirmation;
import org.kingdomfoxes.ralle.lfg.client.LfgKeybindHints;
import org.kingdomfoxes.ralle.lfg.client.RaidLfgService;
import org.kingdomfoxes.ralle.lfg.protocol.LfgProtocol;
import org.kingdomfoxes.ralle.lfg.protocol.LfgNoteText;
import org.kingdomfoxes.ralle.sound.LfgSoundPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Draws and hit-tests fixed Raid LFG notification cards in HUD and screen layers. */
public final class LfgNotificationOverlay {
    public static final String ELEMENT_ID = RalleHudElements.LFG_NOTIFICATIONS;
    public static final int CARD_WIDTH = RalleHudElements.LFG_NOTIFICATION_WIDTH;
    public static final int CARD_HEIGHT = RalleHudElements.LFG_NOTIFICATION_HEIGHT;
    public static final int STACK_GAP = 6;

    private static final int REGION_GOOD = 0xFF00FF55;
    private static final int REGION_MODERATE = 0xFFFFFF00;
    private static final int REGION_POOR = 0xFFFF3333;

    private final Minecraft minecraft;
    private final RaidLfgService service;
    private final LfgNotificationManager notifications;
    private final HostPartyInviteController hostPartyInvites;
    private final HudPlacementRegistry placements;
    private final LfgSoundPlayer sounds;
    private final LfgDisbandConfirmation disbandConfirmation;
    private final LfgKeybindHints keybindHints;
    private List<HitRegion> hitRegions = List.of();
    private final java.util.Map<UUID, LfgElapsedTimerComponent.TextCache> timers = new java.util.HashMap<>();
    // Private, render-only stacks: no caller can mutate a shared global item preview.
    private final java.util.Map<LfgProtocol.RaidType, ItemStack> raidPreviews =
            new java.util.EnumMap<>(LfgProtocol.RaidType.class);

    public LfgNotificationOverlay(Minecraft minecraft, RaidLfgService service,
                                  LfgNotificationManager notifications,
                                  HostPartyInviteController hostPartyInvites,
                                  HudPlacementRegistry placements, LfgSoundPlayer sounds,
                                  LfgDisbandConfirmation disbandConfirmation,
                                  LfgKeybindHints keybindHints) {
        this.minecraft = minecraft;
        this.service = service;
        this.notifications = notifications;
        this.hostPartyInvites = hostPartyInvites;
        this.placements = placements;
        this.sounds = sounds;
        this.disbandConfirmation = disbandConfirmation;
        this.keybindHints = keybindHints;
    }

    public void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.fromNamespaceAndPath(RalleClient.MOD_ID, "lfg_notifications"),
                (graphics, tickCounter) -> {
                    if (minecraft.screen == null) render(graphics, -1, -1, false);
                }
        );
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, tickDelta) -> {
                if (shouldRenderOverScreen(screen)) render(graphics, mouseX, mouseY, true);
                else hitRegions = List.of();
            });
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) ->
                    !shouldRenderOverScreen(screen) || !handleClick(event.x(), event.y(), event.button()));
        });
    }

    private boolean shouldRenderOverScreen(net.minecraft.client.gui.screens.Screen screen) {
        return minecraft.level != null
                && !(screen instanceof RaidLfgScreen)
                && !(screen instanceof LfgSelectorWheelScreen)
                && !(screen instanceof ChatLayoutEditorScreen);
    }

    public void tick() {
        notifications.tick();
        if (minecraft.level == null) {
            timers.clear();
            raidPreviews.clear();
        }
    }

    private void render(GuiGraphics graphics, int mouseX, int mouseY, boolean interactive) {
        try (var diagnosticScope = DiagnosticProfiler.measure(DiagnosticProfiler.Section.LFG_OVERLAY_RENDER)) {
            var cards = notifications.visibleCards();
            timers.keySet().removeIf(id -> cards.stream().noneMatch(card -> card.lobby().lobbyId().equals(id)));
            if (cards.isEmpty()) {
                hitRegions = List.of();
                return;
            }
            int viewportWidth = minecraft.getWindow().getGuiScaledWidth();
            int viewportHeight = minecraft.getWindow().getGuiScaledHeight();
            var anchor = placements.resolveSideAnchored(
                    ELEMENT_ID, viewportWidth, viewportHeight, SideAnchor.RIGHT,
                    Math.max(0, viewportHeight - CARD_HEIGHT - 8)
            );
            var stack = stackBounds(anchor, cards.size(), viewportWidth, viewportHeight);
            var hits = new ArrayList<HitRegion>();
            Component hoveredRosterMember = null;
            for (int index = 0; index < cards.size(); index++) {
                var card = cards.get(index);
                var target = stack.get(index);
                boolean left = anchor.x() < viewportWidth / 2;
                int offscreen = left ? -CARD_WIDTH : viewportWidth;
                int animatedX = (int) Math.round(offscreen + (target.x() - offscreen) * card.animationProgress());
                var animated = new Rectangle(animatedX, target.y(), CARD_WIDTH, CARD_HEIGHT);
                var hovered = renderCard(graphics, card, animated, mouseX, mouseY, interactive, hits);
                if (hovered != null) hoveredRosterMember = hovered;
            }
            if (hoveredRosterMember != null) {
                graphics.renderTooltip(
                        minecraft.font,
                        List.of(ClientTooltipComponent.create(hoveredRosterMember.getVisualOrderText())),
                        mouseX,
                        mouseY,
                        DefaultTooltipPositioner.INSTANCE,
                        null
                );
            }
            hitRegions = interactive ? List.copyOf(hits) : List.of();
        }
    }

    static List<Rectangle> stackBounds(Rectangle anchor, int count, int viewportWidth, int viewportHeight) {
        if (count <= 0) return List.of();
        boolean upward = anchor.y() + anchor.height() / 2 >= viewportHeight / 2;
        var result = new ArrayList<Rectangle>();
        for (int index = 0; index < count; index++) {
            int y = anchor.y() + (upward ? -1 : 1) * index * (CARD_HEIGHT + STACK_GAP);
            result.add(new Rectangle(anchor.x(), y, CARD_WIDTH, CARD_HEIGHT));
        }
        int minimumTop = result.stream().mapToInt(Rectangle::y).min().orElse(0);
        int maximumBottom = result.stream().mapToInt(Rectangle::bottom).max().orElse(viewportHeight);
        int shift = minimumTop < 0 ? -minimumTop : maximumBottom > viewportHeight ? viewportHeight - maximumBottom : 0;
        if (shift == 0) return List.copyOf(result);
        return result.stream().map(bounds ->
                new Rectangle(bounds.x(), bounds.y() + shift, bounds.width(), bounds.height())).toList();
    }

    private Component renderCard(GuiGraphics graphics, LfgNotificationManager.CardSnapshot card,
                                 Rectangle bounds, int mouseX, int mouseY, boolean interactive,
                                 List<HitRegion> hits) {
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                org.kingdomfoxes.ralle.ui.theme.RallePalette.notificationSurface());
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                org.kingdomfoxes.ralle.ui.theme.RallePalette.notificationOutline());

        var lobby = card.lobby();
        graphics.renderItem(raidPreviews.computeIfAbsent(lobby.raidType(),
                raid -> new ItemStack(RaidPresentation.item(raid))), bounds.x() + 7, bounds.y() + 6);
        var close = closeBounds(bounds);
        int titleWidth = Math.max(20, close.x() - (bounds.x() + 28) - 6);
        graphics.drawString(minecraft.font,
                RalleTypography.body(Component.literal(ellipsize(RaidPresentation.name(lobby.raidType()), titleWidth))),
                bounds.x() + 28, bounds.y() + 10, org.kingdomfoxes.ralle.ui.theme.RallePalette.accentArgb(), false);

        drawButtonFace(graphics, close, RalleButtonRenderers.Kind.DESTRUCTIVE, mouseX, mouseY, true);
        drawCloseX(graphics, close, org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText());
        if (interactive && card.mode() != LfgNotificationManager.CardMode.EXITING) {
            hits.add(new HitRegion(close, lobby.lobbyId(), Action.CLOSE));
        }

        String regionText = "[" + lobby.region().name() + "]";
        var renderedRegion = RalleTypography.body(Component.literal(regionText));
        int detailY = bounds.y() + 29;
        graphics.drawString(minecraft.font, renderedRegion, bounds.x() + 8, detailY,
                regionColor(lobby.region()), false);
        int noteX = bounds.x() + 8 + minecraft.font.width(renderedRegion) + 4;
        String note = lobby.note() == null ? "No note" : LfgNoteText.sanitizeForDisplay(lobby.note());
        graphics.drawString(minecraft.font,
                RalleTypography.body(Component.literal(ellipsize(note, bounds.right() - 8 - noteX))),
                noteX, detailY, org.kingdomfoxes.ralle.ui.theme.RallePalette.secondaryText(), false);

        Component hoveredRosterMember = null;
        var viewer = service.store().state().viewer();
        boolean showElapsedTimer = card.presentedMode() == LfgNotificationManager.CardMode.TIMED_OUT
                || viewer != null && lobby.hostedBy(viewer.minecraftUuid());
        for (int slot = 0; slot < 4; slot++) {
            int slotX = showElapsedTimer ? timerRosterSlotX(bounds, slot) : rosterSlotX(bounds, slot);
            var slotBounds = new Rectangle(slotX, bounds.y() + 42, 20, 20);
            renderRosterSlot(graphics, lobby, slot, slotBounds);
            if (interactive && slot < lobby.members().size() && slotBounds.contains(mouseX, mouseY)) {
                hoveredRosterMember = rosterTooltip(lobby.members().get(slot));
            }
        }
        if (showElapsedTimer) {
            timers.computeIfAbsent(lobby.lobbyId(), ignored -> new LfgElapsedTimerComponent.TextCache()).drawCentered(
                    graphics, minecraft.font, bounds.x() + bounds.width() / 2, bounds.y() + 46,
                    lobby.createdAt(), timerNow(card, java.time.Instant.now()));
        }

        var controls = new Rectangle(bounds.x() + 8, bounds.y() + 72, bounds.width() - 16, 20);
        boolean controlsInteractive = interactive && card.mode() != LfgNotificationManager.CardMode.EXITING;
        renderControls(graphics, card, controls, mouseX, mouseY, controlsInteractive, hits);
        return hoveredRosterMember;
    }

    private void renderRosterSlot(GuiGraphics graphics, LfgProtocol.Lobby lobby, int slot, Rectangle bounds) {
        if (slot >= lobby.members().size()) {
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFFFFFFFF);
            graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
                    org.kingdomfoxes.ralle.ui.theme.RallePalette.slotSurface());
            graphics.drawCenteredString(minecraft.font, Component.literal("+"),
                    bounds.x() + bounds.width() / 2, bounds.y() + 6,
                    org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText());
            return;
        }
        var member = lobby.members().get(slot);
        PlayerHeadPresentation.draw(graphics, minecraft, member,
                bounds.x(), bounds.y(), 18, rosterBorderColor(member));
    }

    private void renderControls(GuiGraphics graphics, LfgNotificationManager.CardSnapshot card,
                                Rectangle controls, int mouseX, int mouseY, boolean interactive,
                                List<HitRegion> hits) {
        var id = card.lobby().lobbyId();
        if (card.presentedMode() == LfgNotificationManager.CardMode.TIMED_OUT) {
            drawButton(graphics, controls, "Timed out!", RalleButtonRenderers.Kind.NEUTRAL, -1, -1, true);
            return;
        }
        var viewer = service.store().state().viewer();
        String disbandPrompt = disbandConfirmation.promptFor(id);
        boolean partyFilledControl = showsPartyFilledControl(
                card.lobby(),
                card.persistent(),
                viewer == null ? null : viewer.minecraftUuid(),
                hostPartyInvites.hasCardOffer(id));
        if (partyFilledControl) {
            var split = splitPartyControls(controls);
            String partyFilledLabel = fittingHint(
                    keybindHints.partyFilledLabel("Party filled"), "Party filled", split.left());
            drawButton(graphics, split.left(), partyFilledLabel,
                    RalleButtonRenderers.Kind.PRIMARY, mouseX, mouseY, true);
            if (interactive) hits.add(new HitRegion(split.left(), id, Action.INVITE_ALL));

            boolean pending = service.pending(id, "disband");
            String disbandLabel = pending ? "Disbanding" : disbandPrompt == null
                    ? fittingHint(keybindHints.leaveDisbandLabel("Disband"), "Disband", split.right())
                    : "Confirm";
            drawButton(graphics, split.right(), disbandLabel,
                    RalleButtonRenderers.Kind.DESTRUCTIVE, mouseX, mouseY, !pending);
            if (interactive && !pending) hits.add(new HitRegion(split.right(), id, Action.DISBAND));
            return;
        }
        if (card.persistent() && viewer != null && card.lobby().hostedBy(viewer.minecraftUuid())
                && disbandPrompt != null) {
            drawButton(graphics, controls, disbandPrompt, RalleButtonRenderers.Kind.DESTRUCTIVE,
                    mouseX, mouseY, true);
            if (interactive) hits.add(new HitRegion(controls, id, Action.DISBAND));
            return;
        }
        if (card.persistent() && viewerBelongsTo(card.lobby())) {
            boolean host = viewer != null && card.lobby().hostedBy(viewer.minecraftUuid());
            String action = host ? "Disband" : "Leave";
            String protocolAction = host ? "disband" : "leave";
            boolean pending = service.pending(id, protocolAction);
            String label = pending ? (host ? "Disbanding..." : "Leaving...") : action;
            if (!pending) label = keybindHints.leaveDisbandLabel(label);
            drawButton(graphics, controls, label,
                    RalleButtonRenderers.Kind.DESTRUCTIVE, mouseX, mouseY, !pending);
            if (interactive && !pending) {
                hits.add(new HitRegion(controls, id, host ? Action.DISBAND : Action.LEAVE));
            }
            return;
        }
        if (card.persistent() && card.presentedMode() == LfgNotificationManager.CardMode.READY
                && !joinAvailable(card.lobby())) {
            renderPersistentStatus(graphics, controls, id, persistentStatus(card.lobby()),
                    mouseX, mouseY, interactive, hits);
            return;
        }
        switch (card.presentedMode()) {
            case READY -> {
                drawButton(graphics, controls, keybindHints.joinLabel("Join"),
                        RalleButtonRenderers.Kind.PRIMARY,
                        mouseX, mouseY, true);
                if (interactive) hits.add(new HitRegion(controls, id, Action.JOIN));
            }
            case COUNTDOWN -> {
                int leftWidth = controls.width() / 2;
                var countdown = new Rectangle(controls.x(), controls.y(), leftWidth - 2, controls.height());
                var cancel = new Rectangle(controls.x() + leftWidth + 2, controls.y(),
                        controls.width() - leftWidth - 2, controls.height());
                drawButtonFace(graphics, countdown, RalleButtonRenderers.Kind.PRIMARY, -1, -1, true);
                int progressWidth = RalleButtonRenderers.countdownOverlayWidth(
                        Math.max(0, countdown.width() - 2), card.countdownFraction());
                if (progressWidth > 0) {
                    graphics.fill(countdown.x(), countdown.y() + 1,
                            countdown.x() + progressWidth, countdown.bottom() - 2, 0x553CCB5A);
                }
                graphics.drawCenteredString(minecraft.font, RalleTypography.body(
                        Component.literal(Integer.toString(card.countdownSeconds()))),
                        countdown.x() + countdown.width() / 2, countdown.y() + 6,
                        org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText());
                drawButton(graphics, cancel, "Cancel", RalleButtonRenderers.Kind.DESTRUCTIVE,
                        mouseX, mouseY, true);
                if (interactive) hits.add(new HitRegion(cancel, id, Action.CANCEL));
            }
            case SUBMITTING -> drawButton(graphics, controls, "Joining...",
                    RalleButtonRenderers.Kind.NEUTRAL, -1, -1, false);
            case JOINED -> drawButton(graphics, controls, "Joined",
                    RalleButtonRenderers.Kind.PRIMARY, -1, -1, false);
            case FILLED_SUCCESS -> drawButton(graphics, controls, "Party filled",
                    RalleButtonRenderers.Kind.PRIMARY, -1, -1, true);
            case FILLED_RACE, FAILURE, UNAVAILABLE -> {
                if (card.persistent()) {
                    renderPersistentStatus(graphics, controls, id,
                            card.feedbackText() == null ? "Party unavailable" : card.feedbackText(),
                            mouseX, mouseY, interactive, hits);
                    return;
                }
                    drawButton(graphics, controls,
                            card.feedbackText() == null ? "Party unavailable" : card.feedbackText(),
                            RalleButtonRenderers.Kind.DESTRUCTIVE, -1, -1, true);
            }
            case EXITING, REMOVED -> {}
        }
    }

    private void renderPersistentStatus(GuiGraphics graphics, Rectangle controls, UUID lobbyId,
                                        String status, int mouseX, int mouseY, boolean interactive,
                                        List<HitRegion> hits) {
        drawButton(graphics, controls, status, RalleButtonRenderers.Kind.NEUTRAL, -1, -1, false);
    }

    private boolean viewerBelongsTo(LfgProtocol.Lobby lobby) {
        var viewer = service.store().state().viewer();
        return viewer != null && lobby.contains(viewer.minecraftUuid());
    }

    private boolean joinAvailable(LfgProtocol.Lobby lobby) {
        return !lobby.locked()
                && lobby.status() == LfgProtocol.LobbyStatus.OPEN
                && lobby.members().size() < lobby.capacity()
                && lobby.capabilities() != null
                && lobby.capabilities().join();
    }

    private static String persistentStatus(LfgProtocol.Lobby lobby) {
        if (lobby.status() == LfgProtocol.LobbyStatus.IN_RAID) return "In raid";
        if (lobby.locked()) return "Locked";
        if (lobby.members().size() >= lobby.capacity()) return "Party filled";
        return "Unavailable";
    }

    static boolean showsPartyFilledControl(LfgProtocol.Lobby lobby, boolean persistent,
                                           UUID viewerId, boolean inviteOffer) {
        return persistent
                && inviteOffer
                && viewerId != null
                && lobby.hostedBy(viewerId)
                && lobby.members().size() >= lobby.capacity();
    }

    static SplitControls splitPartyControls(Rectangle controls) {
        int gap = 4;
        int leftWidth = Math.round((controls.width() - gap) * 0.6f);
        return new SplitControls(
                new Rectangle(controls.x(), controls.y(), leftWidth, controls.height()),
                new Rectangle(controls.x() + leftWidth + gap, controls.y(),
                        controls.width() - leftWidth - gap, controls.height())
        );
    }

    private void drawButton(GuiGraphics graphics, Rectangle bounds, String label,
                            RalleButtonRenderers.Kind kind, int mouseX, int mouseY, boolean active) {
        drawButton(graphics, bounds, RalleTypography.body(Component.literal(label)),
                kind, mouseX, mouseY, active);
    }

    private String fittingHint(String hinted, String plain, Rectangle bounds) {
        return minecraft.font.width(RalleTypography.body(Component.literal(hinted))) <= bounds.width() - 6
                ? hinted : plain;
    }

    private void drawButton(GuiGraphics graphics, Rectangle bounds, Component label,
                            RalleButtonRenderers.Kind kind, int mouseX, int mouseY, boolean active) {
        drawButtonFace(graphics, bounds, kind, mouseX, mouseY, active);
        graphics.drawCenteredString(minecraft.font, label,
                bounds.x() + bounds.width() / 2, bounds.y() + 6,
                active ? org.kingdomfoxes.ralle.ui.theme.RallePalette.primaryText() : 0xFF8D96A5);
    }

    private static void drawButtonFace(GuiGraphics graphics, Rectangle bounds,
                                       RalleButtonRenderers.Kind kind,
                                       int mouseX, int mouseY, boolean active) {
        RalleButtonRenderers.drawRaw(
                graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                kind, bounds.contains(mouseX, mouseY), active
        );
    }

    static Rectangle closeBounds(Rectangle card) {
        return new Rectangle(card.right() - 25, card.y() + 4, 20, 20);
    }

    private static void drawCloseX(GuiGraphics graphics, Rectangle bounds, int color) {
        RalleButtonRenderers.drawPixelX(
                graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    static Rectangle closeXBounds(Rectangle closeButton) {
        return new Rectangle(
                closeButton.x() + (closeButton.width() - 10) / 2 - 1,
                closeButton.y() + (closeButton.height() - 10) / 2 - 1,
                10,
                10
        );
    }

    static int rosterSlotX(Rectangle bounds, int slot) {
        if (slot < 0 || slot >= 4) throw new IllegalArgumentException("Roster slot must be between 0 and 3");
        int usableWidth = bounds.width() - 16;
        int travel = usableWidth - 20;
        return bounds.x() + 8 + Math.round(slot * travel / 3f);
    }

    static int timerRosterSlotX(Rectangle bounds, int slot) {
        if (slot < 0 || slot >= 4) throw new IllegalArgumentException("Roster slot must be between 0 and 3");
        int offset = switch (slot) {
            case 0 -> 8;
            case 1 -> 52;
            case 2 -> bounds.width() - 72;
            case 3 -> bounds.width() - 28;
            default -> throw new AssertionError("Validated roster slot");
        };
        return bounds.x() + offset;
    }

    static Component rosterTooltip(LfgProtocol.Member member) {
        return RalleTheme.ui(Component.literal(
                "[" + member.guild().tag() + "] " + member.ign()));
    }

    static int rosterBorderColor(LfgProtocol.Member member) {
        return GuildTerritoryColors.forGuild(member.guild().tag(), member.guild().color());
    }

    static java.time.Instant timerNow(LfgNotificationManager.CardSnapshot card, java.time.Instant now) {
        return card.presentedMode() == LfgNotificationManager.CardMode.TIMED_OUT
                ? card.lobby().createdAt().plusSeconds(30 * 60) : now;
    }

    static int regionColor(LfgProtocol.Region region) {
        return switch (region) {
            case EU -> REGION_GOOD;
            case NA -> REGION_MODERATE;
            case AS -> REGION_POOR;
        };
    }

    private boolean handleClick(double x, double y, int button) {
        if (button != 0) return false;
        for (var hit : hitRegions) {
            if (!hit.bounds().contains(x, y)) continue;
            switch (hit.action()) {
                case JOIN -> notifications.join(hit.lobbyId());
                case CANCEL -> notifications.cancel(hit.lobbyId());
                case CLOSE -> notifications.close(hit.lobbyId());
                case LEAVE -> service.leave(hit.lobbyId()).whenComplete((ignored, failure) -> {
                    if (failure == null) minecraft.execute(sounds::playPartyLeft);
                });
                case DISBAND -> {
                    var lobby = service.store().state().lobbies().get(hit.lobbyId());
                    if (lobby != null && disbandConfirmation.request(
                            lobby.lobbyId(), lobby.revision(), "Click again to disband")
                            == LfgDisbandConfirmation.Result.CONFIRMED) {
                        service.disband(lobby.lobbyId()).whenComplete((ignored, failure) -> {
                            if (failure == null) minecraft.execute(sounds::playPartyLeft);
                        });
                    }
                }
                case INVITE_ALL -> {
                    if (hostPartyInvites.inviteAll(hit.lobbyId())) {
                        notifications.close(hit.lobbyId());
                    }
                }
            }
            return true;
        }
        return false;
    }

    private String ellipsize(String text, int maximumWidth) {
        if (minecraft.font.width(RalleTypography.body(Component.literal(text))) <= maximumWidth) return text;
        var candidate = text;
        while (!candidate.isEmpty()
                && minecraft.font.width(RalleTypography.body(Component.literal(candidate + "..."))) > maximumWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + "...";
    }

    private enum Action { JOIN, CANCEL, CLOSE, LEAVE, DISBAND, INVITE_ALL }

    record SplitControls(Rectangle left, Rectangle right) {}

    private record HitRegion(Rectangle bounds, UUID lobbyId, Action action) {}
}

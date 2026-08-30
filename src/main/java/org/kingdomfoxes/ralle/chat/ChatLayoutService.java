package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.PlacementPolicy;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.SideAnchor;
import org.kingdomfoxes.ralle.api.hud.RalleHudElements;

import java.util.Map;
import java.util.Optional;

public final class ChatLayoutService {
    public static final String CHAT_ELEMENT_ID = RalleHudElements.CHAT;
    public static final int MINIMUM_WIDTH = RalleHudElements.CHAT_MINIMUM_WIDTH;
    public static final int MINIMUM_HEIGHT = RalleHudElements.CHAT_MINIMUM_HEIGHT;
    private static final int VANILLA_BOTTOM_MARGIN = 40;

    private final Minecraft minecraft;
    private final HudPlacementRegistry placements;
    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;
    private int lastCustomWidth = -1;

    public ChatLayoutService(Minecraft minecraft, HudPlacementRegistry placements) {
        this.minecraft = minecraft;
        this.placements = placements;
    }

    public Rectangle editorBounds(int viewportWidth, int viewportHeight) {
        return editorBounds(CHAT_ELEMENT_ID, viewportWidth, viewportHeight);
    }

    public Rectangle editorBounds(String elementId, int viewportWidth, int viewportHeight) {
        if (RalleHudElements.LFG_NOTIFICATIONS.equals(elementId)) {
            return placements.resolveSideAnchored(
                    elementId, viewportWidth, viewportHeight, SideAnchor.RIGHT,
                    Math.max(0, viewportHeight - RalleHudElements.LFG_NOTIFICATION_HEIGHT - 8)
            );
        }
        if (RalleHudElements.LFG_ACTION_BAR.equals(elementId)) {
            return new Rectangle(
                    Math.max(0, (viewportWidth - RalleHudElements.LFG_ACTION_BAR_WIDTH) / 2),
                    Math.max(0, viewportHeight / 2 - 38),
                    Math.min(viewportWidth, RalleHudElements.LFG_ACTION_BAR_WIDTH),
                    Math.min(viewportHeight, RalleHudElements.LFG_ACTION_BAR_HEIGHT)
            );
        }
        var contentBounds = placements.resolve(
                CHAT_ELEMENT_ID,
                viewportWidth,
                viewportHeight,
                vanillaBounds(viewportWidth, viewportHeight, true)
        );
        return renderedEditorBounds(contentBounds, viewportWidth);
    }

    public void saveEditorBounds(Rectangle rectangle, int viewportWidth, int viewportHeight) {
        saveEditorBounds(CHAT_ELEMENT_ID, rectangle, viewportWidth, viewportHeight);
    }

    public void saveEditorBounds(String elementId, Rectangle rectangle, int viewportWidth, int viewportHeight) {
        var savedBounds = CHAT_ELEMENT_ID.equals(elementId)
                ? contentBounds(rectangle)
                : rectangle;
        placements.setPixels(elementId, savedBounds, viewportWidth, viewportHeight);
        if (CHAT_ELEMENT_ID.equals(elementId)) rescaleChat();
    }

    public boolean hasCustomEditorBounds() {
        return hasCustomEditorBounds(CHAT_ELEMENT_ID);
    }

    public boolean hasCustomEditorBounds(String elementId) {
        return placements.hasCustomPlacement(elementId);
    }

    public Map<String, Rectangle> otherEditorBounds(int viewportWidth, int viewportHeight) {
        return otherEditorBounds(CHAT_ELEMENT_ID, viewportWidth, viewportHeight);
    }

    public Map<String, Rectangle> otherEditorBounds(String selectedId, int viewportWidth, int viewportHeight) {
        var allBounds = new java.util.LinkedHashMap<String, Rectangle>();
        allBounds.put(CHAT_ELEMENT_ID, editorBounds(CHAT_ELEMENT_ID, viewportWidth, viewportHeight));
        allBounds.put(RalleHudElements.LFG_NOTIFICATIONS,
                editorBounds(RalleHudElements.LFG_NOTIFICATIONS, viewportWidth, viewportHeight));
        allBounds.put(RalleHudElements.LFG_ACTION_BAR,
                editorBounds(RalleHudElements.LFG_ACTION_BAR, viewportWidth, viewportHeight));
        allBounds.remove(selectedId);
        return Map.copyOf(allBounds);
    }

    public Rectangle resetEditorBounds(int viewportWidth, int viewportHeight) {
        return resetEditorBounds(CHAT_ELEMENT_ID, viewportWidth, viewportHeight);
    }

    public Rectangle resetEditorBounds(String elementId, int viewportWidth, int viewportHeight) {
        placements.reset(elementId);
        if (RalleHudElements.LFG_NOTIFICATIONS.equals(elementId)) {
            return editorBounds(elementId, viewportWidth, viewportHeight);
        }
        rescaleChat();
        return renderedEditorBounds(vanillaBounds(viewportWidth, viewportHeight, true), viewportWidth)
                .clampTo(viewportWidth, viewportHeight, minimumWidth(elementId), minimumHeight(elementId));
    }

    public PlacementPolicy placementPolicy(String elementId) {
        return placements.definition(elementId).placementPolicy();
    }

    public int minimumWidth(String elementId) {
        int minimumWidth = placements.definition(elementId).minimumWidth();
        return CHAT_ELEMENT_ID.equals(elementId)
                ? ChatBoxGeometry.renderedWidth(minimumWidth, chatScale())
                : minimumWidth;
    }

    public int minimumHeight(String elementId) {
        return placements.definition(elementId).minimumHeight();
    }

    public Optional<Rectangle> activeCustomBounds() {
        var window = minecraft.getWindow();
        return placements.resolveCustom(
                CHAT_ELEMENT_ID,
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight()
        );
    }

    public int customUnscaledHeight(Rectangle bounds) {
        return Math.max(1, (int) Math.ceil(bounds.height() / chatScale()));
    }

    public int renderedChatWidth(int contentWidth) {
        return ChatBoxGeometry.renderedWidth(contentWidth, chatScale());
    }

    public int customRenderCanvasHeight(Rectangle bounds) {
        return bounds.bottom() + VANILLA_BOTTOM_MARGIN;
    }

    public void tick() {
        var window = minecraft.getWindow();
        var viewportWidth = window.getGuiScaledWidth();
        var viewportHeight = window.getGuiScaledHeight();
        var customWidth = activeCustomBounds().map(Rectangle::width).orElse(-1);

        if (lastViewportWidth != -1
                && (viewportWidth != lastViewportWidth || viewportHeight != lastViewportHeight || customWidth != lastCustomWidth)
                && customWidth != -1) {
            rescaleChat();
        }

        lastViewportWidth = viewportWidth;
        lastViewportHeight = viewportHeight;
        lastCustomWidth = customWidth;
    }

    private Rectangle vanillaBounds(int viewportWidth, int viewportHeight, boolean focused) {
        var width = ChatComponent.getWidth(minecraft.options.chatWidth().get());
        var heightOption = focused
                ? minecraft.options.chatHeightFocused().get()
                : minecraft.options.chatHeightUnfocused().get();
        var unscaledHeight = ChatComponent.getHeight(heightOption);
        var visualHeight = Math.max(1, (int) Math.floor(unscaledHeight * minecraft.options.chatScale().get()));
        var bottom = Math.max(1, viewportHeight - VANILLA_BOTTOM_MARGIN);
        return new Rectangle(0, Math.max(0, bottom - visualHeight), Math.max(1, width), visualHeight);
    }

    private Rectangle renderedEditorBounds(Rectangle contentBounds, int viewportWidth) {
        int renderedWidth = renderedChatWidth(contentBounds.width());
        int visibleWidth = Math.min(renderedWidth, Math.max(1, viewportWidth - contentBounds.x()));
        return new Rectangle(contentBounds.x(), contentBounds.y(), visibleWidth, contentBounds.height());
    }

    private Rectangle contentBounds(Rectangle editorBounds) {
        int contentWidth = ChatBoxGeometry.chatWidthForRenderedWidth(editorBounds.width(), chatScale());
        return new Rectangle(editorBounds.x(), editorBounds.y(), contentWidth, editorBounds.height());
    }

    private double chatScale() {
        return Math.max(0.01, minecraft.options.chatScale().get());
    }

    private void rescaleChat() {
        if (minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
    }
}

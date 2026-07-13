package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry;
import org.kingdomfoxes.ralle.api.hud.HudPlacementRegistry.Rectangle;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

import java.util.Optional;

public final class ChatLayoutService {
    public static final String CHAT_ELEMENT_ID = "chat";
    public static final int MINIMUM_WIDTH = 120;
    public static final int MINIMUM_HEIGHT = 45;
    private static final int VANILLA_BOTTOM_MARGIN = 40;

    private final Minecraft minecraft;
    private final HudPlacementRegistry placements;
    private final BooleanSetting chatEnabled;
    private int lastViewportWidth = -1;
    private int lastViewportHeight = -1;
    private int lastCustomWidth = -1;

    public ChatLayoutService(Minecraft minecraft, SettingsRegistry settings, HudPlacementRegistry placements) {
        this.minecraft = minecraft;
        this.placements = placements;
        this.chatEnabled = settings.setting("chat-enabled", BooleanSetting.class);
    }

    public Rectangle editorBounds(int viewportWidth, int viewportHeight) {
        return placements.resolve(
                CHAT_ELEMENT_ID,
                viewportWidth,
                viewportHeight,
                vanillaBounds(viewportWidth, viewportHeight, true)
        );
    }

    public void saveEditorBounds(Rectangle rectangle, int viewportWidth, int viewportHeight) {
        placements.setPixels(CHAT_ELEMENT_ID, rectangle, viewportWidth, viewportHeight);
        rescaleChat();
    }

    public Rectangle resetEditorBounds(int viewportWidth, int viewportHeight) {
        placements.reset(CHAT_ELEMENT_ID);
        rescaleChat();
        return vanillaBounds(viewportWidth, viewportHeight, true)
                .clampTo(viewportWidth, viewportHeight, MINIMUM_WIDTH, MINIMUM_HEIGHT);
    }

    public Optional<Rectangle> activeCustomBounds() {
        if (!chatEnabled.value()) return Optional.empty();

        var window = minecraft.getWindow();
        return placements.resolveCustom(
                CHAT_ELEMENT_ID,
                window.getGuiScaledWidth(),
                window.getGuiScaledHeight()
        );
    }

    public int customUnscaledHeight(Rectangle bounds) {
        var scale = Math.max(0.01, minecraft.options.chatScale().get());
        return Math.max(1, (int) Math.ceil(bounds.height() / scale));
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

    private void rescaleChat() {
        if (minecraft.gui != null) minecraft.gui.getChat().rescaleChat();
    }
}

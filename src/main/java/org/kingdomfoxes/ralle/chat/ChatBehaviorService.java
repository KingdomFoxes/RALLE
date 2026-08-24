package org.kingdomfoxes.ralle.chat;

import net.minecraft.client.Minecraft;
import org.kingdomfoxes.ralle.api.settings.BooleanSetting;
import org.kingdomfoxes.ralle.api.settings.ChoiceSetting;
import org.kingdomfoxes.ralle.api.settings.SettingsRegistry;

public final class ChatBehaviorService {
    public static final int DEFAULT_COMPACT_WINDOW_SECONDS = 45;
    public static final int DEFAULT_COMPACT_WINDOW_TICKS = DEFAULT_COMPACT_WINDOW_SECONDS * 20;

    private final Minecraft minecraft;
    private final BooleanSetting hideChatScrollbar;
    private final BooleanSetting removeChatSystemIndicators;
    private final BooleanSetting compactChat;
    private final BooleanSetting stackEmptyLines;
    private final BooleanSetting messageDirectionEnabled;
    private final ChoiceSetting messageDirection;
    private final BooleanSetting horizontalAlignmentEnabled;
    private final ChoiceSetting horizontalAlignment;
    private final BooleanSetting textShadowEnabled;
    private final ChoiceSetting textShadow;
    private RenderedMessageSettings previousRenderedMessageSettings;

    public ChatBehaviorService(Minecraft minecraft, SettingsRegistry settings) {
        this.minecraft = minecraft;
        this.hideChatScrollbar = settings.setting("hide-chat-scrollbar", BooleanSetting.class);
        this.removeChatSystemIndicators = settings.setting("remove-chat-system-indicators", BooleanSetting.class);
        this.compactChat = settings.setting("compact-chat", BooleanSetting.class);
        this.stackEmptyLines = settings.setting("stack-empty-lines", BooleanSetting.class);
        this.messageDirectionEnabled = settings.setting("message-direction-enabled", BooleanSetting.class);
        this.messageDirection = settings.setting("message-direction", ChoiceSetting.class);
        this.horizontalAlignmentEnabled = settings.setting("horizontal-alignment-enabled", BooleanSetting.class);
        this.horizontalAlignment = settings.setting("horizontal-alignment", ChoiceSetting.class);
        this.textShadowEnabled = settings.setting("text-shadow-enabled", BooleanSetting.class);
        this.textShadow = settings.setting("text-shadow", ChoiceSetting.class);
        this.previousRenderedMessageSettings = renderedMessageSettings();
    }

    public boolean projectionEnabled() {
        return compactChatEnabled() || stackEmptyLinesEnabled();
    }

    public boolean hideChatScrollbar() {
        return hideChatScrollbar.value();
    }

    public boolean removeChatSystemIndicators() {
        return removeChatSystemIndicators.value();
    }

    public boolean compactChatEnabled() {
        return compactChat.value();
    }

    public boolean stackEmptyLinesEnabled() {
        return stackEmptyLines.value();
    }

    public MessageDirection messageDirection() {
        if (!messageDirectionEnabled.value()) return MessageDirection.BOTTOM_UP;
        return "top-down".equals(messageDirection.value()) ? MessageDirection.TOP_DOWN : MessageDirection.BOTTOM_UP;
    }

    public HorizontalAlignment horizontalAlignment() {
        if (!horizontalAlignmentEnabled.value()) return HorizontalAlignment.LEFT;
        return "right".equals(horizontalAlignment.value()) ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT;
    }

    public TextShadow textShadow() {
        if (!textShadowEnabled.value()) return TextShadow.VANILLA;
        return parseTextShadow(textShadow.value());
    }

    static TextShadow parseTextShadow(String value) {
        return switch (value) {
            case "none" -> TextShadow.NONE;
            // "full" was the original persisted value for RALLE's opaque offset shadow.
            case "full" -> TextShadow.PARTIAL_FULL;
            case "wrapped-full" -> TextShadow.FULL;
            default -> TextShadow.VANILLA;
        };
    }

    public void tick() {
        var current = renderedMessageSettings();
        if (!current.equals(previousRenderedMessageSettings) && minecraft.gui != null) {
            minecraft.gui.getChat().rescaleChat();
        }
        previousRenderedMessageSettings = current;
    }

    private RenderedMessageSettings renderedMessageSettings() {
        return new RenderedMessageSettings(
                compactChat.value(),
                stackEmptyLines.value(),
                removeChatSystemIndicators.value()
        );
    }

    public enum MessageDirection {
        BOTTOM_UP,
        TOP_DOWN
    }

    public enum HorizontalAlignment {
        LEFT,
        RIGHT
    }

    public enum TextShadow {
        NONE,
        VANILLA,
        PARTIAL_FULL,
        FULL
    }

    private record RenderedMessageSettings(
            boolean compactChat,
            boolean stackEmptyLines,
            boolean removeChatSystemIndicators
    ) {}
}

package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;

/** Client-rendered age of a lobby owned by the synchronized viewer. */
final class LfgElapsedTimerComponent extends BaseUIComponent {
    // The browser reserves the compact m:ss width; the dynamic text may grow into its
    // left-side spacer after one hour without pushing the count or host action out of the card.
    static final int RESERVED_WIDTH = 30;
    static final int HEIGHT = 13;
    static final int GREEN = 0xFF00FF55;
    static final int YELLOW = 0xFFFFFF00;
    static final int RED = 0xFFFF3333;

    private final Instant createdAt;

    LfgElapsedTimerComponent(Instant createdAt) {
        this.createdAt = createdAt;
        sizing(Sizing.fixed(RESERVED_WIDTH), Sizing.fixed(HEIGHT));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        drawCentered(graphics, Minecraft.getInstance().font, x + width / 2, y, createdAt, Instant.now());
    }

    static void drawCentered(GuiGraphics graphics, Font font, int centerX, int top,
                             Instant createdAt, Instant now) {
        var display = display(createdAt, now);
        var text = RalleTypography.body(Component.literal(display.text()));
        graphics.drawString(font, text, centerX - font.width(text) / 2, top + 2, display.color(), false);
    }

    static Display display(Instant createdAt, Instant now) {
        long seconds = createdAt == null || now == null
                ? 0L
                : Math.max(0L, Duration.between(createdAt, now).getSeconds());
        int color = seconds < 5 * 60L ? GREEN : seconds < 10 * 60L ? YELLOW : RED;
        return new Display(format(seconds), color);
    }

    private static String format(long seconds) {
        long hours = seconds / 3600L;
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours == 0L) return "%d:%02d".formatted(minutes, remainingSeconds);
        return "%d:%02d:%02d".formatted(hours, minutes % 60L, remainingSeconds);
    }

    record Display(String text, int color) {}
}

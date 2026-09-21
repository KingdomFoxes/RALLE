package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import net.minecraft.locale.Language;
import java.util.function.ToIntFunction;

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
    private final TextCache textCache = new TextCache();

    LfgElapsedTimerComponent(Instant createdAt) {
        this.createdAt = createdAt;
        sizing(Sizing.fixed(RESERVED_WIDTH), Sizing.fixed(HEIGHT));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        textCache.drawCentered(graphics, Minecraft.getInstance().font, x + width / 2, y, createdAt, Instant.now());
    }

    /** One entry per browser component or visible notification; never keyed globally by lobby. */
    static final class TextCache {
        private final ToIntFunction<Component> measure;
        private long seconds = -1;
        private boolean karla;
        private Object font;
        private Object language;
        private long resources;
        private RenderedText rendered;

        TextCache() { this(text -> Minecraft.getInstance().font.width(text)); }
        TextCache(ToIntFunction<Component> measure) { this.measure = measure; }

        void drawCentered(GuiGraphics graphics, Font font, int centerX, int top,
                          Instant createdAt, Instant now) {
            var value = resolve(elapsedSeconds(createdAt, now), RalleTypography.usesKarla(),
                    font, Language.getInstance(), RalleTypography.resourceVersion());
            graphics.drawString(font, value.text(), centerX - value.width() / 2, top + 2, value.color(), false);
        }

        RenderedText resolve(long elapsed, boolean usesKarla, Object fontIdentity,
                             Object languageIdentity, long resourceVersion) {
            if (rendered == null || seconds != elapsed || karla != usesKarla || font != fontIdentity
                    || language != languageIdentity || resources != resourceVersion) {
                var display = display(elapsed);
                var text = RalleTypography.body(Component.literal(display.text()));
                rendered = new RenderedText(text, measure.applyAsInt(text), display.color());
                seconds = elapsed;
                karla = usesKarla;
                font = fontIdentity;
                language = languageIdentity;
                resources = resourceVersion;
            }
            return rendered;
        }
    }

    record RenderedText(Component text, int width, int color) {}

    static Display display(Instant createdAt, Instant now) {
        return display(elapsedSeconds(createdAt, now));
    }

    static long elapsedSeconds(Instant createdAt, Instant now) {
        if (createdAt == null || now == null) return 0;
        long seconds = now.getEpochSecond() - createdAt.getEpochSecond();
        if (now.getNano() < createdAt.getNano()) seconds--;
        return Math.max(0, seconds);
    }

    private static Display display(long seconds) {
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

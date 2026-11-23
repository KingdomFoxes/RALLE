package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Bottom artwork for the Wheels settings page: unscaled flipped AndThen texture with the clickable eye below. */
final class WheelsAbyssFooterComponent extends BaseUIComponent {
    private static final Identifier AND_THEN_TEXTURE = Identifier.fromNamespaceAndPath(
            "ralle", "textures/gui/wheel/wheels_and_then.png");
    private static final Identifier EYE_TEXTURE = Identifier.fromNamespaceAndPath(
            "ralle", "textures/gui/wheel/wheels_eye.png");

    private final Runnable onEyeClicked;
    private boolean eyeHovered;

    WheelsAbyssFooterComponent(Runnable onEyeClicked) {
        this.onEyeClicked = Objects.requireNonNull(onEyeClicked, "onEyeClicked");
        sizing(Sizing.fill(100), Sizing.fixed(WheelsAbyssState.AND_THEN_HEIGHT));
    }

    @Override
    public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        // Plop the flipped AndThen image unscaled, stepping across 3 horizontal chunks every 0.25s.
        int chunk = WheelsAbyssState.andThenChunk(System.nanoTime() / 1_000_000L);
        int andThenX = WheelsAbyssState.andThenDrawX(x, width, SettingsScreenLayout.DOCUMENT_PADDING, chunk);
        int andThenY = y + SettingsScreenLayout.DOCUMENT_PADDING;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                AND_THEN_TEXTURE,
                andThenX,
                andThenY,
                0.0F,
                0.0F,
                WheelsAbyssState.AND_THEN_WIDTH,
                WheelsAbyssState.AND_THEN_HEIGHT,
                WheelsAbyssState.AND_THEN_WIDTH,
                WheelsAbyssState.AND_THEN_HEIGHT
        );

        var eye = WheelsAbyssState.eyeBounds(width, height);
        eyeHovered = eye.contains(mouseX - x, mouseY - y);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                EYE_TEXTURE,
                x + eye.left(),
                y + eye.top(),
                0.0F,
                0.0F,
                eye.width(),
                eye.height(),
                WheelsAbyssState.EYE_TEXTURE_WIDTH,
                WheelsAbyssState.EYE_TEXTURE_HEIGHT,
                WheelsAbyssState.EYE_TEXTURE_WIDTH,
                WheelsAbyssState.EYE_TEXTURE_HEIGHT
        );
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (click.button() == 0 && WheelsAbyssState.eyeBounds(width, height).contains(click.x(), click.y())) {
            onEyeClicked.run();
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override
    public CursorStyle cursorStyle() {
        return eyeHovered ? CursorStyle.HAND : CursorStyle.POINTER;
    }
}

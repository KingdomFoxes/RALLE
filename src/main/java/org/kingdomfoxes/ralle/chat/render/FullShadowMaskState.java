package org.kingdomfoxes.ralle.chat.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable frame state submitted when the first Full-shadow chat line is seen. */
public final class FullShadowMaskState implements PictureInPictureRenderState {
    private final Font font;
    private final Matrix3x2f pose;
    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;
    private final ScreenRectangle bounds;
    private final List<Line> lines = new ArrayList<>();

    FullShadowMaskState(Font font, Matrix3x2f pose, int x0, int y0, int x1, int y1) {
        this.font = Objects.requireNonNull(font, "font");
        this.pose = new Matrix3x2f(Objects.requireNonNull(pose, "pose"));
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(this.pose);
    }

    void addLine(FormattedCharSequence content, int x, int y, float opacity) {
        lines.add(new Line(Objects.requireNonNull(content, "content"), x, y, opacity));
    }

    Font font() { return font; }
    List<Line> lines() { return lines; }

    @Override public Matrix3x2f pose() { return pose; }
    @Override public int x0() { return x0; }
    @Override public int y0() { return y0; }
    @Override public int x1() { return x1; }
    @Override public int y1() { return y1; }
    @Override public float scale() { return 1.0F; }
    @Override public @Nullable ScreenRectangle scissorArea() { return null; }
    @Override public ScreenRectangle bounds() { return bounds; }

    record Line(FormattedCharSequence content, int x, int y, float opacity) {}
}

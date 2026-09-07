package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Shared compact palette, add, and destructive-X icon actions. */
public final class RalleIconButtons {
    private RalleIconButtons() {}

    public static RalleIconButton palette(Component accessibleLabel, Consumer<ButtonComponent> pressed) {
        return palette(accessibleLabel, null, pressed);
    }

    public static RalleIconButton labeledPalette(Component label, Consumer<ButtonComponent> pressed) {
        return palette(label, RalleTheme.ui(label), pressed);
    }

    private static RalleIconButton palette(Component accessibleLabel, Component visibleLabel, Consumer<ButtonComponent> pressed) {
        var button = base(accessibleLabel, pressed);
        button.renderer((graphics, control, delta) -> {
            RalleButtonRenderers.neutral().draw(graphics, control, delta);
            int[] colors = {0xFFFF5555, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF};
            for (int i = 0; i < colors.length; i++) {
                graphics.fill(control.getX() + 5 + i % 3 * 3, control.getY() + 6 + i / 3 * 3,
                        control.getX() + 8 + i % 3 * 3, control.getY() + 9 + i / 3 * 3, colors[i]);
            }
            if (visibleLabel != null) {
                graphics.drawString(Minecraft.getInstance().font, visibleLabel,
                        control.getX() + 20, control.getY() + 6,
                        control.active ? 0xFFFFFFFF : 0xFF8993A8);
            }
        });
        return button;
    }

    public static RalleIconButton add(Component accessibleLabel, Consumer<ButtonComponent> pressed) {
        var button = base(accessibleLabel, pressed);
        button.renderer((graphics, control, delta) -> {
            RalleButtonRenderers.neutral().draw(graphics, control, delta);
            // Center against the visible face, which excludes the two-pixel bottom/right depth.
            int cx = control.getX() + (control.getWidth() - 2) / 2;
            int cy = control.getY() + (control.getHeight() - 2) / 2;
            graphics.fill(cx - 4, cy, cx + 5, cy + 1, 0xFFFFFFFF);
            graphics.fill(cx, cy - 4, cx + 1, cy + 5, 0xFFFFFFFF);
        });
        return button;
    }

    public static RalleIconButton destructiveX(Component accessibleLabel, Consumer<ButtonComponent> pressed) {
        var button = base(accessibleLabel, pressed);
        button.renderer(RalleButtonRenderers.destructiveX());
        return button;
    }

    public static RalleIconButton disclosure(
            Component accessibleLabel,
            BooleanSupplier expanded,
            Consumer<ButtonComponent> pressed
    ) {
        var button = new RalleIconButton(RalleTheme.ui(accessibleLabel), pressed);
        button.sizing(Sizing.fixed(18), Sizing.fixed(20));
        button.renderer((graphics, control, delta) -> {
            RalleButtonRenderers.navigation(0).draw(graphics, control, delta);
            graphics.drawCenteredString(
                    Minecraft.getInstance().font,
                    Component.literal(expanded.getAsBoolean() ? "▼" : "▶"),
                    control.getX() + control.getWidth() / 2,
                    control.getY() + (control.getHeight() - 8) / 2,
                    0xFFFFFFFF
            );
        });
        return button;
    }

    private static RalleIconButton base(Component accessibleLabel, Consumer<ButtonComponent> pressed) {
        var button = new RalleIconButton(RalleTheme.ui(accessibleLabel), pressed);
        button.sizing(Sizing.fixed(20));
        return button;
    }
}

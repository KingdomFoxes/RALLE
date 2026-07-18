package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Left-aligned, Minecraft-font button used by the unboxed settings navigation list. */
final class NavigationButtonComponent extends ButtonComponent {
    NavigationButtonComponent(Component message, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
    }

    @Override
    public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderer().draw((OwoUIGraphics) graphics, this, delta);
        int color = active() ? 0xFFFFFFFF : 0xFFA0A0A0;
        graphics.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                getX() + 7,
                getY() + Math.max(0, (getHeight() - 8) / 2),
                color,
                false
        );
    }
}

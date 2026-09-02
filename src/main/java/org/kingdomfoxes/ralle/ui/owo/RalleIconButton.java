package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Icon-only button whose message remains available to narration but is never painted as text. */
final class RalleIconButton extends ButtonComponent {
    private BooleanSupplier visible = () -> true;

    RalleIconButton(Component accessibleLabel, Consumer<ButtonComponent> pressed) {
        super(accessibleLabel, pressed);
    }

    RalleIconButton visibleWhen(BooleanSupplier visible) {
        this.visible = visible;
        return this;
    }

    @Override public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (!visible.getAsBoolean() && !isFocused()) return;
        renderer.draw((OwoUIGraphics) graphics, this, delta);
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return visible.getAsBoolean() && super.isMouseOver(mouseX, mouseY);
    }
}

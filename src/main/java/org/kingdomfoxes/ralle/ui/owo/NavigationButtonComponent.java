package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Left-aligned button used by the unboxed settings navigation list. */
final class NavigationButtonComponent extends ButtonComponent {
    private static final Component REMOVED_SUBCATEGORY_PREFIX = Component.literal("| ");
    private final boolean subcategory;

    NavigationButtonComponent(Component message, boolean subcategory, Consumer<ButtonComponent> onPress) {
        super(message, onPress);
        this.subcategory = subcategory;
    }

    @Override
    public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderer().draw((OwoUIGraphics) graphics, this, delta);
        int color = active() ? 0xFFFFFFFF : 0xFFA0A0A0;
        var font = Minecraft.getInstance().font;
        int textInset = 7 + (subcategory ? font.width(REMOVED_SUBCATEGORY_PREFIX) : 0);
        graphics.drawString(
                font,
                getMessage(),
                getX() + textInset,
                getY() + Math.max(0, (getHeight() - 8) / 2),
                color,
                false
        );
    }
}

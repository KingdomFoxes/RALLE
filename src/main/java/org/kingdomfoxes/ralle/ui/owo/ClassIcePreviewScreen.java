package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.kingdomfoxes.ralle.classes.ClassSwitcher;

/** Local compositor preview: no server command, class selection or audio ownership. */
public final class ClassIcePreviewScreen extends Screen {
    private final ClassSwitcher switcher;
    public ClassIcePreviewScreen(ClassSwitcher switcher, int pixels, int style) {
        super(Component.translatable("ralle.classes.ice-preview", pixels, style));
        this.switcher = switcher;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected void renderBlurredBackground(GuiGraphics graphics) { graphics.blurBeforeThisStratum(); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var faces = SelectorWheelLayout.circles(SelectorWheelModel.ring(5, 42, 94, true));
        float scale = Math.min(1, Math.min(width / 220f, height / 260f));
        graphics.pose().pushMatrix();
        graphics.pose().translate(width / 2f, height / 2f);
        graphics.pose().scale(scale, scale);
        for (int i = 0; i < faces.size(); i++) SelectorWheelRenderer.drawFace(graphics, faces.get(i), 0, 0, i == 0);
        graphics.pose().popMatrix();
        graphics.drawCenteredString(font, RalleTypography.body(title), width / 2, 12, RalleTheme.accentArgb());
        graphics.drawCenteredString(font, RalleTypography.body(Component.translatable("ralle.classes.ice-preview-hint")), width / 2, height - 22, 0xFFFFFFFF);
    }
    public ClassWheelSnapshot captureForeground() {
        return new ClassWheelSnapshot(minecraft, graphics -> {
            renderMenuBackground(graphics);
            graphics.nextStratum();
            render(graphics, 0, 0, 0);
        });
    }
    @Override public boolean keyPressed(KeyEvent event) { if (event.key() == 256) onClose(); return true; }
    @Override public void onClose() { switcher.stopPreview(); }
    @Override public void removed() { switcher.stopPreview(); super.removed(); }
}

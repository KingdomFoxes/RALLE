package org.kingdomfoxes.ralle.ui.owo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.kingdomfoxes.ralle.RalleClient;
import org.kingdomfoxes.ralle.cosmetics.CosmeticAppearance;
import org.kingdomfoxes.ralle.cosmetics.LiquidMaterial;
import org.kingdomfoxes.ralle.cosmetics.NameplateStyle;

/** Fits the complete website recipe to a surface instead of cropping its upper-left corner. */
final class LiquidMaterialPresentation {
    private LiquidMaterialPresentation() {}

    static void plate(GuiGraphics graphics, NameplateStyle style, CosmeticAppearance appearance,
                      int x, int y, int width, int height) {
        if (appearance == null || width <= 0 || height <= 0) return;
        try {
            var texture = RalleClient.context().cosmeticTextures().texture(style, appearance.resolution(), System.nanoTime());
            int tw = LiquidMaterial.width(appearance.resolution()), th = LiquidMaterial.height(appearance.resolution());
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f,
                    width, height, tw, th, tw, th);
        } catch (RuntimeException unavailable) {
            // Cosmetic resources must never take down the LFG workflow.
            graphics.fill(x, y, x + width, y + height, 0xff000000 | style.shadow());
        }
        // One logical pixel, independent of the material's sampling resolution.
        graphics.fill(x, y, x + width, y + 1, 0xffffffff);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xffffffff);
        graphics.fill(x, y + 1, x + 1, y + height - 1, 0xffffffff);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, 0xffffffff);
    }
}


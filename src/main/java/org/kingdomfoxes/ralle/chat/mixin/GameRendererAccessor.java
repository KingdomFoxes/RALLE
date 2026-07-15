package org.kingdomfoxes.ralle.chat.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("guiRenderer") GuiRenderer ralle$getGuiRenderer();
    @Accessor("guiRenderState") GuiRenderState ralle$getGuiRenderState();
    @Accessor("fogRenderer") FogRenderer ralle$getFogRenderer();
}

package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.kingdomfoxes.ralle.chat.mixin.GameRendererAccessor;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;

import java.util.List;
import java.util.function.Consumer;

/** One GPU-only capture, then one quad per fade frame. No readback or per-pixel GUI submissions. */
final class CreateWheelSnapshot implements AutoCloseable {
    private static final Identifier MASK = Identifier.fromNamespaceAndPath("ralle", "textures/gui/wheel/create_dissolve.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ralle", "pipeline/create_wheel_dissolve"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("ralle", "core/create_wheel_dissolve"))
                    .withSampler("Sampler1").build());

    private final TextureTarget target;
    private final int extent;
    private final int density;

    private CreateWheelSnapshot(TextureTarget target, int extent, int density) {
        this.target = target;
        this.extent = extent;
        this.density = density;
    }

    static CreateWheelSnapshot capture(Minecraft minecraft, int extent, Consumer<GuiGraphics> draw) {
        int density = Math.max(1, minecraft.getWindow().getGuiScale());
        int size = extent * 2;
        var target = new TextureTarget("RALLE Create segment", size * density, size * density, true);
        var state = new GuiRenderState();
        // An independent state/renderer must never reset the main screen's queued GUI elements.
        try (var renderer = new GuiRenderer(state, minecraft.renderBuffers().bufferSource(),
                minecraft.gameRenderer.getSubmitNodeStorage(), minecraft.gameRenderer.getFeatureRenderDispatcher(), List.of())) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), 0, target.getDepthTexture(), 1);
            var graphics = new GuiGraphics(minecraft, state, 0, 0);
            graphics.pose().scale(minecraft.getWindow().getGuiScaledWidth() / (float) size,
                    minecraft.getWindow().getGuiScaledHeight() / (float) size);
            graphics.pose().translate(extent, extent);
            draw.accept(graphics);
            RenderSystem.backupProjectionMatrix();
            try {
                GuiCaptureTargetOverride.runWith(target, () -> renderer.render(
                        ((GameRendererAccessor) minecraft.gameRenderer).ralle$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE)));
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
            return new CreateWheelSnapshot(target, extent, density);
        } catch (RuntimeException | Error failure) {
            target.destroyBuffers();
            throw failure;
        }
    }

    void draw(GuiGraphics graphics, int dx, int dy, double progress) {
        if (progress >= 1) return;
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var mask = Minecraft.getInstance().getTextureManager().getTexture(MASK).getTextureView();
        int data = 0xFF000000 | CreateWheelAnimation.frame(progress) << 16 | density << 8;
        graphics.guiRenderState.submitBlitToCurrentLayer(new BlitRenderState(PIPELINE,
                TextureSetup.doubleTexture(target.getColorTextureView(), sampler, mask, sampler),
                new Matrix3x2f(graphics.pose()), dx - extent, dy - extent, dx + extent, dy + extent,
                0, 1, 1, 0, data, null));
    }

    @Override public void close() { target.destroyBuffers(); }
}

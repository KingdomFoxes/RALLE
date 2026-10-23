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
import org.kingdomfoxes.ralle.diagnostics.DiagnosticProfiler;
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
    private final int left, top, width, height;
    private final int density;

    private CreateWheelSnapshot(TextureTarget target, int left, int top, int width, int height, int density) {
        this.target = target;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.density = density;
    }

    static CreateWheelSnapshot capture(Minecraft minecraft, int extent, Consumer<GuiGraphics> draw) {
        return capture(minecraft, -extent, -extent, extent, extent, draw);
    }

    static CreateWheelSnapshot capture(Minecraft minecraft, int left, int top, int right, int bottom,
                                       Consumer<GuiGraphics> draw) {
        try (var diagnosticScope = DiagnosticProfiler.measure(DiagnosticProfiler.Section.CREATE_WHEEL_CAPTURE)) {
            return captureSegment(minecraft, left, top, right - left, bottom - top, draw);
        }
    }

    private static CreateWheelSnapshot captureSegment(Minecraft minecraft, int left, int top, int width, int height,
                                                      Consumer<GuiGraphics> draw) {
        // Warm the shared baked atlas on hover, before accepted creation starts playback.
        minecraft.getTextureManager().getTexture(MASK);
        int density = Math.max(1, minecraft.getWindow().getGuiScale());
        var target = new TextureTarget("RALLE Create selection", width * density, height * density, true);
        var state = new GuiRenderState();
        // An independent state/renderer must never reset the main screen's queued GUI elements.
        try (var renderer = new GuiRenderer(state, minecraft.renderBuffers().bufferSource(),
                minecraft.gameRenderer.getSubmitNodeStorage(), minecraft.gameRenderer.getFeatureRenderDispatcher(), List.of())) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), 0, target.getDepthTexture(), 1);
            var graphics = new GuiGraphics(minecraft, state, 0, 0);
            graphics.pose().scale(minecraft.getWindow().getGuiScaledWidth() / (float) width,
                    minecraft.getWindow().getGuiScaledHeight() / (float) height);
            graphics.pose().translate(-left, -top);
            draw.accept(graphics);
            RenderSystem.backupProjectionMatrix();
            try {
                GuiCaptureTargetOverride.runWith(target, () -> renderer.render(
                        ((GameRendererAccessor) minecraft.gameRenderer).ralle$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE)));
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
            return new CreateWheelSnapshot(target, left, top, width, height, density);
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
                new Matrix3x2f(graphics.pose()), dx + left, dy + top, dx + left + width, dy + top + height,
                0, 1, 1, 0, data, null));
    }

    @Override public void close() { target.destroyBuffers(); }
}

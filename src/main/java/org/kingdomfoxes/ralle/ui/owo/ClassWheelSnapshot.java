package org.kingdomfoxes.ralle.ui.owo;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.kingdomfoxes.ralle.chat.mixin.GameRendererAccessor;
import org.kingdomfoxes.ralle.ui.render.GuiCaptureTargetOverride;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;

/** Transparent, exact foreground capture using the native item/text renderer once. */
public final class ClassWheelSnapshot implements AutoCloseable {
    private static final RenderPipeline BLIT = RenderPipelines.register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("ralle", "pipeline/class_wheel_snapshot"))
                    .withVertexShader("core/screenquad").withFragmentShader("core/blit_screen")
                    .withSampler("InSampler")
                    .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                    .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).build());
    private final TextureTarget target;
    public ClassWheelSnapshot(Minecraft minecraft, Consumer<GuiGraphics> draw) {
        var main = minecraft.getMainRenderTarget();
        target = new TextureTarget("RALLE frozen class wheel", main.width, main.height, true);
        var state = new GuiRenderState();
        try (var renderer = new GuiRenderer(state, minecraft.renderBuffers().bufferSource(),
                minecraft.gameRenderer.getSubmitNodeStorage(), minecraft.gameRenderer.getFeatureRenderDispatcher(), List.of())) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1);
            draw.accept(new GuiGraphics(minecraft, state, 0, 0));
            RenderSystem.backupProjectionMatrix();
            try {
                GuiCaptureTargetOverride.runWith(target, () -> renderer.render(
                        ((GameRendererAccessor) minecraft.gameRenderer).ralle$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE)));
            } finally { RenderSystem.restoreProjectionMatrix(); }
        } catch (RuntimeException | Error failure) { target.destroyBuffers(); throw failure; }
    }
    public void present(RenderTarget destination) {
        // Native GUI blending into transparency has already premultiplied the captured RGB.
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RALLE frozen wheel", destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(BLIT);
            pass.bindTexture("InSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }
    @Override public void close() { target.destroyBuffers(); }
}

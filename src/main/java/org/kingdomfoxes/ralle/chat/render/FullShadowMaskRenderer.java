package org.kingdomfoxes.ralle.chat.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;

/** Renders one 2x glyph mask and queues one composite quad for a chat frame. */
public final class FullShadowMaskRenderer extends PictureInPictureRenderer<FullShadowMaskState> {
    private static final int MASK_SCALE = 2;

    private final CachedOrthoProjectionMatrixBuffer projection = new CachedOrthoProjectionMatrixBuffer(
            "RALLE Full Shadow Mask", -1000.0F, 1000.0F, true
    );
    private TextureTarget target;

    public FullShadowMaskRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public void prepare(FullShadowMaskState state, GuiRenderState guiRenderState, int ignoredGuiScale) {
        if (!FullShadowRenderingStrategy.compositorAvailable() || state.lines().isEmpty()) return;

        try {
            int width = Math.max(1, (state.x1() - state.x0()) * MASK_SCALE);
            int height = Math.max(1, (state.y1() - state.y0()) * MASK_SCALE);
            ensureTarget(width, height);
            renderMask(state, width, height);
            submitComposite(state, guiRenderState);
        } catch (Throwable failure) {
            FullShadowRenderingStrategy.disableCompositor("preparing the glyph mask", failure);
        }
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget("RALLE Full Shadow Mask", width, height, true);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }

    private void renderMask(FullShadowMaskState state, int width, int height) {
        var colorTexture = target.getColorTexture();
        var colorView = target.getColorTextureView();
        var depthTexture = target.getDepthTexture();
        var depthView = target.getDepthTextureView();
        if (colorTexture == null || colorView == null || depthTexture == null || depthView == null) {
            throw new IllegalStateException("Full-shadow mask target is incomplete");
        }

        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        boolean flushed = false;
        RenderSystem.backupProjectionMatrix();
        try {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTexture, 0, depthTexture, 1.0);
            RenderSystem.outputColorTextureOverride = colorView;
            RenderSystem.outputDepthTextureOverride = depthView;
            RenderSystem.setProjectionMatrix(projection.getBuffer(width, height), ProjectionType.ORTHOGRAPHIC);

            PoseStack pose = new PoseStack();
            pose.scale(MASK_SCALE, MASK_SCALE, 1.0F);
            for (var line : state.lines()) {
                state.font().drawInBatch(
                        line.content(),
                        line.x() - state.x0(),
                        line.y() - state.y0(),
                        ARGB.white(line.opacity()),
                        false,
                        pose.last().pose(),
                        bufferSource,
                        Font.DisplayMode.NORMAL,
                        0,
                        LightTexture.FULL_BRIGHT
                );
            }
            bufferSource.endBatch();
            flushed = true;
        } finally {
            if (!flushed) {
                try {
                    bufferSource.endBatch();
                } catch (Throwable ignored) {
                    // The original preparation failure selects the fallback.
                }
            }
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void submitComposite(FullShadowMaskState state, GuiRenderState guiRenderState) {
        var colorView = target.getColorTextureView();
        if (colorView == null) throw new IllegalStateException("Full-shadow mask texture is unavailable");

        guiRenderState.submitBlitToCurrentLayer(new BlitRenderState(
                RalleChatRenderPipelines.fullShadowComposite(),
                TextureSetup.singleTexture(colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                state.pose(),
                state.x0(),
                state.y0(),
                state.x1(),
                state.y1(),
                0.0F,
                1.0F,
                1.0F,
                0.0F,
                -1,
                state.scissorArea()
        ));
    }

    @Override public Class<FullShadowMaskState> getRenderStateClass() { return FullShadowMaskState.class; }

    @Override
    protected void renderToTexture(FullShadowMaskState state, PoseStack pose) {
        throw new UnsupportedOperationException("RALLE uses a fixed-resolution mask preparation path");
    }

    @Override protected String getTextureLabel() { return "RALLE Full Shadow Mask"; }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        projection.close();
        super.close();
    }
}

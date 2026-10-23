package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.OptionalInt;

/** Baked arrival field: a logical-resolution GPU pass, nearest composite and 16-byte progress update. */
public final class ClassIceRenderer implements AutoCloseable {
    private static final Identifier MAP = Identifier.fromNamespaceAndPath("ralle", "textures/gui/class_ice.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("ralle", "pipeline/class_ice"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath("ralle", "core/class_ice"))
            .withSampler("Arrival")
            .withUniform("Ice", UniformType.UNIFORM_BUFFER)
            .withDepthWrite(false).withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).build());
    private GpuBuffer uniform;
    private TextureTarget reveal;

    public static void warm(Minecraft minecraft) { minecraft.getTextureManager().getTexture(MAP); }
    private static Identifier map(int style) {
        if (style < 1 || style > 4) throw new IllegalArgumentException("Expected style 1 through 4");
        return style == 1 ? MAP : Identifier.fromNamespaceAndPath("ralle", "textures/gui/class_ice_style" + style + ".png");
    }
    public static void warm(Minecraft minecraft, int style) { minecraft.getTextureManager().getTexture(map(style)); }

    public void draw(RenderTarget background, RenderTarget destination, double progress, int pixelSize, int guiWidth, int guiHeight) {
        draw(background, destination, progress, pixelSize, guiWidth, guiHeight, 1);
    }
    public void draw(RenderTarget background, RenderTarget destination, double progress, int pixelSize, int guiWidth, int guiHeight, int style) {
        draw(background, destination, Minecraft.getInstance().getTextureManager().getTexture(map(style)).getTextureView(), progress, pixelSize, guiWidth, guiHeight);
    }

    void draw(RenderTarget background, RenderTarget destination, GpuTextureView arrival,
              double progress, int pixelSize, int guiWidth, int guiHeight) {
        int width = Math.max(1, Math.ceilDiv(guiWidth, pixelSize)), height = Math.max(1, Math.ceilDiv(guiHeight, pixelSize));
        if (reveal == null || reveal.width != width || reveal.height != height) {
            if (reveal != null) reveal.destroyBuffers();
            reveal = new TextureTarget("RALLE logical ice", width, height, false);
        }
        if (uniform == null) uniform = RenderSystem.getDevice().createBuffer(() -> "RALLE ice progress", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        try (var stack = MemoryStack.stackPush()) {
            var data = stack.malloc(16);
            data.putFloat((float) progress).putFloat(width).putFloat(height).putFloat(1).flip();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniform.slice(), data);
        }
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "RALLE class ice", reveal.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("Ice", uniform);
            pass.bindTexture("Arrival", arrival, sampler);
            pass.draw(0, 3);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(background.getColorTexture(), destination.getColorTexture(),
                0, 0, 0, 0, 0, background.width, background.height);
        reveal.blitAndBlendToTexture(destination.getColorTextureView());
    }
    @Override public void close() {
        try { if (uniform != null) { uniform.close(); uniform = null; } }
        finally { if (reveal != null) { var old = reveal; reveal = null; old.destroyBuffers(); } }
    }
}

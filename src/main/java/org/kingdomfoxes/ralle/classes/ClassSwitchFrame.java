package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.OptionalInt;
import java.util.function.Consumer;

/** One GPU frame, one tiny in-flight readback. Never replaces the live game render target.
 * All methods and GPU callbacks run on Minecraft's render thread. Closing invalidates callbacks;
 * an in-flight readback retains and releases its own resources when the fence completes. */
public final class ClassSwitchFrame implements AutoCloseable {
    private TextureTarget frozen;
    private boolean reading;
    private long generation;

    public void capture(RenderTarget source) {
        close();
        frozen = new TextureTarget("RALLE class switch frame", source.width, source.height, false);
        copy(source, frozen);
    }
    public boolean captured() { return frozen != null; }
    public boolean fits(RenderTarget target) { return frozen != null && frozen.width == target.width && frozen.height == target.height; }
    public void present(RenderTarget target) { copy(frozen, target); }

    private static void copy(RenderTarget source, RenderTarget destination) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                java.util.Objects.requireNonNull(source.getColorTexture()),
                java.util.Objects.requireNonNull(destination.getColorTexture()), 0, 0, 0, 0, 0, source.width, source.height);
    }

    public void sample(RenderTarget source, Consumer<Boolean> completion, Runnable failure) {
        if (reading) return;
        long token = generation;
        var sample = new TextureTarget("RALLE class transition sample", ClassTransitionPixels.WIDTH, ClassTransitionPixels.HEIGHT, false);
        GpuBuffer buffer = null;
        try {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "RALLE class transition downsample",
                    java.util.Objects.requireNonNull(sample.getColorTextureView()), OptionalInt.empty())) {
                pass.setPipeline(RenderPipelines.TRACY_BLIT);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture("InSampler", java.util.Objects.requireNonNull(source.getColorTextureView()),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.draw(0, 3);
            }
            buffer = RenderSystem.getDevice().createBuffer(() -> "RALLE class transition readback", 9,
                    (long) ClassTransitionPixels.WIDTH * ClassTransitionPixels.HEIGHT * 4);
            GpuBuffer readback = buffer;
            reading = true;
            encoder.copyTextureToBuffer(java.util.Objects.requireNonNull(sample.getColorTexture()), readback, 0L, () -> {
                boolean black = false, failed = false;
                try (readback; var mapped = encoder.mapBuffer(readback, true, false)) {
                    black = ClassTransitionPixels.black(mapped.data(), ClassTransitionPixels.WIDTH, ClassTransitionPixels.HEIGHT);
                } catch (RuntimeException error) { failed = true; }
                try { sample.destroyBuffers(); }
                catch (RuntimeException error) { failed = true; }
                finally { reading = false; }
                if (token == generation) {
                    if (failed) failure.run();
                    else completion.accept(black);
                }
            }, 0);
        } catch (RuntimeException error) {
            reading = false;
            if (buffer != null) buffer.close();
            sample.destroyBuffers();
            throw error;
        }
    }
    @Override public void close() {
        generation++;
        if (frozen != null) {
            var old = frozen; frozen = null;
            try { old.destroyBuffers(); }
            catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(ClassSwitchFrame.class).warn("Could not destroy class switch frame", error);
            }
        }
    }
}

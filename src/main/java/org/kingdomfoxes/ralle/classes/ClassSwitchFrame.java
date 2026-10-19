package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;

/** One retained GPU frame. Underlying rendering, networking and ticks continue normally.
 * No pixel readback or visual heuristic participates in class switching. */
public final class ClassSwitchFrame implements AutoCloseable {
    private TextureTarget frozen;

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
    @Override public void close() {
        if (frozen != null) {
            var old = frozen; frozen = null;
            try { old.destroyBuffers(); }
            catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(ClassSwitchFrame.class).warn("Could not destroy class switch frame", error);
            }
        }
    }
}

package org.kingdomfoxes.ralle.classes;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;

/** Opt-in real Minecraft GPU API test, isolated invisible window; RALLE_GPU_TEST=1. */
class ClassSwitchFrameGpuTest {
    @Test void realFrameStaysFrozenAcrossBlackDoorAndWorldFramesUntilExplicitRelease() throws Exception {
        assumeTrue("1".equals(System.getenv("RALLE_GPU_TEST")));
        assertTrue(glfwInit()); glfwDefaultWindowHints(); glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(320, 180, "RALLE class frame test", 0, 0);
        assertNotEquals(0, window);
        try {
            RenderSystem.initRenderThread();
            RenderSystem.initRenderer(window, 0, false, (id, type) -> {
                String extension = type == com.mojang.blaze3d.shaders.ShaderType.VERTEX ? ".vsh" : ".fsh";
                try (var source = getClass().getResourceAsStream("/assets/" + id.getNamespace() + "/shaders/" + id.getPath() + extension)) {
                    if (source == null) throw new IllegalStateException("Missing vanilla shader " + id);
                    return new String(source.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            }, false);
            var live = new TextureTarget("Class switch test live", 320, 180, false);
            var frame = new ClassSwitchFrame();
            try {
                var encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.clearColorTexture(live.getColorTexture(), 0xFF6090C0);
                int original = pixel(live);
                frame.capture(live); assertTrue(frame.captured()); assertTrue(frame.fits(live));
                for (int color : new int[] {0xFF000000, 0xFF806020, 0xFF20A050}) {
                    encoder.clearColorTexture(live.getColorTexture(), color);
                    assertNotEquals(original, pixel(live)); // Underlying rendering keeps changing.
                    frame.present(live);
                    assertEquals(original, pixel(live)); // All transition scenes remain hidden.
                }
                var resized = new TextureTarget("Class switch test resized", 160, 90, false);
                try { assertFalse(frame.fits(resized)); } finally { resized.destroyBuffers(); }
                frame.close(); frame.close(); assertFalse(frame.captured());
                encoder.clearColorTexture(live.getColorTexture(), 0xFF20A050);
                assertNotEquals(original, pixel(live));
                frame.capture(live); int nextFrame = pixel(live);
                encoder.clearColorTexture(live.getColorTexture(), 0xFF000000);
                frame.present(live); assertEquals(nextFrame, pixel(live));
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            } finally { frame.close(); live.destroyBuffers(); RenderSystem.getDevice().close(); }
        } finally { GL.setCapabilities(null); glfwDestroyWindow(window); glfwTerminate(); }
    }

    /** Test-only readback; the shipped switching code never reads pixels. */
    private static int pixel(RenderTarget source) {
        var result = new AtomicReference<Integer>();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var buffer = RenderSystem.getDevice().createBuffer(() -> "Class frame test readback", 9, (long) source.width * source.height * 4);
        encoder.copyTextureToBuffer(source.getColorTexture(), buffer, 0L, () -> {
            try (buffer; var mapped = encoder.mapBuffer(buffer, true, false)) { result.set(mapped.data().getInt(0)); }
        }, 0);
        GL11.glFinish(); RenderSystem.executePendingTasks();
        assertNotNull(result.get()); return result.get();
    }
}

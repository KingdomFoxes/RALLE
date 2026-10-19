package org.kingdomfoxes.ralle.classes;

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
    @Test void realFrameCopyReadbackAndCancellationKeepLiveRenderingIndependent() throws Exception {
        assumeTrue("1".equals(System.getenv("RALLE_GPU_TEST")));
        assertTrue(glfwInit());
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
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
                frame.capture(live);
                assertTrue(frame.captured());
                assertTrue(frame.fits(live));
                var black = new AtomicReference<Boolean>();
                encoder.clearColorTexture(live.getColorTexture(), 0xFF000000);
                frame.sample(live, black::set, () -> fail("Readback failed"));
                frame.present(live); // Readback must see the underlying black, not the frozen color.
                flush();
                assertEquals(Boolean.TRUE, black.get());
                black.set(null);
                frame.sample(live, black::set, () -> fail("Readback failed"));
                flush();
                assertEquals(Boolean.FALSE, black.get()); // The frozen frame was really copied back.

                encoder.clearColorTexture(live.getColorTexture(), 0xFF000000);
                black.set(null);
                frame.sample(live, black::set, () -> fail("A cancelled readback cannot report failure"));
                frame.close(); frame.close(); // A fence still owns the tiny sample and buffer.
                flush();
                assertNull(black.get());
                assertFalse(frame.captured());
                // A fresh operation after cancellation must still sample and release correctly.
                frame.capture(live);
                frame.sample(live, black::set, () -> fail("Readback after cancellation failed"));
                flush();
                assertEquals(Boolean.TRUE, black.get());
                var resized = new TextureTarget("Class switch test resized", 160, 90, false);
                try { assertFalse(frame.fits(resized)); }
                finally { resized.destroyBuffers(); }
                frame.close(); // The native black transition is now visible; only monitor world return.
                black.set(null);
                encoder.clearColorTexture(live.getColorTexture(), 0xFF6090C0);
                frame.sample(live, black::set, () -> fail("Post-black gameplay readback failed"));
                flush();
                assertEquals(Boolean.FALSE, black.get());
                assertFalse(frame.captured());
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            } finally {
                frame.close(); flush(); live.destroyBuffers();
                RenderSystem.getDevice().close();
            }
        } finally {
            GL.setCapabilities(null);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
    private static void flush() {
        GL11.glFinish();
        RenderSystem.executePendingTasks();
    }
}

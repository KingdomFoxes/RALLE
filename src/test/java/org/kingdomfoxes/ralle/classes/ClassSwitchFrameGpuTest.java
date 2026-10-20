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
                verifyIceShader(live, frame);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
            } finally { frame.close(); live.destroyBuffers(); RenderSystem.getDevice().close(); }
        } finally { GL.setCapabilities(null); glfwDestroyWindow(window); glfwTerminate(); }
    }

    private void verifyIceShader(RenderTarget live, ClassSwitchFrame frame) throws Exception {
        for (int style = 1; style <= 4; style++) {
            String suffix = style == 1 ? "" : "_style" + style;
            try (var stream = getClass().getResourceAsStream("/assets/ralle/textures/gui/class_ice" + suffix + ".png");
                 var map = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Ice test arrival",
                         com.mojang.blaze3d.platform.NativeImage.read(java.util.Objects.requireNonNull(stream)));
                 var ice = new ClassIceRenderer()) {
                int original = pixel(frame.texture());
                var encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.clearColorTexture(live.getColorTexture(), 0xFF000000);
                int black = pixel(live);
                for (int scale : new int[] {1, 2, 4}) for (int pixels : new int[] {1, 2}) {
                    ice.draw(frame.texture(), live, map.getTextureView(), 0, pixels, live.width / scale, live.height / scale);
                    assertEquals(original, pixel(live));
                    ice.draw(frame.texture(), live, map.getTextureView(), .2, pixels, live.width / scale, live.height / scale);
                    int[] partial = pixels(live);
                    assertTrue(java.util.Arrays.stream(partial).anyMatch(p -> p == original));
                    assertTrue(java.util.Arrays.stream(partial).anyMatch(p -> p == black));
                    assertTrue(java.util.Arrays.stream(partial).anyMatch(p -> p == -1), "White contour must survive the shader");
                    int cellsX = Math.ceilDiv(live.width / scale, pixels), cellsY = Math.ceilDiv(live.height / scale, pixels);
                    for (int y = 0; y < live.height; y++) for (int x = 0; x < live.width; x++) {
                        int cx = (int) ((x + .5) * cellsX / live.width), cy = (int) ((y + .5) * cellsY / live.height);
                        int firstX = (int) Math.ceil(cx * (double) live.width / cellsX - .5);
                        int firstY = (int) Math.ceil(cy * (double) live.height / cellsY - .5);
                        assertEquals(partial[firstY * live.width + firstX], partial[y * live.width + x]);
                    }
                    if (style != 3) assertConnectedToEdge(partial, live.width, live.height, black);
                    if (style == 1) savePreview(partial, live.width, live.height, pixels, scale);
                    ice.draw(frame.texture(), live, map.getTextureView(), 1, pixels, live.width / scale, live.height / scale);
                    assertTrue(java.util.Arrays.stream(pixels(live)).allMatch(p -> p == black), "Full opaque coverage");
                }
                // A repeated operation uses a fresh background; no retained shader/frame state bleeds through.
                encoder.clearColorTexture(live.getColorTexture(), 0xFF3060D0);
                frame.capture(live); int next = pixel(live);
                ice.draw(frame.texture(), live, map.getTextureView(), 0, 1, live.width, live.height);
                assertEquals(next, pixel(live));
            }
        }
    }

    private static void assertConnectedToEdge(int[] pixels, int width, int height, int black) {
        var seen = new boolean[pixels.length]; var queue = new java.util.ArrayDeque<Integer>();
        int total = 0, connected = 0;
        for (int i = 0; i < pixels.length; i++) if (pixels[i] == black) {
            total++;
            int x = i % width, y = i / width;
            if (x == 0 || y == 0 || x == width - 1 || y == height - 1) { seen[i] = true; queue.add(i); }
        }
        while (!queue.isEmpty()) {
            int i = queue.remove(); connected++;
            int x = i % width, y = i / width;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int n = ny * width + nx;
                if (!seen[n] && pixels[n] == black) { seen[n] = true; queue.add(n); }
            }
        }
        assertEquals(total, connected, "Logical-pixel reduction must preserve connected growth");
    }

    private static void savePreview(int[] pixels, int width, int height, int size, int scale) throws java.io.IOException {
        var image = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < pixels.length; i++) {
            int value = pixels[i];
            image.setRGB(i % width, i / width, value & 0xFF00FF00 | (value & 255) << 16 | value >> 16 & 255);
        }
        var directory = java.nio.file.Path.of("build", "ice-preview"); java.nio.file.Files.createDirectories(directory);
        javax.imageio.ImageIO.write(image, "png", directory.resolve("ice-" + size + "-scale-" + scale + ".png").toFile());
    }

    private static int[] pixels(RenderTarget source) {
        var result = new AtomicReference<int[]>();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var buffer = RenderSystem.getDevice().createBuffer(() -> "Ice shader readback", 9, (long) source.width * source.height * 4);
        encoder.copyTextureToBuffer(source.getColorTexture(), buffer, 0L, () -> {
            try (buffer; var mapped = encoder.mapBuffer(buffer, true, false)) {
                int[] data = new int[source.width * source.height];
                mapped.data().asIntBuffer().get(data); result.set(data);
            }
        }, 0);
        GL11.glFinish(); RenderSystem.executePendingTasks();
        assertNotNull(result.get()); return result.get();
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

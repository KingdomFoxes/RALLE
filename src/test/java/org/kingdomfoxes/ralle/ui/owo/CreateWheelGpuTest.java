package org.kingdomfoxes.ralle.ui.owo;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/** Opt-in native smoke test: RALLE_GPU_TEST=1. Uses an invisible isolated OpenGL window. */
class CreateWheelGpuTest {
    @Test void shippedShaderCompositesPremultipliedCaptureAndPremadeFrames() throws Exception {
        assumeTrue("1".equals(System.getenv("RALLE_GPU_TEST")));
        assertTrue(glfwInit());
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(128, 128, "RALLE shader test", 0, 0);
        assertNotEquals(0, window);
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int vertex = compile(GL_VERTEX_SHADER, """
                    #version 330
                    uniform vec4 data;
                    out vec2 texCoord0;
                    out vec4 vertexColor;
                    void main() {
                        vec2 p = vec2((gl_VertexID & 1) * 2 - 1, (gl_VertexID / 2) * 2 - 1);
                        gl_Position = vec4(p, 0, 1);
                        texCoord0 = (p + 1.0) / 2.0;
                        vertexColor = data;
                    }
                    """);
            int fragment = compile(GL_FRAGMENT_SHADER, Files.readString(Path.of(
                    "src/main/resources/assets/ralle/shaders/core/create_wheel_dissolve.fsh")));
            int program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            glUniform1i(glGetUniformLocation(program, "Sampler1"), 1);
            int vao = glGenVertexArrays();
            glBindVertexArray(vao);

            var source = BufferUtils.createByteBuffer(128 * 128 * 4);
            for (int i = 0; i < 128 * 128; i++) source.put((byte) 32).put((byte) 48).put((byte) 80).put((byte) 128);
            source.flip();
            int input = texture(GL_TEXTURE0, 128, 128, source);
            var image = ImageIO.read(Path.of("src/main/resources/assets/ralle/textures/gui/wheel/create_dissolve.png").toFile());
            var pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                pixels.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
            }
            pixels.flip();
            int mask = texture(GL_TEXTURE1, image.getWidth(), image.getHeight(), pixels);
            int output = texture(GL_TEXTURE2, 128, 128, null);
            int framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, output, 0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckFramebufferStatus(GL_FRAMEBUFFER));
            glViewport(0, 0, 128, 128);
            var result = BufferUtils.createByteBuffer(128 * 128 * 4);
            for (int density : new int[] {1, 2}) {
                int logicalSize = 128 / density;
                var captured = new java.awt.image.BufferedImage(logicalSize, logicalSize, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < logicalSize; y++) for (int x = 0; x < logicalSize; x++) {
                    if ((x + y) % 7 != 0) captured.setRGB(x, y, 0xC0000000 | (x % 192) << 16 | (y % 192) << 8 | 80);
                }
                source.clear();
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    int color = captured.getRGB(x / density, (127 - y) / density);
                    source.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
                }
                source.flip();
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, input);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 128, 128, GL_RGBA, GL_UNSIGNED_BYTE, source);
                for (int frame : new int[] {0, 17, 31, 48, 63}) {
                    glClearColor(0, 0, 0, 0);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glUniform4f(glGetUniformLocation(program, "data"), frame / 255f, density / 255f, 0, 1);
                    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                    glReadPixels(0, 0, 128, 128, GL_RGBA, GL_UNSIGNED_BYTE, result);
                    var expected = CreateDustReference.render(captured, frame, null);
                    for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                        int color = expected.getRGB(x / density, (127 - y) / density);
                        int at = (y * 128 + x) * 4;
                        assertEquals(color >>> 24, Byte.toUnsignedInt(result.get(at + 3)), 1, "Alpha at " + x + "," + y);
                        if ((color >>> 24) > 1) for (int c = 0; c < 3; c++) {
                            assertEquals(color >> (16 - c * 8) & 255, Byte.toUnsignedInt(result.get(at + c)), 1);
                        }
                    }
                }
            }
            int query = glGenQueries();
            glUniform4f(glGetUniformLocation(program, "data"), 31 / 255f, 1 / 255f, 0, 1);
            glBeginQuery(GL_TIME_ELAPSED, query);
            for (int i = 0; i < 100; i++) glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glEndQuery(GL_TIME_ELAPSED);
            System.out.println("128px baked dust GPU draw average (warm, isolated): "
                    + glGetQueryObjectui64(query, GL_QUERY_RESULT) / 100_000d + " microseconds");
            glDeleteQueries(query);
            assertEquals(GL_NO_ERROR, glGetError());
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(new int[] {input, mask, output});
            glDeleteVertexArrays(vao);
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
        } finally {
            glfwMakeContextCurrent(0);
            GL.setCapabilities(null);
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static int texture(int unit, int width, int height, ByteBuffer pixels) {
        glActiveTexture(unit);
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return texture;
    }
}

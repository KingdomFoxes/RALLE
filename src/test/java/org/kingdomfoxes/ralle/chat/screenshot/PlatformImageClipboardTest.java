package org.kingdomfoxes.ralle.chat.screenshot;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformImageClipboardTest {
    @Test
    void platformSelectionUsesTheOperatingSystemAndActiveLinuxDisplay() {
        assertInstanceOf(WindowsPlatformImageClipboard.class,
                PlatformImageClipboards.select("Windows 11", Map.of()));
        assertInstanceOf(MacPlatformImageClipboard.class,
                PlatformImageClipboards.select("Mac OS X", Map.of()));
        assertInstanceOf(MacPlatformImageClipboard.class,
                PlatformImageClipboards.select("Darwin", Map.of()));
        var wayland = (LinuxPlatformImageClipboard) PlatformImageClipboards.select(
                "Linux", Map.of("WAYLAND_DISPLAY", "wayland-0", "DISPLAY", ":0")
        );
        var x11 = (LinuxPlatformImageClipboard) PlatformImageClipboards.select(
                "Linux", Map.of("DISPLAY", ":0")
        );
        assertEquals(LinuxPlatformImageClipboard.Backend.WAYLAND, wayland.backend());
        assertEquals(LinuxPlatformImageClipboard.Backend.X11, x11.backend());
        assertEquals(LinuxPlatformImageClipboard.Backend.X11,
                LinuxPlatformImageClipboard.detectBackend(Map.of(
                        "XDG_SESSION_TYPE", "x11", "WAYLAND_DISPLAY", "stale-wayland", "DISPLAY", ":0"
                )));
    }

    @Test
    void pngEncodingPreservesDimensionsChannelsAndStraightAlpha() throws Exception {
        byte[] rgba = {(byte) 0xFA, 0x20, 0x30, (byte) 0x80, 0x01, 0x02, 0x03, 0x00};
        var image = ImageIO.read(new ByteArrayInputStream(PngEncoder.encode(2, 1, rgba)));
        assertEquals(2, image.getWidth());
        assertEquals(1, image.getHeight());
        assertEquals(0x80FA2030, image.getRGB(0, 0));
        assertEquals(0x00010203, image.getRGB(1, 0));
    }

    @Test
    void windowsDibV5IsTopDownBgraWithExplicitAlphaMasks() {
        byte[] dib = WindowsPlatformImageClipboard.dibV5(2, 1, new byte[]{
                0x11, 0x22, 0x33, 0x44,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD
        });
        ByteBuffer header = ByteBuffer.wrap(dib).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(124, header.getInt(0));
        assertEquals(2, header.getInt(4));
        assertEquals(-1, header.getInt(8));
        assertEquals(32, Short.toUnsignedInt(header.getShort(14)));
        assertEquals(3, header.getInt(16));
        assertEquals(0x00FF0000, header.getInt(40));
        assertEquals(0x0000FF00, header.getInt(44));
        assertEquals(0x000000FF, header.getInt(48));
        assertEquals(0xFF000000, header.getInt(52));
        assertArrayEquals(new byte[]{0x33, 0x22, 0x11, 0x44, (byte) 0xCC, (byte) 0xBB, (byte) 0xAA, (byte) 0xDD},
                java.util.Arrays.copyOfRange(dib, 124, 132));
    }

    @Test
    void busyClipboardRetriesAreBoundedAndFailuresAreActionable() {
        var attempts = new AtomicInteger();
        PlatformImageClipboard busy = ignored -> {
            attempts.incrementAndGet();
            throw new ClipboardBusyException("busy");
        };
        var retrying = new RetryingPlatformImageClipboard(busy, 3, 1, ignored -> {});

        IOException failure = assertThrows(IOException.class, () -> retrying.publish(image()));
        assertEquals(3, attempts.get());
        assertTrue(failure.getMessage().contains("close other clipboard tools"));
        assertTrue(failure.getMessage().contains("retry"));
    }

    @Test
    void linuxPublicationUsesImagePngOnTheSelectedNativeProtocol() throws Exception {
        var commands = new java.util.ArrayList<List<String>>();
        var payloads = new java.util.ArrayList<byte[]>();
        LinuxPlatformImageClipboard.CommandRunner runner = (command, input) -> {
            commands.add(command);
            payloads.add(input.clone());
            return new LinuxPlatformImageClipboard.Result(0, "");
        };
        var clipboard = new LinuxPlatformImageClipboard(LinuxPlatformImageClipboard.Backend.WAYLAND, runner);

        ClipboardImage image = image();
        clipboard.publish(image);
        assertEquals(List.of("wl-copy", "--type", "image/png"), commands.getFirst());
        assertArrayEquals(image.encodedPng(), payloads.getFirst());
    }

    private static ClipboardImage image() throws IOException {
        byte[] rgba = {(byte) 0xFF, 0, 0, (byte) 0xFF};
        return new ClipboardImage(PngEncoder.encode(1, 1, rgba), 1, 1, rgba);
    }
}

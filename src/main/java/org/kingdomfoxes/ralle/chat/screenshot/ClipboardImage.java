package org.kingdomfoxes.ralle.chat.screenshot;

import java.util.Arrays;

/** Immutable clipboard payload with a lossless PNG and top-down straight-alpha RGBA pixels. */
public record ClipboardImage(byte[] encodedPng, int width, int height, byte[] straightRgba) {
    public ClipboardImage {
        encodedPng = Arrays.copyOf(encodedPng, encodedPng.length);
        straightRgba = Arrays.copyOf(straightRgba, straightRgba.length);
        if (width < 1 || height < 1) throw new IllegalArgumentException("Clipboard image dimensions must be positive");
        if (straightRgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new IllegalArgumentException("RGBA payload does not match the image dimensions");
        }
        if (encodedPng.length < 8) throw new IllegalArgumentException("Encoded PNG payload is empty");
    }

    @Override public byte[] encodedPng() { return Arrays.copyOf(encodedPng, encodedPng.length); }
    @Override public byte[] straightRgba() { return Arrays.copyOf(straightRgba, straightRgba.length); }

    byte[] encodedPngUnsafe() { return encodedPng; }
    byte[] straightRgbaUnsafe() { return straightRgba; }
}

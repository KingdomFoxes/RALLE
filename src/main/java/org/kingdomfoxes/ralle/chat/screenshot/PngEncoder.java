package org.kingdomfoxes.ralle.chat.screenshot;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Minimal lossless RGBA PNG encoder which does not initialize AWT. */
final class PngEncoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};

    private PngEncoder() {}

    static byte[] encode(int width, int height, byte[] rgba) throws IOException {
        if (width < 1 || height < 1 || rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new IllegalArgumentException("Invalid RGBA image dimensions");
        }

        var output = new ByteArrayOutputStream();
        output.write(SIGNATURE);
        var header = new ByteArrayOutputStream(13);
        try (var data = new DataOutputStream(header)) {
            data.writeInt(width);
            data.writeInt(height);
            data.writeByte(8);
            data.writeByte(6); // RGBA
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
        }
        writeChunk(output, "IHDR", header.toByteArray());

        var compressed = new ByteArrayOutputStream();
        var deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try (var stream = new DeflaterOutputStream(compressed, deflater)) {
            int stride = width * 4;
            for (int y = 0; y < height; y++) {
                stream.write(0);
                stream.write(rgba, y * stride, stride);
            }
        } finally {
            deflater.end();
        }
        writeChunk(output, "IDAT", compressed.toByteArray());
        writeChunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream output, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        var binary = new DataOutputStream(output);
        binary.writeInt(data.length);
        binary.write(typeBytes);
        binary.write(data);
        binary.writeInt((int) crc.getValue());
        binary.flush();
    }
}

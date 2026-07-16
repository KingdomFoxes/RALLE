package org.kingdomfoxes.ralle.chat.screenshot;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Win32 clipboard publisher for registered PNG and alpha-preserving CF_DIBV5. */
final class WindowsPlatformImageClipboard implements PlatformImageClipboard {
    static final int CF_DIBV5 = 17;
    private static final int GMEM_MOVEABLE = 0x0002;
    private static final int BI_BITFIELDS = 3;
    private static final int LCS_SRGB = 0x73524742;
    private static final int LCS_GM_IMAGES = 4;
    private static final int HEADER_SIZE = 124;

    @Override
    public void publish(ClipboardImage image) throws IOException {
        if (!User32.INSTANCE.OpenClipboard(null)) {
            throw new ClipboardBusyException("Windows clipboard is busy (error " + Native.getLastError() + ")");
        }
        try {
            if (!User32.INSTANCE.EmptyClipboard()) throw win32Failure("clear the Windows clipboard");
            int pngFormat = User32.INSTANCE.RegisterClipboardFormat("PNG");
            if (pngFormat == 0) throw win32Failure("register the PNG clipboard format");
            publishBytes(pngFormat, image.encodedPngUnsafe());
            publishBytes(CF_DIBV5, dibV5(image.width(), image.height(), image.straightRgbaUnsafe()));
        } finally {
            User32.INSTANCE.CloseClipboard();
        }
    }

    static byte[] dibV5(int width, int height, byte[] rgba) {
        if (width < 1 || height < 1 || rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4)) {
            throw new IllegalArgumentException("Invalid RGBA image dimensions");
        }
        int pixelBytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        ByteBuffer output = ByteBuffer.allocate(Math.addExact(HEADER_SIZE, pixelBytes)).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(HEADER_SIZE);
        output.putInt(width);
        output.putInt(-height); // top-down rows
        output.putShort((short) 1);
        output.putShort((short) 32);
        output.putInt(BI_BITFIELDS);
        output.putInt(pixelBytes);
        output.putInt(0).putInt(0).putInt(0).putInt(0);
        output.putInt(0x00FF0000);
        output.putInt(0x0000FF00);
        output.putInt(0x000000FF);
        output.putInt(0xFF000000);
        output.putInt(LCS_SRGB);
        for (int index = 0; index < 9; index++) output.putInt(0); // CIEXYZTRIPLE
        output.putInt(0).putInt(0).putInt(0);
        output.putInt(LCS_GM_IMAGES);
        output.putInt(0).putInt(0).putInt(0);
        for (int index = 0; index < rgba.length; index += 4) {
            output.put(rgba[index + 2]);
            output.put(rgba[index + 1]);
            output.put(rgba[index]);
            output.put(rgba[index + 3]);
        }
        return output.array();
    }

    private static void publishBytes(int format, byte[] bytes) throws IOException {
        WinNT.HANDLE allocation = Kernel32.INSTANCE.GlobalAlloc(GMEM_MOVEABLE, new BaseTSD.SIZE_T(bytes.length));
        if (allocation == null) throw win32Failure("allocate clipboard memory");
        boolean ownedByClipboard = false;
        try {
            Pointer memory = Kernel32.INSTANCE.GlobalLock(allocation);
            if (memory == null) throw win32Failure("lock clipboard memory");
            try {
                memory.write(0, bytes, 0, bytes.length);
            } finally {
                Kernel32.INSTANCE.GlobalUnlock(allocation);
            }
            if (User32.INSTANCE.SetClipboardData(format, allocation) == null) {
                throw win32Failure("publish image data to the Windows clipboard");
            }
            ownedByClipboard = true;
        } finally {
            if (!ownedByClipboard) Kernel32.INSTANCE.GlobalFree(allocation);
        }
    }

    private static IOException win32Failure(String action) {
        return new IOException("Could not " + action + " (Win32 error " + Native.getLastError()
                + "); the preview was kept so you can retry");
    }

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean OpenClipboard(WinDef.HWND owner);
        boolean CloseClipboard();
        boolean EmptyClipboard();
        int RegisterClipboardFormat(String format);
        WinNT.HANDLE SetClipboardData(int format, WinNT.HANDLE memory);
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);
        WinNT.HANDLE GlobalAlloc(int flags, BaseTSD.SIZE_T bytes);
        Pointer GlobalLock(WinNT.HANDLE memory);
        boolean GlobalUnlock(WinNT.HANDLE memory);
        WinNT.HANDLE GlobalFree(WinNT.HANDLE memory);
    }
}

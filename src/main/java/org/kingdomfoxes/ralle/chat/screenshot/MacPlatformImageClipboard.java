package org.kingdomfoxes.ralle.chat.screenshot;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** AppKit NSPasteboard publisher using the standard public.png pasteboard type. */
final class MacPlatformImageClipboard implements PlatformImageClipboard {
    @Override
    public void publish(ClipboardImage image) throws IOException {
        try {
            NativeLibrary.getInstance("AppKit");
            NativeLibrary objectiveC = NativeLibrary.getInstance("objc");
            Function getClass = objectiveC.getFunction("objc_getClass");
            Function selector = objectiveC.getFunction("sel_registerName");
            Function message = objectiveC.getFunction("objc_msgSend");

            Pointer pool = send(message, getClass(getClass, "NSAutoreleasePool"), sel(selector, "new"));
            try {
                Pointer pasteboard = send(message, getClass(getClass, "NSPasteboard"), sel(selector, "generalPasteboard"));
                if (pasteboard == null) throw new IOException("macOS did not provide a general pasteboard");
                message.invokeLong(new Object[]{pasteboard, sel(selector, "clearContents")});

                byte[] png = image.encodedPngUnsafe();
                Memory pngMemory = new Memory(png.length);
                pngMemory.write(0, png, 0, png.length);
                Pointer data = send(message, getClass(getClass, "NSData"), sel(selector, "dataWithBytes:length:"), pngMemory, png.length);
                Pointer type = nsString(message, getClass, selector, "public.png");
                long accepted = message.invokeLong(new Object[]{pasteboard, sel(selector, "setData:forType:"), data, type});
                if (accepted == 0) {
                    throw new IOException("macOS rejected PNG clipboard data; check clipboard permissions and retry");
                }
            } finally {
                if (pool != null) send(message, pool, sel(selector, "release"));
            }
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Could not access the macOS image clipboard; check clipboard permissions and retry", failure);
        }
    }

    private static Pointer nsString(Function message, Function getClass, Function selector, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        Memory memory = new Memory(utf8.length + 1L);
        memory.write(0, utf8, 0, utf8.length);
        memory.setByte(utf8.length, (byte) 0);
        return send(message, getClass(getClass, "NSString"), sel(selector, "stringWithUTF8String:"), memory);
    }

    private static Pointer getClass(Function function, String name) {
        return function.invokePointer(new Object[]{name});
    }

    private static Pointer sel(Function function, String name) {
        return function.invokePointer(new Object[]{name});
    }

    private static Pointer send(Function message, Pointer receiver, Pointer selector, Object... arguments) {
        Object[] values = new Object[arguments.length + 2];
        values[0] = receiver;
        values[1] = selector;
        System.arraycopy(arguments, 0, values, 2, arguments.length);
        return message.invokePointer(values);
    }
}

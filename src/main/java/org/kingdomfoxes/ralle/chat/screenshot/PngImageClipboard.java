package org.kingdomfoxes.ralle.chat.screenshot;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Publishes both the standard platform image flavor and lossless PNG bytes. */
public final class PngImageClipboard {
    static final DataFlavor PNG_FLAVOR;

    static {
        try {
            PNG_FLAVOR = new DataFlavor("image/png;class=java.io.InputStream");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public void copy(BufferedImage image) throws IOException {
        var png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) throw new IOException("No PNG encoder is available");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransfer(image, png.toByteArray()), null);
    }

    private record ImageTransfer(BufferedImage image, byte[] png) implements Transferable {
        private static final DataFlavor[] FLAVORS = {PNG_FLAVOR, DataFlavor.imageFlavor};

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FLAVORS.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PNG_FLAVOR.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (PNG_FLAVOR.equals(flavor)) return new ByteArrayInputStream(png);
            if (DataFlavor.imageFlavor.equals(flavor)) return (Image) image;
            throw new UnsupportedFlavorException(flavor);
        }
    }
}

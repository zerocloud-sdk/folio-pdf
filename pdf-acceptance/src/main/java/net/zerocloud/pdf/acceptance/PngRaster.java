package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Strict fixed-size PNG validation performed before raster comparison. */
final class PngRaster {

    private static final byte[] SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private PngRaster() {
    }

    static void requireProfileRaster(
            Path path,
            int expectedWidth,
            int expectedHeight) throws IOException {
        require(path, expectedWidth, expectedHeight, true);
    }

    static void requireDifferenceRaster(
            Path path,
            int expectedWidth,
            int expectedHeight) throws IOException {
        require(path, expectedWidth, expectedHeight, false);
    }

    private static void require(
            Path path,
            int expectedWidth,
            int expectedHeight,
            boolean requireRgb) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Raster output is missing");
        }
        try (InputStream raw = Files.newInputStream(path);
                DataInputStream input = new DataInputStream(raw)) {
            for (byte expected : SIGNATURE) {
                if (input.readUnsignedByte() != (expected & 0xff)) {
                    throw new IOException("Raster output is not a PNG image");
                }
            }
            if (input.readInt() != 13
                    || input.readUnsignedByte() != 'I'
                    || input.readUnsignedByte() != 'H'
                    || input.readUnsignedByte() != 'D'
                    || input.readUnsignedByte() != 'R') {
                throw new IOException("Raster output has a malformed PNG header");
            }
            int width = input.readInt();
            int height = input.readInt();
            int bitDepth = input.readUnsignedByte();
            int colorType = input.readUnsignedByte();
            int compression = input.readUnsignedByte();
            int filter = input.readUnsignedByte();
            int interlace = input.readUnsignedByte();
            if (width != expectedWidth || height != expectedHeight) {
                throw new IOException("Raster dimensions were " + width + "x" + height
                        + "; expected " + expectedWidth + "x" + expectedHeight);
            }
            if (compression != 0 || filter != 0 || interlace != 0) {
                throw new IOException("Raster output uses an unsupported PNG encoding");
            }
            if (requireRgb && (bitDepth != 8 || colorType != 2)) {
                throw new IOException("Raster output is not opaque 8-bit RGB PNG");
            }
        }
        BufferedImage decoded = ImageIO.read(path.toFile());
        if (decoded == null
                || decoded.getWidth() != expectedWidth
                || decoded.getHeight() != expectedHeight) {
            throw new IOException("Raster output is not a decodable fixed-size PNG");
        }
    }
}

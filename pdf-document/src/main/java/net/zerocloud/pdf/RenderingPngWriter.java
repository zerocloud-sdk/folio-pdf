package net.zerocloud.pdf;

import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Fixed sRGB PNG profile with bounded row/chunk buffers and no ImageIO cache. */
final class RenderingPngWriter {
    private static final int BUFFER = 8192;
    private RenderingPngWriter() { }

    static void write(BufferedImage image, RenderOptions options,
            OutputStream target, WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        try (WorkflowResourceContext.MemoryReservation memory =
                resources.reserveOwnedMemory(3L * BUFFER + 13)) {
            DataOutputStream out = new DataOutputStream(target);
            out.writeLong(0x89504e470d0a1a0aL);
            boolean alpha = options.getAlphaMode() == RenderOptions.AlphaMode.PRESERVE;
            byte[] header = new byte[13];
            putInt(header, 0, image.getWidth());
            putInt(header, 4, image.getHeight());
            header[8] = 8;
            header[9] = (byte) (alpha ? 6 : 2);
            chunk(out, 0x49484452, header, header.length);
            header[0] = 0;
            chunk(out, 0x73524742, header, 1);
            IdatOutput idat = new IdatOutput(out);
            Deflater deflater = new Deflater(6);
            try {
                DeflaterOutputStream encoded = new DeflaterOutputStream(idat, deflater, BUFFER);
                byte[] samples = new byte[BUFFER];
                int channels = alpha ? 4 : 3;
                for (int y = 0; y < image.getHeight(); y++) {
                    resources.checkpoint();
                    encoded.write(0); // fixed PNG filter None
                    int used = 0;
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        int a = argb >>> 24;
                        int r = (argb >>> 16) & 255;
                        int g = (argb >>> 8) & 255;
                        int b = argb & 255;
                        if (!alpha) {
                            int background = options.getBackgroundRgb();
                            r = composite(r, a, (background >>> 16) & 255);
                            g = composite(g, a, (background >>> 8) & 255);
                            b = composite(b, a, background & 255);
                        }
                        if (options.getColorMode() == RenderOptions.ColorMode.GRAY) {
                            r = (299 * r + 587 * g + 114 * b + 500) / 1000;
                            g = r; b = r;
                        }
                        samples[used++] = (byte) r;
                        samples[used++] = (byte) g;
                        samples[used++] = (byte) b;
                        if (alpha) { samples[used++] = (byte) a; }
                        if (used > BUFFER - channels) {
                            resources.checkpoint();
                            encoded.write(samples, 0, used);
                            used = 0;
                        }
                    }
                    encoded.write(samples, 0, used);
                }
                encoded.finish();
                idat.finish();
                chunk(out, 0x49454e44, header, 0);
                resources.checkpoint();
            } finally {
                deflater.end();
            }
        }
    }

    private static int composite(int value, int alpha, int background) {
        return (value * alpha + background * (255 - alpha) + 127) / 255;
    }
    private static void putInt(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) { bytes[offset + i] = (byte) (value >>> (24 - 8 * i)); }
    }
    private static void chunk(DataOutputStream out, int kind, byte[] bytes, int length)
            throws IOException {
        out.writeInt(length);
        out.writeInt(kind);
        out.write(bytes, 0, length);
        CRC32 crc = new CRC32();
        for (int i = 0; i < 4; i++) { crc.update(kind >>> (24 - 8 * i)); }
        crc.update(bytes, 0, length);
        out.writeInt((int) crc.getValue());
    }
    private static final class IdatOutput extends OutputStream {
        private final DataOutputStream out;
        private final byte[] bytes = new byte[BUFFER];
        private int size;
        IdatOutput(DataOutputStream out) { this.out = out; }
        @Override public void write(int value) throws IOException {
            bytes[size++] = (byte) value;
            if (size == bytes.length) { finish(); }
        }
        @Override public void write(byte[] source, int offset, int length) throws IOException {
            while (length > 0) {
                int count = Math.min(length, bytes.length - size);
                System.arraycopy(source, offset, bytes, size, count);
                size += count; offset += count; length -= count;
                if (size == bytes.length) { finish(); }
            }
        }
        void finish() throws IOException {
            if (size != 0) { chunk(out, 0x49444154, bytes, size); size = 0; }
        }
    }
}

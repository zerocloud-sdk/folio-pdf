package net.zerocloud.pdf.consumer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.spi.CharsetProvider;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Decoder-only Unicode mapping data for ZXing on JDKs without ISO-8859-10,
 * -14 or -16. No product encoder code, tables or output are consulted.
 */
public final class Barcode2DCharsetProvider extends CharsetProvider {
    @Override public Iterator<Charset> charsets() {
        return Arrays.asList(charsetForName("ISO-8859-10"), charsetForName("ISO-8859-14"), charsetForName("ISO-8859-16")).iterator();
    }
    @Override public Charset charsetForName(String name) {
        for (int part : new int[] {10, 14, 16}) {
            if (name.replace("_", "-").replace("-", "").equalsIgnoreCase("ISO8859" + part)) { return new MappingCharset(part); }
        }
        return null;
    }
    private static final class MappingCharset extends Charset {
        private final char[] mapping = new char[256];
        MappingCharset(int part) {
            super("ISO-8859-" + part, new String[] {"ISO8859_" + part});
            String resource = "/net/zerocloud/pdf/fixtures/barcode-charset/8859-" + part + ".TXT";
            try (InputStream input = Barcode2DCharsetProvider.class.getResourceAsStream(resource);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("0x")) { continue; }
                    String[] values = line.split("\\s+");
                    mapping[Integer.decode(values[0])] = (char) Integer.decode(values[1]).intValue();
                }
            } catch (Exception failure) { throw new IllegalStateException("Missing independent Unicode mapping", failure); }
        }
        @Override public boolean contains(Charset other) { return name().equals(other.name()) || "US-ASCII".equals(other.name()); }
        @Override public CharsetEncoder newEncoder() { throw new UnsupportedOperationException("Independent decoder only"); }
        @Override public CharsetDecoder newDecoder() {
            return new CharsetDecoder(this, 1, 1) {
                @Override protected CoderResult decodeLoop(ByteBuffer input, CharBuffer output) {
                    while (input.hasRemaining()) {
                        if (!output.hasRemaining()) { return CoderResult.OVERFLOW; }
                        output.put(mapping[input.get() & 255]);
                    }
                    return CoderResult.UNDERFLOW;
                }
            };
        }
    }
}

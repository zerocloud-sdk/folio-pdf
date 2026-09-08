package net.zerocloud.pdf.itext7.kernel.pdf;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** An immutable PDF byte string with text conversion. @since 0.1.0 */
public final class PdfString extends PdfObject {
    // ISO 32000-1:2008, Annex D.3, Table D.2. Undefined codes have no text value.
    private static final String DIACRITICS = "\u02d8\u02c7\u02c6\u02d9\u02dd\u02db\u02da\u02dc";
    private static final String SPECIALS = "\u2022\u2020\u2021\u2026\u2014\u2013\u0192\u2044"
            + "\u2039\u203a\u2212\u2030\u201e\u201c\u201d\u2018"
            + "\u2019\u201a\u2122\ufb01\ufb02\u0141\u0152\u0160"
            + "\u0178\u017d\u0131\u0142\u0153\u0161\u017e";
    private final net.zerocloud.pdf.PdfString value;

    /** @param value text, encoded as ASCII or BOM-prefixed UTF-16BE */
    public PdfString(String value) {
        this(encodeText(value));
    }

    private static byte[] encodeText(String value) {
        Objects.requireNonNull(value, "value");
        boolean ascii = true;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isSurrogate(character)) {
                if (!Character.isHighSurrogate(character) || index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("The PDF text contains an unpaired surrogate.");
                }
                index++;
            }
            if ((character < 32 || character > 126) && character != '\t' && character != '\n' && character != '\r') {
                ascii = false;
            }
        }
        return ascii
                ? value.getBytes(StandardCharsets.US_ASCII)
                : ("\ufeff" + value).getBytes(StandardCharsets.UTF_16BE);
    }

    /** @param content exact PDF string bytes, defensively copied */
    public PdfString(byte[] content) {
        this(net.zerocloud.pdf.PdfString.of(content));
    }

    PdfString(net.zerocloud.pdf.PdfString value) {
        this.value = value;
    }

    /**
     * Interprets PDFDocEncoding or BOM-prefixed UTF-16BE/UTF-8 text.
     * Undefined codes and malformed Unicode are replaced by U+FFFD; the
     * original bytes remain available through {@link #getValueBytes()}.
     * @return the decoded text
     */
    public String getValue() {
        byte[] bytes = value.getBytes();
        if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return decodePdfDocEncoding(bytes);
    }

    private static String decodePdfDocEncoding(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length);
        for (byte encoded : bytes) {
            int code = encoded & 255;
            if (code >= 24 && code <= 31) {
                text.append(DIACRITICS.charAt(code - 24));
            } else if (code >= 128 && code <= 158) {
                text.append(SPECIALS.charAt(code - 128));
            } else if (code == 160) {
                text.append('\u20ac');
            } else if ((code < 24 && code != 9 && code != 10 && code != 13)
                    || code == 127 || code == 159 || code == 173) {
                text.append('\ufffd');
            } else {
                text.append((char) code);
            }
        }
        return text.toString();
    }

    /** @return a defensive copy of the exact PDF string bytes */
    public byte[] getValueBytes() {
        return value.getBytes();
    }

    @Override
    public byte getType() {
        return STRING;
    }

    @Override
    net.zerocloud.pdf.PdfString nativeValue() {
        return value;
    }
}

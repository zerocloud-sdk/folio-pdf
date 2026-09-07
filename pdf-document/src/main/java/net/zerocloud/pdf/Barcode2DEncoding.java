package net.zerocloud.pdf;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/** Closed character-set / ECI mapping, shared by the private symbol adapters. */
final class Barcode2DEncoding {
    final int eci;
    private final Charset charset;
    private final String highCharacters;
    private Barcode2DEncoding(int eci, Charset charset, String highCharacters) {
        this.eci = eci; this.charset = charset; this.highCharacters = highCharacters;
    }

    static Barcode2DEncoding of(String name) throws DocumentFailure {
        String key = name.toUpperCase(Locale.ROOT).replace('_', '-');
        if (key.startsWith("ECI:")) {
            int assignment;
            try { assignment = Integer.parseInt(key.substring(4)); }
            catch (NumberFormatException invalid) { throw PdfBoxBarcode2DOperations.modeFailure(); }
            if (assignment == 2) { return of("CP437"); }
            if (assignment >= 3 && assignment <= 13 || assignment >= 15 && assignment <= 18) {
                return of("ISO-8859-" + (assignment - 2));
            }
            if (assignment == 20) { return of("Shift_JIS"); }
            if (assignment == 26) { return of("UTF-8"); }
            throw PdfBoxBarcode2DOperations.modeFailure();
        }
        int eci;
        String canonical;
        if (key.equals("CP437") || key.equals("IBM437")) { eci = 2; canonical = "IBM437"; }
        else if (key.equals("SHIFT-JIS")) { eci = 20; canonical = "Shift_JIS"; }
        else if (key.equals("UTF-8")) { eci = 26; canonical = "UTF-8"; }
        else if (key.matches("ISO-8859-(?:[1-9]|10|11|13|14|15|16)")) {
            int part = Integer.parseInt(key.substring(9));
            eci = part + 2;
            canonical = key;
            if (part == 10 || part == 14 || part == 16) {
                return new Barcode2DEncoding(eci, null, BarcodeIso8859.highCharacters(part));
            }
        } else { throw PdfBoxBarcode2DOperations.modeFailure(); }
        if (!Charset.isSupported(canonical)) { throw PdfBoxBarcode2DOperations.modeFailure(); }
        return new Barcode2DEncoding(eci, Charset.forName(canonical), null);
    }

    int[] encode(String text) throws DocumentFailure {
        if (text.isEmpty()) { throw PdfBoxBarcode2DOperations.inputFailure(); }
        if (highCharacters != null) {
            int[] result = new int[text.length()];
            for (int index = 0; index < result.length; index++) {
                char character = text.charAt(index);
                int position = character < 160 ? character : highCharacters.indexOf(character);
                if (position < 0) { throw PdfBoxBarcode2DOperations.inputFailure(); }
                result[index] = character < 160 ? position : 160 + position;
            }
            return result;
        }
        try {
            ByteBuffer bytes = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
            String restored = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes.asReadOnlyBuffer()).toString();
            if (!restored.equals(text)) { throw PdfBoxBarcode2DOperations.inputFailure(); }
            int[] result = new int[bytes.remaining()];
            for (int index = 0; index < result.length; index++) { result[index] = bytes.get() & 255; }
            return result;
        } catch (CharacterCodingException failure) { throw PdfBoxBarcode2DOperations.inputFailure(); }
    }
}

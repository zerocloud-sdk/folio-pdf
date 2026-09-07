package net.zerocloud.pdf;

import net.zerocloud.pdf.composition.Barcode1D;
import net.zerocloud.pdf.composition.BarcodeText;

/** Label policy over validated caller data and canonical encoder text, never a barcode decoder. */
final class BarcodeLabels {
    private static final String CODE39_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%";
    private BarcodeLabels() { }

    static String caption(Barcode1D barcode, String encodedReadable, String encodedContent) throws DocumentFailure {
        BarcodeText text = barcode.getHumanReadable().get();
        boolean code39 = barcode.getMode() == Barcode1D.Mode.CODE39 || barcode.getMode() == Barcode1D.Mode.CODE39_EXTENDED;
        if (text.isShowStartStop() && !code39 && barcode.getMode() != Barcode1D.Mode.CODABAR) {
            throw PdfBoxBarcodeOperations.invalidMode();
        }
        if (text.getAlternateText().isPresent()) { return text.getAlternateText().get(); }
        String caption = barcode.getContent();
        switch (barcode.getMode()) {
            case CODE128_RAW: throw PdfBoxBarcodeOperations.invalidMode();
            case CODE128: caption = printable(caption); break;
            case GS1_128: caption = caption.replace('[', '(').replace(']', ')'); break;
            case CODE39:
            case CODE39_EXTENDED:
                if (barcode.isGenerateChecksum() && text.isShowChecksum()) {
                    int sum = 0;
                    for (int index = 0; index < caption.length(); index++) {
                        String symbols = barcode.getMode() == Barcode1D.Mode.CODE39_EXTENDED
                                ? fullAscii(caption.charAt(index)) : String.valueOf(caption.charAt(index));
                        for (int symbol = 0; symbol < symbols.length(); symbol++) { sum += CODE39_ALPHABET.indexOf(symbols.charAt(symbol)); }
                    }
                    caption += CODE39_ALPHABET.charAt(sum % 43);
                }
                caption = printable(caption);
                if (text.isShowStartStop()) { caption = "*" + caption + "*"; }
                break;
            case CODABAR:
                caption = barcode.isGenerateChecksum() && text.isShowChecksum() ? encodedContent : caption;
                if (!text.isShowStartStop()) { caption = caption.substring(1, caption.length() - 1); }
                break;
            case EAN13: case EAN8: case UPCA: case UPCE:
                caption = encodedReadable;
                break;
            case INTERLEAVED_2_OF_5: case MSI:
                if (text.isShowChecksum()) { caption = encodedReadable; }
                break;
            case POSTNET: case PLANET:
                int sum = 0;
                for (int index = 0; index < caption.length(); index++) { sum += caption.charAt(index) - '0'; }
                caption += (10 - sum % 10) % 10;
                break;
            default: break;
        }
        return barcode.getSupplement().isEmpty() ? caption : caption + " " + barcode.getSupplement();
    }

    private static String printable(String value) {
        StringBuilder caption = new StringBuilder();
        String hex = "0123456789ABCDEF";
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= Barcode1D.FNC1 && character <= Barcode1D.FNC4) {
                caption.append("<FNC").append(character - Barcode1D.FNC1 + 1).append('>');
            } else if (character < 32 || character == 127) {
                caption.append("\\x").append(hex.charAt(character / 16)).append(hex.charAt(character % 16));
            } else { caption.append(character); }
        }
        return caption.toString();
    }

    /** Full ASCII character expansion for label checksum arithmetic only. */
    private static String fullAscii(char character) {
        if (character == 0) { return "%U"; }
        if (character <= 26) { return "$" + (char) (character + 64); }
        if (character <= 31) { return "%" + (char) (character + 38); }
        if (character == 32 || character == 45 || character == 46 || character >= 48 && character <= 57
                || character >= 65 && character <= 90) { return String.valueOf(character); }
        if (character <= 44) { return "/" + (char) (character + 32); }
        if (character == 47) { return "/O"; }
        if (character == 58) { return "/Z"; }
        if (character <= 63) { return "%" + (char) (character + 11); }
        if (character == 64) { return "%V"; }
        if (character <= 95) { return "%" + (char) (character - 16); }
        if (character == 96) { return "%W"; }
        if (character <= 122) { return "+" + (char) (character - 32); }
        return "%" + (char) (character - 43);
    }
}

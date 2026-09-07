package net.zerocloud.pdf;

import java.math.BigInteger;

/** Bounded grammar for raw PDF417 data; Macro control belongs to its typed declaration. */
final class BarcodePdf417Raw {
    private int textMode;
    private int priorMode;
    private boolean payload;
    private BarcodePdf417Raw() { }
    static void validate(int[] words) throws DocumentFailure { new BarcodePdf417Raw().read(words); }
    private void read(int[] words) throws DocumentFailure {
        for (int word : words) { require(word >= 0 && word <= 928); }
        for (int index = 0; index < words.length;) {
            int word = words[index++];
            if (word < 900) { text(word/30); text(word%30); }
            else if (word == 900) { textMode = 0; }
            else if (word == 913) {
                require(index < words.length && words[index] <= 255); index++; payload = true;
                if (textMode >= 4) { textMode = priorMode; }
            } else if (word == 927) {
                index = eci(words, index);
            } else if (word == 901 || word == 924) {
                index = byteSequence(words, index, word == 924);
                payload = true; textMode = 0;
            } else if (word == 902) {
                int end = index;
                while (end < words.length && words[end] < 900) { end++; }
                require(end > index);
                numeric(words,index,end);
                index = end; payload = true; textMode = 0;
            } else { throw PdfBoxBarcode2DOperations.inputFailure(); }
        }
        require(payload);
    }
    private static int eci(int[] words, int index) throws DocumentFailure {
        require(index < words.length);
        int assignment = words[index++];
        require(assignment == 2 || assignment >= 3 && assignment <= 13
                || assignment >= 15 && assignment <= 18 || assignment == 20 || assignment == 26);
        return index;
    }
    private static int byteSequence(int[] words, int index, boolean completeGroups) throws DocumentFailure {
        boolean hasBytes = false, literalTail = false;
        while (true) {
            int end = index;
            while (end < words.length && words[end] < 900) { end++; }
            if (end > index) {
                if (literalTail) { literalBytes(words,index,end); }
                else { binary(words,index,end,completeGroups); }
                hasBytes = true;
                // ECI changes the charset, but cannot restart 901 grouping after its literal tail.
                literalTail = !completeGroups;
            }
            index = end;
            if (index == words.length || words[index] != 927) { break; }
            index = eci(words,index + 1);
        }
        require(hasBytes);
        return index;
    }
    private void text(int value) {
        if (textMode == 4 || textMode == 5) {
            int shift = textMode; textMode = priorMode;
            if (value <= (shift == 4 ? 26 : 28)) { payload = true; }
            else if (shift == 5) { textMode = 0; }
        } else if (textMode == 3) {
            if (value < 29) { payload = true; } else { textMode = 0; }
        } else if (textMode == 2 && value == 25) { textMode = 3; }
        else if (value <= 26) { payload = true; }
        else if (value == 29 || textMode == 1 && value == 27) {
            priorMode = textMode; textMode = value == 29 ? 5 : 4;
        } else if (value == 27) { textMode = 1; }
        else { textMode = textMode == 2 ? 0 : 2; }
    }
    private static void numeric(int[] words, int first, int end) throws DocumentFailure {
        while (first < end) {
            int stop = Math.min(end,first+15);
            BigInteger value = BigInteger.ZERO;
            while (first < stop) { value = value.multiply(BigInteger.valueOf(900)).add(BigInteger.valueOf(words[first++])); }
            String decimal = value.toString();
            require(decimal.length() > 1 && decimal.charAt(0) == '1');
        }
    }
    private static void binary(int[] words, int first, int end, boolean completeGroups) throws DocumentFailure {
        int count = end-first;
        require(!completeGroups || count%5 == 0);
        int groups = completeGroups ? count/5 : (count-1)/5;
        for (int group = 0; group < groups; group++) {
            long value = 0;
            for (int item = 0; item < 5; item++) { value = value*900+words[first++]; }
            require(value < (1L << 48));
        }
        literalBytes(words,first,end);
    }
    private static void literalBytes(int[] words, int first, int end) throws DocumentFailure {
        while (first < end) { require(words[first++] <= 255); }
    }
    private static void require(boolean valid) throws DocumentFailure {
        if (!valid) { throw PdfBoxBarcode2DOperations.inputFailure(); }
    }
}

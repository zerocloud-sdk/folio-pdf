package net.zerocloud.pdf;

/** Validates caller words and the capacity needed to preserve explicit EDIFACT termination. */
final class BarcodeDataMatrixRaw {
    private int minimumCapacity;
    private BarcodeDataMatrixRaw() { }
    static int minimumCapacity(int[] words) throws DocumentFailure {
        BarcodeDataMatrixRaw reader = new BarcodeDataMatrixRaw();
        reader.minimumCapacity = words.length;
        reader.read(words);
        return reader.minimumCapacity;
    }
    private void read(int[] words) throws DocumentFailure {
        require(words.length != 0);
        for (int word : words) { require(word >= 0 && word <= 255); }
        for (int index = 0; index < words.length;) {
            int word = words[index++];
            if (word >= 1 && word <= 229 && word != 129 || word == 232) { continue; }
            if (word == 235) {
                require(index < words.length && words[index] >= 1 && words[index] <= 128); index++;
            } else if (word == 230 || word == 238 || word == 239) {
                index = triplets(words, index, word == 238);
            } else if (word == 231) {
                require(index < words.length);
                int count = unrandomize(words, index++);
                require(count != 0);
                if (count >= 250) {
                    require(index < words.length);
                    count = (count - 249) * 250 + unrandomize(words, index++);
                }
                require(count <= words.length - index); index += count;
            } else if (word == 240) {
                index = edifact(words, index);
            } else if (word == 241) {
                require(index < words.length);
                int eci = words[index++] - 1;
                require(eci == 2 || eci >= 3 && eci <= 13 || eci >= 15 && eci <= 18 || eci == 20 || eci == 26);
            } else if (word == 233) {
                require(index == 1 && words.length >= 4);
                int sequence = words[index++], total = 17 - (sequence & 15), position = (sequence >>> 4) + 1;
                require(total >= 2 && total <= 16 && position <= total);
                require(words[index] >= 1 && words[index] <= 254 && words[index + 1] >= 1 && words[index + 1] <= 254);
                index += 2;
            } else { require(index == 1 && (word == 234 || word == 236 || word == 237)); }
        }
    }
    private static int triplets(int[] words, int index, boolean x12) throws DocumentFailure {
        int shift = 0;
        boolean upper = false;
        while (index < words.length && words[index] != 254) {
            require(index + 1 < words.length);
            int packed = 256 * words[index] + words[index + 1] - 1;
            require(packed >= 0 && packed < 64000); index += 2;
            for (int value : new int[] {packed / 1600, packed / 40 % 40, packed % 40}) {
                if (x12) { continue; }
                if (shift == 0) {
                    if (value < 3) { shift = value + 1; }
                    else { upper = false; }
                } else {
                    require(shift == 2 ? value <= 27 || value == 30 : value <= 31);
                    if (shift == 2 && value == 30) { require(!upper); upper = true; }
                    else { upper = false; }
                    shift = 0;
                }
            }
        }
        require(index < words.length && shift == 0 && !upper);
        return index + 1;
    }
    private int edifact(int[] words, int index) throws DocumentFailure {
        int bit = index * 8;
        while (bit + 6 <= words.length * 8) {
            if ((bit - index * 8) % 24 == 0) {
                // ECC200 reads an EDIFACT group only when more than two data words remain.
                minimumCapacity = Math.max(minimumCapacity, bit / 8 + 3);
            }
            int value = 0;
            for (int offset = 0; offset < 6; offset++, bit++) {
                value = (value << 1) | ((words[bit / 8] >>> (7 - bit % 8)) & 1);
            }
            if (value == 31) {
                while (bit % 8 != 0) { require((words[bit / 8] & (1 << (7 - bit % 8))) == 0); bit++; }
                return bit / 8;
            }
        }
        throw PdfBoxBarcode2DOperations.inputFailure();
    }
    private static int unrandomize(int[] words, int index) {
        return (words[index] - (149 * (index + 1)) % 255 - 1 + 256) % 256;
    }
    private static void require(boolean valid) throws DocumentFailure {
        if (!valid) { throw PdfBoxBarcode2DOperations.inputFailure(); }
    }
}

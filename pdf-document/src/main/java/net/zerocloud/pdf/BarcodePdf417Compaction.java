/*
 * Copyright 2014-2017 Robin Stuart, Daniel Gredler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from OkapiBarcode 0.5.6 Pdf417 high-level compaction only.
// Folio owns strict input, Macro framing, dimensions, ECC and PDF painting.
package net.zerocloud.pdf;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.composition.Barcode2D;
import uk.org.okapibarcode.backend.OkapiInputException;

/** Narrow compaction extension needed to preserve single-segment Macro PDF417. */
final class BarcodePdf417Compaction {
    private enum EncodingMode { FALSE, TEX, BYT, NUM }
    private static final int MAX_NUMERIC_COMPACTION_BLOCK_SIZE = 44;
    private final int[] codeWords = new int[2700];
    private int codeWordCount;
    private BarcodePdf417Compaction() { }
    static int[] encode(Barcode2D declaration) throws DocumentFailure {
        Barcode2DEncoding encoding = Barcode2DEncoding.of(declaration.getEncoding());
        int[] data = encoding.encode(declaration.getContent());
        BarcodePdf417Compaction encoder = new BarcodePdf417Compaction();
        encoder.processEci(encoding.eci);
        List<Block> blocks = createBlocks(data,declaration.getPdf417Encoding() == Barcode2D.Pdf417Encoding.BINARY);
        int offset = 0;
        for (int index = 0; index < blocks.size(); index++) {
            Block block = blocks.get(index);
            if (block.mode == EncodingMode.TEX) { encoder.processText(data,offset,block.length,index == 0); }
            else if (block.mode == EncodingMode.NUM) { encoder.processNumbers(data,offset,block.length,false); }
            else { encoder.processBytes(data,offset,block.length,index == 0 ? EncodingMode.TEX : blocks.get(index-1).mode); }
            offset += block.length;
        }
        return Arrays.copyOf(encoder.codeWords,encoder.codeWordCount);
    }
    private static final int[] ASCII_X = {
        7, 8, 8, 4, 12, 4, 4, 8, 8, 8, 12, 4, 12, 12, 12, 12, 4, 4, 4, 4, 4, 4, 4, 4,
        4, 4, 12, 8, 8, 4, 8, 8, 8, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
        1, 1, 1, 1, 8, 8, 8, 4, 8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
        2, 2, 2, 2, 8, 8, 8, 8
    };

    private static final int[] ASCII_Y = {
        26, 10, 20, 15, 18, 21, 10, 28, 23, 24, 22, 20, 13, 16, 17, 19, 0, 1, 2, 3,
        4, 5, 6, 7, 8, 9, 14, 0, 1, 23, 2, 25, 3, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 4, 5, 6, 24, 7, 8, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
        11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 21, 27, 9
    };

    private static void checkCodewordCount(int count) {
        if (count > 929) {
            throw new OkapiInputException("Too many codewords required (" + count + ", but max is 929)");
        }
    }

    private static EncodingMode chooseMode(int codeascii) {
        if (codeascii >= '0' && codeascii <= '9') {
            return EncodingMode.NUM;
        } else if (codeascii == '\t' || codeascii == '\n' || codeascii == '\r' || (codeascii >= ' ' && codeascii <= '~')) {
            return EncodingMode.TEX;
        } else {
            return EncodingMode.BYT;
        }
    }

    private static List< Block > createBlocks(int[] data, boolean forceByteCompaction) {

        if (forceByteCompaction) {
            return Collections.singletonList(new Block(EncodingMode.BYT, data.length));
        }

        List< Block > blocks = new ArrayList<>();
        Block current = null;

        for (int i = 0; i < data.length; i++) {
            EncodingMode mode = chooseMode(data[i]);
            if ((current != null && current.mode == mode) &&
                (mode != EncodingMode.NUM || current.length < MAX_NUMERIC_COMPACTION_BLOCK_SIZE)) {
                current.length++;
            } else {
                current = new Block(mode, 1);
                blocks.add(current);
            }
        }

        smoothBlocks(blocks);

        return blocks;
    }

    private static void smoothBlocks(List< Block > blocks) {

        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            EncodingMode last = (i > 0 ? blocks.get(i - 1).mode : EncodingMode.FALSE);
            EncodingMode next = (i < blocks.size() - 1 ? blocks.get(i + 1).mode : EncodingMode.FALSE);
            if (block.mode == EncodingMode.NUM) {
                if (i == 0) { /* first block */
                    if (next == EncodingMode.TEX && block.length < 8) {
                        block.mode = EncodingMode.TEX;
                    } else if (next == EncodingMode.BYT && block.length == 1) {
                        block.mode = EncodingMode.BYT;
                    }
                } else if (i == blocks.size() - 1) { /* last block */
                    if (last == EncodingMode.TEX && block.length < 7) {
                        block.mode = EncodingMode.TEX;
                    } else if (last == EncodingMode.BYT && block.length == 1) {
                        block.mode = EncodingMode.BYT;
                    }
                } else { /* not first or last block */
                    if (last == EncodingMode.BYT && next == EncodingMode.BYT && block.length < 4) {
                        block.mode = EncodingMode.BYT;
                    } else if (last == EncodingMode.BYT && next == EncodingMode.TEX && block.length < 4) {
                        block.mode = EncodingMode.TEX;
                    } else if (last == EncodingMode.TEX && next == EncodingMode.BYT && block.length < 5) {
                        block.mode = EncodingMode.TEX;
                    } else if (last == EncodingMode.TEX && next == EncodingMode.TEX && block.length < 8) {
                        block.mode = EncodingMode.TEX;
                    } else if (last == EncodingMode.NUM && next == EncodingMode.TEX && block.length < 8) {
                        block.mode = EncodingMode.TEX;
                    }
                }
            }
        }

        mergeBlocks(blocks);

        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            EncodingMode last = (i > 0 ? blocks.get(i - 1).mode : EncodingMode.FALSE);
            EncodingMode next = (i < blocks.size() - 1 ? blocks.get(i + 1).mode : EncodingMode.FALSE);
            if (block.mode == EncodingMode.TEX && i > 0) { /* not the first */
                if (i == blocks.size() - 1) { /* the last one */
                    if (last == EncodingMode.BYT && block.length == 1) {
                        block.mode = EncodingMode.BYT;
                    }
                } else { /* not the last one */
                    if (last == EncodingMode.BYT && next == EncodingMode.BYT && block.length < 5) {
                        block.mode = EncodingMode.BYT;
                    }
                    if (((last == EncodingMode.BYT && next != EncodingMode.BYT) ||
                         (last != EncodingMode.BYT && next == EncodingMode.BYT)) && (block.length < 3)) {
                        block.mode = EncodingMode.BYT;
                    }
                }
            }
        }

        mergeBlocks(blocks);
    }

    private static void mergeBlocks(List< Block > blocks) {
        for (int i = 1; i < blocks.size(); i++) {
            Block b1 = blocks.get(i - 1);
            Block b2 = blocks.get(i);
            if ((b1.mode == b2.mode) &&
                (b1.mode != EncodingMode.NUM || b1.length + b2.length <= MAX_NUMERIC_COMPACTION_BLOCK_SIZE)) {
                b1.length += b2.length;
                blocks.remove(i);
                i--;
            }
        }
    }

    private void processEci(int eci) {
        if (eci == 3) {
            return; // default, no need to specify
        }
        /* Encoding ECI assignment number, from ISO/IEC 15438 Table 8 */
        if (eci <= 899) {
            checkCodewordCount(codeWordCount + 2);
            codeWords[codeWordCount++] = 927;
            codeWords[codeWordCount++] = eci;
        } else if (eci >= 900 && eci <= 810899) {
            checkCodewordCount(codeWordCount + 3);
            codeWords[codeWordCount++] = 926;
            codeWords[codeWordCount++] = (eci / 900) - 1;
            codeWords[codeWordCount++] = eci % 900;
        } else if (eci >= 810900 && eci <= 811799) {
            checkCodewordCount(codeWordCount + 2);
            codeWords[codeWordCount++] = 925;
            codeWords[codeWordCount++] = eci - 810900;
        }
    }

    private void processText(int[] data, int start, int length, boolean skipLatch) {
        int j, blockIndext, curtable;
        int codeascii;
        int wnet = 0;
        int[] listet0 = new int[length];
        int[] listet1 = new int[length];
        int[] chainet = new int[length * 4];

        /* listet will contain the table numbers and the value of each characters */
        for (blockIndext = 0; blockIndext < length; blockIndext++) {
            codeascii = data[start + blockIndext];
            switch (codeascii) {
            case '\t':
                listet0[blockIndext] = 12;
                listet1[blockIndext] = 12;
                break;
            case '\n':
                listet0[blockIndext] = 8;
                listet1[blockIndext] = 15;
                break;
            case '\r':
                listet0[blockIndext] = 12;
                listet1[blockIndext] = 11;
                break;
            default:
                listet0[blockIndext] = ASCII_X[codeascii - 32];
                listet1[blockIndext] = ASCII_Y[codeascii - 32];
                break;
            }
        }

        curtable = 1; /* default table */
        for (j = 0; j < length; j++) {
            if ((listet0[j] & curtable) != 0) { /* The character is in the current table */
                chainet[wnet] = listet1[j];
                wnet++;
            } else { /* Obliged to change table */
                boolean flag = false; /* True if we change table for only one character */
                if (j == (length - 1)) {
                    flag = true;
                } else {
                    if ((listet0[j] & listet0[j + 1]) == 0) {
                        flag = true;
                    }
                }

                if (flag) { /* we change only one character - look for temporary switch */
                    if (((listet0[j] & 1) != 0) && (curtable == 2)) { /* T_UPP */
                        chainet[wnet] = 27;
                        chainet[wnet + 1] = listet1[j];
                        wnet += 2;
                    }
                    if ((listet0[j] & 8) != 0) { /* T_PUN */
                        chainet[wnet] = 29;
                        chainet[wnet + 1] = listet1[j];
                        wnet += 2;
                    }
                    if (!((((listet0[j] & 1) != 0) && (curtable == 2)) || ((listet0[j] & 8) != 0))) {
                        /* No temporary switch available */
                        flag = false;
                    }
                }

                if (!(flag)) {
                    int newtable;

                    if (j == (length - 1)) {
                        newtable = listet0[j];
                    } else {
                        if ((listet0[j] & listet0[j + 1]) == 0) {
                            newtable = listet0[j];
                        } else {
                            newtable = listet0[j] & listet0[j + 1];
                        }
                    }

                    /* Maintain the first if several tables are possible */
                    switch (newtable) {
                    case 3:
                    case 5:
                    case 7:
                    case 9:
                    case 11:
                    case 13:
                    case 15:
                        newtable = 1;
                        break;
                    case 6:
                    case 10:
                    case 14:
                        newtable = 2;
                        break;
                    case 12:
                        newtable = 4;
                        break;
                    }

                    /* select the switch */
                    switch (curtable) {
                    case 1:
                        switch (newtable) {
                        case 2:
                            chainet[wnet] = 27;
                            wnet++;
                            break;
                        case 4:
                            chainet[wnet] = 28;
                            wnet++;
                            break;
                        case 8:
                            chainet[wnet] = 28;
                            wnet++;
                            chainet[wnet] = 25;
                            wnet++;
                            break;
                        }
                        break;
                    case 2:
                        switch (newtable) {
                        case 1:
                            chainet[wnet] = 28;
                            wnet++;
                            chainet[wnet] = 28;
                            wnet++;
                            break;
                        case 4:
                            chainet[wnet] = 28;
                            wnet++;
                            break;
                        case 8:
                            chainet[wnet] = 28;
                            wnet++;
                            chainet[wnet] = 25;
                            wnet++;
                            break;
                        }
                        break;
                    case 4:
                        switch (newtable) {
                        case 1:
                            chainet[wnet] = 28;
                            wnet++;
                            break;
                        case 2:
                            chainet[wnet] = 27;
                            wnet++;
                            break;
                        case 8:
                            chainet[wnet] = 25;
                            wnet++;
                            break;
                        }
                        break;
                    case 8:
                        switch (newtable) {
                        case 1:
                            chainet[wnet] = 29;
                            wnet++;
                            break;
                        case 2:
                            chainet[wnet] = 29;
                            wnet++;
                            chainet[wnet] = 27;
                            wnet++;
                            break;
                        case 4:
                            chainet[wnet] = 29;
                            wnet++;
                            chainet[wnet] = 28;
                            wnet++;
                            break;
                        }
                        break;
                    }
                    curtable = newtable;
                    /* at last we add the character */
                    chainet[wnet] = listet1[j];
                    wnet++;
                }
            }
        }

        if ((wnet & 1) != 0) {
            chainet[wnet] = 29;
            wnet++;
        }

        /* Now translate the string chainet into codewords */

        checkCodewordCount(codeWordCount + (skipLatch ? 0 : 1) + (wnet / 2));

        if (!skipLatch) {
            // text compaction mode is the default mode for PDF417,
            // so no need for an explicit latch if this is the first block
            codeWords[codeWordCount] = 900;
            codeWordCount++;
        }

        for (j = 0; j < wnet; j += 2) {
            int cw_number = (30 * chainet[j]) + chainet[j + 1];
            codeWords[codeWordCount] = cw_number;
            codeWordCount++;
        }
    }

    private void processBytes(int[] data, int start, int length, EncodingMode lastMode) {
        int len = 0;
        int chunkLen = 0;
        BigInteger mantisa;
        BigInteger total;
        BigInteger word;

        mantisa = new BigInteger("0");
        total = new BigInteger("0");

        checkCodewordCount(codeWordCount + 1 + (5 * length / 6) + (length % 6));

        if (length == 1 && lastMode == EncodingMode.TEX) {
            codeWords[codeWordCount++] = 913;
            codeWords[codeWordCount++] = data[start];
        } else {
            /* select the switch for multiple of 6 bytes */
            if (length % 6 == 0) {
                codeWords[codeWordCount++] = 924;
            } else {
                codeWords[codeWordCount++] = 901;
            }

            while (len < length) {
                chunkLen = length - len;
                if (6 <= chunkLen) /* Take groups of 6 */{
                    chunkLen = 6;
                    len += chunkLen;
                    total = BigInteger.valueOf(0);

                    while ((chunkLen--) != 0) {
                        mantisa = BigInteger.valueOf(data[start++]);
                        total = total.or(mantisa.shiftLeft(chunkLen * 8));
                    }

                    chunkLen = 5;

                    while ((chunkLen--) != 0) {

                        word = total.mod(BigInteger.valueOf(900));
                        codeWords[codeWordCount + chunkLen] = word.intValue();
                        total = total.divide(BigInteger.valueOf(900));
                    }
                    codeWordCount += 5;
                } else /* If it remain a group of less than 6 bytes */{
                    len += chunkLen;
                    while ((chunkLen--) != 0) {
                        codeWords[codeWordCount++] = data[start++];
                    }
                }
            }
        }
    }

    private void processNumbers(int[] data, int start, int length, boolean skipLatch) {

        BigInteger tVal, dVal;
        int[] d = new int[16];
        int cw_count;

        StringBuilder t = new StringBuilder(length + 1);
        t.append('1');
        for (int i = 0; i < length; i++) {
            t.append((char) data[start + i]);
        }

        tVal = new BigInteger(t.toString());

        cw_count = 0;
        do {
            dVal = tVal.mod(BigInteger.valueOf(900));
            d[cw_count] = dVal.intValue();
            tVal = tVal.divide(BigInteger.valueOf(900));
            cw_count++;
        } while (tVal.compareTo(BigInteger.ZERO) == 1);

        checkCodewordCount(codeWordCount + (skipLatch ? 0 : 1) + cw_count);

        if (!skipLatch) {
            // we don't need to latch to numeric mode in some cases, e.g.
            // during numeric compaction of the Macro PDF417 segment index
            codeWords[codeWordCount++] = 902;
        }

        for (int i = cw_count - 1; i >= 0; i--) {
            codeWords[codeWordCount++] = d[i];
        }
    }

    private static class Block {

        public EncodingMode mode;
        public int length;

        public Block(EncodingMode mode, int length) {
            this.mode = mode;
            this.length = length;
        }

        @Override
        public String toString() {
            return mode + "x" + length;
        }
    }
}

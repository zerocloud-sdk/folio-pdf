/*
 * Copyright 2014 Robin Stuart
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

// Adapted from OkapiBarcode 0.5.6 DataMatrix: retain ECC200 padding, ECC and placement.
// Folio supplies explicit data codewords and dimension selection; automatic compaction
// remains in the unmodified dependency. See docs/third-party/okapibarcode-0.5.6.md.
package net.zerocloud.pdf;

import net.zerocloud.pdf.composition.Barcode2D;
import uk.org.okapibarcode.backend.ReedSolomon;
import uk.org.okapibarcode.backend.Symbol;

/** Private ECC200 grid for explicitly compacted data. */
final class BarcodeDataMatrixGrid extends Symbol {
    private final BarcodeDataMatrixSize size;
    private final int[] target;
    private int[] places;

    BarcodeDataMatrixGrid(int[] data, Barcode2D declaration) throws DocumentFailure {
        BarcodeDataMatrixSize selected = null;
        int minimumCapacity = declaration.getDataMatrixEncoding() == Barcode2D.DataMatrixEncoding.RAW
                ? BarcodeDataMatrixRaw.minimumCapacity(data) : 0;
        for (BarcodeDataMatrixSize candidate : BarcodeDataMatrixSize.matching(declaration.getDataMatrixWidth(), declaration.getDataMatrixHeight())) {
            if (candidate.dataCapacity < minimumCapacity) { continue; }
            int[] fitted = BarcodeDataMatrixCompaction.forCapacity(data, declaration, candidate.dataCapacity);
            if (fitted.length <= candidate.dataCapacity) { selected = candidate; data = fitted; break; }
        }
        if (selected == null) { throw PdfBoxBarcode2DOperations.inputFailure(); }
        size = selected;
        int blocks = (size.dataCapacity + 2) / size.dataBlock;
        target = new int[size.dataCapacity + blocks * size.eccBlock];
        System.arraycopy(data, 0, target, 0, data.length);
        addPadBits(data.length, size.dataCapacity - data.length);
        calculateErrorCorrection(size.dataCapacity, size.dataBlock, size.eccBlock, size.width == 144);
        encode();
        plotSymbol();
    }

    @Override protected void encode() {
        int H = size.height, W = size.width, FH = size.regionHeight, FW = size.regionWidth;
        int NC, NR, i, x, y, v;
        int[] grid;
        NC = W - 2 * (W / FW);
        NR = H - 2 * (H / FH);
        places = new int[NC * NR];
        placeData(NR, NC);
        grid = new int[W * H];
        for (i = 0; i < (W * H); i++) {
            grid[i] = 0;
        }
        for (y = 0; y < H; y += FH) {
            for (x = 0; x < W; x++) {
                grid[y * W + x] = 1;
            }
            for (x = 0; x < W; x += 2) {
                grid[(y + FH - 1) * W + x] = 1;
            }
        }
        for (x = 0; x < W; x += FW) {
            for (y = 0; y < H; y++) {
                grid[y * W + x] = 1;
            }
            for (y = 0; y < H; y += 2) {
                grid[y * W + x + FW - 1] = 1;
            }
        }
        for (y = 0; y < NR; y++) {
            for (x = 0; x < NC; x++) {
                v = places[(NR - y - 1) * NC + x];
                if (v == 1 || (v > 7 && (target[(v >> 3) - 1] & (1 << (v & 7))) != 0)) {
                    grid[(1 + y + 2 * (y / (FH - 2))) * W + 1 + x + 2 * (x / (FW - 2))] = 1;
                }
            }
        }

        readable = "";
        pattern = new String[H];
        rowCount = H;
        rowHeight = new int[H];

        StringBuilder pat = new StringBuilder(W);
        for (y = H - 1; y >= 0; y--) {
            pattern[(H - y) - 1] = bin2pat(grid, W * y, W, pat);
            rowHeight[(H - y) - 1] = moduleWidth;
        }

    }

    private void calculateErrorCorrection(int bytes, int datablock, int rsblock, boolean skew) {
        // calculate and append ecc code, and if necessary interleave
        ReedSolomon rs = ReedSolomon.get(0x12d, rsblock, 1, true);
        int blocks = (bytes + 2) / datablock, b;
        int n, p;
        for (b = 0; b < blocks; b++) {
            int[] buf = new int[256];
            int[] ecc = new int[256];
            p = 0;
            for (n = b; n < bytes; n += blocks) {
                buf[p++] = target[n];
            }
            int[] result = rs.encode(p, buf);
            System.arraycopy(result, 0, ecc, 0, rsblock);
            p = rsblock - 1; // comes back reversed
            for (n = b; n < rsblock * blocks; n += blocks) {
                if (skew) {
                    /* Rotate ecc data to make 144x144 size symbols acceptable */
                    /* See http://groups.google.com/group/postscriptbarcode/msg/5ae8fda7757477da */
                    if (b < 8) {
                        target[bytes + n + 2] = ecc[p--];
                    } else {
                        target[bytes + n - 8] = ecc[p--];
                    }
                } else {
                    target[bytes + n] = ecc[p--];
                }
            }
        }
    }

    private void addPadBits(int tp, int tail_length) {
        int i, prn, temp;

        for (i = tail_length; i > 0; i--) {
            if (i == tail_length) {
                target[tp] = 129;
                tp++; /* Pad */
            } else {
                prn = ((149 * (tp + 1)) % 253) + 1;
                temp = 129 + prn;
                if (temp <= 254) {
                    target[tp] = temp;
                    tp++;
                } else {
                    target[tp] = temp - 254;
                    tp++;
                }
            }
        }
    }

    private void placeData(int NR, int NC) {
        int r, c, p;
        // invalidate
        for (r = 0; r < NR; r++) {
            for (c = 0; c < NC; c++) {
                places[r * NC + c] = 0;
            }
        }
        // start
        p = 1;
        r = 4;
        c = 0;
        do {
            // check corner
            if (r == NR && (c == 0)) {
                placeCornerA(NR, NC, p++);
            }
            if (r == NR - 2 && (c == 0) && ((NC % 4) != 0)) {
                placeCornerB(NR, NC, p++);
            }
            if (r == NR - 2 && (c == 0) && (NC % 8) == 4) {
                placeCornerC(NR, NC, p++);
            }
            if (r == NR + 4 && c == 2 && ((NC % 8) == 0)) {
                placeCornerD(NR, NC, p++);
            }
            // up/right
            do {
                if (r < NR && c >= 0 && (places[r * NC + c] == 0)) {
                    placeBlock(NR, NC, r, c, p++);
                }
                r -= 2;
                c += 2;
            } while (r >= 0 && c < NC);
            r++;
            c += 3;
            // down/left
            do {
                if (r >= 0 && c < NC && (places[r * NC + c] == 0)) {
                    placeBlock(NR, NC, r, c, p++);
                }
                r += 2;
                c -= 2;
            } while (r < NR && c >= 0);
            r += 3;
            c++;
        } while (r < NR || c < NC);
        // unfilled corner
        if (places[NR * NC - 1] == 0) {
            places[NR * NC - 1] = places[NR * NC - NC - 2] = 1;
        }
    }

    private void placeCornerA(int NR, int NC, int p) {
        placeBit(NR, NC, NR - 1, 0, p, 7);
        placeBit(NR, NC, NR - 1, 1, p, 6);
        placeBit(NR, NC, NR - 1, 2, p, 5);
        placeBit(NR, NC, 0, NC - 2, p, 4);
        placeBit(NR, NC, 0, NC - 1, p, 3);
        placeBit(NR, NC, 1, NC - 1, p, 2);
        placeBit(NR, NC, 2, NC - 1, p, 1);
        placeBit(NR, NC, 3, NC - 1, p, 0);
    }

    private void placeCornerB(int NR, int NC, int p) {
        placeBit(NR, NC, NR - 3, 0, p, 7);
        placeBit(NR, NC, NR - 2, 0, p, 6);
        placeBit(NR, NC, NR - 1, 0, p, 5);
        placeBit(NR, NC, 0, NC - 4, p, 4);
        placeBit(NR, NC, 0, NC - 3, p, 3);
        placeBit(NR, NC, 0, NC - 2, p, 2);
        placeBit(NR, NC, 0, NC - 1, p, 1);
        placeBit(NR, NC, 1, NC - 1, p, 0);
    }

    private void placeCornerC(int NR, int NC, int p) {
        placeBit(NR, NC, NR - 3, 0, p, 7);
        placeBit(NR, NC, NR - 2, 0, p, 6);
        placeBit(NR, NC, NR - 1, 0, p, 5);
        placeBit(NR, NC, 0, NC - 2, p, 4);
        placeBit(NR, NC, 0, NC - 1, p, 3);
        placeBit(NR, NC, 1, NC - 1, p, 2);
        placeBit(NR, NC, 2, NC - 1, p, 1);
        placeBit(NR, NC, 3, NC - 1, p, 0);
    }

    private void placeCornerD(int NR, int NC, int p) {
        placeBit(NR, NC, NR - 1, 0, p, 7);
        placeBit(NR, NC, NR - 1, NC - 1, p, 6);
        placeBit(NR, NC, 0, NC - 3, p, 5);
        placeBit(NR, NC, 0, NC - 2, p, 4);
        placeBit(NR, NC, 0, NC - 1, p, 3);
        placeBit(NR, NC, 1, NC - 3, p, 2);
        placeBit(NR, NC, 1, NC - 2, p, 1);
        placeBit(NR, NC, 1, NC - 1, p, 0);
    }

    private void placeBlock(int NR, int NC, int r, int c, int p) {
        placeBit(NR, NC, r - 2, c - 2, p, 7);
        placeBit(NR, NC, r - 2, c - 1, p, 6);
        placeBit(NR, NC, r - 1, c - 2, p, 5);
        placeBit(NR, NC, r - 1, c - 1, p, 4);
        placeBit(NR, NC, r - 1, c - 0, p, 3);
        placeBit(NR, NC, r - 0, c - 2, p, 2);
        placeBit(NR, NC, r - 0, c - 1, p, 1);
        placeBit(NR, NC, r - 0, c - 0, p, 0);
    }

    private void placeBit(int NR, int NC, int r, int c, int p, int b) {
        if (r < 0) {
            r += NR;
            c += 4 - ((NR + 4) % 8);
        }
        if (c < 0) {
            c += NC;
            r += 4 - ((NC + 4) % 8);
        }
        places[r * NC + c] = (p << 3) + b;
    }

}

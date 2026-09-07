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

// ECC200 table data adapted from OkapiBarcode 0.5.6; see docs/third-party/okapibarcode-0.5.6.md.
package net.zerocloud.pdf;

import java.util.ArrayList;
import java.util.List;

/** The standard 30 ECC200 sizes, ordered by data capacity. */
final class BarcodeDataMatrixSize {
    private static final int[][] SIZES = {
        {10,10,1,10,10,3,3,5},
        {12,12,2,12,12,5,5,7},
        {18,8,25,18,8,5,5,7},
        {14,14,3,14,14,8,8,10},
        {32,8,26,16,8,10,10,11},
        {16,16,4,16,16,12,12,12},
        {26,12,27,26,12,16,16,14},
        {18,18,5,18,18,18,18,14},
        {20,20,6,20,20,22,22,18},
        {36,12,28,18,12,22,22,18},
        {22,22,7,22,22,30,30,20},
        {36,16,29,18,16,32,32,24},
        {24,24,8,24,24,36,36,24},
        {26,26,9,26,26,44,44,28},
        {48,16,30,24,16,49,49,28},
        {32,32,10,16,16,62,62,36},
        {36,36,11,18,18,86,86,42},
        {40,40,12,20,20,114,114,48},
        {44,44,13,22,22,144,144,56},
        {48,48,14,24,24,174,174,68},
        {52,52,15,26,26,204,102,42},
        {64,64,16,16,16,280,140,56},
        {72,72,17,18,18,368,92,36},
        {80,80,18,20,20,456,114,48},
        {88,88,19,22,22,576,144,56},
        {96,96,20,24,24,696,174,68},
        {104,104,21,26,26,816,136,56},
        {120,120,22,20,20,1050,175,68},
        {132,132,23,22,22,1304,163,62},
        {144,144,24,24,24,1558,156,62}
    };
    final int width;
    final int height;
    final int okapiIndex;
    final int regionWidth;
    final int regionHeight;
    final int dataCapacity;
    final int dataBlock;
    final int eccBlock;
    private BarcodeDataMatrixSize(int[] size) {
        width = size[0]; height = size[1]; okapiIndex = size[2];
        regionWidth = size[3]; regionHeight = size[4]; dataCapacity = size[5];
        dataBlock = size[6]; eccBlock = size[7];
    }
    static List<BarcodeDataMatrixSize> matching(int width, int height) throws DocumentFailure {
        List<BarcodeDataMatrixSize> matches = new ArrayList<BarcodeDataMatrixSize>();
        for (int[] size : SIZES) {
            if ((width == 0 || width == size[0]) && (height == 0 || height == size[1])) { matches.add(new BarcodeDataMatrixSize(size)); }
        }
        if (matches.isEmpty()) { throw PdfBoxBarcode2DOperations.geometryFailure(); }
        return matches;
    }
}

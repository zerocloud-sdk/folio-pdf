/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from ZXing 3.5.3 for repository-only T31 independent acceptance.
// Package/type names changed; Structured Append headers are consumed and exposed as metadata.
// No encoder code or product runtime dependency is introduced.
package net.zerocloud.pdf.acceptance;

import com.google.zxing.FormatException;

/**
 * The T31DataMatrixVersion object encapsulates attributes about a particular
 * size Data Matrix Code.
 *
 * @author bbrown@google.com (Brian Brown)
 */
final class T31DataMatrixVersion {

  private static final T31DataMatrixVersion[] VERSIONS = buildVersions();

  private final int versionNumber;
  private final int symbolSizeRows;
  private final int symbolSizeColumns;
  private final int dataRegionSizeRows;
  private final int dataRegionSizeColumns;
  private final ECBlocks ecBlocks;
  private final int totalCodewords;

  private T31DataMatrixVersion(int versionNumber,
                  int symbolSizeRows,
                  int symbolSizeColumns,
                  int dataRegionSizeRows,
                  int dataRegionSizeColumns,
                  ECBlocks ecBlocks) {
    this.versionNumber = versionNumber;
    this.symbolSizeRows = symbolSizeRows;
    this.symbolSizeColumns = symbolSizeColumns;
    this.dataRegionSizeRows = dataRegionSizeRows;
    this.dataRegionSizeColumns = dataRegionSizeColumns;
    this.ecBlocks = ecBlocks;

    // Calculate the total number of codewords
    int total = 0;
    int ecCodewords = ecBlocks.getECCodewords();
    ECB[] ecbArray = ecBlocks.getECBlocks();
    for (ECB ecBlock : ecbArray) {
      total += ecBlock.getCount() * (ecBlock.getDataCodewords() + ecCodewords);
    }
    this.totalCodewords = total;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public int getSymbolSizeRows() {
    return symbolSizeRows;
  }

  public int getSymbolSizeColumns() {
    return symbolSizeColumns;
  }

  public int getDataRegionSizeRows() {
    return dataRegionSizeRows;
  }

  public int getDataRegionSizeColumns() {
    return dataRegionSizeColumns;
  }

  public int getTotalCodewords() {
    return totalCodewords;
  }

  ECBlocks getECBlocks() {
    return ecBlocks;
  }

  /**
   * <p>Deduces version information from Data Matrix dimensions.</p>
   *
   * @param numRows Number of rows in modules
   * @param numColumns Number of columns in modules
   * @return T31DataMatrixVersion for a Data Matrix Code of those dimensions
   * @throws FormatException if dimensions do correspond to a valid Data Matrix size
   */
  public static T31DataMatrixVersion getVersionForDimensions(int numRows, int numColumns) throws FormatException {
    if ((numRows & 0x01) != 0 || (numColumns & 0x01) != 0) {
      throw FormatException.getFormatInstance();
    }

    for (T31DataMatrixVersion version : VERSIONS) {
      if (version.symbolSizeRows == numRows && version.symbolSizeColumns == numColumns) {
        return version;
      }
    }

    throw FormatException.getFormatInstance();
  }

  /**
   * <p>Encapsulates a set of error-correction blocks in one symbol version. Most versions will
   * use blocks of differing sizes within one version, so, this encapsulates the parameters for
   * each set of blocks. It also holds the number of error-correction codewords per block since it
   * will be the same across all blocks within one version.</p>
   */
  static final class ECBlocks {
    private final int ecCodewords;
    private final ECB[] ecBlocks;

    private ECBlocks(int ecCodewords, ECB ecBlocks) {
      this.ecCodewords = ecCodewords;
      this.ecBlocks = new ECB[] { ecBlocks };
    }

    private ECBlocks(int ecCodewords, ECB ecBlocks1, ECB ecBlocks2) {
      this.ecCodewords = ecCodewords;
      this.ecBlocks = new ECB[] { ecBlocks1, ecBlocks2 };
    }

    int getECCodewords() {
      return ecCodewords;
    }

    ECB[] getECBlocks() {
      return ecBlocks;
    }
  }

  /**
   * <p>Encapsulates the parameters for one error-correction block in one symbol version.
   * This includes the number of data codewords, and the number of times a block with these
   * parameters is used consecutively in the Data Matrix code version's format.</p>
   */
  static final class ECB {
    private final int count;
    private final int dataCodewords;

    private ECB(int count, int dataCodewords) {
      this.count = count;
      this.dataCodewords = dataCodewords;
    }

    int getCount() {
      return count;
    }

    int getDataCodewords() {
      return dataCodewords;
    }
  }

  @Override
  public String toString() {
    return String.valueOf(versionNumber);
  }

  /**
   * See ISO 16022:2006 5.5.1 Table 7
   */
  private static T31DataMatrixVersion[] buildVersions() {
    return new T31DataMatrixVersion[]{
        new T31DataMatrixVersion(1, 10, 10, 8, 8,
            new ECBlocks(5, new ECB(1, 3))),
        new T31DataMatrixVersion(2, 12, 12, 10, 10,
            new ECBlocks(7, new ECB(1, 5))),
        new T31DataMatrixVersion(3, 14, 14, 12, 12,
            new ECBlocks(10, new ECB(1, 8))),
        new T31DataMatrixVersion(4, 16, 16, 14, 14,
            new ECBlocks(12, new ECB(1, 12))),
        new T31DataMatrixVersion(5, 18, 18, 16, 16,
            new ECBlocks(14, new ECB(1, 18))),
        new T31DataMatrixVersion(6, 20, 20, 18, 18,
            new ECBlocks(18, new ECB(1, 22))),
        new T31DataMatrixVersion(7, 22, 22, 20, 20,
            new ECBlocks(20, new ECB(1, 30))),
        new T31DataMatrixVersion(8, 24, 24, 22, 22,
            new ECBlocks(24, new ECB(1, 36))),
        new T31DataMatrixVersion(9, 26, 26, 24, 24,
            new ECBlocks(28, new ECB(1, 44))),
        new T31DataMatrixVersion(10, 32, 32, 14, 14,
            new ECBlocks(36, new ECB(1, 62))),
        new T31DataMatrixVersion(11, 36, 36, 16, 16,
            new ECBlocks(42, new ECB(1, 86))),
        new T31DataMatrixVersion(12, 40, 40, 18, 18,
            new ECBlocks(48, new ECB(1, 114))),
        new T31DataMatrixVersion(13, 44, 44, 20, 20,
            new ECBlocks(56, new ECB(1, 144))),
        new T31DataMatrixVersion(14, 48, 48, 22, 22,
            new ECBlocks(68, new ECB(1, 174))),
        new T31DataMatrixVersion(15, 52, 52, 24, 24,
            new ECBlocks(42, new ECB(2, 102))),
        new T31DataMatrixVersion(16, 64, 64, 14, 14,
            new ECBlocks(56, new ECB(2, 140))),
        new T31DataMatrixVersion(17, 72, 72, 16, 16,
            new ECBlocks(36, new ECB(4, 92))),
        new T31DataMatrixVersion(18, 80, 80, 18, 18,
            new ECBlocks(48, new ECB(4, 114))),
        new T31DataMatrixVersion(19, 88, 88, 20, 20,
            new ECBlocks(56, new ECB(4, 144))),
        new T31DataMatrixVersion(20, 96, 96, 22, 22,
            new ECBlocks(68, new ECB(4, 174))),
        new T31DataMatrixVersion(21, 104, 104, 24, 24,
            new ECBlocks(56, new ECB(6, 136))),
        new T31DataMatrixVersion(22, 120, 120, 18, 18,
            new ECBlocks(68, new ECB(6, 175))),
        new T31DataMatrixVersion(23, 132, 132, 20, 20,
            new ECBlocks(62, new ECB(8, 163))),
        new T31DataMatrixVersion(24, 144, 144, 22, 22,
            new ECBlocks(62, new ECB(8, 156), new ECB(2, 155))),
        new T31DataMatrixVersion(25, 8, 18, 6, 16,
            new ECBlocks(7, new ECB(1, 5))),
        new T31DataMatrixVersion(26, 8, 32, 6, 14,
            new ECBlocks(11, new ECB(1, 10))),
        new T31DataMatrixVersion(27, 12, 26, 10, 24,
            new ECBlocks(14, new ECB(1, 16))),
        new T31DataMatrixVersion(28, 12, 36, 10, 16,
            new ECBlocks(18, new ECB(1, 22))),
        new T31DataMatrixVersion(29, 16, 36, 14, 16,
            new ECBlocks(24, new ECB(1, 32))),
        new T31DataMatrixVersion(30, 16, 48, 14, 22,
            new ECBlocks(28, new ECB(1, 49))),

        // extended forms as specified in
        // ISO 21471:2020 (DMRE) 5.5.1 Table 7
        new T31DataMatrixVersion(31, 8, 48, 6, 22,
            new ECBlocks(15, new ECB(1, 18))),
        new T31DataMatrixVersion(32, 8, 64, 6, 14,
            new ECBlocks(18, new ECB(1, 24))),
        new T31DataMatrixVersion(33, 8, 80, 6, 18,
            new ECBlocks(22, new ECB(1, 32))),
        new T31DataMatrixVersion(34, 8, 96, 6, 22,
            new ECBlocks(28, new ECB(1, 38))),
        new T31DataMatrixVersion(35, 8, 120, 6, 18,
            new ECBlocks(32, new ECB(1, 49))),
        new T31DataMatrixVersion(36, 8, 144, 6, 22,
            new ECBlocks(36, new ECB(1, 63))),
        new T31DataMatrixVersion(37, 12, 64, 10, 14,
            new ECBlocks(27, new ECB(1, 43))),
        new T31DataMatrixVersion(38, 12, 88, 10, 20,
            new ECBlocks(36, new ECB(1, 64))),
        new T31DataMatrixVersion(39, 16, 64, 14, 14,
            new ECBlocks(36, new ECB(1, 62))),
        new T31DataMatrixVersion(40, 20, 36, 18, 16,
            new ECBlocks(28, new ECB(1, 44))),
        new T31DataMatrixVersion(41, 20, 44, 18, 20,
            new ECBlocks(34, new ECB(1, 56))),
        new T31DataMatrixVersion(42, 20, 64, 18, 14,
            new ECBlocks(42, new ECB(1, 84))),
        new T31DataMatrixVersion(43, 22, 48, 20, 22,
            new ECBlocks(38, new ECB(1, 72))),
        new T31DataMatrixVersion(44, 24, 48, 22, 22,
            new ECBlocks(41, new ECB(1, 80))),
        new T31DataMatrixVersion(45, 24, 64, 22, 14,
            new ECBlocks(46, new ECB(1, 108))),
        new T31DataMatrixVersion(46, 26, 40, 24, 18,
            new ECBlocks(38, new ECB(1, 70))),
        new T31DataMatrixVersion(47, 26, 48, 24, 22,
            new ECBlocks(42, new ECB(1, 90))),
        new T31DataMatrixVersion(48, 26, 64, 24, 14,
            new ECBlocks(50, new ECB(1, 118)))
    };
  }

}

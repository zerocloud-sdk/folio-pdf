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

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

/**
 * <p>The main class which implements Data Matrix Code decoding -- as opposed to locating and extracting
 * the Data Matrix Code from an image.</p>
 *
 * @author bbrown@google.com (Brian Brown)
 */
final class T31DataMatrixDecoder {

  private final ReedSolomonDecoder rsDecoder;

  public T31DataMatrixDecoder() {
    rsDecoder = new ReedSolomonDecoder(GenericGF.DATA_MATRIX_FIELD_256);
  }

  /**
   * <p>Convenience method that can decode a Data Matrix Code represented as a 2D array of booleans.
   * "true" is taken to mean a black module.</p>
   *
   * @param image booleans representing white/black Data Matrix Code modules
   * @return text and bytes encoded within the Data Matrix Code
   * @throws FormatException if the Data Matrix Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(boolean[][] image) throws FormatException, ChecksumException {
    return decode(BitMatrix.parse(image));
  }

  /**
   * <p>Decodes a Data Matrix Code represented as a {@link BitMatrix}. A 1 or "true" is taken
   * to mean a black module.</p>
   *
   * @param bits booleans representing white/black Data Matrix Code modules
   * @return text and bytes encoded within the Data Matrix Code
   * @throws FormatException if the Data Matrix Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(BitMatrix bits) throws FormatException, ChecksumException {

    // Construct a parser and read version, error-correction level
    T31DataMatrixBitMatrixParser parser = new T31DataMatrixBitMatrixParser(bits);
    T31DataMatrixVersion version = parser.getVersion();

    // Read codewords
    byte[] codewords = parser.readCodewords();
    // Separate into data blocks
    T31DataMatrixDataBlock[] dataBlocks = T31DataMatrixDataBlock.getDataBlocks(codewords, version);

    // Count total number of data bytes
    int totalBytes = 0;
    for (T31DataMatrixDataBlock db : dataBlocks) {
      totalBytes += db.getNumDataCodewords();
    }
    byte[] resultBytes = new byte[totalBytes];

    int errorsCorrected = 0;
    int dataBlocksCount = dataBlocks.length;
    // Error-correct and copy data blocks together into a stream of bytes
    for (int j = 0; j < dataBlocksCount; j++) {
      T31DataMatrixDataBlock dataBlock = dataBlocks[j];
      byte[] codewordBytes = dataBlock.getCodewords();
      int numDataCodewords = dataBlock.getNumDataCodewords();
      errorsCorrected += correctErrors(codewordBytes, numDataCodewords);
      for (int i = 0; i < numDataCodewords; i++) {
        // De-interlace data blocks.
        resultBytes[i * dataBlocksCount + j] = codewordBytes[i];
      }
    }

    // Decode the contents of that stream of bytes
    DecoderResult result = T31DataMatrixDecodedBitStreamParser.decode(resultBytes);
    result.setErrorsCorrected(errorsCorrected);
    return result;
  }

  /**
   * <p>Given data and error-correction codewords received, possibly corrupted by errors, attempts to
   * correct the errors in-place using Reed-Solomon error correction.</p>
   *
   * @param codewordBytes data and error correction codewords
   * @param numDataCodewords number of codewords that are data bytes
   * @return the number of errors corrected
   * @throws ChecksumException if error correction fails
   */
  private int correctErrors(byte[] codewordBytes, int numDataCodewords) throws ChecksumException {
    int numCodewords = codewordBytes.length;
    // First read into an array of ints
    int[] codewordsInts = new int[numCodewords];
    for (int i = 0; i < numCodewords; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    int errorsCorrected = 0;
    try {
      errorsCorrected = rsDecoder.decodeWithECCount(codewordsInts, codewordBytes.length - numDataCodewords);
    } catch (ReedSolomonException ignored) {
      throw ChecksumException.getChecksumInstance();
    }
    // Copy back into array of bytes -- only need to worry about the bytes that were data
    // We don't care about errors in the error-correction codewords
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
    return errorsCorrected;
  }

  /** Independent semantic header observation, outside decoded text. */
  static final class Headers {
    int position;
    int total;
    int fileId;
    boolean readerProgramming;
    int macro;
    int paddingStart = -1;
  }
}

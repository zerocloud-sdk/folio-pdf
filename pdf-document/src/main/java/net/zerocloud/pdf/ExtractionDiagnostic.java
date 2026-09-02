package net.zerocloud.pdf;

import java.util.Arrays;
import java.util.Objects;

/** An immutable public diagnostic for uncertain extraction data. @since 0.1.0 */
public final class ExtractionDiagnostic {

    /** Stable extraction diagnostic categories. */
    public enum Code {
        /** No defensible Unicode mapping exists for a shown character code. */
        MISSING_UNICODE_MAPPING,
        /** Explicit and independently derivable standard mappings disagree. */
        CONTRADICTORY_UNICODE_MAPPING
    }

    private final Code code;
    private final int pageNumber;
    private final int textItemIndex;
    private final byte[] sourceCode;
    private final String message;

    ExtractionDiagnostic(
            Code code,
            int pageNumber,
            int textItemIndex,
            byte[] sourceCode,
            String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.pageNumber = pageNumber;
        this.textItemIndex = textItemIndex;
        this.sourceCode = Arrays.copyOf(sourceCode, sourceCode.length);
        this.message = Objects.requireNonNull(message, "message");
    }

    /** @return stable code */ public Code getCode() { return code; }
    /** @return one-based page number */ public int getPageNumber() { return pageNumber; }
    /** @return one-based page-local text item index */
    public int getTextItemIndex() { return textItemIndex; }
    /** @return defensive source-code copy */
    public byte[] getSourceCode() { return Arrays.copyOf(sourceCode, sourceCode.length); }
    /** @return safe diagnostic message */ public String getMessage() { return message; }
}

package net.zerocloud.pdf;

/** Single authority for the stable T19 font-program failure contract. */
final class PdfBoxFontFailures {

    private PdfBoxFontFailures() {
    }

    static DocumentFailure sourceInvalid() {
        return failure(
                DocumentFailureCode.FONT_SOURCE_INVALID,
                "The font source could not be loaded safely.");
    }

    static DocumentFailure formatUnsupported() {
        return failure(
                DocumentFailureCode.FONT_FORMAT_UNSUPPORTED,
                "The font format or profile is unsupported.");
    }

    static DocumentFailure embeddingRestricted() {
        return failure(
                DocumentFailureCode.FONT_EMBEDDING_RESTRICTED,
                "The font embedding permissions reject this operation.");
    }

    static DocumentFailure operationLimitExceeded() {
        return failure(
                DocumentFailureCode.FONT_LIMIT_EXCEEDED,
                "The font operation limit was exceeded.");
    }

    private static DocumentFailure failure(
            DocumentFailureCode code,
            String diagnostic) {
        return new DocumentFailure(
                code,
                PdfBoxPositionedTextOperations.CAPABILITY_ID,
                diagnostic);
    }
}

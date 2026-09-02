package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Keeps backend version parsing and output markers behind the workflow seam. */
final class PdfBoxVersionPolicy {

    private PdfBoxVersionPolicy() {
    }

    private static final int HEADER_SEARCH_LIMIT = 1024;

    static PdfVersionInfo inspect(
            PDDocument document,
            PdfVersion declaredHeader) throws DocumentFailure {
        PdfVersion header = declaredHeader == null
                ? fromBackend(document.getDocument().getVersion())
                : declaredHeader;
        COSBase rawCatalogVersion = document.getDocumentCatalog()
                .getCOSObject().getItem(COSName.VERSION);
        PdfVersion catalog = null;
        if (rawCatalogVersion != null) {
            COSBase resolved = rawCatalogVersion instanceof COSObject
                    ? ((COSObject) rawCatalogVersion).getObject()
                    : rawCatalogVersion;
            if (!(resolved instanceof COSName)) {
                throw PdfBoxWorkflowEngine.versionFailure(
                        DocumentFailureCode.PDF_VERSION_INVALID,
                        "The PDF version declaration is malformed.");
            }
            catalog = fromText(((COSName) resolved).getName());
        }
        PdfVersion effective = catalog != null
                && compare(catalog, header) > 0 ? catalog : header;
        return new PdfVersionInfo(header, catalog, effective);
    }

    static PdfVersion inspectHeader(Path path) throws DocumentFailure {
        byte[] prefix = new byte[HEADER_SEARCH_LIMIT];
        int count = 0;
        try (InputStream input = Files.newInputStream(path)) {
            while (count < prefix.length) {
                int read = input.read(prefix, count, prefix.length - count);
                if (read < 0) {
                    break;
                }
                count += read;
            }
        } catch (IOException | RuntimeException failure) {
            throw PdfBoxWorkflowEngine.failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
        return inspectHeader(prefix, count);
    }

    static PdfVersion inspectHeader(byte[] bytes) throws DocumentFailure {
        return inspectHeader(bytes, Math.min(bytes.length, HEADER_SEARCH_LIMIT));
    }

    private static PdfVersion inspectHeader(byte[] bytes, int length)
            throws DocumentFailure {
        int marker = -1;
        for (int index = 0; index + 5 <= length; index++) {
            if (bytes[index] == '%'
                    && bytes[index + 1] == 'P'
                    && bytes[index + 2] == 'D'
                    && bytes[index + 3] == 'F'
                    && bytes[index + 4] == '-') {
                marker = index;
                break;
            }
        }
        if (marker < 0) {
            throw PdfBoxWorkflowEngine.failure(
                    DocumentFailureCode.SOURCE_READ_FAILED,
                    "The source could not be opened as a PDF document.");
        }
        int value = marker + 5;
        if (value + 3 > length
                || !asciiDigit(bytes[value])
                || bytes[value + 1] != '.'
                || !asciiDigit(bytes[value + 2])
                || (value + 3 < length && !headerWhitespace(bytes[value + 3]))) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.PDF_VERSION_INVALID,
                    "The PDF version declaration is malformed.");
        }
        PdfVersion version = requireSupported(
                bytes[value] - '0',
                bytes[value + 2] - '0');
        if (version.getMajor() == 1 && marker != 0) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.PDF_VERSION_INVALID,
                    "A PDF 1.x header must begin at byte zero.");
        }
        return version;
    }

    private static boolean asciiDigit(byte value) {
        return value >= '0' && value <= '9';
    }

    private static boolean headerWhitespace(byte value) {
        return value == 0
                || value == 9
                || value == 10
                || value == 12
                || value == 13
                || value == 32;
    }

    static void setOutputVersion(PDDocument document, PdfVersion version) {
        document.getDocument().setVersion(version.backendValue());
        document.getDocumentCatalog().getCOSObject().removeItem(COSName.VERSION);
    }

    private static PdfVersion fromBackend(float value) throws DocumentFailure {
        int major = (int) value;
        int minor = Math.round((value - major) * 10.0f);
        if (Math.abs(value - (major + minor / 10.0f)) > 0.0001f) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.PDF_VERSION_INVALID,
                    "The PDF version declaration is malformed.");
        }
        return requireSupported(major, minor);
    }

    private static PdfVersion fromText(String value) throws DocumentFailure {
        if (value == null
                || value.length() != 3
                || value.charAt(1) != '.'
                || value.charAt(0) < '0'
                || value.charAt(0) > '9'
                || value.charAt(2) < '0'
                || value.charAt(2) > '9') {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.PDF_VERSION_INVALID,
                    "The PDF version declaration is malformed.");
        }
        return requireSupported(
                value.charAt(0) - '0',
                value.charAt(2) - '0');
    }

    private static PdfVersion requireSupported(int major, int minor)
            throws DocumentFailure {
        PdfVersion version = PdfVersion.from(major, minor);
        if (version == null) {
            throw PdfBoxWorkflowEngine.versionFailure(
                    DocumentFailureCode.PDF_VERSION_UNSUPPORTED,
                    "The declared PDF version is not supported.");
        }
        return version;
    }

    private static int compare(PdfVersion left, PdfVersion right) {
        int major = left.getMajor() - right.getMajor();
        return major != 0 ? major : left.getMinor() - right.getMinor();
    }
}

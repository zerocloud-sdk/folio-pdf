package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Writes stable identifiers for new split products that do not already have one. */
final class PdfBoxSplitProductWriter {

    private static final byte[] IDENTIFIER_PLACEHOLDER = new byte[32];

    private PdfBoxSplitProductWriter() {
    }

    static void save(
            PDDocument document,
            Path target,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        if (document.isEncrypted()) {
            // PDFBox creates the identifier needed by the security handler.
            // A protected document must not be reused for a second save.
            saveDocument(document, target, resources);
            return;
        }
        COSDictionary trailer = document.getDocument().getTrailer();
        boolean needsIdentifier = trailer.getItem(COSName.ID) == null;
        if (!needsIdentifier) {
            saveDocument(document, target, resources);
            return;
        }

        try (WorkflowResourceContext.OwnedMemoryScope finalOwnership =
                resources.ownedMemoryScope()) {
            try (WorkflowResourceContext.OwnedMemoryScope placeholderOwnership =
                    resources.ownedMemoryScope()) {
                trailer.setItem(
                        COSName.ID,
                        identifiers(
                                IDENTIFIER_PLACEHOLDER,
                                resources,
                                placeholderOwnership));
                saveDocument(document, target, resources);
                try (WorkflowResourceContext.MemoryReservation digestMemory =
                        resources.reserveOwnedMemory(32L)) {
                    trailer.setItem(
                            COSName.ID,
                            identifiers(
                                    contentDigest(target, resources),
                                    resources,
                                    finalOwnership));
                }
            }
            saveDocument(document, target, resources);
            finalOwnership.transfer();
        }
    }

    private static void saveDocument(
            PDDocument document,
            Path target,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        try (OutputStream output = resources.openTemporaryOutput(target)) {
            document.save(output);
        }
    }

    private static byte[] contentDigest(
            Path path,
            WorkflowResourceContext resources)
            throws IOException, DocumentFailure {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    resources.checkpoint();
                    digest.update(buffer, 0, count);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static COSArray identifiers(
            byte[] identifier,
            WorkflowResourceContext resources,
            WorkflowResourceContext.OwnedMemoryScope ownership)
            throws DocumentFailure {
        COSString value = PdfBoxStringSupport.backendBytes(
                identifier,
                resources,
                ownership,
                () -> resources.policyFailure(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        "The workflow owned-memory limit was exceeded."));
        COSArray identifiers = new COSArray();
        identifiers.setDirect(true);
        identifiers.add(value);
        identifiers.add(value);
        return identifiers;
    }
}

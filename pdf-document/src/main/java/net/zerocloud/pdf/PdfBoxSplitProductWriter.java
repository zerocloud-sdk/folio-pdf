package net.zerocloud.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    static void save(PDDocument document, Path target) throws IOException {
        COSDictionary trailer = document.getDocument().getTrailer();
        boolean needsIdentifier = trailer.getItem(COSName.ID) == null;
        if (needsIdentifier) {
            trailer.setItem(
                    COSName.ID,
                    identifiers(IDENTIFIER_PLACEHOLDER));
        }
        document.save(target.toFile());
        if (!needsIdentifier) {
            return;
        }

        trailer.setItem(COSName.ID, identifiers(contentDigest(target)));
        Path identified = Files.createTempFile(".folio-pdf-id-", ".pdf");
        try {
            document.save(identified.toFile());
            Files.move(
                    identified,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(identified);
        }
    }

    private static byte[] contentDigest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static COSArray identifiers(byte[] identifier) {
        COSString value = new COSString(identifier);
        COSArray identifiers = new COSArray();
        identifiers.setDirect(true);
        identifiers.add(value);
        identifiers.add(value);
        return identifiers;
    }
}

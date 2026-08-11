package net.zerocloud.pdf.itext7.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfPage;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.layout.Document;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PreviewBlankDocumentFacadeTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void migratedCallShapeCreatesPublishesReopensAndObservesOnePage()
            throws Exception {
        Path target = temporaryFolder.newFolder("publication").toPath()
                .resolve("blank.pdf");

        PdfDocument created = new PdfDocument(new PdfWriter(target.toString()));
        Document layout = new Document(created);
        PdfPage addedPage = created.addNewPage();
        layout.close();

        assertNotNull(addedPage);
        assertTrue(Files.isRegularFile(target));
        assertTrue(Files.size(target) > 0L);

        try (PdfReader reader = new PdfReader(target.toString());
                PdfDocument reopened = new PdfDocument(reader)) {
            assertEquals(1, reopened.getNumberOfPages());
        }
    }

    @Test
    public void missingSourceReportsStableNativeFailureCode() throws Exception {
        Path missing = temporaryFolder.getRoot().toPath().resolve("missing.pdf");

        try {
            new PdfReader(missing.toString());
            fail("Expected the missing source to fail");
        } catch (IOException failure) {
            assertEquals(
                    "SOURCE_READ_FAILED: The source could not be opened as a PDF document.",
                    failure.getMessage());
            assertFalse(failure.getMessage().contains("pdfbox"));
        }
    }

    @Test
    public void publicationFailureRetainsTheNativeFailureReceipt() throws Exception {
        Path publicationDirectory = temporaryFolder.newFolder("removed-publication").toPath();
        Path target = publicationDirectory.resolve("blank.pdf");
        PdfDocument created = new PdfDocument(new PdfWriter(target.toString()));
        created.addNewPage();
        Files.delete(publicationDirectory);

        try {
            created.close();
            fail("Expected publication to fail");
        } catch (PdfException failure) {
            assertTrue(failure.getCause() instanceof DocumentFailure);
            DocumentFailure nativeFailure = (DocumentFailure) failure.getCause();
            assertEquals(DocumentFailureCode.INVALID_REQUEST, nativeFailure.getCode());
            assertEquals(1, nativeFailure.getPublicationReceipts().size());
            PublicationReceipt receipt = nativeFailure.getPublicationReceipts().get(0);
            assertEquals(PublicationStatus.NOT_ATTEMPTED, receipt.getStatus());
            assertEquals(target.toAbsolutePath().normalize(), receipt.getPathTarget().get());
            assertFalse(receipt.isPartialOutputPossible());
        }
    }
}

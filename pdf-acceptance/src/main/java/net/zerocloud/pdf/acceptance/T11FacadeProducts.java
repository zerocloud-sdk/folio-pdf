package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** The frozen operations performed through the mapped Facade metadata surface. */
final class T11FacadeProducts {
    private T11FacadeProducts() {
    }

    static void create(T11Corpus corpus, Path output) throws Exception {
        PdfDocument edited;
        DocumentFailure orphan;
        try (PdfReader reader = new PdfReader(corpus.source("primary").toString())) {
            edited = new PdfDocument(Collections.singletonMap("primary", reader), "primary",
                    Collections.singletonMap("edited", new PdfWriter(
                            T11MetadataProducts.pdf(output, "facade", "edited").toString())), PdfVersion.PDF_2_0);
            try (PdfDocument document = edited) {
                document.getDocumentInfo().setTitle("Changed title").setAuthor(null).setMoreInfo("Custom", "Edited info");
                document.setXmpMetadata(T11MetadataProducts.editedXmp());
                document.addNamedDestination("xyz", T11MetadataProducts.editedDestination());
                document.addNamedDestination("temporary", PageDestination.fit(1));
                document.setNamedDestinations(Collections.<String, PageDestination>emptyMap(),
                        Collections.singletonList("temporary"));
                document.setOutlines(T11MetadataProducts.editedOutlines());
                document.addFileAttachment(T11MetadataProducts.editedAttachment());
                document.movePage(3, 1);
                document.copyPages(2, 2, 4);
                try {
                    document.removePage(3);
                    throw new IllegalStateException("T11 Facade accepted orphaning page removal");
                } catch (PdfException failure) {
                    orphan = nativeFailure(failure);
                    T11MetadataProducts.requireOrphan(orphan);
                    if (document.getNumberOfPages() != 4) {
                        throw new IllegalStateException("T11 Facade rejected removal changed page count");
                    }
                }
            }
        }
        T11MetadataProducts.publication(output, "facade", "edited", WorkflowExecutionProfile.IN_PROCESS,
                "fixed-facade-contract", orphan, null, edited.getPublicationReceipts(), "edited");

        PdfDocument split;
        DocumentFailureCode terminal;
        try (PdfReader appendix = new PdfReader(corpus.source("appendix").toString());
                PdfReader primary = new PdfReader(corpus.source("primary").toString())) {
            Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
            sources.put("appendix", appendix);
            sources.put("primary", primary);
            Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
            for (String name : new String[] {"left", "merged", "right"}) {
                targets.put(name, new PdfWriter(T11MetadataProducts.pdf(output, "facade", name).toString()));
            }
            split = new PdfDocument(sources, "primary", targets, PdfVersion.PDF_2_0);
            try (PdfDocument document = split) {
                document.getMerger().merge("appendix");
                document.getSplitter().extractPageRanges(new String[] {"right", "merged", "left"},
                        new PageRange[] {PageRange.of(3, 4), PageRange.of(1, 4), PageRange.of(1, 2)});
                try {
                    document.setXmpMetadata(T11MetadataProducts.editedXmp());
                    throw new IllegalStateException("T11 Facade split accepted a later Command");
                } catch (PdfException failure) {
                    terminal = nativeFailure(failure).getCode();
                    if (terminal != DocumentFailureCode.COMMAND_REJECTED || document.getNumberOfPages() != 4) {
                        throw failure;
                    }
                }
            }
        }
        T11MetadataProducts.publication(output, "facade", "split", WorkflowExecutionProfile.IN_PROCESS,
                "fixed-facade-contract", null, terminal, split.getPublicationReceipts(), "left", "merged", "right");
    }

    static DocumentFailure nativeFailure(PdfException failure) {
        if (!(failure.getCause() instanceof DocumentFailure)) {
            throw failure;
        }
        return (DocumentFailure) failure.getCause();
    }
}

package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfArray;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfObject;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Same frozen T10 operations, executed only through the mapped Facade subset. */
final class T10FacadeProducts {
    private T10FacadeProducts() {
    }

    static void create(T10Corpus corpus, Path output) throws Exception {
        PdfDocument edited;
        try (PdfReader reader = new PdfReader(corpus.source("primary").toString())) {
            edited = new PdfDocument(Collections.singletonMap("primary", reader), "primary",
                    Collections.singletonMap("edited", new PdfWriter(T10PageProducts.pdf(output, "facade", "edited").toString())));
            try (PdfDocument document = edited) {
                document.addNewPage(2);
                document.movePage(4, 1);
                document.copyPages(2, 2, 4);
                document.removePage(1);
                PdfObject contents = document.getPage(3).getPdfObject().get(new PdfName("Contents"));
                PdfStream stream = (PdfStream) (contents instanceof PdfArray ? ((PdfArray) contents).get(0) : contents);
                stream.setData(corpus.recolorCopiedContent(stream.getBytes()));
            }
        }
        T10PageProducts.publication(output, "facade", "edited", WorkflowExecutionProfile.IN_PROCESS,
                "fixed-facade-contract", null, edited.getPublicationReceipts(), "edited");

        PdfDocument split;
        DocumentFailureCode terminalCode;
        try (PdfReader appendix = new PdfReader(corpus.source("appendix").toString());
                PdfReader primary = new PdfReader(corpus.source("primary").toString());
                PdfReader cover = new PdfReader(corpus.source("cover").toString())) {
            Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
            sources.put("appendix", appendix);
            sources.put("primary", primary);
            sources.put("cover", cover);
            Map<String, PdfWriter> targets = new LinkedHashMap<String, PdfWriter>();
            for (String name : new String[] {"merged", "left", "right"}) {
                targets.put(name, new PdfWriter(T10PageProducts.pdf(output, "facade", name).toString()));
            }
            split = new PdfDocument(sources, "primary", targets);
            try (PdfDocument document = split) {
                document.getMerger().merge("cover", "appendix");
                document.getSplitter().extractPageRanges(new String[] {"right", "merged", "left"},
                        new PageRange[] {PageRange.of(3, 5), PageRange.of(1, 5), PageRange.of(1, 3)});
                try {
                    document.removePage(1);
                    throw new IllegalStateException("T10 Facade split accepted a later Command");
                } catch (PdfException failure) {
                    if (!(failure.getCause() instanceof DocumentFailure)) {
                        throw failure;
                    }
                    terminalCode = ((DocumentFailure) failure.getCause()).getCode();
                    if (terminalCode != DocumentFailureCode.COMMAND_REJECTED || document.getNumberOfPages() != 5) {
                        throw failure;
                    }
                }
            }
        }
        T10PageProducts.publication(output, "facade", "split", WorkflowExecutionProfile.IN_PROCESS,
                "fixed-facade-contract", terminalCode, split.getPublicationReceipts(), "merged", "left", "right");
    }
}

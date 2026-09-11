package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

/** Performs the same frozen operations through the mapped Facade surface. */
final class T12FacadeProducts {
    private T12FacadeProducts() { }

    static void create(T12Corpus corpus, Path output) throws Exception {
        for (String product : T12Corpus.PRODUCTS) {
            Path input = "created".equals(product) ? corpus.source("primary")
                    : "changed".equals(product) ? T12AnnotationProducts.pdf(output, "facade", "created")
                    : "left".equals(product) || "right".equals(product) ? T12AnnotationProducts.pdf(output, "facade", "merged")
                    : T12AnnotationProducts.pdf(output, "facade", "changed");
            PdfDocument produced;
            try (PdfReader primary = new PdfReader(input.toString());
                    PdfReader appendix = new PdfReader(corpus.source("appendix").toString())) {
                Map<String, PdfReader> sources = new LinkedHashMap<String, PdfReader>();
                sources.put("primary", primary);
                sources.put("appendix", appendix);
                produced = new PdfDocument(sources, "primary", Collections.singletonMap(product,
                        new PdfWriter(T12AnnotationProducts.pdf(output, "facade", product).toString())), PdfVersion.PDF_2_0);
                try (PdfDocument document = produced) {
                    if ("created".equals(product) || "changed".equals(product)) {
                        boolean changed = "changed".equals(product);
                        if (changed) {
                            document.updateAnnotations(T12AnnotationProducts.annotations(true), Collections.<String>emptyList());
                        } else {
                            for (Annotation annotation : T12AnnotationProducts.annotations(false)) {
                                document.getPage(annotation.getProperties().getPageNumber()).addAnnotation(annotation);
                            }
                        }
                        document.getCatalog().setOpenAction(T12AnnotationProducts.named());
                        document.getPage(1).setAdditionalAction(new PdfName("O"), T12AnnotationProducts.pageOpen(changed, 1));
                        document.getPage(2).setAdditionalAction(new PdfName("C"), T12AnnotationProducts.named());
                        document.getPage(3).setAdditionalAction(new PdfName("O"), T12AnnotationProducts.pageOpen(changed, 3));
                    } else if ("flattened".equals(product)) {
                        document.flattenAnnotations(T12AnnotationProducts.FLATTEN);
                    } else if ("copied".equals(product)) {
                        document.copyPages(1, 2, 4);
                    } else if ("merged".equals(product) || "adopted".equals(product)) {
                        if ("adopted".equals(product)) { document.getCatalog().setOpenAction(null); }
                        document.getMerger().merge("appendix");
                    } else {
                        document.getSplitter().extractPageRanges(new String[] {product}, new PageRange[] {
                            "left".equals(product) ? PageRange.of(1, 2) : PageRange.of(3, 4)});
                    }
                }
            }
            T12AnnotationProducts.publication(output, "facade", product, WorkflowExecutionProfile.IN_PROCESS,
                    "fixed-facade-contract", produced.getPublicationReceipts());
        }
    }
}

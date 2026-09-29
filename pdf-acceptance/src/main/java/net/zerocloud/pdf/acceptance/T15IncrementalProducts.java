package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.AnnotationAppearance;
import net.zerocloud.pdf.AnnotationFlag;
import net.zerocloud.pdf.AnnotationProperties;
import net.zerocloud.pdf.AnnotationRectangle;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.kernel.pdf.StampingProperties;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.PageObjectReference;

/** Produces actual revisions through the public Native and Stable Facade seams. */
final class T15IncrementalProducts {
    static final String[] PRODUCTS = {"unsigned-first", "unsigned-second", "create", "replace", "move", "remove"};
    private T15IncrementalProducts() { }

    static RetainedEvidence create(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        Path corpus = root.resolve("capabilities/profiles/T15-signatures");
        RetainedEvidence retained = new RetainedEvidence(output);
        for (String api : new String[] {"native", "facade"}) {
            for (String product : PRODUCTS) {
                Path source = "unsigned-second".equals(product)
                        ? output.resolve(api + "-unsigned-first/incremental.pdf")
                        : corpus.resolve(product.startsWith("unsigned") ? "unsigned.pdf" : "p3.pdf");
                byte[] original = Files.readAllBytes(source);
                Path directory = Files.createDirectory(output.resolve(api + "-" + product));
                Path pdf = directory.resolve("incremental.pdf");
                Properties observed;
                Properties reopened;
                List<PublicationReceipt> receipts;
                WorkflowExecutionProfile actual;
                if ("native".equals(api)) {
                    WorkflowOutcome<Properties> result = new DocumentWorkflow().execute(request(source, execution)
                            .target("target", PublicationTarget.path(pdf)).build(), session -> {
                                change(session, product);
                                return observe(session);
                            });
                    actual = result.getExecutionProfile();
                    require(actual == execution && result.getSaveMode() == SaveMode.INCREMENTAL
                            && "document.incremental-signature.protect".equals(result.getCapabilityId()),
                            "Native outcome identity mismatch");
                    observed = result.getResult();
                    receipts = result.getPublicationReceipts();
                    WorkflowOutcome<Properties> query = new DocumentWorkflow().execute(
                            request(pdf, actual).saveMode(SaveMode.REWRITE).build(), T15IncrementalProducts::observe);
                    require(query.getExecutionProfile() == actual && query.getPublicationReceipts().isEmpty(),
                            "Native reopening mode or publication mismatch");
                    reopened = query.getResult();
                } else {
                    PdfDocument document;
                    try (PdfReader reader = new PdfReader(source.toString())) {
                        document = new PdfDocument(reader, new PdfWriter(pdf.toString()), new StampingProperties().useAppendMode());
                        try (PdfDocument opened = document) {
                            require(opened.isAppendMode(), "Facade append selection lost");
                            change(opened, product);
                            observed = observe(opened);
                        }
                    }
                    actual = WorkflowExecutionProfile.IN_PROCESS;
                    receipts = document.getPublicationReceipts();
                    try (PdfDocument opened = new PdfDocument(new PdfReader(pdf.toString()))) { reopened = observe(opened); }
                }
                Properties expected = properties(corpus.resolve(product + ".properties"));
                require(expected.equals(observed) && expected.equals(reopened), product + " public values differ from literal expectations");
                byte[] published = Files.readAllBytes(pdf);
                require(published.length > original.length && Arrays.equals(original, Arrays.copyOf(published, original.length)),
                        "An incremental product lost its complete Source prefix");
                require(Arrays.equals(original, Files.readAllBytes(source)), "The declared Source changed");
                require(receipts.size() == 1, "Unexpected publication receipt count");
                PublicationReceipt receipt = receipts.get(0);
                require("target".equals(receipt.getTargetName()) && receipt.getStatus() == PublicationStatus.COMMITTED
                        && receipt.getPathTarget().isPresent() && pdf.equals(receipt.getPathTarget().get())
                        && !receipt.isPartialOutputPossible(), "Incomplete publication receipt");
                Properties publication = new Properties();
                publication.setProperty("execution-profile", actual.name());
                publication.setProperty("save-mode", "INCREMENTAL");
                publication.setProperty("target-name", receipt.getTargetName());
                publication.setProperty("status", receipt.getStatus().name());
                publication.setProperty("partial-output-possible", "false");
                publication.setProperty("source-sha256", EvidenceFiles.sha256(original));
                publication.setProperty("output-sha256", EvidenceFiles.sha256(published));
                publication.setProperty("source-preserved", "pass");
                publication.setProperty("reopened", "pass");
                retained.retain(pdf, publication.getProperty("output-sha256"));
                retained.write(directory.resolve("publication.properties"), publication);
                retained.write(directory.resolve("observation.properties"), observed);
                retained.write(directory.resolve("reopened.properties"), reopened);
            }
        }
        Properties phase = new Properties();
        phase.setProperty("phase", "products-only");
        phase.setProperty("native-execution-profile", execution.name());
        phase.setProperty("facade-execution-profile", "IN_PROCESS");
        retained.write(output.resolve("products.properties"), phase);
        retained.verify();
        return retained;
    }

    private static void change(DocumentSession session, String product) throws DocumentFailure {
        if (product.startsWith("unsigned")) {
            session.execute(AddBlankPage.INSTANCE);
            if ("unsigned-first".equals(product)) {
                session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(DocumentRootReference.INSTANCE),
                        PdfName.of("FolioRevision"), PdfString.of("first".getBytes(StandardCharsets.US_ASCII))).build());
            }
        } else {
            session.execute("remove".equals(product) ? UpdateAnnotations.version1().remove("existing").build()
                    : UpdateAnnotations.version1().put(annotation(product)).build());
        }
    }

    private static void change(PdfDocument document, String product) {
        if (product.startsWith("unsigned")) {
            document.addNewPage();
            if ("unsigned-first".equals(product)) {
                document.getCatalog().getPdfObject().put(new net.zerocloud.pdf.itext7.kernel.pdf.PdfName("FolioRevision"),
                        new net.zerocloud.pdf.itext7.kernel.pdf.PdfString("first"));
            }
        } else {
            document.updateAnnotations("remove".equals(product) ? Collections.<Annotation>emptyList()
                    : Collections.singletonList(annotation(product)), "remove".equals(product)
                    ? Collections.singletonList("existing") : Collections.<String>emptyList());
        }
    }

    private static Annotation annotation(String product) {
        boolean create = "create".equals(product), move = "move".equals(product);
        byte[] paint = (move ? "1 0 0 rg 0 0 20 20 re f\n" : "0 1 0 rg 0 0 20 20 re f\n")
                .getBytes(StandardCharsets.US_ASCII);
        return Annotation.stamp(AnnotationProperties.version1(create ? "added" : "existing", move ? 2 : 1,
                create ? AnnotationRectangle.of(65, 10, 85, 30) : AnnotationRectangle.of(40, 40, 60, 60))
                .contents(create ? "Added" : move ? "Original" : "Replacement").flag(AnnotationFlag.PRINT)
                .appearance(AnnotationAppearance.version1(AnnotationRectangle.of(0, 0, 20, 20), paint)).build(), "Approved");
    }

    private static Properties observe(DocumentSession session) throws DocumentFailure {
        Properties values = new Properties();
        int pages = session.query(PageCount.INSTANCE);
        values.setProperty("pages.count", Integer.toString(pages));
        PdfDictionary root = (PdfDictionary) session.query(InspectObject.version1(session.query(DocumentRootReference.INSTANCE), limits()));
        values.setProperty("keep.token", string(((PdfDictionary) resolve(session, root.get(PdfName.of("FolioKeep")))).get(PdfName.of("Token"))));
        values.setProperty("revision.marker", string(root.get(PdfName.of("FolioRevision"))));
        int annotations = 0;
        for (int number = 1; number <= pages; number++) {
            PdfDictionary page = (PdfDictionary) session.query(InspectObject.version1(session.query(PageObjectReference.version1(number)), limits()));
            values.setProperty("page." + number + ".box", box((PdfArray) resolve(session, page.get(PdfName.of("MediaBox")))));
            PdfArray array = (PdfArray) resolve(session, page.get(PdfName.of("Annots")));
            if (array == null) { continue; }
            for (int index = 0; index < array.size(); index++) {
                PdfDictionary annotation = (PdfDictionary) resolve(session, array.get(index));
                PdfName subtype = (PdfName) annotation.get(PdfName.of("Subtype"));
                if (PdfName.of("Widget").equals(subtype)) { continue; }
                String prefix = "annotation." + string(annotation.get(PdfName.of("NM"))) + ".";
                values.setProperty(prefix + "page", Integer.toString(number));
                values.setProperty(prefix + "subtype", subtype.getValue());
                values.setProperty(prefix + "contents", string(annotation.get(PdfName.of("Contents"))));
                values.setProperty(prefix + "rect", box((PdfArray) resolve(session, annotation.get(PdfName.of("Rect")))));
                annotations++;
            }
        }
        values.setProperty("annotations.count", Integer.toString(annotations));
        return values;
    }

    private static Properties observe(PdfDocument document) {
        Properties values = new Properties();
        int pages = document.getNumberOfPages();
        values.setProperty("pages.count", Integer.toString(pages));
        net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary root = document.getCatalog().getPdfObject();
        net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary keep =
                (net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary) root.get(name("FolioKeep"));
        values.setProperty("keep.token", facadeString(keep.get(name("Token"))));
        values.setProperty("revision.marker", facadeString(root.get(name("FolioRevision"))));
        int annotations = 0;
        for (int number = 1; number <= pages; number++) {
            net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary page = document.getPage(number).getPdfObject();
            values.setProperty("page." + number + ".box", facadeBox((net.zerocloud.pdf.itext7.kernel.pdf.PdfArray) page.get(name("MediaBox"))));
            net.zerocloud.pdf.itext7.kernel.pdf.PdfArray array = (net.zerocloud.pdf.itext7.kernel.pdf.PdfArray) page.get(name("Annots"));
            if (array == null) { continue; }
            for (int index = 0; index < array.size(); index++) {
                net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary annotation = (net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary) array.get(index);
                String subtype = ((net.zerocloud.pdf.itext7.kernel.pdf.PdfName) annotation.get(name("Subtype"))).getValue();
                if ("Widget".equals(subtype)) { continue; }
                String prefix = "annotation." + facadeString(annotation.get(name("NM"))) + ".";
                values.setProperty(prefix + "page", Integer.toString(number));
                values.setProperty(prefix + "subtype", subtype);
                values.setProperty(prefix + "contents", facadeString(annotation.get(name("Contents"))));
                values.setProperty(prefix + "rect", facadeBox((net.zerocloud.pdf.itext7.kernel.pdf.PdfArray) annotation.get(name("Rect"))));
                annotations++;
            }
        }
        values.setProperty("annotations.count", Integer.toString(annotations));
        return values;
    }

    private static String facadeBox(net.zerocloud.pdf.itext7.kernel.pdf.PdfArray array) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < array.size(); index++) {
            if (index > 0) { text.append(','); }
            text.append(java.math.BigDecimal.valueOf(((net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber)
                    array.get(index)).doubleValue()).stripTrailingZeros().toPlainString());
        }
        return text.toString();
    }

    private static String facadeString(net.zerocloud.pdf.itext7.kernel.pdf.PdfObject value) {
        return value == null ? "" : ((net.zerocloud.pdf.itext7.kernel.pdf.PdfString) value).getValue();
    }

    private static net.zerocloud.pdf.itext7.kernel.pdf.PdfName name(String name) {
        return new net.zerocloud.pdf.itext7.kernel.pdf.PdfName(name);
    }

    private static String box(PdfArray array) throws DocumentFailure {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < array.size(); index++) {
            if (index > 0) { text.append(','); }
            text.append(((PdfNumber) array.get(index)).decimalValue().stripTrailingZeros().toPlainString());
        }
        return text.toString();
    }

    private static String string(PdfValue value) {
        return value == null ? "" : new String(((PdfString) value).getBytes(), StandardCharsets.US_ASCII);
    }
    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(((PdfIndirectReference) value).getReference(), limits())) : value;
    }
    private static PdfInspectionLimits limits() { return PdfInspectionLimits.of(256, 4096); }
    private static WorkflowRequest.Builder request(Path source, WorkflowExecutionProfile execution) {
        return WorkflowRequest.builder().source("source", DocumentSource.path(source)).primarySource("source")
                .saveMode(SaveMode.INCREMENTAL).executionProfile(execution);
    }
    private static Properties properties(Path path) throws IOException {
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(path)) { result.load(input); }
        return result;
    }
    private static void require(boolean condition, String message) throws IOException {
        if (!condition) { throw new IOException("T15 " + message); }
    }
}

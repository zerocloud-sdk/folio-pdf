package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.math.BigDecimal;
import net.zerocloud.pdf.Annotation;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.GoToAction;
import net.zerocloud.pdf.LinkActivation;
import net.zerocloud.pdf.NavigationTarget;
import net.zerocloud.pdf.PdfArray;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PdfStreamEncoding;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfValue;
import net.zerocloud.pdf.PdfValuePath;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.UpdateAnnotations;
import net.zerocloud.pdf.command.UpdateActions;
import net.zerocloud.pdf.query.Annotations;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageObjectReference;

/** Mutates actual published products through public values; frozen expectations stay unchanged. */
final class T12NegativeControls {
    private static final String[] CHANGES = {"order", "identifier", "rectangle", "appearance", "appearance-box", "payload", "icon",
        "direct-target", "action-operand", "named-target", "copy-target", "copy-external", "merge-name", "split-survival",
        "open-adoption", "flatten-removal", "flatten-placement", "retained-paint"};

    private T12NegativeControls() { }

    static Properties record(Path root, Path products, Path output, WorkflowExecutionProfile execution, String release) throws Exception {
        Properties result = new Properties();
        Path created = T12AnnotationProducts.pdf(products, "native", "created");
        Path invalid = output.resolve("invalid.pdf");
        Files.write(invalid, Arrays.copyOf(Files.readAllBytes(created), 16));
        result.setProperty("syntax-source-sha256", EvidenceFiles.sha256(created));
        QpdfSyntaxRecorder.Profile syntax = QpdfSyntaxRecorder.Profile.exactDocument("T74 negative control", T12AnnotationProducts.CAPABILITY,
                T12Corpus.PROFILE, "capabilities/evidence/T12-annotations-document-actions.md", "invalid.pdf", "syntax.md", "syntax.txt");
        result.setProperty("syntax", QpdfSyntaxRecorder.record(output, output, QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")),
                EvidenceFiles.sha256(invalid), release, syntax).recordValue());

        Path malformed = output.resolve("invalid-rectangle.pdf");
        mutate(created, malformed, "invalid-rectangle", execution);
        Path standards = output.resolve("standards");
        StandardsEvidenceCommand.main(new String[] {standards.toString(), root.resolve("scripts/pdfcpu-pin.properties").toString(),
            root.resolve("capabilities/profiles/T12-standards/pdfcpu.properties").toString(), malformed.toString()});
        PinProperties standard = PinProperties.load(standards.resolve("standards.properties"), "T12 standards control");
        result.setProperty("standards", EvidenceFiles.sha256(malformed).equals(standard.required("input-sha256"))
                && T12Corpus.PROFILE.equals(standard.required("profile")) ? standard.required("result") : "indeterminate");
        EvidenceFiles.write(output.resolve("standards.txt"), "Actual product rectangle type mutation: " + EvidenceFiles.sha256(malformed)
                + "\n" + new String(Files.readAllBytes(standards.resolve("findings.txt")), StandardCharsets.UTF_8)
                + "\nEvery product's three standards groups separately require all 174 rule-specific controls.\n");

        Path semantic = Files.createDirectory(output.resolve("semantic"));
        boolean detected = true;
        StringBuilder findings = new StringBuilder();
        for (String change : CHANGES) {
            String product = product(change);
            Path source = T12AnnotationProducts.pdf(products, "native", product);
            Path wrong = semantic.resolve(change + ".pdf");
            mutate(source, wrong, change, execution);
            Path directory = Files.createDirectory(semantic.resolve(change));
            Properties observation = T12AnnotationSemantics.inspect(root, wrong, product, directory, execution);
            observation.setProperty("source-sha256", EvidenceFiles.sha256(source));
            observation.setProperty("control", change);
            T12AnnotationProducts.save(directory.resolve("result.properties"), observation);
            detected &= "fail".equals(observation.getProperty("semantic"));
            findings.append(change).append('=').append(observation.getProperty("semantic"))
                    .append(' ').append(observation.getProperty("finding")).append('\n');
        }
        result.setProperty("semantic", detected ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("semantic.txt"), findings.toString());

        Path visual = Files.createDirectory(output.resolve("visual"));
        Properties pixel = T12PixelControl.record(root, Files.createDirectory(visual.resolve("one-pixel")));
        detected = "fail".equals(pixel.getProperty("visual"));
        findings = new StringBuilder();
        findings.append("one-pixel=").append(pixel.getProperty("visual")).append('\n');
        for (String change : new String[] {"appearance", "rectangle", "retained-paint"}) {
            Path source = T12AnnotationProducts.pdf(products, "native", product(change));
            Path wrong = visual.resolve(change + ".pdf");
            mutate(source, wrong, "rectangle".equals(change) ? "widget-rectangle" : change, execution);
            Path directory = Files.createDirectory(visual.resolve(change));
            Properties observation = T12IndependentEvidence.recordVisual(root, wrong, product(change), directory, release);
            observation.setProperty("source-sha256", EvidenceFiles.sha256(source));
            observation.setProperty("control", change);
            T12AnnotationProducts.save(directory.resolve("result.properties"), observation);
            detected &= "fail".equals(observation.getProperty("visual")) && "fail".equals(observation.getProperty("page.1.visual"));
            findings.append(change).append('=').append(observation.getProperty("visual")).append('\n');
        }
        result.setProperty("visual", detected ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("visual.txt"), findings.toString());
        return result;
    }

    private static String product(String change) {
        if ("payload".equals(change) || "icon".equals(change)) { return "changed"; }
        if (change.startsWith("copy-")) { return "copied"; }
        if ("merge-name".equals(change)) { return "merged"; }
        if ("split-survival".equals(change)) { return "left"; }
        if ("open-adoption".equals(change)) { return "adopted"; }
        if (change.startsWith("flatten-") || "retained-paint".equals(change)) { return "flattened"; }
        return "created";
    }

    private static void mutate(Path source, Path target, String change, WorkflowExecutionProfile execution) throws Exception {
        String before = EvidenceFiles.sha256(source);
        WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(T12AnnotationProducts.request(execution)
                .source("source", DocumentSource.path(source)).primarySource("source")
                .target("control", PublicationTarget.path(target)).build(), session -> {
            if ("order".equals(change)) {
                List<Annotation> annotations = new java.util.ArrayList<Annotation>(session.query(Annotations.version1(100, 65536, 65536)));
                UpdateAnnotations.Builder remove = UpdateAnnotations.version1();
                for (Annotation annotation : annotations) { remove.remove(annotation.getProperties().getIdentifier()); }
                session.execute(remove.build());
                Collections.swap(annotations, 0, 1);
                UpdateAnnotations.Builder put = UpdateAnnotations.version1();
                for (Annotation annotation : annotations) { put.put(annotation); }
                session.execute(put.build());
            } else if ("direct-target".equals(change) || "copy-target".equals(change)
                    || "action-operand".equals(change) || "merge-name".equals(change)) {
                changeLink(session, change);
            } else if ("open-adoption".equals(change)) {
                session.execute(UpdateActions.version1().setDocumentOpenAction(T12AnnotationProducts.named()).build());
            } else if ("named-target".equals(change) || "copy-external".equals(change) || "split-survival".equals(change)) {
                session.execute(SetNamedDestinations.version1().set("shared",
                        PageDestination.fit("named-target".equals(change) ? 2 : "copy-external".equals(change) ? 4 : 1)).build());
            } else if ("flatten-removal".equals(change)) {
                session.execute(UpdateAnnotations.version1().put(T12AnnotationProducts.annotations(true).get(0)).build());
            } else {
                session.execute(patch(session, change));
            }
            return null;
        });
        if (outcome.getExecutionProfile() != execution || outcome.getPublicationReceipts().size() != 1
                || outcome.getPublicationReceipts().get(0).getStatus() != PublicationStatus.COMMITTED
                || outcome.getPublicationReceipts().get(0).isPartialOutputPossible()
                || !target.equals(outcome.getPublicationReceipts().get(0).getPathTarget().orElse(null))
                || !before.equals(EvidenceFiles.sha256(source)) || before.equals(EvidenceFiles.sha256(target))) {
            throw new IOException("T12 control publication failed: " + change);
        }
    }

    private static void changeLink(DocumentSession session, String change) throws DocumentFailure {
        String identifier = "copy-target".equals(change) ? "direct-1" : "action-operand".equals(change) ? "action"
                : "merge-name".equals(change) ? "appendix-link" : "direct";
        for (Annotation annotation : session.query(Annotations.version1(100, 65536, 65536))) {
            if (identifier.equals(annotation.getProperties().getIdentifier())) {
                LinkActivation activation;
                if ("merge-name".equals(change)) {
                    activation = LinkActivation.action(T12AnnotationProducts.named());
                } else if ("action-operand".equals(change)) {
                    activation = LinkActivation.action(GoToAction.version1(NavigationTarget.toPage(
                            PageDestination.xyz(2, BigDecimal.TEN, BigDecimal.valueOf(90), new BigDecimal("1.25")))));
                } else {
                    activation = LinkActivation.destination(NavigationTarget.toPage(PageDestination.fitR(
                            "copy-target".equals(change) ? 1 : 2, BigDecimal.valueOf(5), BigDecimal.TEN,
                            BigDecimal.valueOf(100), BigDecimal.valueOf(90))));
                }
                session.execute(UpdateAnnotations.version1().put(Annotation.link(annotation.getProperties(), activation)).build());
                return;
            }
        }
        throw new IllegalStateException("T12 control link is missing: " + identifier);
    }

    private static DocumentPatch patch(DocumentSession session, String change) throws DocumentFailure {
        DocumentPatch.Builder patch = DocumentPatch.builder();
        PdfValuePath annots = page(session, 1).dictionaryEntry(PdfName.of("Annots"));
        PdfValuePath note = annots.arrayElement(0);
        if ("identifier".equals(change)) {
            patch.setDictionaryEntry(annots.arrayElement(1), PdfName.of("NM"), string("note"));
        } else if ("rectangle".equals(change) || "widget-rectangle".equals(change)) {
            patch.setArrayElement(annots.arrayElement("rectangle".equals(change) ? 0 : 4)
                    .dictionaryEntry(PdfName.of("Rect")), 0, PdfNumber.of("rectangle".equals(change) ? 6L : 101L));
        } else if ("invalid-rectangle".equals(change)) {
            patch.setDictionaryEntry(note, PdfName.of("Rect"), string("bad rectangle"));
        } else if ("appearance".equals(change)) {
            patch.replaceStreamData(appearance(annots.arrayElement(4)), bytes("q 1 0 0 rg 2 3 10 10 re f Q\n"), PdfStreamEncoding.UNFILTERED);
        } else if ("appearance-box".equals(change)) {
            patch.setArrayElement(appearance(note).dictionaryEntry(PdfName.of("BBox")), 0, PdfNumber.of(1L));
        } else if ("payload".equals(change)) {
            patch.replaceStreamData(annots.arrayElement(3).dictionaryEntry(PdfName.of("FS"))
                    .dictionaryEntry(PdfName.of("EF")).dictionaryEntry(PdfName.of("F")), new byte[] {97, 98, 99, 1}, PdfStreamEncoding.UNFILTERED);
        } else if ("icon".equals(change)) {
            patch.setDictionaryEntry(note, PdfName.of("Name"), PdfName.of("Note"));
        } else {
            PdfValuePath contents = page(session, 1).dictionaryEntry(PdfName.of("Contents"));
            PdfValue value = resolve(session, pageDictionary(session, 1).get(PdfName.of("Contents")));
            int count = value instanceof PdfArray ? ((PdfArray) value).size() : 1;
            boolean changed = false;
            for (int index = 0; index < count; index++) {
                PdfValuePath streamPath = value instanceof PdfArray ? contents.arrayElement(index) : contents;
                PdfStream stream = (PdfStream) resolve(session, value instanceof PdfArray ? ((PdfArray) value).get(index) : value);
                String program = new String(stream.readBytes(), StandardCharsets.US_ASCII);
                String replacement = "retained-paint".equals(change) ? program.replace("10 20 40 30 re", "11 20 40 30 re")
                        : program.replace("1.5 0 0 1.5 2.0 40.5 cm", "1.5 0 0 1.5 3 40.5 cm");
                if (!replacement.equals(program)) {
                    patch.replaceStreamData(streamPath, bytes(replacement), PdfStreamEncoding.UNFILTERED);
                    changed = true;
                }
            }
            if (!changed) { throw new IllegalStateException("T12 control did not find its original paint/placement: " + change); }
        }
        return patch.build();
    }

    private static PdfValuePath page(DocumentSession session, int number) throws DocumentFailure {
        return PdfValuePath.root(session.query(PageObjectReference.version1(number)));
    }

    private static PdfValuePath appearance(PdfValuePath annotation) {
        return annotation.dictionaryEntry(PdfName.of("AP")).dictionaryEntry(PdfName.of("N"));
    }

    private static PdfDictionary pageDictionary(DocumentSession session, int number) throws DocumentFailure {
        return (PdfDictionary) session.query(InspectObject.version1(session.query(PageObjectReference.version1(number)),
                PdfInspectionLimits.of(256, 65536)));
    }

    private static PdfValue resolve(DocumentSession session, PdfValue value) throws DocumentFailure {
        return value instanceof PdfIndirectReference ? session.query(InspectObject.version1(
                ((PdfIndirectReference) value).getReference(), PdfInspectionLimits.of(256, 65536))) : value;
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private static PdfString string(String text) { return PdfString.of(bytes(text)); }
}

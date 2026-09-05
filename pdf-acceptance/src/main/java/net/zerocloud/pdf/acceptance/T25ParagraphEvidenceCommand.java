package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.WorkflowOutcome;

/** Repository-only independent evidence for each advanced paragraph rule. */
public final class T25ParagraphEvidenceCommand {
    static final String CAPABILITY = "composition.layout.paragraph-pagination";
    static final String PROFILE = "T25-paragraph-pagination";
    private T25ParagraphEvidenceCommand() { }

    /**
     * Receives output, qpdf pin, PDFium pin, ImageMagick pin, profiles directory and release train.
     * The separate {@code --reference DIRECTORY} mode writes only independent hand-positioned PDFs.
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && arguments[0].equals("--reference")) {
            Path output = Paths.get(arguments[1]); Files.createDirectories(output);
            for (T25ParagraphExpectations.Profile profile : T25ParagraphExpectations.PROFILES) {
                T25ParagraphProducts.createReference(profile, output.resolve(profile.id + "-reference.pdf"));
            }
            return;
        }
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T25 evidence arguments"); }
        Path output = Paths.get(arguments[0]);
        Path artifacts = output.resolve("artifacts"); Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]); String release = arguments[5];
        EvidenceResult allSyntax = EvidenceResult.PASS, allSemantic = EvidenceResult.PASS, allVisual = EvidenceResult.PASS;
        StringBuilder syntaxLinks = new StringBuilder(), semanticLinks = new StringBuilder(), visualLinks = new StringBuilder();
        for (T25ParagraphExpectations.Profile profile : T25ParagraphExpectations.PROFILES) {
            Path artifact = artifacts.resolve(profile.id + ".pdf");
            StringBuilder controls = new StringBuilder();
            WorkflowOutcome<Void> creation = T25ParagraphProducts.create(profile, artifact, controls);
            T25ParagraphProducts.createReference(profile, artifacts.resolve(profile.id + "-reference.pdf"));
            String hash = EvidenceFiles.idNeutralPdfSha256(artifact);
            T25ParagraphSemanticAssertions.Observation semantic = T25ParagraphSemanticAssertions.inspect(profile, creation, artifact);
            EvidenceFiles.write(artifacts.resolve(profile.id + "-semantic.txt"), provenance(profile, hash) + "\n" + controls + semantic.findings);
            EvidenceFiles.write(output.resolve(profile.id + "-semantic.md"), "# T25 independent semantic evidence\n\n"
                    + recordMetadata(profile.id, "semantic", semantic.result(), "project-test", "folio-pdf-t25-semantic-assertions", release, release)
                    + provenance(profile, hash) + "\nThe observer reopens the PDF through public queries and checks the independent numeric oracle.\n\n"
                    + "[Detailed observations](artifacts/" + profile.id + "-semantic.txt)\n\n"
                    + "Final determination: `" + semantic.result().recordValue() + "`\n");
            EvidenceResult syntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, release,
                    new QpdfSyntaxRecorder.Profile("T25", CAPABILITY, profile.id, "capabilities/evidence/" + profile.id + ".md",
                            profile.id + ".pdf", profile.id + "-syntax.md", profile.id + "-qpdf.txt"));
            EvidenceResult visual = EvidenceResult.PASS;
            for (int page = 1; page <= 2; page++) {
                VisualProfile selection = VisualProfile.load(profiles.resolve(profile.id + "-page-" + page + "-visual.properties"));
                if (!profile.id.equals(selection.profileId()) || selection.pageNumber() != page || selection.pageCount() != 2) {
                    throw new IllegalArgumentException("T25 visual selection does not match its declared profile and page");
                }
                VisualEvidenceChain chain = VisualEvidenceChain.t25(profile.id, page);
                VisualEvidence evidence = VisualEvidenceRecorder.record(chain, artifact, hash, artifacts, pdfium, comparator, selection, release);
                EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
                EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
                visual = combine(visual, evidence.result());
            }
            EvidenceFiles.write(output.resolve(profile.id + "-visual.md"), "# T25 independent visual evidence\n\n"
                    + recordMetadata(profile.id, "visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(), release)
                    + provenance(profile, hash) + "\nBoth pinned page comparisons are mandatory. Expected rasters are rendered from the hand-positioned reference, which never calls paragraph composition.\n\n"
                    + "- [Page 1](" + profile.id + "-page-1-visual.md)\n- [Page 2](" + profile.id + "-page-2-visual.md)\n"
                    + "- [Independent reference](artifacts/" + profile.id + "-reference.pdf)\n\nFinal determination: `" + visual.recordValue() + "`\n");
            allSyntax = combine(allSyntax, syntax); allSemantic = combine(allSemantic, semantic.result()); allVisual = combine(allVisual, visual);
            syntaxLinks.append(link(profile.id, "syntax")); semanticLinks.append(link(profile.id, "semantic")); visualLinks.append(link(profile.id, "visual"));
            System.out.println(profile.id + ": syntax=" + syntax + ", semantic=" + semantic.result() + ", visual=" + visual);
        }
        aggregate(output, "syntax", allSyntax, "external-tool", "qpdf", qpdf.version(), release, syntaxLinks);
        aggregate(output, "semantic", allSemantic, "project-test", "folio-pdf-t25-semantic-assertions", release, release, semanticLinks);
        aggregate(output, "visual", allVisual, "external-tool", "pdfium-cli", pdfium.producerVersion(), release, visualLinks);
        System.out.println("T25: syntax=" + allSyntax + ", semantic=" + allSemantic + ", visual=" + allVisual
                + "; standards and compatibility remain indeterminate.");
    }
    private static String link(String id, String chain) { return "- [" + id + "](" + id + "-" + chain + ".md)\n"; }
    private static EvidenceResult combine(EvidenceResult a, EvidenceResult b) {
        if (a == EvidenceResult.FAIL || b == EvidenceResult.FAIL) { return EvidenceResult.FAIL; }
        return a == EvidenceResult.INDETERMINATE || b == EvidenceResult.INDETERMINATE ? EvidenceResult.INDETERMINATE : EvidenceResult.PASS;
    }
    private static void aggregate(Path output, String chain, EvidenceResult result, String kind, String producer,
            String version, String release, StringBuilder links) throws Exception {
        EvidenceFiles.write(output.resolve(PROFILE + "-" + chain + ".md"), "# T25 paragraph pagination " + chain + " evidence\n\n"
                + recordMetadata(PROFILE, chain, result, kind, producer, version, release)
                + "\nEvery independent rule profile below is mandatory for this chain. Missing tools or evidence remain INDETERMINATE; this chain does not establish standards conformance.\n\n"
                + links + "\nFinal determination: `" + result.recordValue() + "`\n");
    }
    private static String provenance(T25ParagraphExpectations.Profile profile, String hash) {
        return metadata("Input ID-neutral SHA-256", hash) + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Primary font SHA-256", T25ParagraphProducts.PRIMARY_HASH)
                + metadata("Fallback font SHA-256", T25ParagraphProducts.FALLBACK_HASH)
                + metadata("Expected declaration SHA-256", EvidenceFiles.sha256(profile.declaration()))
                + metadata("Geometry tolerance in points", Double.toString(T25ParagraphExpectations.TOLERANCE))
                + "\nExpected declaration: `" + profile.declaration() + "`\n";
    }
    private static String recordMetadata(String id, String chain, EvidenceResult result, String kind, String producer, String version, String release) {
        return metadata("Capability", CAPABILITY) + metadata("Acceptance Profile", id)
                + metadata("Profile record", "capabilities/evidence/" + id + ".md") + metadata("Release train", release)
                + metadata("Chain", chain) + metadata("Result", result.recordValue()) + metadata("Producer kind", kind)
                + metadata("Producer", producer) + metadata("Producer version", version);
    }
}

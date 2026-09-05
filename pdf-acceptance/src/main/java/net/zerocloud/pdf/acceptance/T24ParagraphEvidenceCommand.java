package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.WorkflowOutcome;

/** Repository-only T24 paragraph syntax, semantic and two-page visual evidence. */
public final class T24ParagraphEvidenceCommand {
    static final String CAPABILITY = "composition.layout.paragraph-areas";
    static final String PROFILE = "T24-paragraph-composition";
    private T24ParagraphEvidenceCommand() { }

    /**
     * Receives output, qpdf pin, PDFium pin, ImageMagick pin, profiles directory,
     * and release train. The separate {@code --reference OUTPUT.pdf} form authors
     * only the hand-positioned oracle used to regenerate the expected PNGs.
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && arguments[0].equals("--reference")) {
            T24ParagraphProducts.createReference(Paths.get(arguments[1]));
            return;
        }
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T24 evidence arguments"); }
        Path output = Paths.get(arguments[0]);
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]);
        String releaseTrain = arguments[5];
        Path artifact = artifacts.resolve(PROFILE + ".pdf");
        WorkflowOutcome<Void> creation = T24ParagraphProducts.create(artifact);
        T24ParagraphProducts.createReference(artifacts.resolve(PROFILE + "-reference.pdf"));
        String hash = EvidenceFiles.idNeutralPdfSha256(artifact);
        T24ParagraphSemanticAssertions.Observation semantic = T24ParagraphSemanticAssertions.inspect(creation, artifact);
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-semantic.txt"),
                provenance(hash) + "\n" + semantic.findings);
        EvidenceFiles.write(output.resolve(PROFILE + "-semantic.md"),
                "# T24 independent project semantic evidence\n\n"
                + recordMetadata("semantic", semantic.result(), "project-test", "folio-pdf-t24-semantic-assertions",
                        releaseTrain, releaseTrain)
                + provenance(hash)
                + "\nThe oracle is a hand-calculated table, not a call to the layout implementation. "
                + "All observations use public queries after reopening the published artifact.\n\n"
                + "[Detailed observations](artifacts/" + PROFILE + "-semantic.txt)\n\n"
                + "Final determination: `" + semantic.result().recordValue() + "`\n");
        EvidenceResult syntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, releaseTrain,
                new QpdfSyntaxRecorder.Profile("T24", CAPABILITY, PROFILE,
                        "capabilities/evidence/" + PROFILE + ".md", PROFILE + ".pdf",
                        PROFILE + "-syntax.md", PROFILE + "-qpdf.txt"));
        EvidenceResult visual = EvidenceResult.PASS;
        for (int page = 1; page <= 2; page++) {
            VisualProfile profile = VisualProfile.load(profiles.resolve(PROFILE + "-page-" + page + "-visual.properties"));
            if (profile.pageNumber() != page || !profile.profileId().equals(PROFILE)) {
                throw new IllegalArgumentException("T24 visual selection does not match its declared page");
            }
            VisualEvidenceChain chain = VisualEvidenceChain.t24(page);
            VisualEvidence evidence = VisualEvidenceRecorder.record(chain, artifact, hash,
                    artifacts, pdfium, comparator, profile, releaseTrain);
            EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
            EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
            if (evidence.result() == EvidenceResult.FAIL) { visual = EvidenceResult.FAIL; }
            else if (evidence.result() == EvidenceResult.INDETERMINATE && visual != EvidenceResult.FAIL) {
                visual = EvidenceResult.INDETERMINATE;
            }
        }
        EvidenceFiles.write(output.resolve(PROFILE + "-visual.md"), "# T24 independent visual evidence\n\n"
                + recordMetadata("visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(),
                        releaseTrain)
                + provenance(hash)
                + "\nBoth pages must pass their pinned PDFium/ImageMagick profiles. "
                + "Expected rasters come from the separate hand-positioned reference PDF; "
                + "that reference never invokes paragraph composition.\n\n"
                + "- [Page 1 profile and differences](" + PROFILE + "-page-1-visual.md)\n"
                + "- [Page 2 profile and differences](" + PROFILE + "-page-2-visual.md)\n"
                + "- [Hand-positioned reference PDF](artifacts/" + PROFILE + "-reference.pdf)\n\n"
                + "Final determination: `" + visual.recordValue() + "`\n");
        System.out.println("T24: syntax=" + syntax + ", semantic=" + semantic.result() + ", visual=" + visual
                + "; standards and compatibility remain indeterminate.");
    }

    private static String provenance(String hash) {
        return metadata("Input ID-neutral SHA-256", hash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Primary font SHA-256", T24ParagraphProducts.PRIMARY_HASH)
                + metadata("Fallback font SHA-256", T24ParagraphProducts.FALLBACK_HASH)
                + metadata("Expected declaration SHA-256", EvidenceFiles.sha256(T24ParagraphExpectations.DECLARATION))
                + metadata("Geometry tolerance in points", Double.toString(T24ParagraphExpectations.TOLERANCE))
                + "\nExpected declaration: `" + T24ParagraphExpectations.DECLARATION + "`\n";
    }
    private static String recordMetadata(String chain, EvidenceResult result, String kind,
            String producer, String version, String releaseTrain) {
        return metadata("Capability", CAPABILITY) + metadata("Acceptance Profile", PROFILE)
                + metadata("Profile record", "capabilities/evidence/" + PROFILE + ".md")
                + metadata("Release train", releaseTrain) + metadata("Chain", chain)
                + metadata("Result", result.recordValue()) + metadata("Producer kind", kind)
                + metadata("Producer", producer) + metadata("Producer version", version);
    }
}

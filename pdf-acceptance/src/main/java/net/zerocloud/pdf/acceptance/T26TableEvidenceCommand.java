package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.WorkflowOutcome;

/** Repository-only T26 table syntax, semantic and three-page visual evidence. */
public final class T26TableEvidenceCommand {
    static final String CAPABILITY = "composition.layout.tables";
    static final String PROFILE = "T26-table-composition";
    private T26TableEvidenceCommand() { }

    /**
     * Receives output, qpdf pin, PDFium pin, ImageMagick pin, profiles directory,
     * and release train. The separate {@code --reference OUTPUT.pdf} form authors
     * only the hand-positioned oracle used to regenerate the expected PNGs.
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 2 && arguments[0].equals("--reference")) {
            T26TableProducts.createReference(Paths.get(arguments[1]));
            return;
        }
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T26 evidence arguments"); }
        Path output = Paths.get(arguments[0]);
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]);
        String releaseTrain = arguments[5];
        Path artifact = artifacts.resolve(PROFILE + ".pdf");
        WorkflowOutcome<Void> creation = T26TableProducts.create(artifact);
        T26TableProducts.createReference(artifacts.resolve(PROFILE + "-reference.pdf"));
        String hash = EvidenceFiles.idNeutralPdfSha256(artifact);
        T26TableSemanticAssertions.Observation semantic = T26TableSemanticAssertions.inspect(creation, artifact);
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-semantic.txt"),
                provenance(hash) + "\n" + semantic.findings);
        EvidenceFiles.write(output.resolve(PROFILE + "-semantic.md"),
                "# T26 independent project semantic evidence\n\n"
                + recordMetadata("semantic", semantic.result(), "project-test", "folio-pdf-t26-semantic-assertions",
                        releaseTrain, releaseTrain)
                + provenance(hash)
                + "\nThe oracle contains independently calculated scalar and border coordinates. "
                + "All observations use public queries after reopening the published artifact.\n\n"
                + "[Detailed observations](artifacts/" + PROFILE + "-semantic.txt)\n\n"
                + "Final determination: `" + semantic.result().recordValue() + "`\n");
        EvidenceResult syntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, releaseTrain,
                new QpdfSyntaxRecorder.Profile("T26", CAPABILITY, PROFILE,
                        "capabilities/evidence/" + PROFILE + ".md", PROFILE + ".pdf",
                        PROFILE + "-syntax.md", PROFILE + "-qpdf.txt"));
        EvidenceResult visual = EvidenceResult.PASS;
        for (int page = 1; page <= 3; page++) {
            VisualProfile profile = VisualProfile.load(profiles.resolve(PROFILE + "-page-" + page + "-visual.properties"));
            if (profile.pageNumber() != page || !profile.profileId().equals(PROFILE)) {
                throw new IllegalArgumentException("T26 visual selection does not match its declared page");
            }
            VisualEvidenceChain chain = VisualEvidenceChain.t26(page);
            VisualEvidence evidence = VisualEvidenceRecorder.record(chain, artifact, hash,
                    artifacts, pdfium, comparator, profile, releaseTrain);
            EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
            EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
            if (evidence.result() == EvidenceResult.FAIL) { visual = EvidenceResult.FAIL; }
            else if (evidence.result() == EvidenceResult.INDETERMINATE && visual != EvidenceResult.FAIL) {
                visual = EvidenceResult.INDETERMINATE;
            }
        }
        EvidenceFiles.write(output.resolve(PROFILE + "-visual.md"), "# T26 independent visual evidence\n\n"
                + recordMetadata("visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(),
                        releaseTrain)
                + provenance(hash)
                + "\nAll three pages must pass their pinned PDFium/ImageMagick profiles. "
                + "Expected rasters come from the separate hand-positioned reference PDF; "
                + "that reference never invokes table composition.\n\n"
                + "- [Page 1 profile and differences](" + PROFILE + "-page-1-visual.md)\n"
                + "- [Page 2 profile and differences](" + PROFILE + "-page-2-visual.md)\n"
                + "- [Page 3 profile and differences](" + PROFILE + "-page-3-visual.md)\n"
                + "- [Hand-positioned reference PDF](artifacts/" + PROFILE + "-reference.pdf)\n\n"
                + "Final determination: `" + visual.recordValue() + "`\n");
        System.out.println("T26: syntax=" + syntax + ", semantic=" + semantic.result() + ", visual=" + visual
                + "; standards and compatibility remain indeterminate.");
    }

    private static String provenance(String hash) {
        return metadata("Input ID-neutral SHA-256", hash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + metadata("Primary font SHA-256", T26TableProducts.PRIMARY_HASH)
                + metadata("Fallback font SHA-256", T26TableProducts.FALLBACK_HASH)
                + metadata("Expected declaration SHA-256", EvidenceFiles.sha256(T26TableExpectations.DECLARATION))
                + metadata("Geometry tolerance in points", Double.toString(T26TableExpectations.TOLERANCE))
                + "\nExpected declaration: `" + T26TableExpectations.DECLARATION + "`\n";
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

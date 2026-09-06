package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;

/** Repository-only seven-profile Unicode acceptance; every expectation comes from offline reference data. */
public final class T28UnicodeEvidenceCommand {
    private static final String PROFILE = "T28-unicode";

    /** Receives output, qpdf pin, PDFium pin, ImageMagick pin, profiles directory and release train. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T28 evidence arguments"); }
        Path output = Paths.get(arguments[0]);
        Path artifacts = output.resolve("artifacts");
        Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profilesDirectory = Paths.get(arguments[4]);
        String release = arguments[5];
        Path artifact = artifacts.resolve(PROFILE + ".pdf");
        WorkflowOutcome<Void> creation = T28UnicodeProducts.create(artifact, WorkflowExecutionProfile.IN_PROCESS);
        T28UnicodeProducts.copyReference(artifacts.resolve(PROFILE + "-reference.pdf"));
        String hash = EvidenceFiles.idNeutralPdfSha256(artifact);
        StringBuilder provenance = new StringBuilder(metadata("Input ID-neutral SHA-256", hash))
                .append(metadata("Input hash policy", EvidenceFiles.inputHashPolicy()))
                .append(metadata("ICU4J implementation version", "77.1"))
                .append(metadata("Geometry tolerance in points", "0.0001"))
                .append(metadata("Producer owned-memory budget in bytes", Long.toString(T28UnicodeProducts.OWNED_MEMORY_BYTES)))
                .append(metadata("Reference PDF SHA-256", EvidenceFiles.sha256(artifacts.resolve(PROFILE + "-reference.pdf"))));
        for (String name : new String[] {"T28-corpus.properties", "T28-glyphs.tsv", "T28-reference-receipt.json"}) {
            try (InputStream input = T28UnicodeProducts.resource("unicode/" + name)) { Files.copy(input, artifacts.resolve(name)); }
            provenance.append(metadata(name + " SHA-256", EvidenceFiles.sha256(artifacts.resolve(name))));
        }
        try (InputStream input = T28UnicodeProducts.resource("fonts/noto/fonts.properties")) {
            Files.copy(input, artifacts.resolve("T28-fonts.properties"));
        }
        provenance.append(metadata("Font manifest SHA-256", EvidenceFiles.sha256(artifacts.resolve("T28-fonts.properties"))));
        T28UnicodeSemanticAssertions.Observation semantic = T28UnicodeSemanticAssertions.inspect(
                creation, artifact, WorkflowExecutionProfile.IN_PROCESS);
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-semantic.txt"), provenance + "\n" + semantic.findings);
        EvidenceFiles.write(output.resolve(PROFILE + "-semantic.md"), "# T28 independent Unicode semantic and geometric evidence\n\n"
                + recordMetadata("semantic", semantic.result(), "project-test", "folio-pdf-t28-semantic-assertions", release, release)
                + provenance + "\nAll seven profiles compare every scalar's display order, original glyph ID, baseline, advance, "
                + "text matrix, page box and explicit embedded subset names after public Workflow reopen. The expected rows "
                + "come from manual lines and pinned source-font metrics, without ICU or Folio layout.\n\n"
                + "[All seven profile observations](artifacts/" + PROFILE + "-semantic.txt)\n\n"
                + "Final determination: `" + semantic.result().recordValue() + "`\n");
        EvidenceResult syntax = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, release,
                new QpdfSyntaxRecorder.Profile("T28", T28UnicodeProducts.CAPABILITY, PROFILE,
                        "capabilities/evidence/" + PROFILE + ".md", PROFILE + ".pdf",
                        PROFILE + "-syntax.md", PROFILE + "-qpdf.txt"));
        EvidenceResult visual = EvidenceResult.PASS;
        StringBuilder links = new StringBuilder();
        int page = 0;
        for (String suffix : T28UnicodeProducts.properties("unicode/T28-corpus.properties").getProperty("profiles").split(",")) {
            page++;
            String id = PROFILE + "-" + suffix;
            VisualProfile profile = VisualProfile.load(profilesDirectory.resolve(id + "-visual.properties"));
            if (!profile.profileId().equals(id) || profile.pageNumber() != page || profile.pageCount() != 7) {
                throw new IllegalArgumentException("T28 visual selection does not match the declared Unicode page");
            }
            VisualEvidenceChain chain = VisualEvidenceChain.t28(id);
            VisualEvidence evidence = VisualEvidenceRecorder.record(chain, artifact, hash, artifacts, pdfium, comparator, profile, release);
            EvidenceFiles.write(artifacts.resolve(chain.findingsName()), evidence.rawFindings());
            EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
            if (evidence.result() == EvidenceResult.FAIL) { visual = EvidenceResult.FAIL; }
            else if (evidence.result() == EvidenceResult.INDETERMINATE && visual != EvidenceResult.FAIL) { visual = EvidenceResult.INDETERMINATE; }
            links.append("- [").append(suffix).append("](").append(chain.recordName()).append(")\n");
        }
        EvidenceFiles.write(output.resolve(PROFILE + "-visual.md"), "# T28 independent seven-profile visual evidence\n\n"
                + recordMetadata("visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(), release)
                + provenance + "\nEvery page must pass the original 144-DPI opaque-white sRGB profile: zero-fuzz AE 0 "
                + "and zero changed RGB pixels against the independent raw PDF/fontTools reference. Secondary renderer "
                + "disagreement is limited to 12000 changed pixels. Missing tools remain INDETERMINATE.\n\n" + links
                + "\n[Independent reference PDF](artifacts/" + PROFILE + "-reference.pdf)\n\n"
                + "Final determination: `" + visual.recordValue() + "`\n");
        System.out.println("T28: syntax=" + syntax + ", semantic=" + semantic.result() + ", visual=" + visual
                + "; standards, dependency gates and full Foundation certification remain open.");
    }

    private static String recordMetadata(String chain, EvidenceResult result, String kind,
            String producer, String version, String release) {
        return metadata("Capability", T28UnicodeProducts.CAPABILITY) + metadata("Acceptance Profile", PROFILE)
                + metadata("Profile record", "capabilities/evidence/" + PROFILE + ".md") + metadata("Release train", release)
                + metadata("Chain", chain) + metadata("Result", result.recordValue()) + metadata("Producer kind", kind)
                + metadata("Producer", producer) + metadata("Producer version", version);
    }

    private T28UnicodeEvidenceCommand() { }
}

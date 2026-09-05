package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import static net.zerocloud.pdf.acceptance.EvidenceFiles.write;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.TextRenderingMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.composition.CanvasMatrix;
import net.zerocloud.pdf.composition.FontLimits;
import net.zerocloud.pdf.composition.FontSelection;
import net.zerocloud.pdf.composition.FontSource;
import net.zerocloud.pdf.composition.PositionedUnicodeText;
import net.zerocloud.pdf.composition.ReferenceFontSet;
import net.zerocloud.pdf.composition.command.DrawPositionedUnicodeText;

/** Records the repository-owned T19 syntax, semantic, and visual chains. */
final class T19FontEvidenceRecorder {

    static final String CAPABILITY =
            "composition.fonts.load-embed-subset-fallback";
    static final String PROFILE = "T19-font-loading-embedding-subsetting";
    static final String PROFILE_RECORD =
            "capabilities/evidence/T19-font-loading-embedding-subsetting.md";
    static final String ARTIFACT =
            "T19-font-loading-embedding-subsetting.pdf";
    static final String SYNTAX_RECORD =
            "T19-font-loading-embedding-subsetting-syntax.md";
    static final String SYNTAX_FINDINGS =
            "T19-font-loading-embedding-subsetting-qpdf.txt";
    static final String SEMANTIC_RECORD =
            "T19-font-loading-embedding-subsetting-semantic.md";
    static final String SEMANTIC_FINDINGS =
            "T19-font-loading-embedding-subsetting-semantic.txt";
    private static final QpdfSyntaxRecorder.Profile QPDF_PROFILE =
            new QpdfSyntaxRecorder.Profile(
                    "T19",
                    CAPABILITY,
                    PROFILE,
                    PROFILE_RECORD,
                    ARTIFACT,
                    SYNTAX_RECORD,
                    SYNTAX_FINDINGS);

    private T19FontEvidenceRecorder() {
    }

    static Result record(
            Path output,
            Path artifacts,
            QpdfPin qpdfPin,
            PdfiumPin pdfiumPin,
            ImageMagickPin imageMagickPin,
            VisualProfile visualProfile,
            String releaseTrain) throws Exception {
        Path artifact = artifacts.resolve(ARTIFACT);
        WorkflowOutcome<Void> creation = createProduct(artifact);
        String inputHash = EvidenceFiles.idNeutralPdfSha256(artifact);

        T19FontSemanticObservation semantic =
                T19FontSemanticAssertions.inspect(creation, artifact);
        write(artifacts.resolve(SEMANTIC_FINDINGS),
                semantic.findings(inputHash, releaseTrain));
        write(output.resolve(SEMANTIC_RECORD), semanticRecord(
                inputHash, releaseTrain, semantic));

        EvidenceResult syntax = QpdfSyntaxRecorder.record(
                output,
                artifacts,
                qpdfPin,
                inputHash,
                releaseTrain,
                QPDF_PROFILE);
        VisualEvidenceChain chain = VisualEvidenceChain.t19();
        VisualEvidence visual = VisualEvidenceRecorder.record(
                chain,
                artifact,
                inputHash,
                artifacts,
                pdfiumPin,
                imageMagickPin,
                visualProfile,
                releaseTrain);
        write(artifacts.resolve(chain.findingsName()), visual.rawFindings());
        write(output.resolve(chain.recordName()), visual.record());
        return new Result(syntax, semantic.result(), visual.result());
    }

    private static WorkflowOutcome<Void> createProduct(Path target)
            throws Exception {
        FontSource primary = FontSource.bytes(font("FolioPrimary.ttf.base64"));
        FontSource fallback = FontSource.bytes(font("FolioFallback.ttf.base64"));
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .referenceFontSet(ReferenceFontSet.version1(primary, fallback))
                .build();
        WorkflowOutcome<Void> creation = new DocumentWorkflow(environment)
                .execute(
                        WorkflowRequest.create(target, SaveMode.REWRITE),
                        session -> {
                            session.execute(AddBlankPage.INSTANCE);
                            session.execute(DrawPositionedUnicodeText.version1(
                                    1,
                                    PositionedUnicodeText.version1(
                                            "A\u03a9B",
                                            FontSelection.referenceFontSet(),
                                            96d,
                                            TextRenderingMode.FILL,
                                            CanvasMatrix.of(
                                                    1d, 0d, 0d, 1d,
                                                    80d, 520d)),
                                    limits(3, 4L)));
                            session.execute(DrawPositionedUnicodeText.version1(
                                    1,
                                    PositionedUnicodeText.version1(
                                            "AB",
                                            FontSelection.referenceFontSet(),
                                            48d,
                                            TextRenderingMode.FILL,
                                            CanvasMatrix.of(
                                                    1d, 0d, 0d, 1d,
                                                    80d, 360d)),
                                    limits(2, 2L)));
                            return null;
                        });
        if (!CAPABILITY.equals(creation.getCapabilityId())
                || creation.getPublicationReceipts().size() != 1
                || creation.getPublicationReceipts().get(0).getStatus()
                        != PublicationStatus.COMMITTED) {
            throw new IllegalStateException(
                    "The T19 product was not committed by its public capability");
        }
        return creation;
    }

    private static FontLimits limits(int codePoints, long fallbackChecks) {
        return FontLimits.builder()
                .maximumFontSources(2)
                .maximumSourceBytes(2000L)
                .maximumCodePoints(codePoints)
                .maximumFallbackChecks(fallbackChecks)
                .maximumGeneratedContentBytes(4096L)
                .build();
    }

    static byte[] font(String name) throws IOException {
        String resource = "/net/zerocloud/pdf/acceptance/fonts/" + name;
        try (InputStream input = T19FontEvidenceRecorder.class
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing project font fixture");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return Base64.getMimeDecoder().decode(bytes.toByteArray());
        }
    }

    private static String semanticRecord(
            String inputHash,
            String releaseTrain,
            T19FontSemanticObservation semantic) {
        return "# T19 project semantic evidence\n\n"
                + metadata("Capability", CAPABILITY)
                + metadata("Acceptance Profile", PROFILE)
                + metadata("Profile record", PROFILE_RECORD)
                + metadata("Release train", releaseTrain)
                + metadata("Chain", "semantic")
                + metadata("Result", semantic.result().recordValue())
                + metadata("Producer kind", "project-test")
                + metadata("Producer", "folio-pdf-t19-semantic-assertions")
                + metadata("Producer version", releaseTrain)
                + metadata("Input ID-neutral SHA-256", inputHash)
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy())
                + "Final determination: `" + semantic.result().recordValue()
                + "`\n\n## Finding and artifact\n\n"
                + "- Product: [`artifacts/" + ARTIFACT + "`](artifacts/"
                + ARTIFACT + ")\n"
                + "- Semantic findings: [`artifacts/" + SEMANTIC_FINDINGS
                + "`](artifacts/" + SEMANTIC_FINDINGS + ")\n"
                + "- " + semantic.recordFinding() + "\n\n"
                + "Expected values are project-owned font declarations observed only through public APIs.\n";
    }

    static final class Result {
        private final EvidenceResult syntax;
        private final EvidenceResult semantic;
        private final EvidenceResult visual;

        Result(
                EvidenceResult syntax,
                EvidenceResult semantic,
                EvidenceResult visual) {
            this.syntax = syntax;
            this.semantic = semantic;
            this.visual = visual;
        }

        EvidenceResult syntax() {
            return syntax;
        }

        EvidenceResult semantic() {
            return semantic;
        }

        EvidenceResult visual() {
            return visual;
        }
    }

}

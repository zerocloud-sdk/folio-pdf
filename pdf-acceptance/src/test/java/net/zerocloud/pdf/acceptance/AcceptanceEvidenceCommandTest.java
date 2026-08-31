package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class AcceptanceEvidenceCommandTest {

    private static final String COMMAND_CLASS =
            "net.zerocloud.pdf.acceptance.AcceptanceEvidenceCommand";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void runRecordsIndependentPassingChainsAgainstOneWorkflowOutput()
            throws Exception {
        Path output = temporaryFolder.newFolder("evidence").toPath();
        Path qpdf = qpdfFixture("qpdf", "12.4.0",
                "if [ \"${1-}\" = \"--check\" ]; then",
                "  echo 'PDF Version: 1.7'",
                "  echo 'No syntax or stream encoding errors found'",
                "  exit 0",
                "fi",
                "exit 2");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Acceptance Profile determination: indeterminate"));

        Path artifact = output.resolve("artifacts/T06-document-blank-output.pdf");
        assertTrue(Files.isRegularFile(artifact));
        assertTrue(Files.size(artifact) > 0L);

        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        String semantic = read(output.resolve("T06-document-blank-semantic.md"));
        String visual = read(output.resolve("T07-document-blank-visual.md"));
        String determination = read(output.resolve("T06-document-blank-determination.md"));

        assertMetadata(syntax, "Chain", "syntax");
        assertMetadata(syntax, "Result", "pass");
        assertMetadata(syntax, "Producer kind", "external-tool");
        assertMetadata(syntax, "Producer", "qpdf");
        assertMetadata(syntax, "Producer version", "12.4.0");
        assertMetadata(syntax, "Tool distribution SHA-256",
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3");
        assertTrue(syntax.contains("Final determination: `pass`"));
        assertTrue(syntax.contains("artifacts/T06-document-blank-qpdf.txt"));

        assertMetadata(semantic, "Chain", "semantic");
        assertMetadata(semantic, "Result", "pass");
        assertMetadata(semantic, "Producer kind", "project-test");
        assertMetadata(semantic, "Producer", "folio-pdf-semantic-assertions");
        assertMetadata(semantic, "Producer version", "0.1.0-SNAPSHOT");
        assertTrue(semantic.contains("Final determination: `pass`"));
        assertTrue(semantic.contains("artifacts/T06-document-blank-semantic.txt"));

        assertMetadata(visual, "Chain", "visual");
        assertMetadata(visual, "Result", "pass");
        assertMetadata(visual, "Producer kind", "external-tool");
        assertMetadata(visual, "Producer", "pdfium-cli");
        assertMetadata(
                visual,
                "Producer version",
                "v0.11.2-pdfium-chromium-7881");
        assertMetadata(visual, "ImageMagick version", "7.1.2-30");
        assertMetadata(visual, "Expected comparison AE", "0");
        assertMetadata(visual, "Renderer agreement AE", "0");
        assertMetadata(visual, "Review required", "false");
        assertTrue(visual.contains("Raster dimensions: `1224x1584`"));
        assertTrue(visual.contains("Capability threshold: `0` changed pixels"));
        String visualFindings = read(output.resolve(
                "artifacts/T07-document-blank-visual.txt"));
        assertTrue(!visualFindings.contains("fixture  \n"));

        String syntaxHash = metadata(syntax, "Input ID-neutral SHA-256");
        assertEquals(syntaxHash, metadata(
                semantic, "Input ID-neutral SHA-256"));
        assertEquals(syntaxHash, metadata(
                visual, "Input ID-neutral SHA-256"));
        assertTrue(syntaxHash.matches("[0-9a-f]{64}"));
        assertMetadata(
                visual,
                "Input hash policy",
                EvidenceFiles.inputHashPolicy());
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains(
                "Passing chains: `syntax`, `semantic`, `visual`"));
        assertTrue(determination.contains("Missing mandatory chains: `standards`"));

        String pdfiumArguments = read(output.resolve("pdfium-arguments.txt"));
        assertTrue(pdfiumArguments, pdfiumArguments.contains("render\n"));
        assertTrue(pdfiumArguments, pdfiumArguments.contains(
                "T06-document-blank-output.pdf\n"));
        assertTrue(pdfiumArguments, pdfiumArguments.contains(
                "T07-document-blank-pdfium.png\n"));
        assertTrue(pdfiumArguments, pdfiumArguments.contains("--dpi\n144\n"));

        String imageMagickArguments = read(output.resolve(
                "imagemagick-arguments.txt"));
        assertTrue(imageMagickArguments, imageMagickArguments.contains(
                "T07-document-blank-expected.png\n"));
        assertTrue(imageMagickArguments, imageMagickArguments.contains(
                "T07-document-blank-pdfium.png\n"));
        assertTrue(imageMagickArguments, imageMagickArguments.contains(
                "T07-document-blank-implementation.png\n"));
        assertTrue(imageMagickArguments, imageMagickArguments.contains(
                "-metric\nAE\n-fuzz\n0%\n"));
        assertTrue(imageMagickArguments, !imageMagickArguments.contains(".pdf"));

        String qpdfFindings = read(
                output.resolve("artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(qpdfFindings.contains("Exit code: `0`"));
        assertTrue(qpdfFindings.contains(
                "Distribution SHA-256: "
                        + "`a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`"));
        assertTrue(qpdfFindings.contains("No syntax or stream encoding errors found"));

        String semanticFindings = read(
                output.resolve("artifacts/T06-document-blank-semantic.txt"));
        assertTrue(semanticFindings.contains("Publication status: `COMMITTED`"));
        assertTrue(semanticFindings.contains("Reopened page count: `1`"));

        Path t10Front = output.resolve(
                "artifacts/T10-page-manipulation-front.pdf");
        Path t10Back = output.resolve(
                "artifacts/T10-page-manipulation-back.pdf");
        assertTrue(Files.isRegularFile(t10Front));
        assertTrue(Files.isRegularFile(t10Back));
        assertTrue(Files.size(t10Front) > 0L);
        assertTrue(Files.size(t10Back) > 0L);

        String t10Syntax = read(output.resolve(
                "T10-page-manipulation-merge-split-syntax.md"));
        assertMetadata(
                t10Syntax,
                "Capability",
                "document.page.manipulate-merge-split");
        assertMetadata(
                t10Syntax,
                "Acceptance Profile",
                "T10-page-manipulation-merge-split");
        assertMetadata(t10Syntax, "Chain", "syntax");
        assertMetadata(t10Syntax, "Result", "pass");
        assertMetadata(t10Syntax, "Producer kind", "external-tool");
        assertMetadata(t10Syntax, "Producer", "qpdf");
        assertMetadata(t10Syntax, "Producer version", "12.4.0");
        assertTrue(t10Syntax.contains(
                "artifacts/T10-page-manipulation-front.pdf"));
        assertTrue(t10Syntax.contains(
                "artifacts/T10-page-manipulation-back.pdf"));
        assertTrue(t10Syntax.contains(
                "artifacts/T10-page-manipulation-merge-split-qpdf.txt"));

        String t10Findings = read(output.resolve(
                "artifacts/T10-page-manipulation-merge-split-qpdf.txt"));
        assertTrue(t10Findings.contains(
                "T10-page-manipulation-front.pdf` exit code: `0`"));
        assertTrue(t10Findings.contains(
                "T10-page-manipulation-back.pdf` exit code: `0`"));

        Path t11Front = output.resolve(
                "artifacts/T11-metadata-front.pdf");
        Path t11Back = output.resolve(
                "artifacts/T11-metadata-back.pdf");
        assertTrue(Files.isRegularFile(t11Front));
        assertTrue(Files.isRegularFile(t11Back));
        assertTrue(Files.size(t11Front) > 0L);
        assertTrue(Files.size(t11Back) > 0L);

        String t11Syntax = readT11Syntax(output);
        assertMetadata(
                t11Syntax,
                "Capability",
                "document.metadata.outlines-destinations-attachments");
        assertMetadata(
                t11Syntax,
                "Acceptance Profile",
                "T11-metadata-outlines-destinations-attachments");
        assertMetadata(t11Syntax, "Chain", "syntax");
        assertMetadata(t11Syntax, "Result", "pass");
        assertMetadata(t11Syntax, "Producer kind", "external-tool");
        assertMetadata(t11Syntax, "Producer", "qpdf");
        assertMetadata(t11Syntax, "Producer version", "12.4.0");
        assertTrue(t11Syntax.contains(
                "artifacts/T11-metadata-front.pdf"));
        assertTrue(t11Syntax.contains(
                "artifacts/T11-metadata-back.pdf"));
        assertTrue(t11Syntax.contains(
                "artifacts/T11-metadata-outlines-destinations-attachments-qpdf.txt"));

        String t11Findings = read(output.resolve(
                "artifacts/T11-metadata-outlines-destinations-attachments-qpdf.txt"));
        assertTrue(t11Findings.contains(
                "T11-metadata-front.pdf` exit code: `0`"));
        assertTrue(t11Findings.contains(
                "T11-metadata-back.pdf` exit code: `0`"));
    }

    @Test
    public void visualProfileRejectsUnsupportedComparisonPolicy()
            throws Exception {
        Path output = temporaryFolder.newFolder("unsupported-profile").toPath();
        VisualFixtures fixtures = visualFixtures(
                output,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                null);
        List<String> properties = new ArrayList<String>(Files.readAllLines(
                fixtures.profile,
                StandardCharsets.UTF_8));
        properties.add("COMPARISON_METRIC=RMSE");
        Files.write(fixtures.profile, properties, StandardCharsets.UTF_8);

        try {
            VisualProfile.load(fixtures.profile);
            throw new AssertionError("unsupported comparison metric was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "Unsupported visual profile property COMPARISON_METRIC"));
        }
    }

    @Test
    public void repeatedRunsProduceIdenticalEvidenceMetadata()
            throws Exception {
        Path firstOutput = temporaryFolder.newFolder("reproduction-first").toPath();
        Path secondOutput = temporaryFolder.newFolder("reproduction-second").toPath();
        Path firstQpdf = qpdfFixture(
                "reproduction-qpdf-first",
                "12.4.0",
                "if [ \"${1-}\" = \"--check\" ]; then",
                "  echo 'No syntax or stream encoding errors found'",
                "  exit 0",
                "fi",
                "exit 2");
        Path secondQpdf = qpdfFixture(
                "reproduction-qpdf-second",
                "12.4.0",
                "if [ \"${1-}\" = \"--check\" ]; then",
                "  echo 'No syntax or stream encoding errors found'",
                "  exit 0",
                "fi",
                "exit 2");

        CommandResult first = runCommand(firstOutput, firstQpdf);
        CommandResult second = runCommand(secondOutput, secondQpdf);

        assertEquals(first.output, 0, first.exitCode);
        assertEquals(second.output, 0, second.exitCode);
        for (String relative : Arrays.asList(
                "T06-document-blank-syntax.md",
                "T06-document-blank-semantic.md",
                "T06-document-blank-determination.md",
                "T07-document-blank-visual.md",
                "artifacts/T06-document-blank-qpdf.txt",
                "artifacts/T06-document-blank-semantic.txt",
                "artifacts/T07-document-blank-visual.txt")) {
            assertEquals(relative,
                    read(firstOutput.resolve(relative)),
                    read(secondOutput.resolve(relative)));
        }
    }

    @Test
    public void semanticAssertionsReportTheObservedPageSequenceOnFailure()
            throws Exception {
        Path pdf = temporaryFolder.newFile("two-pages.pdf").toPath();
        WorkflowOutcome<Void> creation = new DocumentWorkflow().execute(
                WorkflowRequest.create(pdf, SaveMode.REWRITE),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        SemanticObservation observation = SemanticAssertions.inspect(creation, pdf);

        assertEquals(EvidenceResult.FAIL, observation.result());
        assertEquals("[1, 2]", observation.pageSequence());
        assertTrue(observation.recordFinding().contains(
                "observed `COMMITTED` and `2` reopened pages"));
        assertTrue(!observation.recordFinding().contains("reopened exactly one page"));
        assertTrue(observation.findings("fixture-hash", "fixture-version").contains(
                "Object graph observation: `reopened through DocumentWorkflow`"));
        assertTrue(observation.findings("fixture-hash", "fixture-version").contains(
                "Text order: not applicable; the blank-document profile emits no text."));
    }

    @Test
    public void missingQpdfRecordsIndeterminateAndNeverPass() throws Exception {
        Path output = temporaryFolder.newFolder("missing-qpdf-evidence").toPath();
        Path missingQpdf = output.resolve("missing-qpdf");

        CommandResult result = runCommand(output, missingQpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertMetadata(syntax, "Producer version", "unavailable");
        assertTrue(syntax.contains("Final determination: `indeterminate`"));
        assertTrue(syntax.contains("The pinned qpdf tool was unavailable."));
        assertTrue(!syntax.contains("Result: `pass`"));

        String semantic = read(output.resolve("T06-document-blank-semantic.md"));
        assertMetadata(semantic, "Result", "pass");
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains(
                "Indeterminate mandatory chains: `syntax`"));
        String t10Syntax = readT10Syntax(output);
        assertMetadata(t10Syntax, "Result", "indeterminate");
        assertMetadata(t10Syntax, "Producer version", "unavailable");
        assertTrue(t10Syntax.contains("The pinned qpdf tool was unavailable."));
        assertTrue(!t10Syntax.contains("Result: `pass`"));
        String t11Syntax = readT11Syntax(output);
        assertMetadata(t11Syntax, "Result", "indeterminate");
        assertMetadata(t11Syntax, "Producer version", "unavailable");
        assertTrue(t11Syntax.contains("The pinned qpdf tool was unavailable."));
        assertTrue(!t11Syntax.contains("Result: `pass`"));
    }

    @Test
    public void unpinnedQpdfVersionRecordsIndeterminateAndDoesNotRunCheck()
            throws Exception {
        Path output = temporaryFolder.newFolder("wrong-qpdf-evidence").toPath();
        Path qpdf = qpdfFixture("wrong-qpdf", "12.3.2",
                "echo 'unpinned qpdf check unexpectedly ran' >&2",
                "exit 99");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertMetadata(syntax, "Producer version", "12.3.2");
        assertTrue(syntax.contains(
                "Expected pinned qpdf version `12.4.0`; observed `12.3.2`."));
        assertTrue(!syntax.contains("Result: `pass`"));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(!findings.contains("unpinned qpdf check unexpectedly ran"));
        String t10Syntax = readT10Syntax(output);
        assertMetadata(t10Syntax, "Result", "indeterminate");
        assertMetadata(t10Syntax, "Producer version", "12.3.2");
        assertTrue(t10Syntax.contains(
                "Expected pinned qpdf version `12.4.0`; observed `12.3.2`."));
        String t11Syntax = readT11Syntax(output);
        assertMetadata(t11Syntax, "Result", "indeterminate");
        assertMetadata(t11Syntax, "Producer version", "12.3.2");
        assertTrue(t11Syntax.contains(
                "Expected pinned qpdf version `12.4.0`; observed `12.3.2`."));
    }

    @Test
    public void qpdfWarningsAreRecordedAsFailingSyntaxEvidence() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-warning-evidence").toPath();
        Path qpdf = qpdfFixture("warning-qpdf", "12.4.0",
                "echo 'WARNING: recovered malformed xref' >&2",
                "exit 3");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                "Acceptance Profile determination: fail"));
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "fail");
        assertTrue(syntax.contains("Final determination: `fail`"));
        assertTrue(syntax.contains("qpdf reported warnings (exit code `3`)."));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `3`"));
        assertTrue(findings.contains("WARNING: recovered malformed xref"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `fail`"));
        assertTrue(determination.contains("Failing mandatory chains: `syntax`"));
        String t10Syntax = readT10Syntax(output);
        assertMetadata(t10Syntax, "Result", "fail");
        assertTrue(t10Syntax.contains(
                "qpdf reported warnings or errors for a T10 product."));
        String t11Syntax = readT11Syntax(output);
        assertMetadata(t11Syntax, "Result", "fail");
        assertTrue(t11Syntax.contains(
                "qpdf reported warnings or errors for a T11 product."));
    }

    @Test
    public void qpdfErrorsAreRecordedAsFailingSyntaxEvidence() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-error-evidence").toPath();
        Path qpdf = qpdfFixture("error-qpdf", "12.4.0",
                "echo 'ERROR: unable to find trailer dictionary' >&2",
                "exit 2");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "fail");
        assertTrue(syntax.contains("qpdf reported errors (exit code `2`)."));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `2`"));
        assertTrue(findings.contains("ERROR: unable to find trailer dictionary"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `fail`"));
        assertMetadata(readT10Syntax(output), "Result", "fail");
        assertMetadata(readT11Syntax(output), "Result", "fail");
    }

    @Test
    public void unexpectedQpdfProcessExitRecordsIndeterminate() throws Exception {
        Path output = temporaryFolder.newFolder("qpdf-unavailable-evidence").toPath();
        Path qpdf = qpdfFixture("unavailable-qpdf", "12.4.0",
                "echo 'pinned qpdf payload is not provisioned' >&2",
                "exit 127");

        CommandResult result = runCommand(output, qpdf);

        assertEquals(result.output, 0, result.exitCode);
        String syntax = read(output.resolve("T06-document-blank-syntax.md"));
        assertMetadata(syntax, "Result", "indeterminate");
        assertTrue(syntax.contains(
                "qpdf did not return a documented inspection status (exit code `127`)."));
        assertTrue(!syntax.contains("Result: `pass`"));
        String findings = read(output.resolve(
                "artifacts/T06-document-blank-qpdf.txt"));
        assertTrue(findings.contains("Exit code: `127`"));
        assertTrue(findings.contains("pinned qpdf payload is not provisioned"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `indeterminate`"));
        assertTrue(determination.contains(
                "Indeterminate mandatory chains: `syntax`"));
        String t10Syntax = readT10Syntax(output);
        assertMetadata(t10Syntax, "Result", "indeterminate");
        assertTrue(t10Syntax.contains(
                "qpdf returned an undocumented status for a T10 product."));
        String t11Syntax = readT11Syntax(output);
        assertMetadata(t11Syntax, "Result", "indeterminate");
        assertTrue(t11Syntax.contains(
                "qpdf returned an undocumented status for a T11 product."));
    }

    @Test
    public void thresholdMismatchFailsWithMetricsAndReviewableDifference()
            throws Exception {
        Path output = temporaryFolder.newFolder("visual-mismatch-evidence").toPath();
        Path qpdf = qpdfFixture("visual-mismatch-qpdf", "12.4.0",
                "exit 0");
        VisualFixtures visuals = visualFixtures(
                output,
                "v0.11.2",
                "7.1.2-30",
                17L,
                0L,
                null);

        CommandResult result = runCommand(output, qpdf, visuals);

        assertEquals(result.output, 0, result.exitCode);
        String visual = read(output.resolve("T07-document-blank-visual.md"));
        assertMetadata(visual, "Result", "fail");
        assertMetadata(visual, "Expected comparison AE", "17");
        assertMetadata(visual, "Renderer agreement AE", "0");
        assertTrue(visual.contains("exceeded the capability threshold"));
        assertTrue(visual.contains("artifacts/T07-document-blank-difference.png"));
        assertTrue(Files.isRegularFile(output.resolve(
                "artifacts/T07-document-blank-difference.png")));
        String findings = read(output.resolve(
                "artifacts/T07-document-blank-visual.txt"));
        assertMetadata(findings, "Expected comparison AE", "17");
        assertTrue(findings.contains("Exit code: `1`"));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains("Final determination: `fail`"));
        assertTrue(determination.contains("Failing mandatory chains: `visual`"));
    }

    @Test
    public void secondaryRendererDisagreementRequiresReviewAndNeverPasses()
            throws Exception {
        Path output = temporaryFolder.newFolder("renderer-disagreement").toPath();
        Path qpdf = qpdfFixture("renderer-disagreement-qpdf", "12.4.0",
                "exit 0");
        VisualFixtures visuals = visualFixtures(
                output,
                "v0.11.2",
                "7.1.2-30",
                0L,
                9L,
                null);

        CommandResult result = runCommand(output, qpdf, visuals);

        assertEquals(result.output, 0, result.exitCode);
        String visual = read(output.resolve("T07-document-blank-visual.md"));
        assertMetadata(visual, "Result", "indeterminate");
        assertMetadata(visual, "Expected comparison AE", "0");
        assertMetadata(visual, "Renderer agreement AE", "9");
        assertMetadata(visual, "Review required", "true");
        assertTrue(visual.contains("review is required"));
        assertTrue(!visual.contains("Result: `pass`"));
        assertTrue(Files.isRegularFile(output.resolve(
                "artifacts/T07-document-blank-renderer-difference.png")));
        String determination = read(output.resolve(
                "T06-document-blank-determination.md"));
        assertTrue(determination.contains(
                "Indeterminate mandatory chains: `visual`"));
    }

    @Test
    public void missingAndUnpinnedVisualToolsRemainIndeterminate()
            throws Exception {
        Path missingOutput = temporaryFolder.newFolder("missing-pdfium").toPath();
        Path qpdf = qpdfFixture("missing-visual-qpdf", "12.4.0", "exit 0");
        VisualFixtures available = visualFixtures(
                missingOutput,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                null);
        VisualFixtures missing = new VisualFixtures(
                missingOutput.resolve("missing-pdfium"),
                available.imageMagick,
                available.profile);

        CommandResult missingResult = runCommand(missingOutput, qpdf, missing);

        assertEquals(missingResult.output, 0, missingResult.exitCode);
        String missingVisual = read(missingOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(missingVisual, "Result", "indeterminate");
        assertMetadata(missingVisual, "PDFium CLI version", "unavailable");
        assertTrue(missingVisual.contains("PDFium renderer was unavailable"));
        assertTrue(!missingVisual.contains("Result: `pass`"));

        Path missingImageOutput = temporaryFolder.newFolder(
                "missing-imagemagick").toPath();
        VisualFixtures imageAvailable = visualFixtures(
                missingImageOutput,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                null);
        VisualFixtures missingImageMagick = new VisualFixtures(
                imageAvailable.pdfium,
                missingImageOutput.resolve("missing-imagemagick"),
                imageAvailable.profile);

        CommandResult missingImageResult = runCommand(
                missingImageOutput,
                qpdf,
                missingImageMagick);

        assertEquals(missingImageResult.output, 0, missingImageResult.exitCode);
        String missingImageVisual = read(missingImageOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(missingImageVisual, "Result", "indeterminate");
        assertMetadata(missingImageVisual, "ImageMagick version", "unavailable");
        assertTrue(missingImageVisual.contains(
                "ImageMagick comparator was unavailable"));
        assertTrue(!missingImageVisual.contains("Result: `pass`"));

        Path wrongOutput = temporaryFolder.newFolder("wrong-visual-versions").toPath();
        VisualFixtures wrongPdfium = visualFixtures(
                wrongOutput,
                "v0.10.0",
                "7.1.2-30",
                0L,
                0L,
                null);
        CommandResult wrongResult = runCommand(wrongOutput, qpdf, wrongPdfium);

        assertEquals(wrongResult.output, 0, wrongResult.exitCode);
        String wrongVisual = read(wrongOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(wrongVisual, "Result", "indeterminate");
        assertMetadata(wrongVisual, "PDFium CLI version", "v0.10.0");
        assertTrue(wrongVisual.contains(
                "Expected pinned PDFium CLI version `v0.11.2`; observed `v0.10.0`"));
        assertTrue(!wrongVisual.contains("Result: `pass`"));

        Path wrongImageOutput = temporaryFolder.newFolder(
                "wrong-imagemagick-version").toPath();
        VisualFixtures wrongImageMagick = visualFixtures(
                wrongImageOutput,
                "v0.11.2",
                "7.1.2-29",
                0L,
                0L,
                null);
        CommandResult wrongImageResult = runCommand(
                wrongImageOutput,
                qpdf,
                wrongImageMagick);

        assertEquals(wrongImageResult.output, 0, wrongImageResult.exitCode);
        String wrongImageVisual = read(wrongImageOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(wrongImageVisual, "Result", "indeterminate");
        assertMetadata(wrongImageVisual, "ImageMagick version", "7.1.2-29");
        assertTrue(!wrongImageVisual.contains("Result: `pass`"));
    }

    @Test
    public void malformedAndWrongSizedPdfiumRastersAreRejectedBeforeComparison()
            throws Exception {
        Path qpdf = qpdfFixture("raster-validation-qpdf", "12.4.0", "exit 0");

        Path malformedOutput = temporaryFolder.newFolder("malformed-raster").toPath();
        Path malformed = malformedOutput.resolve("malformed.png");
        Files.write(malformed, Arrays.asList("not a png"), StandardCharsets.UTF_8);
        VisualFixtures malformedVisuals = visualFixtures(
                malformedOutput,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                malformed);
        CommandResult malformedResult = runCommand(
                malformedOutput,
                qpdf,
                malformedVisuals);

        assertEquals(malformedResult.output, 0, malformedResult.exitCode);
        String malformedVisual = read(malformedOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(malformedVisual, "Result", "indeterminate");
        assertTrue(malformedVisual.contains("malformed or wrong-sized raster"));
        assertTrue(!Files.exists(malformedOutput.resolve(
                "imagemagick-arguments.txt")));

        Path wrongSizeOutput = temporaryFolder.newFolder("wrong-size-raster").toPath();
        Path wrongSize = wrongSizeOutput.resolve("wrong-size.png");
        writeRaster(wrongSize, 12, 16, Color.WHITE);
        VisualFixtures wrongSizeVisuals = visualFixtures(
                wrongSizeOutput,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                wrongSize);
        CommandResult wrongSizeResult = runCommand(
                wrongSizeOutput,
                qpdf,
                wrongSizeVisuals);

        assertEquals(wrongSizeResult.output, 0, wrongSizeResult.exitCode);
        String wrongSizeVisual = read(wrongSizeOutput.resolve(
                "T07-document-blank-visual.md"));
        assertMetadata(wrongSizeVisual, "Result", "indeterminate");
        String wrongSizeFindings = read(wrongSizeOutput.resolve(
                "artifacts/T07-document-blank-visual.txt"));
        assertTrue(wrongSizeFindings.contains(
                "Raster dimensions were 12x16; expected 1224x1584"));
        assertTrue(!Files.exists(wrongSizeOutput.resolve(
                "imagemagick-arguments.txt")));
    }

    @Test
    public void unexpectedImageMagickStatusCannotProduceVisualPass()
            throws Exception {
        Path output = temporaryFolder.newFolder("unexpected-imagemagick").toPath();
        Path qpdf = qpdfFixture("unexpected-imagemagick-qpdf", "12.4.0", "exit 0");
        VisualFixtures visuals = visualFixtures(
                output,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                null);
        writeExecutable(visuals.imageMagick, Arrays.asList(
                "#!/bin/sh",
                "if [ \"${1-}\" = \"--version\" ]; then",
                "  echo 'Version: ImageMagick 7.1.2-30 Q16-HDRI x86_64 fixture'",
                "  exit 0",
                "fi",
                "if [ \"${1-}\" = \"compare\" ]; then",
                "  cp \"${12}\" \"${14}\"",
                "  printf '0 (0)' >&2",
                "  exit 42",
                "fi",
                "exit 99"));

        CommandResult result = runCommand(output, qpdf, visuals);

        assertEquals(result.output, 0, result.exitCode);
        String visual = read(output.resolve("T07-document-blank-visual.md"));
        assertMetadata(visual, "Result", "indeterminate");
        assertTrue(visual.contains("unexpected comparison status `42`"));
        assertTrue(!visual.contains("Result: `pass`"));
    }

    @Test
    public void provisionerVerifiesAndStagesThePinnedQpdfArchive() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path archive = temporaryFolder.newFile(
                "qpdf-12.4.0-bin-linux-x86_64.zip").toPath();
        Path cache = temporaryFolder.newFolder("qpdf-cache").toPath();
        Path sha256 = executable("fixture-sha256sum", Arrays.asList(
                "#!/bin/sh",
                "case ${1} in",
                "  */bin/qpdf) digest=9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b ;;",
                "  *) digest=a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3 ;;",
                "esac",
                "printf '%s  %s\\n' \"${digest}\" \"${1}\""));
        Path unzip = executable("fixture-unzip", Arrays.asList(
                "#!/bin/sh",
                "destination=${4:?missing destination}",
                "mkdir -p \"${destination}/bin\" \"${destination}/lib\"",
                "printf '%s' 'libqpdf.so.30.4.0' > \"${destination}/lib/libqpdf.so.30\"",
                ": > \"${destination}/lib/libqpdf.so.30.4.0\"",
                "printf '%s\\n' '#!/bin/sh' "
                        + "'if [ \"${1-}\" = \"--version\" ]; then' "
                        + "'  echo \"qpdf version 12.4.0\"' "
                        + "'  echo \"Run qpdf --copyright for details.\"' "
                        + "'  exit 0' 'fi' "
                        + "'echo \"fixture qpdf\"' > \"${destination}/bin/qpdf\""));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());
        environment.put("SHA256_COMMAND", sha256.toString());
        environment.put("UNZIP_COMMAND", unzip.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/provision-qpdf").toString(),
                        archive.toString()),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("Provisioned qpdf 12.4.0"));
        assertTrue(Files.isExecutable(cache.resolve("12.4.0/bin/qpdf")));
        assertTrue(Files.isSymbolicLink(cache.resolve("12.4.0/lib/libqpdf.so.30")));
        assertEquals(
                "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b\n",
                read(cache.resolve("12.4.0/.binary-sha256")));
    }

    @Test
    public void visualProvisionersVerifyAndStageOnlyPinnedDistributions()
            throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path pdfiumDistribution = executable(
                "pdfium-webassembly-linux-amd64",
                Arrays.asList(
                        "#!/bin/sh",
                        "echo 'pdfium version v0.11.2'"));
        Path pdfiumCache = temporaryFolder.newFolder("pdfium-cache").toPath();
        Path pdfiumSha = executable("fixture-pdfium-sha256sum", Arrays.asList(
                "#!/bin/sh",
                "printf '%s  %s\\n' "
                        + "'3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab' "
                        + "\"${1}\""));
        Map<String, String> pdfiumEnvironment = new HashMap<String, String>();
        pdfiumEnvironment.put("PDFIUM_CACHE_DIRECTORY", pdfiumCache.toString());
        pdfiumEnvironment.put("SHA256_COMMAND", pdfiumSha.toString());

        CommandResult pdfiumResult = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/provision-pdfium").toString(),
                        pdfiumDistribution.toString()),
                pdfiumEnvironment);

        assertEquals(pdfiumResult.output, 0, pdfiumResult.exitCode);
        assertTrue(pdfiumResult.output, pdfiumResult.output.contains(
                "Provisioned PDFium v0.11.2 (chromium-7881)"));
        Path pdfiumHome = pdfiumCache.resolve("v0.11.2-chromium-7881");
        assertTrue(Files.isExecutable(pdfiumHome.resolve("bin/pdfium")));
        assertEquals("chromium-7881\n", read(pdfiumHome.resolve(".engine-version")));

        Path imageMagickDistribution = executable(
                "ImageMagick-7.1.2-30-gcc-x86_64.AppImage",
                Arrays.asList(
                        "#!/bin/sh",
                        "echo 'Version: ImageMagick 7.1.2-30 Q16-HDRI x86_64 fixture'"));
        Path imageMagickCache = temporaryFolder.newFolder(
                "imagemagick-cache").toPath();
        Path imageMagickSha = executable(
                "fixture-imagemagick-sha256sum",
                Arrays.asList(
                        "#!/bin/sh",
                        "printf '%s  %s\\n' "
                                + "'372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e' "
                                + "\"${1}\""));
        Map<String, String> imageMagickEnvironment = new HashMap<String, String>();
        imageMagickEnvironment.put(
                "IMAGEMAGICK_CACHE_DIRECTORY",
                imageMagickCache.toString());
        imageMagickEnvironment.put("SHA256_COMMAND", imageMagickSha.toString());

        CommandResult imageMagickResult = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/provision-imagemagick").toString(),
                        imageMagickDistribution.toString()),
                imageMagickEnvironment);

        assertEquals(imageMagickResult.output, 0, imageMagickResult.exitCode);
        assertTrue(imageMagickResult.output, imageMagickResult.output.contains(
                "Provisioned ImageMagick 7.1.2-30"));
        Path imageMagickHome = imageMagickCache.resolve("7.1.2-30");
        assertTrue(Files.isExecutable(imageMagickHome.resolve(
                "bin/imagemagick.AppImage")));
        assertEquals(
                "372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e\n",
                read(imageMagickHome.resolve(".executable-sha256")));
    }

    @Test
    public void visualWrappersRejectWrongDigestMarkers() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path pdfiumCache = temporaryFolder.newFolder(
                "wrong-pdfium-wrapper-cache").toPath();
        Path pdfiumHome = pdfiumCache.resolve("v0.11.2-chromium-7881");
        Files.createDirectories(pdfiumHome.resolve("bin"));
        writeExecutable(pdfiumHome.resolve("bin/pdfium"), Arrays.asList(
                "#!/bin/sh",
                "exit 0"));
        Files.write(pdfiumHome.resolve(".archive-sha256"), Arrays.asList(
                "wrong-digest"), StandardCharsets.UTF_8);
        Files.write(pdfiumHome.resolve(".executable-sha256"), Arrays.asList(
                "3ef3375c429ce665e834f933a028225bf28ac837695aaa69c6fc21facf6780ab"),
                StandardCharsets.UTF_8);
        Files.write(pdfiumHome.resolve(".engine-version"), Arrays.asList(
                "chromium-7881"), StandardCharsets.UTF_8);
        Map<String, String> pdfiumEnvironment = new HashMap<String, String>();
        pdfiumEnvironment.put("PDFIUM_CACHE_DIRECTORY", pdfiumCache.toString());

        CommandResult pdfiumResult = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/pdfium").toString(),
                        "--version"),
                pdfiumEnvironment);

        assertEquals(pdfiumResult.output, 127, pdfiumResult.exitCode);
        assertTrue(pdfiumResult.output, pdfiumResult.output.contains(
                "digest marker is invalid"));

        Path imageMagickCache = temporaryFolder.newFolder(
                "wrong-imagemagick-wrapper-cache").toPath();
        Path imageMagickHome = imageMagickCache.resolve("7.1.2-30");
        Files.createDirectories(imageMagickHome.resolve("bin"));
        writeExecutable(
                imageMagickHome.resolve("bin/imagemagick.AppImage"),
                Arrays.asList("#!/bin/sh", "exit 0"));
        Files.write(imageMagickHome.resolve(".archive-sha256"), Arrays.asList(
                "372af8a3fd61ef5f15c6331cde3e21f840eb165d8b533f34ed05d68736dd682e"),
                StandardCharsets.UTF_8);
        Files.write(imageMagickHome.resolve(".executable-sha256"), Arrays.asList(
                "wrong-digest"), StandardCharsets.UTF_8);
        Map<String, String> imageMagickEnvironment = new HashMap<String, String>();
        imageMagickEnvironment.put(
                "IMAGEMAGICK_CACHE_DIRECTORY",
                imageMagickCache.toString());

        CommandResult imageMagickResult = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/imagemagick").toString(),
                        "--version"),
                imageMagickEnvironment);

        assertEquals(imageMagickResult.output, 127, imageMagickResult.exitCode);
        assertTrue(imageMagickResult.output, imageMagickResult.output.contains(
                "digest marker is invalid"));
    }

    @Test
    public void qpdfWrapperRunsOnlyTheDigestMarkedPinnedPayload() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path cache = temporaryFolder.newFolder("wrapper-qpdf-cache").toPath();
        Path qpdfHome = cache.resolve("12.4.0");
        Files.createDirectories(qpdfHome.resolve("bin"));
        Files.createDirectories(qpdfHome.resolve("lib"));
        Path qpdf = qpdfHome.resolve("bin/qpdf");
        Files.write(qpdf, Arrays.asList(
                "#!/bin/sh",
                "echo \"${LD_LIBRARY_PATH-}|${1-}|${2-}\""), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(qpdf, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Files.write(qpdfHome.resolve(".archive-sha256"), Arrays.asList(
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3"),
                StandardCharsets.UTF_8);
        Files.write(qpdfHome.resolve(".binary-sha256"), Arrays.asList(
                "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b"),
                StandardCharsets.UTF_8);
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/qpdf").toString(),
                        "--check",
                        "fixture.pdf"),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains(
                qpdfHome.resolve("lib").toString() + "|--check|fixture.pdf"));
    }

    @Test
    public void qpdfWrapperRejectsCacheWithoutThePinnedBinaryMarker() throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path cache = temporaryFolder.newFolder("unmarked-qpdf-cache").toPath();
        Path qpdfHome = cache.resolve("12.4.0");
        Files.createDirectories(qpdfHome.resolve("bin"));
        Path qpdf = qpdfHome.resolve("bin/qpdf");
        Files.write(qpdf, Arrays.asList("#!/bin/sh", "exit 0"), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(qpdf, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        Files.write(qpdfHome.resolve(".archive-sha256"), Arrays.asList(
                "a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3"),
                StandardCharsets.UTF_8);
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("QPDF_CACHE_DIRECTORY", cache.toString());

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/container-bin/qpdf").toString(),
                        "--version"),
                environment);

        assertEquals(result.output, 127, result.exitCode);
        assertTrue(result.output, result.output.contains("digest marker is invalid"));
    }

    @Test
    public void acceptanceRunnerIgnoresToolOverridesAndUsesRepositoryPinAuthorities()
            throws Exception {
        Path root = Paths.get(requiredProperty("repositoryRoot"));
        Path output = temporaryFolder.newFolder("runner-output").toPath();
        Path maven = executable("fixture-maven", Arrays.asList(
                "#!/bin/sh",
                "printf '%s\\n' \"$@\""));
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("MAVEN_COMMAND", maven.toString());
        environment.put("QPDF_COMMAND", "/tmp/untrusted-qpdf");
        environment.put("PDFIUM_COMMAND", "/tmp/untrusted-pdfium");
        environment.put("IMAGEMAGICK_COMMAND", "/tmp/untrusted-imagemagick");

        CommandResult result = runProcess(
                Arrays.asList(
                        "sh",
                        root.resolve("scripts/acceptance").toString(),
                        output.toString()),
                environment);

        assertEquals(result.output, 0, result.exitCode);
        assertTrue(result.output, result.output.contains("-pl\npdf-acceptance\n"));
        assertTrue(result.output, result.output.contains("-Pacceptance-record"));
        assertTrue(result.output, result.output.contains(
                "-Dacceptance.output=" + output.toAbsolutePath().normalize()));
        assertTrue(result.output, !result.output.contains("-Dacceptance.qpdf="));
        assertTrue(result.output, !result.output.contains("-Dacceptance.pdfium="));
        assertTrue(result.output, !result.output.contains("-Dacceptance.imagemagick="));
        String acceptancePom = read(root.resolve("pdf-acceptance/pom.xml"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/scripts/qpdf-pin.properties"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/scripts/pdfium-pin.properties"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/scripts/imagemagick-pin.properties"));
        assertTrue(read(root.resolve("scripts/qpdf-pin.properties"))
                .contains("QPDF_EXECUTABLE=container-bin/qpdf"));
        assertTrue(read(root.resolve("scripts/pdfium-pin.properties"))
                .contains("PDFIUM_EXECUTABLE=container-bin/pdfium"));
        assertTrue(read(root.resolve("scripts/imagemagick-pin.properties"))
                .contains("IMAGEMAGICK_EXECUTABLE=container-bin/imagemagick"));
        assertTrue(acceptancePom.contains(
                "${maven.multiModuleProjectDirectory}/capabilities/profiles/"
                        + "T03-document-blank-visual.properties"));
        assertTrue(result.output, result.output.endsWith("verify\n"));
    }

    private Path executable(String name, Iterable<String> lines) throws IOException {
        Path executable = temporaryFolder.newFile(name).toPath();
        Files.write(executable, lines, StandardCharsets.UTF_8);
        Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(executable, permissions);
        return executable;
    }

    private Path qpdfFixture(String name, String version, String... checkBehavior)
            throws IOException {
        List<String> lines = new ArrayList<String>();
        lines.add("#!/bin/sh");
        lines.add("if [ \"${1-}\" = \"--version\" ]; then");
        lines.add("  echo 'qpdf version " + version + "'");
        lines.add("  exit 0");
        lines.add("fi");
        lines.addAll(Arrays.asList(checkBehavior));
        return executable(name, lines);
    }

    private static CommandResult runCommand(Path output, Path qpdf)
            throws IOException, InterruptedException {
        return runCommand(output, qpdf, visualFixtures(
                output,
                "v0.11.2",
                "7.1.2-30",
                0L,
                0L,
                null));
    }

    private static CommandResult runCommand(
            Path output,
            Path qpdf,
            VisualFixtures visualFixtures)
            throws IOException, InterruptedException {
        Path repositoryRoot = Paths.get(requiredProperty("repositoryRoot"));
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        Path qpdfPin = configuredPin(
                repositoryRoot.resolve("scripts/qpdf-pin.properties"),
                "QPDF_EXECUTABLE",
                qpdf,
                output.resolve("qpdf-fixture-pin.properties"));
        Path pdfiumPin = configuredPin(
                repositoryRoot.resolve("scripts/pdfium-pin.properties"),
                "PDFIUM_EXECUTABLE",
                visualFixtures.pdfium,
                output.resolve("pdfium-fixture-pin.properties"));
        Path imageMagickPin = configuredPin(
                repositoryRoot.resolve("scripts/imagemagick-pin.properties"),
                "IMAGEMAGICK_EXECUTABLE",
                visualFixtures.imageMagick,
                output.resolve("imagemagick-fixture-pin.properties"));
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                COMMAND_CLASS,
                output.toString(),
                qpdfPin.toString(),
                pdfiumPin.toString(),
                imageMagickPin.toString(),
                visualFixtures.profile.toString(),
                "0.1.0-SNAPSHOT")
                .redirectErrorStream(true)
                .start();
        String commandOutput;
        try (InputStream input = process.getInputStream()) {
            commandOutput = read(input);
        }
        return new CommandResult(process.waitFor(), commandOutput);
    }

    private static Path configuredPin(
            Path repositoryPin,
            String executableProperty,
            Path executable,
            Path fixturePin) throws IOException {
        List<String> properties = new ArrayList<String>(
                Files.readAllLines(repositoryPin, StandardCharsets.UTF_8));
        properties.add(executableProperty + "="
                + executable.toAbsolutePath().normalize());
        Files.write(fixturePin, properties, StandardCharsets.UTF_8);
        return fixturePin;
    }

    private static VisualFixtures visualFixtures(
            Path output,
            String pdfiumVersion,
            String imageMagickVersion,
            long primaryMetric,
            long rendererMetric,
            Path renderedRaster) throws IOException {
        Path fixtures = output.resolve("visual-fixture");
        Files.createDirectories(fixtures);
        Path expected = fixtures.resolve("expected.png");
        writeRaster(expected, 1224, 1584, Color.WHITE);
        Path rendererSource = renderedRaster == null ? expected : renderedRaster;
        Path profile = fixtures.resolve("visual-profile.properties");
        Files.write(profile, Arrays.asList(
                "PROFILE_ID=T03-document-workflow-transaction",
                "PAGE_BOX=effective CropBox; CropBox is absent, so MediaBox [0 0 612 792] points is used",
                "DPI=144",
                "COLOR_POLICY=sRGB, opaque 8-bit RGB PNG; grayscale and alpha are disabled",
                "FONT_POLICY=not applicable; the blank document has no text or font resources and uses no system fonts",
                "ANTIALIASING_POLICY=pinned PDFium default smoothing; no marks are present for antialiasing to affect",
                "BACKGROUND=opaque white (#ffffff)",
                "RASTER_WIDTH=1224",
                "RASTER_HEIGHT=1584",
                "COMPARISON_METRIC=AE",
                "COMPARISON_FUZZ_PERCENT=0",
                "COMPARISON_THRESHOLD=0",
                "RENDERER_AGREEMENT_THRESHOLD=0",
                "EXPECTED_RASTER=expected.png",
                "EXPECTED_RASTER_SHA256=" + EvidenceFiles.sha256(expected)),
                StandardCharsets.UTF_8);

        Path pdfiumArguments = output.resolve("pdfium-arguments.txt");
        Path pdfium = writeExecutable(fixtures.resolve("pdfium"), Arrays.asList(
                "#!/bin/sh",
                "printf '%s\\n' '---' \"$@\" >> " + shellQuote(pdfiumArguments),
                "if [ \"${1-}\" = \"--version\" ]; then",
                "  echo 'pdfium version " + pdfiumVersion + "'",
                "  exit 0",
                "fi",
                "if [ \"${1-}\" = \"render\" ]; then",
                "  cp " + shellQuote(rendererSource) + " \"${3}\"",
                "  echo \"Rendered page 1 into ${3}\"",
                "  exit 0",
                "fi",
                "exit 99"));

        Path imageMagickArguments = output.resolve("imagemagick-arguments.txt");
        Path comparisonCount = fixtures.resolve("comparison-count");
        Path imageMagick = writeExecutable(
                fixtures.resolve("imagemagick"),
                Arrays.asList(
                        "#!/bin/sh",
                        "printf '%s\\n' '---' \"$@\" >> "
                                + shellQuote(imageMagickArguments),
                        "if [ \"${1-}\" = \"--version\" ]; then",
                        "  echo 'Version: ImageMagick " + imageMagickVersion
                                + " Q16-HDRI x86_64 fixture  '",
                        "  exit 0",
                        "fi",
                        "if [ \"${1-}\" = \"compare\" ]; then",
                        "  comparison_count=0",
                        "  if [ -f " + shellQuote(comparisonCount) + " ]; then",
                        "    IFS= read -r comparison_count < "
                                + shellQuote(comparisonCount) + " || true",
                        "  fi",
                        "  comparison_count=$((comparison_count + 1))",
                        "  printf '%s\\n' \"${comparison_count}\" > "
                                + shellQuote(comparisonCount),
                        "  cp \"${12}\" \"${14}\"",
                        "  if [ \"${comparison_count}\" -eq 1 ]; then",
                        "    metric=" + primaryMetric,
                        "  else",
                        "    metric=" + rendererMetric,
                        "  fi",
                        "  printf '%s (0)' \"${metric}\" >&2",
                        "  if [ \"${metric}\" -eq 0 ]; then exit 0; fi",
                        "  exit 1",
                        "fi",
                        "exit 99"));
        return new VisualFixtures(pdfium, imageMagick, profile);
    }

    private static void writeRaster(
            Path path,
            int width,
            int height,
            Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        assertTrue("PNG writer unavailable", ImageIO.write(image, "png", path.toFile()));
    }

    private static Path writeExecutable(Path path, Iterable<String> lines)
            throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
        return path;
    }

    private static String shellQuote(Path path) {
        return "'" + path.toString().replace("'", "'\"'\"'") + "'";
    }

    private static CommandResult runProcess(
            Iterable<String> command,
            Map<String, String> environment)
            throws IOException, InterruptedException {
        java.util.ArrayList<String> arguments = new java.util.ArrayList<String>();
        for (String argument : command) {
            arguments.add(argument);
        }
        ProcessBuilder builder = new ProcessBuilder(arguments).redirectErrorStream(true);
        builder.environment().putAll(environment);
        Process process = builder.start();
        String commandOutput;
        try (InputStream input = process.getInputStream()) {
            commandOutput = read(input);
        }
        return new CommandResult(process.waitFor(), commandOutput);
    }

    private static void assertMetadata(String record, String label, String expected) {
        assertEquals(expected, metadata(record, label));
    }

    private static String metadata(String record, String label) {
        String prefix = label + ": `";
        String value = null;
        for (String line : record.split("\\r?\\n")) {
            if (line.startsWith(prefix) && line.endsWith("`")) {
                assertTrue("Duplicate metadata label " + label, value == null);
                value = line.substring(prefix.length(), line.length() - 1);
            }
        }
        assertTrue("Missing metadata label " + label, value != null);
        return value;
    }

    private static String readT10Syntax(Path output) throws IOException {
        return read(output.resolve(
                "T10-page-manipulation-merge-split-syntax.md"));
    }

    private static String readT11Syntax(Path output) throws IOException {
        return read(output.resolve(
                "T11-metadata-outlines-destinations-attachments-syntax.md"));
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }

    private static final class CommandResult {
        private final int exitCode;
        private final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static final class VisualFixtures {
        private final Path pdfium;
        private final Path imageMagick;
        private final Path profile;

        VisualFixtures(Path pdfium, Path imageMagick, Path profile) {
            this.pdfium = pdfium;
            this.imageMagick = imageMagick;
            this.profile = profile;
        }
    }
}

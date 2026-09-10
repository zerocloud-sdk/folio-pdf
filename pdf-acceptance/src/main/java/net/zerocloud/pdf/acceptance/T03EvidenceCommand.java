package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.layout.Document;

/** Repository-only four-chain recorder for the T03 transaction slice. */
public final class T03EvidenceCommand {
    static final String PROFILE = "T03-document-workflow-transaction";
    private static final String PROFILE_RECORD = "capabilities/evidence/T03-document-workflow-transaction.md";
    private static final String GOLDEN_SHA256 = "c7bbf03603aee1dba4ef80c9eee9abb93b7f3adfb94b84e4abf0203d78f89011";
    private static final Set<String> REQUIRED_RULES = new TreeSet<String>(Arrays.asList(
            "catalog-pages",
            "catalog-pages-indirect",
            "catalog-type",
            "catalog-type-required",
            "page-mediabox-numbers",
            "page-mediabox-rectangle",
            "page-mediabox-required",
            "page-parent-indirect",
            "page-parent-link",
            "page-parent-required",
            "page-resources-required",
            "page-resources-type",
            "page-type",
            "page-type-value",
            "pages-count-integer",
            "pages-count-nonnegative",
            "pages-count-required",
            "pages-count-value",
            "pages-kid-reference",
            "pages-kids",
            "pages-type",
            "pages-type-value"));
    private static final String[] CHAINS = {"syntax", "standards", "semantic", "visual"};

    private T03EvidenceCommand() {
    }

    /**
     * Records fresh evidence; throws after retaining observations if any chain is not PASS.
     * @param arguments repository root, fresh output directory, Native execution profile, release
     * @throws Exception if observation fails or any required chain does not pass
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException("Usage: T03EvidenceCommand <repository> <fresh-output> <execution-profile> <release>");
        }
        Path root = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path output = Files.createDirectory(Paths.get(arguments[1]).toAbsolutePath().normalize());
        WorkflowExecutionProfile execution = WorkflowExecutionProfile.valueOf(arguments[2]);
        Path visualPath = root.resolve("capabilities/profiles/T03-document-blank-visual.properties");
        VisualProfile visual = VisualProfile.load(visualPath);
        requireOriginalVisual(visual);
        Path nativeOutput = Files.createDirectory(output.resolve("native"));
        Path facadeOutput = Files.createDirectory(output.resolve("facade"));
        createNative(nativeOutput.resolve("blank.pdf"), execution, 1);
        createFacade(facadeOutput.resolve("blank.pdf"));
        Properties nativeResults = record(root, nativeOutput, execution, visual, arguments[3]);
        // The lifecycle Facade has no execution-profile mapping. Its established contract is IN_PROCESS.
        Properties facadeResults = record(root, facadeOutput, WorkflowExecutionProfile.IN_PROCESS, visual, arguments[3]);
        Properties controls = negativeControls(root, output, nativeOutput.resolve("blank.pdf"), execution, visualPath, arguments[3],
                "pass".equals(nativeResults.getProperty("standards")) && "pass".equals(facadeResults.getProperty("standards")));
        Properties results = new Properties();
        for (String chain : CHAINS) {
            String value = combine(nativeResults.getProperty(chain), facadeResults.getProperty(chain));
            if (!"fail".equals(controls.getProperty(chain))) {
                value = "indeterminate";
            }
            results.setProperty(chain, value);
        }
        save(output.resolve("result.properties"), results);
        if (!results.values().stream().allMatch("pass"::equals)) {
            throw new IOException("T03 did not PASS: " + results + "; retained observations: " + output);
        }
    }

    private static Properties record(Path root, Path output, WorkflowExecutionProfile execution,
            VisualProfile visual, String release) throws Exception {
        Path pdf = output.resolve("blank.pdf");
        String exactHash = EvidenceFiles.sha256(pdf);
        Properties results = new Properties();
        QpdfSyntaxRecorder.Profile syntaxProfile = QpdfSyntaxRecorder.Profile.blankDocument("T70",
                "document.blank.create-publish-reopen", PROFILE, PROFILE_RECORD, "blank.pdf", "syntax.md", "syntax.txt");
        results.setProperty("syntax", QpdfSyntaxRecorder.record(output, output,
                QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")),
                EvidenceFiles.idNeutralPdfSha256(pdf), release, syntaxProfile).recordValue());
        StringBuilder standards = new StringBuilder();
        String standardResult = "pass";
        Set<String> covered = new TreeSet<String>();
        for (String checker : Arrays.asList("pdfcpu", "arlington")) {
            Path directory = output.resolve(checker);
            StandardsEvidenceCommand.main(new String[] {directory.toString(),
                root.resolve("scripts/" + checker + "-pin.properties").toString(),
                root.resolve("capabilities/profiles/T03-standards/" + checker + ".properties").toString(), pdf.toString()});
            PinProperties observation = PinProperties.load(directory.resolve("standards.properties"), "standards observation");
            standardResult = combine(standardResult, observation.required("result"));
            if ("pass".equals(observation.required("result"))) {
                covered.addAll(Arrays.asList(observation.required("covered-rules").split(",")));
            }
            if (!PROFILE.equals(observation.required("profile")) || !exactHash.equals(observation.required("input-sha256"))) {
                standardResult = "indeterminate";
            }
            standards.append(checker).append("\n").append(new String(Files.readAllBytes(
                    directory.resolve("standards.properties")), java.nio.charset.StandardCharsets.UTF_8));
            standards.append(new String(Files.readAllBytes(directory.resolve("findings.txt")), java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!covered.equals(REQUIRED_RULES)) {
            standards.append("Required T03 rule union is incomplete: ").append(covered).append('\n');
            standardResult = "fail".equals(standardResult) ? "fail" : "indeterminate";
        }
        results.setProperty("standards", standardResult);
        EvidenceFiles.write(output.resolve("standards.txt"), standards.toString());
        EvidenceResult semantic = T03BlankSemantics.inspect(pdf, execution);
        results.setProperty("semantic", semantic.recordValue());
        EvidenceFiles.write(output.resolve("semantic.txt"), "Public Native reopen; execution=" + execution
                + "\nresult=" + semantic.recordValue()
                + "\nAssertions: exactly one Page; Catalog keys Type/Pages; Pages keys Type/Kids/Count;"
                + " Page keys Type/Parent/MediaBox/Resources; MediaBox [0 0 612 792]; empty Resources.\n");
        VisualEvidence raster = VisualEvidenceRecorder.record(pdf, EvidenceFiles.idNeutralPdfSha256(pdf), output,
                PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties")), visual, release);
        results.setProperty("visual", raster.result().recordValue());
        EvidenceFiles.write(output.resolve("visual.txt"), raster.rawFindings());
        EvidenceFiles.write(output.resolve("visual.md"), raster.record());
        if (!exactHash.equals(EvidenceFiles.sha256(pdf))) {
            throw new IOException("Product changed during independent observations");
        }
        for (String chain : CHAINS) {
            Path findings = output.resolve(chain + ".txt");
            String original = new String(Files.readAllBytes(findings), java.nio.charset.StandardCharsets.UTF_8);
            EvidenceFiles.write(findings, "Input exact SHA-256: " + exactHash + "\n" + original);
        }
        results.setProperty("input-sha256", exactHash);
        save(output.resolve("result.properties"), results);
        return results;
    }

    private static Properties negativeControls(Path root, Path output, Path valid,
            WorkflowExecutionProfile execution, Path visualPath, String release, boolean standardsQualified) throws Exception {
        Path directory = Files.createDirectory(output.resolve("negative"));
        Properties result = new Properties();
        Path invalid = directory.resolve("invalid.pdf");
        EvidenceFiles.write(invalid, "This is intentionally not a PDF.\n");
        String syntaxFinding;
        try {
            ProcessResult syntax = ExternalProcess.run(QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")).executable(),
                    directory, "--check", invalid.toString());
            syntaxFinding = "exit=" + syntax.exitCode + "\n" + syntax.combinedOutput();
            result.setProperty("syntax", syntax.exitCode == 2 ? "fail" : "indeterminate");
        } catch (IOException unavailable) {
            syntaxFinding = "INDETERMINATE: syntax control execution unavailable: " + unavailable.getMessage() + "\n";
            if (unavailable instanceof ExternalProcess.LimitExceededException) {
                syntaxFinding += ((ExternalProcess.LimitExceededException) unavailable).retainedOutput();
            }
            result.setProperty("syntax", "indeterminate");
        }
        EvidenceFiles.write(directory.resolve("syntax.txt"), "input-sha256=" + EvidenceFiles.sha256(invalid)
                + "\n" + syntaxFinding);
        // Both standards adapters execute every hashed rule-specific negative while recording each product.
        result.setProperty("standards", standardsQualified ? "fail" : "indeterminate");
        EvidenceFiles.write(directory.resolve("standards.txt"),
                "Rule-specific negative observations are retained separately under native/{pdfcpu,arlington} and facade/{pdfcpu,arlington}.\n");
        Path twoPages = directory.resolve("two-pages.pdf");
        createNative(twoPages, execution, 2);
        result.setProperty("semantic", T03BlankSemantics.inspect(twoPages, execution).recordValue());
        EvidenceFiles.write(directory.resolve("semantic.txt"), "Known two-page control; input-sha256=" + EvidenceFiles.sha256(twoPages)
                + "\nobserved=" + result.getProperty("semantic") + "\n");
        VisualProfile positive = VisualProfile.load(visualPath);
        BufferedImage expected = ImageIO.read(positive.expectedRaster().toFile());
        expected.setRGB(0, 0, 0xff000000);
        Path changed = directory.resolve("one-pixel-control.png");
        ImageIO.write(expected, "png", changed.toFile());
        Properties negativeProfile = load(visualPath);
        negativeProfile.setProperty("EXPECTED_RASTER", changed.getFileName().toString());
        negativeProfile.setProperty("EXPECTED_RASTER_SHA256", EvidenceFiles.sha256(changed));
        Path config = directory.resolve("one-pixel-control.properties");
        save(config, negativeProfile);
        Path negativeProduct = Files.copy(valid, directory.resolve("blank.pdf"));
        VisualEvidence mismatch = VisualEvidenceRecorder.record(negativeProduct, EvidenceFiles.idNeutralPdfSha256(negativeProduct), directory,
                PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties")), VisualProfile.load(config), release);
        result.setProperty("visual", mismatch.result().recordValue());
        EvidenceFiles.write(directory.resolve("visual.txt"), "Negative control only; canonical expected raster is unchanged.\n" + mismatch.rawFindings());
        save(directory.resolve("result.properties"), result);
        return result;
    }

    private static void createNative(Path pdf, WorkflowExecutionProfile execution, int count) throws Exception {
        WorkflowOutcome<Integer> outcome = new DocumentWorkflow().execute(WorkflowRequest.builder()
                .target("output", PublicationTarget.path(pdf)).executionProfile(execution).saveMode(SaveMode.REWRITE).build(), session -> {
                    for (int page = 0; page < count; page++) {
                        session.execute(AddBlankPage.INSTANCE);
                    }
                    return session.query(PageCount.INSTANCE);
                });
        if (outcome.getResult() != count || outcome.getExecutionProfile() != execution
                || outcome.getPublicationReceipts().size() != 1
                || outcome.getPublicationReceipts().get(0).getStatus() != PublicationStatus.COMMITTED) {
            throw new IOException("Unexpected Native publication outcome");
        }
    }

    private static void createFacade(Path pdf) throws Exception {
        PdfDocument document = new PdfDocument(new PdfWriter(pdf.toString()));
        document.addNewPage();
        new Document(document).close();
        document.close();
        try (PdfReader reader = new PdfReader(pdf.toString()); PdfDocument reopened = new PdfDocument(reader)) {
            if (reopened.getNumberOfPages() != 1) {
                throw new IOException("Unexpected Stable Facade page count");
            }
        }
    }

    private static void requireOriginalVisual(VisualProfile visual) throws IOException {
        if (!PROFILE.equals(visual.profileId()) || visual.dpi() != 144 || visual.rasterWidth() != 1224
                || visual.rasterHeight() != 1584 || visual.comparisonFuzzPercent() != 0
                || visual.comparisonThreshold() != 0 || visual.rendererAgreementThreshold() != 0
                || !"AE".equals(visual.comparisonMetric()) || !GOLDEN_SHA256.equals(visual.expectedRasterSha256())) {
            throw new IOException("T03 requires its original 144 DPI, opaque sRGB, AE 0, zero-fuzz profile");
        }
    }

    private static String combine(String first, String second) {
        return "fail".equals(first) || "fail".equals(second) ? "fail"
                : "pass".equals(first) && "pass".equals(second) ? "pass" : "indeterminate";
    }

    private static Properties load(Path path) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        return values;
    }

    private static void save(Path path, Properties values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            values.store(output, "Actual T03 observation");
        }
    }
}

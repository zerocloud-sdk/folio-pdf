package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.EmbeddedFile;
import net.zerocloud.pdf.PageDestination;
import net.zerocloud.pdf.PdfOutputPolicy;
import net.zerocloud.pdf.PdfString;
import net.zerocloud.pdf.PdfVersion;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.EmbedFile;
import net.zerocloud.pdf.command.SetNamedDestinations;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.command.UpdateDocumentInfo;

/** Independent syntax, standards, semantic, and visual observations and controls for T11. */
final class T11IndependentEvidence {
    private static final String REQUIRED_RULES_SHA256 = "becd5883a41e2a461cd217e1457fa2570086111f41a57806cf13239327c7465d";
    private static final String PDFCPU_PROFILE_SHA256 = "8a654bf0b52a8bebcc80723d262ad8586da57a3013411c1578e8b705f7439e03";
    private static final String ARLINGTON_CORE_PROFILE_SHA256 = "eed4dc15f483ca00c1a7f23c5e1cfacf85bd9a92ec876acfeabc2b44887fc606";
    private static final String ARLINGTON_METADATA_PROFILE_SHA256 = "32b91782327ef4705a32a446d1e1212cbad637b133cd6b773bc07c174d7f6f98";
    private static final String COLOR_POLICY =
            "sRGB, opaque 8-bit RGB PNG after compositing over opaque white";
    private static final String FONT_POLICY =
            "not applicable; the artifact has no text or font resources and uses no system fonts";
    private static final String ANTIALIASING_POLICY =
            "pinned PDFium default smoothing; vector edges are axis-aligned";

    private T11IndependentEvidence() {
    }

    static Properties record(
            Path root,
            Path input,
            String product,
            Path output,
            net.zerocloud.pdf.WorkflowExecutionProfile execution,
            String release) throws Exception {
        T11Corpus corpus = new T11Corpus(root);
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("metadata.pdf");
        if (!input.toAbsolutePath().normalize().equals(
                observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Properties result = new Properties();
        result.setProperty("profile", T11Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);

        QpdfSyntaxRecorder.Profile syntaxProfile =
                QpdfSyntaxRecorder.Profile.exactDocument(
                        "T73",
                        "document.metadata.outlines-destinations-attachments",
                        T11Corpus.PROFILE,
                        "capabilities/evidence/T11-metadata-outlines-destinations-attachments.md",
                        "metadata.pdf",
                        "syntax.md",
                        "syntax.txt");
        result.setProperty("syntax", QpdfSyntaxRecorder.record(
                output,
                output,
                QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")),
                exactHash,
                release,
                syntaxProfile).recordValue());

        Set<String> requiredRules = requiredRules(root);
        Set<String> covered = new TreeSet<String>();
        String standards = "pass";
        StringBuilder standardsFindings = new StringBuilder(
                "Input exact SHA-256: " + exactHash + "\n");
        for (String checker : Arrays.asList("pdfcpu", "arlington-core", "arlington-metadata")) {
            Path profile = root.resolve(
                    "capabilities/profiles/T11-standards/"
                            + checker + ".properties");
            String expectedProfileHash = "pdfcpu".equals(checker)
                    ? PDFCPU_PROFILE_SHA256 : "arlington-core".equals(checker)
                    ? ARLINGTON_CORE_PROFILE_SHA256 : ARLINGTON_METADATA_PROFILE_SHA256;
            if (!expectedProfileHash.equals(EvidenceFiles.sha256(profile))) {
                throw new IOException("T11 " + checker
                        + " standards profile identity mismatch");
            }
            Path checkerOutput = output.resolve(checker);
            StandardsEvidenceCommand.main(new String[] {
                checkerOutput.toString(),
                root.resolve("scripts/" + (checker.startsWith("arlington")
                        ? "t11-arlington" : checker) + "-pin.properties").toString(),
                profile.toString(),
                observed.toString()});
            PinProperties observation = PinProperties.load(
                    checkerOutput.resolve("standards.properties"),
                    "T11 " + checker + " standards");
            String checkerResult = observation.required("result");
            standards = EvidenceResult.combine(standards, checkerResult);
            if (!T11Corpus.PROFILE.equals(observation.required("profile"))
                    || !exactHash.equals(observation.required("input-sha256"))) {
                standards = "indeterminate";
            }
            if ("pass".equals(checkerResult)) {
                for (String rule : observation.required("covered-rules").split(",")) {
                    if (!covered.add(rule)) {
                        throw new IOException("Duplicate T11 standards assignment: " + rule);
                    }
                }
            }
            standardsFindings.append(checker).append('\n')
                    .append(new String(Files.readAllBytes(
                            checkerOutput.resolve("standards.properties")),
                            StandardCharsets.UTF_8))
                    .append(new String(Files.readAllBytes(
                            checkerOutput.resolve("findings.txt")),
                            StandardCharsets.UTF_8));
        }
        if (!covered.equals(requiredRules)) {
            standardsFindings.append("Required T11 rule union is incomplete: ")
                    .append(covered).append('\n');
            standards = "fail".equals(standards)
                    ? standards : "indeterminate";
        }
        result.setProperty("standards", standards);
        result.setProperty("standards-rule-count",
                Integer.toString(requiredRules.size()));
        EvidenceFiles.write(output.resolve("standards.txt"),
                standardsFindings.toString());

        Properties semantic = T11MetadataSemantics.inspect(
                corpus, observed, product, execution);
        result.setProperty("semantic", semantic.getProperty("semantic"));
        result.setProperty("execution-profile",
                semantic.getProperty("execution-profile"));
        result.setProperty("semantic-scope",
                semantic.getProperty("semantic-scope"));
        EvidenceFiles.write(output.resolve("semantic.txt"),
                "Input exact SHA-256: " + exactHash + "\n"
                        + semantic.getProperty("finding") + "\n");

        Properties visual = recordVisual(
                root, observed, product, output, release);
        result.setProperty("visual", visual.getProperty("visual"));
        result.setProperty("page-count", visual.getProperty("page-count"));
        for (String key : visual.stringPropertyNames()) {
            if (key.startsWith("page.")) {
                result.setProperty(key, visual.getProperty(key));
            }
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input))
                || !exactHash.equals(EvidenceFiles.sha256(observed))) {
            for (String chain : Arrays.asList(
                    "syntax", "standards", "semantic", "visual")) {
                result.setProperty(chain, "indeterminate");
            }
        }
        return result;
    }

    private static Set<String> requiredRules(Path root) throws IOException {
        Path authority = root.resolve(
                "capabilities/profiles/T11-standards/required-rules.txt");
        if (!REQUIRED_RULES_SHA256.equals(EvidenceFiles.sha256(authority))) {
            throw new IOException("T11 required standards rule identity mismatch");
        }
        List<String> lines = Files.readAllLines(
                authority, StandardCharsets.US_ASCII);
        Set<String> rules = new TreeSet<String>();
        for (String line : lines) {
            String rule = line.trim();
            if (rule.isEmpty() || !rule.matches("[a-z0-9-]+")
                    || !rules.add(rule)) {
                throw new IOException("Invalid T11 required standards rule catalog");
            }
        }
        if (rules.size() != 170) {
            throw new IOException("T11 requires exactly 170 qualified standards rules");
        }
        return rules;
    }

    static Properties recordVisual(
            Path root,
            Path input,
            String product,
            Path output,
            String release) throws Exception {
        T11Corpus corpus = new T11Corpus(root);
        int pageCount = Integer.parseInt(
                corpus.expected("products." + product + ".pages.count"));
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("metadata.pdf");
        if (!input.toAbsolutePath().normalize().equals(
                observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        PdfiumPin pdfium = PdfiumPin.load(
                root.resolve("scripts/pdfium-pin.properties"));
        ImageMagickPin comparator = ImageMagickPin.load(
                root.resolve("scripts/imagemagick-pin.properties"));
        Properties result = new Properties();
        result.setProperty("profile", T11Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("page-count", Integer.toString(pageCount));
        String combined = "pass";
        StringBuilder aggregate = new StringBuilder(
                "Input exact SHA-256: " + exactHash + "\n");
        for (int page = 1; page <= pageCount; page++) {
            String pageName = corpus.expected(
                    "products." + product + ".pages." + (page - 1));
            VisualProfile profile = VisualProfile.load(root.resolve(
                    "capabilities/profiles/T11-metadata/visual/"
                            + product + "-page-" + page + ".properties"));
            requireFrozenProfile(root, corpus, product, page, pageCount,
                    pageName, profile);
            VisualEvidenceChain chain = VisualEvidenceChain.t11(root, page);
            VisualEvidence evidence = VisualEvidenceRecorder.record(
                    chain,
                    observed,
                    exactHash,
                    output,
                    pdfium,
                    comparator,
                    profile,
                    release);
            String pageResult = evidence.result().recordValue();
            result.setProperty("page." + page + ".visual", pageResult);
            combined = EvidenceResult.combine(combined, pageResult);
            aggregate.append("page ").append(page).append('=')
                    .append(pageResult).append('\n');
            EvidenceFiles.write(
                    output.resolve(chain.findingsName()),
                    "Input exact SHA-256: " + exactHash + "\n"
                            + evidence.rawFindings());
            EvidenceFiles.write(
                    output.resolve(chain.recordName()), evidence.record());
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input))
                || !exactHash.equals(EvidenceFiles.sha256(observed))) {
            combined = "indeterminate";
        }
        result.setProperty("visual", combined);
        EvidenceFiles.write(output.resolve("visual.txt"),
                aggregate.append("result=").append(combined)
                        .append('\n').toString());
        return result;
    }

    static Properties negativeControls(
            Path root,
            Path output,
            Path semanticProduct,
            Path visualProduct,
            WorkflowExecutionProfile execution,
            boolean standardsQualified,
            String release) throws Exception {
        Properties result = new Properties();

        Path invalid = output.resolve("invalid.pdf");
        EvidenceFiles.write(invalid, "Intentionally not a PDF.\n");
        QpdfSyntaxRecorder.Profile syntaxProfile =
                QpdfSyntaxRecorder.Profile.exactDocument(
                        "T73 negative control",
                        T11MetadataProducts.CAPABILITY,
                        T11Corpus.PROFILE,
                        "capabilities/evidence/T11-metadata-outlines-destinations-attachments.md",
                        "invalid.pdf",
                        "syntax.md",
                        "syntax.txt");
        result.setProperty("syntax", QpdfSyntaxRecorder.record(
                output,
                output,
                QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")),
                EvidenceFiles.sha256(invalid),
                release,
                syntaxProfile).recordValue());

        Path semanticOutput = Files.createDirectory(output.resolve("semantic"));
        boolean allSemanticControlsDetected = true;
        StringBuilder semanticFindings = new StringBuilder();
        for (String change : new String[] {
                "target", "operand", "packet", "payload", "retained-info"}) {
            Path wrong = semanticOutput.resolve(change + ".pdf");
            createSemanticControl(semanticProduct, wrong, change, execution);
            Properties observed = T11MetadataSemantics.inspect(
                    new T11Corpus(root), wrong, "edited", execution);
            observed.setProperty("input-sha256", EvidenceFiles.sha256(wrong));
            observed.setProperty("control", change);
            T11MetadataProducts.save(
                    semanticOutput.resolve(change + ".properties"), observed);
            allSemanticControlsDetected &= "fail".equals(
                    observed.getProperty("semantic"));
            semanticFindings.append(change).append('=')
                    .append(observed.getProperty("semantic")).append(' ')
                    .append(observed.getProperty("finding")).append('\n');
        }
        result.setProperty("semantic",
                allSemanticControlsDetected ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("semantic.txt"),
                semanticFindings.toString());

        result.setProperty("standards",
                standardsQualified ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("standards.txt"),
                "All 170 rule-specific illegal PDFs and matching real checker diagnostics are retained under every product's three checker directories.\n"
                        + "Control result=" + result.getProperty("standards") + ".\n");

        Path visual = Files.createDirectory(output.resolve("visual"));
        Path visualPdf = Files.copy(
                visualProduct, visual.resolve("metadata.pdf"));
        Path canonicalProfile = root.resolve(
                "capabilities/profiles/T11-metadata/visual/edited-page-1.properties");
        Properties changed = new Properties();
        try (InputStream input = Files.newInputStream(canonicalProfile)) {
            changed.load(input);
        }
        VisualProfile original = VisualProfile.load(canonicalProfile);
        BufferedImage raster = ImageIO.read(original.expectedRaster().toFile());
        if (raster == null) {
            throw new IOException(
                    "Unable to load T11 visual negative control source");
        }
        int pixel = raster.getRGB(0, 0);
        raster.setRGB(0, 0,
                pixel == 0xff000000 ? 0xffffffff : 0xff000000);
        Path changedRaster = visual.resolve("one-pixel-control.png");
        if (!ImageIO.write(raster, "png", changedRaster.toFile())) {
            throw new IOException("Unable to retain T11 visual negative control");
        }
        changed.setProperty("EXPECTED_RASTER",
                changedRaster.getFileName().toString());
        changed.setProperty("EXPECTED_RASTER_SHA256",
                EvidenceFiles.sha256(changedRaster));
        Path changedProfile = visual.resolve("one-pixel-control.properties");
        try (OutputStream stream = Files.newOutputStream(changedProfile)) {
            changed.store(stream,
                    "T11 one-pixel negative; canonical profile is unchanged");
        }
        VisualEvidenceChain chain = VisualEvidenceChain.t11(root, 1);
        VisualEvidence evidence = VisualEvidenceRecorder.record(
                chain,
                visualPdf,
                EvidenceFiles.sha256(visualPdf),
                visual,
                PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                ImageMagickPin.load(root.resolve(
                        "scripts/imagemagick-pin.properties")),
                VisualProfile.load(changedProfile),
                release);
        result.setProperty("visual", evidence.result().recordValue());
        EvidenceFiles.write(visual.resolve(chain.findingsName()),
                evidence.rawFindings());
        EvidenceFiles.write(visual.resolve(chain.recordName()),
                evidence.record());
        EvidenceFiles.write(output.resolve("visual.txt"),
                "A one-pixel change to the frozen expected raster produced result="
                        + result.getProperty("visual") + ".\n");
        return result;
    }

    private static void createSemanticControl(
            Path source,
            Path target,
            String change,
            WorkflowExecutionProfile execution) throws Exception {
        new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.path(source))
                        .primarySource("input")
                        .target("out", PublicationTarget.path(target))
                        .executionProfile(execution)
                        .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_2_0))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    if ("target".equals(change) || "operand".equals(change)) {
                        session.execute(SetNamedDestinations.version1()
                                .set("xyz", PageDestination.xyz(
                                        "target".equals(change) ? 2 : 1,
                                        BigDecimal.valueOf(
                                                "operand".equals(change)
                                                        ? 11 : 10),
                                        null,
                                        BigDecimal.valueOf(2)))
                                .build());
                    } else if ("packet".equals(change)) {
                        session.execute(SetXmpMetadata.version1(
                                new String(
                                        T11MetadataProducts.editedXmp(),
                                        StandardCharsets.UTF_8)
                                        .replace("edited", "tampered")
                                        .getBytes(StandardCharsets.UTF_8)));
                    } else if ("payload".equals(change)) {
                        session.execute(EmbedFile.version1(
                                EmbeddedFile.version1(
                                        "payload.txt",
                                        new byte[] {9},
                                        "application/octet-stream",
                                        "Edited payload",
                                        EmbeddedFile.Relationship.SOURCE)));
                    } else {
                        session.execute(UpdateDocumentInfo.version1()
                                .set("T73Keep", PdfString.of(
                                        "tampered".getBytes(
                                                StandardCharsets.US_ASCII)))
                                .build());
                    }
                    return null;
                });
    }

    private static void requireFrozenProfile(
            Path root,
            T11Corpus corpus,
            String product,
            int page,
            int pageCount,
            String pageName,
            VisualProfile profile) throws IOException {
        String prefix = "pages." + pageName + ".";
        StringBuilder crop = new StringBuilder();
        int coordinates = Integer.parseInt(
                corpus.expected(prefix + "crop-box.count"));
        for (int index = 0; index < coordinates; index++) {
            if (index > 0) {
                crop.append(' ');
            }
            crop.append(corpus.expected(prefix + "crop-box." + index));
        }
        Path expectedRaster = root.resolve("capabilities/profiles/T11-metadata")
                .resolve(corpus.expected(prefix + "raster"))
                .toAbsolutePath().normalize();
        if (!(T11Corpus.PROFILE + "-" + product + "-page-" + page)
                        .equals(profile.profileId())
                || profile.pageNumber() != page
                || profile.pageCount() != pageCount
                || profile.dpi() != 144
                || !("effective CropBox [" + crop + "] points")
                        .equals(profile.pageBox())
                || !COLOR_POLICY.equals(profile.colorPolicy())
                || !FONT_POLICY.equals(profile.fontPolicy())
                || !ANTIALIASING_POLICY.equals(
                        profile.antialiasingPolicy())
                || !"opaque white (#ffffff)".equals(profile.background())
                || profile.rasterWidth() != Integer.parseInt(
                        corpus.expected(prefix + "raster-width"))
                || profile.rasterHeight() != Integer.parseInt(
                        corpus.expected(prefix + "raster-height"))
                || !"AE".equals(profile.comparisonMetric())
                || profile.comparisonFuzzPercent() != 0
                || profile.comparisonThreshold() != 0
                || profile.rendererAgreementThreshold() != 0
                || !expectedRaster.equals(
                        profile.expectedRaster().toAbsolutePath().normalize())
                || !corpus.expected(prefix + "raster-sha256")
                        .equals(profile.expectedRasterSha256())) {
            throw new IOException(
                    "T11 visual profile differs from the frozen corpus: "
                            + product + " page " + page);
        }
    }

}

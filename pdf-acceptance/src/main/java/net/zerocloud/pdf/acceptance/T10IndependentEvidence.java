package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/** Independent visual observations over immutable T10 products. */
final class T10IndependentEvidence {
    private static final String REQUIRED_RULES_SHA256 =
            "ef9fca293259a1d19aa49c179f4f7932960abe7410b7592093ba53da9bc022dd";
    private static final String PDFCPU_PROFILE_SHA256 =
            "1e23be30ee10ec043e9178ca331473561c8c04f3110874c25864a387b153d3d0";
    private static final String ARLINGTON_PROFILE_SHA256 =
            "f07cbe2b7d8d0d20316288b2c4ee832b7019348ecff3a30efc1d0c2a7ac6cb5a";
    private static final String COLOR_POLICY =
            "sRGB, opaque 8-bit RGB PNG after compositing over opaque white";
    private static final String FONT_POLICY =
            "not applicable; the artifact has no text or font resources and uses no system fonts";
    private static final String ANTIALIASING_POLICY =
            "pinned PDFium default smoothing; vector edges are axis-aligned";

    private T10IndependentEvidence() {
    }

    static Properties record(
            Path root,
            Path input,
            String product,
            Path output,
            net.zerocloud.pdf.WorkflowExecutionProfile execution,
            String release) throws Exception {
        T10Corpus corpus = new T10Corpus(root);
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("pages.pdf");
        if (!input.toAbsolutePath().normalize().equals(
                observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Properties result = new Properties();
        result.setProperty("profile", T10Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);

        QpdfSyntaxRecorder.Profile syntaxProfile =
                QpdfSyntaxRecorder.Profile.exactDocument(
                        "T72",
                        "document.page.manipulate-merge-split",
                        T10Corpus.PROFILE,
                        "capabilities/evidence/T10-page-manipulation-merge-split.md",
                        "pages.pdf",
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
        for (String checker : Arrays.asList("pdfcpu", "arlington")) {
            Path profile = root.resolve(
                    "capabilities/profiles/T10-standards/"
                            + checker + ".properties");
            String expectedProfileHash = "pdfcpu".equals(checker)
                    ? PDFCPU_PROFILE_SHA256 : ARLINGTON_PROFILE_SHA256;
            if (!expectedProfileHash.equals(EvidenceFiles.sha256(profile))) {
                throw new IOException("T10 " + checker
                        + " standards profile identity mismatch");
            }
            Path checkerOutput = output.resolve(checker);
            StandardsEvidenceCommand.main(new String[] {
                checkerOutput.toString(),
                root.resolve("scripts/" + ("arlington".equals(checker)
                        ? "t10-arlington" : checker) + "-pin.properties").toString(),
                profile.toString(),
                observed.toString()});
            PinProperties observation = PinProperties.load(
                    checkerOutput.resolve("standards.properties"),
                    "T10 " + checker + " standards");
            String checkerResult = observation.required("result");
            standards = EvidenceResult.combine(standards, checkerResult);
            if (!T10Corpus.PROFILE.equals(observation.required("profile"))
                    || !exactHash.equals(observation.required("input-sha256"))) {
                standards = "indeterminate";
            }
            if ("pass".equals(checkerResult)) {
                covered.addAll(Arrays.asList(
                        observation.required("covered-rules").split(",")));
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
            standardsFindings.append("Required T10 rule union is incomplete: ")
                    .append(covered).append('\n');
            standards = "fail".equals(standards)
                    ? standards : "indeterminate";
        }
        result.setProperty("standards", standards);
        result.setProperty("standards-rule-count",
                Integer.toString(requiredRules.size()));
        EvidenceFiles.write(output.resolve("standards.txt"),
                standardsFindings.toString());

        Properties semantic = T10PageSemantics.inspect(
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

    static Properties recordVisual(
            Path root,
            Path input,
            String product,
            Path output,
            String release) throws Exception {
        T10Corpus corpus = new T10Corpus(root);
        int pageCount = Integer.parseInt(
                corpus.expected("products." + product + ".pages.count"));
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("pages.pdf");
        if (!input.toAbsolutePath().normalize().equals(
                observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        PdfiumPin pdfium = PdfiumPin.load(
                root.resolve("scripts/pdfium-pin.properties"));
        ImageMagickPin comparator = ImageMagickPin.load(
                root.resolve("scripts/imagemagick-pin.properties"));
        Properties result = new Properties();
        result.setProperty("profile", T10Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("page-count", Integer.toString(pageCount));
        String combined = "pass";
        StringBuilder aggregate = new StringBuilder(
                "Input exact SHA-256: " + exactHash + "\n");
        for (int page = 1; page <= pageCount; page++) {
            String pageName = corpus.expected(
                    "products." + product + ".pages." + (page - 1));
            VisualProfile profile = VisualProfile.load(root.resolve(
                    "capabilities/profiles/T10-pages/visual/"
                            + product + "-page-" + page + ".properties"));
            requireFrozenProfile(root, corpus, product, page, pageCount,
                    pageName, profile);
            VisualEvidenceChain chain = VisualEvidenceChain.t10(root, page);
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
            Path wrongOrder,
            Path visualProduct,
            net.zerocloud.pdf.WorkflowExecutionProfile execution,
            boolean standardsQualified,
            String release) throws Exception {
        Properties result = new Properties();

        Path invalid = output.resolve("invalid.pdf");
        EvidenceFiles.write(invalid, "Intentionally not a PDF.\n");
        QpdfSyntaxRecorder.Profile syntaxProfile =
                QpdfSyntaxRecorder.Profile.exactDocument(
                        "T72 negative control",
                        "document.page.manipulate-merge-split",
                        T10Corpus.PROFILE,
                        "capabilities/evidence/T10-page-manipulation-merge-split.md",
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

        Path retainedWrongOrder = Files.copy(
                wrongOrder, output.resolve("wrong-order.pdf"));
        Properties semantic = T10PageSemantics.inspect(
                new T10Corpus(root), retainedWrongOrder, "right", execution);
        result.setProperty("semantic", semantic.getProperty("semantic"));
        EvidenceFiles.write(output.resolve("semantic.txt"),
                "Known left product evaluated as the same-length right product.\n"
                        + "Input exact SHA-256: "
                        + EvidenceFiles.sha256(retainedWrongOrder) + "\n"
                        + semantic.getProperty("finding") + "\n");

        result.setProperty("standards",
                standardsQualified ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("standards.txt"),
                "All 84 original rule-specific illegal PDFs and matching real checker diagnostics are retained under each product's pdfcpu and arlington directories.\n"
                        + "Control result=" + result.getProperty("standards") + "\n");

        Path visual = Files.createDirectory(output.resolve("visual"));
        Path visualPdf = Files.copy(visualProduct, visual.resolve("pages.pdf"));
        Path canonicalProfile = root.resolve(
                "capabilities/profiles/T10-pages/visual/edited-page-1.properties");
        Properties changed = new Properties();
        try (InputStream input = Files.newInputStream(canonicalProfile)) {
            changed.load(input);
        }
        VisualProfile original = VisualProfile.load(canonicalProfile);
        BufferedImage raster = ImageIO.read(original.expectedRaster().toFile());
        if (raster == null) {
            throw new IOException("Unable to load T10 visual negative control source");
        }
        int pixel = raster.getRGB(0, 0);
        raster.setRGB(0, 0, pixel == 0xff000000 ? 0xffffffff : 0xff000000);
        Path changedRaster = visual.resolve("one-pixel-control.png");
        if (!ImageIO.write(raster, "png", changedRaster.toFile())) {
            throw new IOException("Unable to retain T10 visual negative control");
        }
        changed.setProperty("EXPECTED_RASTER", changedRaster.getFileName().toString());
        changed.setProperty("EXPECTED_RASTER_SHA256",
                EvidenceFiles.sha256(changedRaster));
        Path changedProfile = visual.resolve("one-pixel-control.properties");
        try (OutputStream stream = Files.newOutputStream(changedProfile)) {
            changed.store(stream,
                    "T10 one-pixel negative; canonical profile is unchanged");
        }
        VisualEvidenceChain chain = VisualEvidenceChain.t10(root, 1);
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
                "A one-pixel change to the original expected raster produced result="
                        + result.getProperty("visual") + ".\n");
        return result;
    }

    private static Set<String> requiredRules(Path root) throws IOException {
        Path authority = root.resolve(
                "capabilities/profiles/T10-standards/required-rules.txt");
        if (!REQUIRED_RULES_SHA256.equals(EvidenceFiles.sha256(authority))) {
            throw new IOException("T10 required standards rule identity mismatch");
        }
        List<String> lines = Files.readAllLines(
                authority, StandardCharsets.US_ASCII);
        Set<String> rules = new TreeSet<String>();
        for (String line : lines) {
            String rule = line.trim();
            if (rule.isEmpty() || !rule.matches("[a-z0-9-]+")
                    || !rules.add(rule)) {
                throw new IOException("Invalid T10 required standards rule catalog");
            }
        }
        if (rules.size() != 84) {
            throw new IOException("T10 requires exactly 84 qualified standards rules");
        }
        return rules;
    }

    private static void requireFrozenProfile(
            Path root,
            T10Corpus corpus,
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
        Path expectedRaster = root.resolve("capabilities/profiles/T10-pages")
                .resolve(corpus.expected(prefix + "raster"))
                .toAbsolutePath().normalize();
        if (!(T10Corpus.PROFILE + "-" + product + "-page-" + page)
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
                    "T10 visual profile differs from the frozen corpus: "
                            + product + " page " + page);
        }
    }

}

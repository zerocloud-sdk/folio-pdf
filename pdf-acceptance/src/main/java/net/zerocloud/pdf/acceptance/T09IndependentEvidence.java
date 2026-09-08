package net.zerocloud.pdf.acceptance;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;

/** Runs independent T09 syntax, standards and visual observations without editing a product. */
final class T09IndependentEvidence {
    private static final String GOLDEN_SHA256 = "f74af509a0267ddf480a6d1fb1d17110b68503f53f9120d48c905e9477976f4c";
    private static final Set<String> REQUIRED_RULES = new TreeSet<String>(Arrays.asList(
            "catalog-lang-string",
            "catalog-page-mode-name",
            "catalog-page-mode-value",
            "catalog-pages",
            "catalog-pages-indirect",
            "catalog-type",
            "catalog-type-required",
            "data-lastmodified-date",
            "data-lastmodified-null",
            "data-lastmodified-required",
            "info-moddate-date",
            "info-moddate-required",
            "page-contents-array-member",
            "page-contents-type",
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
            "pages-type-value",
            "piece-entry-dictionary",
            "pieceinfo-dictionary",
            "stream-decodeparams-array-member",
            "stream-decodeparams-count",
            "stream-decodeparams-position",
            "stream-decodeparams-type",
            "stream-filter-array-name-type",
            "stream-filter-array-name-value",
            "stream-filter-name-value",
            "stream-filter-type",
            "stream-flate-predictor-type",
            "stream-flate-predictor-value",
            "trailer-info-indirect",
            "trailer-info-null",
            "trailer-info-required",
            "viewer-hide-toolbar-boolean",
            "viewer-numcopies-integer",
            "viewer-numcopies-positive",
            "viewer-preferences-dictionary"));

    private T09IndependentEvidence() {
    }

    static VisualProfile visualProfile(Path root) throws IOException {
        VisualProfile profile = VisualProfile.load(root.resolve("capabilities/profiles/T09-values-visual.properties"));
        if (!T09EvidenceCommand.PROFILE.equals(profile.profileId()) || profile.dpi() != 144
                || profile.rasterWidth() != 1224 || profile.rasterHeight() != 1584
                || profile.comparisonFuzzPercent() != 0 || profile.comparisonThreshold() != 0
                || profile.rendererAgreementThreshold() != 0 || !"AE".equals(profile.comparisonMetric())
                || !GOLDEN_SHA256.equals(profile.expectedRasterSha256())) {
            throw new IOException("T09 requires its frozen blue-rectangle, 144 DPI, opaque sRGB, AE 0 profile");
        }
        return profile;
    }

    static Properties record(Path root, Path directory, VisualProfile visual, String release) throws Exception {
        Path pdf = directory.resolve("values.pdf");
        String exactHash = EvidenceFiles.sha256(pdf);
        Properties result = new Properties();
        QpdfSyntaxRecorder.Profile syntax = QpdfSyntaxRecorder.Profile.exactDocument("T71",
                "document.value.inspect-patch", T09EvidenceCommand.PROFILE,
                "capabilities/evidence/T09-document-value-inspection-patch.md", "values.pdf", "syntax.md", "syntax.txt");
        result.setProperty("syntax", QpdfSyntaxRecorder.record(directory, directory,
                QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")), exactHash, release, syntax).recordValue());
        Set<String> covered = new TreeSet<String>();
        String standards = "pass";
        StringBuilder findings = new StringBuilder("Input exact SHA-256: " + exactHash + "\n");
        for (String checker : Arrays.asList("pdfcpu", "arlington")) {
            Path output = directory.resolve(checker);
            StandardsEvidenceCommand.main(new String[] {output.toString(),
                root.resolve("scripts/" + checker + "-pin.properties").toString(),
                root.resolve("capabilities/profiles/T09-standards/" + checker + ".properties").toString(), pdf.toString()});
            PinProperties observation = PinProperties.load(output.resolve("standards.properties"), "T09 standards");
            standards = combine(standards, observation.required("result"));
            if (!T09EvidenceCommand.PROFILE.equals(observation.required("profile"))
                    || !exactHash.equals(observation.required("input-sha256"))) {
                standards = "indeterminate";
            }
            if ("pass".equals(observation.required("result"))) {
                covered.addAll(Arrays.asList(observation.required("covered-rules").split(",")));
            }
            findings.append(checker).append('\n')
                    .append(new String(Files.readAllBytes(output.resolve("standards.properties")), StandardCharsets.UTF_8))
                    .append(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8));
        }
        if (!covered.equals(REQUIRED_RULES)) {
            findings.append("Required T09 rule union is incomplete: ").append(covered).append('\n');
            standards = "fail".equals(standards) ? standards : "indeterminate";
        }
        result.setProperty("standards", standards);
        EvidenceFiles.write(directory.resolve("standards.txt"), findings.toString());
        result.setProperty("visual", recordVisual(root, pdf, directory, visual, release));
        if (!exactHash.equals(EvidenceFiles.sha256(pdf))) {
            throw new IOException("T09 product changed during independent observations");
        }
        return result;
    }

    static String recordVisual(Path root, Path pdf, Path output, VisualProfile profile, String release) throws Exception {
        String exact = EvidenceFiles.sha256(pdf);
        VisualEvidence evidence = VisualEvidenceRecorder.record(VisualEvidenceChain.t09(root), pdf, exact, output,
                PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties")), profile, release);
        EvidenceFiles.write(output.resolve("visual.txt"), "Input exact SHA-256: " + exact + "\n" + evidence.rawFindings());
        EvidenceFiles.write(output.resolve("visual.md"), evidence.record());
        if (!exact.equals(EvidenceFiles.sha256(pdf))) {
            throw new IOException("T09 visual observation changed its input");
        }
        return evidence.result().recordValue();
    }

    static void negativeControls(Path root, Path output, Path product, VisualProfile visual,
            String release, boolean standardsQualified, Properties results) throws Exception {
        Path invalid = output.resolve("invalid.pdf");
        EvidenceFiles.write(invalid, "Intentionally not a PDF.\n");
        ProcessResult syntax = ExternalProcess.run(QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")).executable(),
                output, "--check", invalid.toString());
        EvidenceFiles.write(output.resolve("syntax.txt"), "input-sha256=" + EvidenceFiles.sha256(invalid)
                + "\nexit=" + syntax.exitCode + "\n" + syntax.combinedOutput());
        results.setProperty("syntax", syntax.exitCode == 2 ? "fail" : "indeterminate");
        results.setProperty("standards", standardsQualified ? "fail" : "indeterminate");
        EvidenceFiles.write(output.resolve("standards.txt"),
                "Actual rule-specific controls and checker diagnostics are retained for each product under {pdfcpu,arlington}.\n");
        BufferedImage expected = ImageIO.read(visual.expectedRaster().toFile());
        expected.setRGB(0, 0, 0xff000000);
        Path changed = output.resolve("one-pixel-control.png");
        if (!ImageIO.write(expected, "png", changed.toFile())) {
            throw new IOException("Unable to retain the T09 visual negative control");
        }
        Properties negative = new Properties();
        try (InputStream input = Files.newInputStream(root.resolve("capabilities/profiles/T09-values-visual.properties"))) {
            negative.load(input);
        }
        negative.setProperty("EXPECTED_RASTER", changed.getFileName().toString());
        negative.setProperty("EXPECTED_RASTER_SHA256", EvidenceFiles.sha256(changed));
        Path config = output.resolve("one-pixel-control.properties");
        try (OutputStream stream = Files.newOutputStream(config)) {
            negative.store(stream, "T09 one-pixel negative; canonical profile is unchanged");
        }
        Path copied = Files.copy(product, output.resolve("values.pdf"));
        results.setProperty("visual", recordVisual(root, copied, output, VisualProfile.load(config), release));
    }

    static String combine(String first, String second) {
        return "fail".equals(first) || "fail".equals(second) ? "fail"
                : "pass".equals(first) && "pass".equals(second) ? "pass" : "indeterminate";
    }
}

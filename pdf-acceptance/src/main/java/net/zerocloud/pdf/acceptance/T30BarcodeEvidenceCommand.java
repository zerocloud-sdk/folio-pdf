package net.zerocloud.pdf.acceptance;

import static net.zerocloud.pdf.acceptance.EvidenceFiles.metadata;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Repository-only T30 syntax, independent semantic, raster-decode and visual evidence recorder. */
public final class T30BarcodeEvidenceCommand {
    static final String CAPABILITY = "composition.barcodes.one-dimensional";
    static final String PROFILE = "T30-one-dimensional-barcodes";
    private T30BarcodeEvidenceCommand() { }

    /**
     * Receives output directory, qpdf pin, PDFium pin, ImageMagick pin,
     * repository profiles directory and release train. Existing T30 outputs
     * are rejected; other capabilities' evidence is preserved.
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 6) { throw new IllegalArgumentException("Expected six T30 evidence arguments"); }
        Path output = Paths.get(arguments[0]).toAbsolutePath().normalize();
        Path artifacts = output.resolve("artifacts");
        requireFresh(output); requireFresh(artifacts);
        Files.createDirectories(artifacts);
        QpdfPin qpdf = QpdfPin.load(Paths.get(arguments[1]));
        PdfiumPin pdfium = PdfiumPin.load(Paths.get(arguments[2]));
        ImageMagickPin comparator = ImageMagickPin.load(Paths.get(arguments[3]));
        Path profiles = Paths.get(arguments[4]).toAbsolutePath().normalize();
        String release = arguments[5];
        String sources = sources(profiles.getParent().getParent(), profiles.resolve(PROFILE + ".md"));
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-sources.sha256"), sources);
        String provenance = metadata("Source declaration SHA-256", EvidenceFiles.sha256(sources))
                + metadata("Reference font SHA-256", T30FontMetrics.SHA256)
                + metadata("ZXing core version", "3.5.3")
                + metadata("ZXing JAR SHA-256", "8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82")
                + metadata("OkapiBarcode version", "0.5.6")
                + metadata("OkapiBarcode JAR SHA-256", "fc07c5e28f200a53b980e36719901e095e06d1432d7ae959a22456f838765f2a")
                + metadata("Input hash policy", EvidenceFiles.inputHashPolicy());
        Path reference = artifacts.resolve(PROFILE + "-reference.pdf");
        T30BarcodeReference.create(reference);
        T30BarcodeAssertions.Observation referenceObservation = T30BarcodeAssertions.inspect(reference);
        EvidenceFiles.write(artifacts.resolve(PROFILE + "-reference-semantic.txt"), referenceObservation.findings);
        if (!referenceObservation.passed) { throw new IllegalStateException("Independent reference did not meet its declaration"); }
        provenance += metadata("Reference PDF SHA-256", EvidenceFiles.sha256(reference))
                + metadata("Reference ID-neutral SHA-256", EvidenceFiles.idNeutralPdfSha256(reference))
                + "[Source declarations](artifacts/" + PROFILE + "-sources.sha256)\n\n";
        EvidenceResult syntax = EvidenceResult.PASS, semantic = EvidenceResult.PASS, visual = EvidenceResult.PASS;
        int decoded = 0, labelled = 0, rasterDecoded = 0;
        StringBuilder semanticLinks = new StringBuilder(), syntaxLinks = new StringBuilder(), visualLinks = new StringBuilder();
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            String stem = PROFILE + "-" + profile.name().toLowerCase(Locale.ROOT).replace('_', '-');
            Path pdf = artifacts.resolve(stem + ".pdf");
            T30BarcodeProducts.create(pdf, profile);
            String hash = EvidenceFiles.idNeutralPdfSha256(pdf);
            String identity = metadata("Execution profile", profile.name()) + metadata("Actual PDF SHA-256", EvidenceFiles.sha256(pdf))
                    + metadata("Input ID-neutral SHA-256", hash) + "[Actual PDF](artifacts/" + stem + ".pdf)\n\n";
            T30BarcodeAssertions.Observation observed = T30BarcodeAssertions.inspect(pdf);
            EvidenceFiles.write(artifacts.resolve(stem + "-semantic.txt"), observed.findings);
            semantic = combine(semantic, observed.passed ? EvidenceResult.PASS : EvidenceResult.FAIL);
            decoded += observed.decodedPages; labelled += observed.labelledPages;
            semanticLinks.append(identity).append("[Reopened path decoding, geometry and labels](artifacts/").append(stem).append("-semantic.txt)\n\n");
            EvidenceResult syntaxResult = QpdfSyntaxRecorder.record(output, artifacts, qpdf, hash, release,
                    new QpdfSyntaxRecorder.Profile("T30", CAPABILITY, PROFILE, "capabilities/evidence/" + PROFILE + ".md",
                            stem + ".pdf", stem + "-syntax.md", stem + "-qpdf.txt"));
            syntax = combine(syntax, syntaxResult);
            syntaxLinks.append(identity).append("[Pinned qpdf observation](").append(stem).append("-syntax.md)\n\n");
            T30RasterEvidence.Observation raster = T30RasterEvidence.record(pdf, reference, artifacts, pdfium, comparator);
            visual = combine(visual, raster.result); rasterDecoded += raster.decodedPages;
            EvidenceFiles.write(artifacts.resolve(stem + "-visual.txt"), raster.findings);
            visualLinks.append(identity).append("[Every page's raster decode, PNG hashes and comparison](artifacts/")
                    .append(stem).append("-visual.txt)\n\n").append(rasterLinks(stem, artifacts));
        }
        EvidenceFiles.write(output.resolve(PROFILE + "-semantic.md"), "# T30 independent semantic evidence\n\n"
                + record("semantic", semantic, "project-test", "folio-pdf-t30-semantic-assertions", release, release) + provenance
                + metadata("Decoded pages", Integer.toString(decoded)) + metadata("Verified labels", Integer.toString(labelled))
                + "Every observed PDF path is compared to independent modules and point geometry. Actual scanlines are decoded, including checks, raw symbols and supplements. "
                + "Labels are compared character by character to independent source-font metrics.\n\n" + semanticLinks);
        EvidenceFiles.write(output.resolve(PROFILE + "-syntax.md"), "# T30 independent syntax evidence\n\n"
                + record("syntax", syntax, "external-tool", "qpdf", qpdf.version(), release) + provenance + syntaxLinks
                + "qpdf checks syntax; it does not establish PDF standards conformance.\n");
        EvidenceFiles.write(output.resolve(PROFILE + "-visual.md"), "# T30 independent visual and raster decoding evidence\n\n"
                + record("visual", visual, "external-tool", "pdfium-cli", pdfium.producerVersion(), release) + provenance
                + metadata("Raster-decoded pages", Integer.toString(rasterDecoded))
                + "Every 612x792-point page is rendered at 288 DPI to an opaque white sRGB 2448x3168 PNG. ImageMagick " + comparator.version()
                + " compares it with the independent module/position reference at AE 0 and fuzz 0%. Captions participate in the comparison. "
                + "Reference pages use existing Canvas/T19 commands and never call barcode generation. Every actual raster is also independently decoded.\n\n"
                + visualLinks);
        EvidenceFiles.write(output.resolve(PROFILE + "-standards.md"), "# T30 standards evidence\n\n"
                + record("standards", EvidenceResult.INDETERMINATE, "project-test", "folio-pdf-t30-evidence-recorder", release, release)
                + "No independent PDF standards-conformance evidence is supplied for this profile. Syntax, barcode decoding and visual agreement do not substitute for it. "
                + "Compatible-status Canvas, font and Workflow dependency gates and Foundation platform/font certification remain open. Capability status remains experimental.\n");
        System.out.println("T30: syntax=" + syntax + ", semantic=" + semantic + ", visual=" + visual + ", path-decoded=" + decoded
                + ", raster-decoded=" + rasterDecoded + "; standards and compatibility remain indeterminate.");
        if (syntax == EvidenceResult.FAIL || semantic == EvidenceResult.FAIL || visual == EvidenceResult.FAIL) {
            throw new IllegalStateException("T30 acceptance has a failing evidence chain; retained findings identify it");
        }
    }

    private static String rasterLinks(String stem, Path artifacts) {
        if (!Files.isRegularFile(artifacts.resolve(stem + "-actual-1.png"))) {
            return "Raster files are unavailable; the tool findings explain the missing evidence.\n\n";
        }
        StringBuilder links = new StringBuilder();
        int page = 0;
        for (T30BarcodeProfile.Fixture fixture : T30BarcodeProfile.fixtures()) {
            page++;
            links.append("- Page ").append(page).append(" ").append(fixture.id).append(':');
            for (String kind : new String[] {"actual", "reference", "difference"}) {
                String file = stem + "-" + kind + "-" + page + ".png";
                if (Files.isRegularFile(artifacts.resolve(file))) { links.append(" [").append(kind).append("](artifacts/").append(file).append(')'); }
                else { links.append(' ').append(kind).append(" unavailable"); }
            }
            links.append('\n');
        }
        return links.append('\n').toString();
    }

    private static EvidenceResult combine(EvidenceResult current, EvidenceResult next) {
        if (current == EvidenceResult.FAIL || next == EvidenceResult.FAIL) { return EvidenceResult.FAIL; }
        return current == EvidenceResult.INDETERMINATE || next == EvidenceResult.INDETERMINATE ? EvidenceResult.INDETERMINATE : EvidenceResult.PASS;
    }

    private static String record(String chain, EvidenceResult result, String kind, String producer, String version, String release) {
        return metadata("Capability", CAPABILITY) + metadata("Acceptance Profile", PROFILE)
                + metadata("Profile record", "capabilities/evidence/" + PROFILE + ".md") + metadata("Release train", release)
                + metadata("Chain", chain) + metadata("Result", result.recordValue()) + metadata("Producer kind", kind)
                + metadata("Producer", producer) + metadata("Producer version", version);
    }

    private static void requireFresh(Path directory) throws IOException {
        if (!Files.exists(directory)) { return; }
        try (DirectoryStream<Path> existing = Files.newDirectoryStream(directory, "T30*")) {
            if (existing.iterator().hasNext()) { throw new IOException("Existing T30 evidence must be preserved; select a fresh output directory"); }
        }
    }

    private static String sources(Path root, Path profile) throws IOException {
        List<Path> files = new ArrayList<Path>();
        files.add(profile); files.add(root.resolve("pom.xml"));
        for (String module : new String[] {"pdf-document", "pdf-acceptance"}) {
            files.add(root.resolve(module + "/pom.xml"));
            try (Stream<Path> source = Files.walk(root.resolve(module + "/src/main/java"))) {
                source.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
            }
        }
        Collections.sort(files);
        StringBuilder result = new StringBuilder();
        for (Path path : files) { result.append(EvidenceFiles.sha256(path)).append("  ").append(root.relativize(path).toString().replace('\\', '/')).append('\n'); }
        return result.toString();
    }
}

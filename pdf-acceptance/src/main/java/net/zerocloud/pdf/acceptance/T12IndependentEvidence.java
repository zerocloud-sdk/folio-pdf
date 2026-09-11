package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Observes unchanged products against original, identity-bound acceptance authorities. */
final class T12IndependentEvidence {
    private static final String REQUIRED_RULES_SHA256 = "4be1e31062eafdfff6efd2d29d5217287c5c1a5186aed693a0c608672ba9e98e";
    private static final String[] STANDARD_GROUPS = {"pdfcpu", "arlington-core", "arlington-annotations"};
    private static final String[] STANDARD_PROFILE_HASHES = {
        "267ab605cb48f61f16503f424db871b6af943852ed134a5ab9ef54f2f3d57762",
        "82d3e333e01a5fbdffaddbd7a3ed72cc65adc39d655764e2d1bbcb315df0d9b4",
        "f59ffac3fb99c0dcd1e6c93e7c8de3864b79ca6555e6400e63a90ddd1b1f10dd"
    };
    private T12IndependentEvidence() { }

    static Properties record(Path root, Path input, String product, Path output,
            WorkflowExecutionProfile execution, String release) throws Exception {
        new T12Corpus(root).product(product);
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("annotations.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Properties result = new Properties();
        result.setProperty("profile", T12Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        QpdfSyntaxRecorder.Profile syntax = QpdfSyntaxRecorder.Profile.exactDocument("T74", T12AnnotationProducts.CAPABILITY,
                T12Corpus.PROFILE, "capabilities/evidence/T12-annotations-document-actions.md", "annotations.pdf", "syntax.md", "syntax.txt");
        result.setProperty("syntax", QpdfSyntaxRecorder.record(output, output,
                QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")), exactHash, release, syntax).recordValue());

        Set<String> required = requiredRules(root);
        Set<String> covered = new TreeSet<String>();
        String standards = "pass";
        StringBuilder findings = new StringBuilder("Input exact SHA-256: " + exactHash + "\n");
        for (int index = 0; index < STANDARD_GROUPS.length; index++) {
            String group = STANDARD_GROUPS[index];
            Path profile = root.resolve("capabilities/profiles/T12-standards/" + group + ".properties");
            if (!STANDARD_PROFILE_HASHES[index].equals(EvidenceFiles.sha256(profile))) {
                throw new IOException("T12 " + group + " standards profile identity mismatch");
            }
            Path directory = output.resolve(group);
            StandardsEvidenceCommand.main(new String[] {directory.toString(), root.resolve("scripts/"
                    + (group.startsWith("arlington") ? "t12-arlington" : group) + "-pin.properties").toString(),
                profile.toString(), observed.toString()});
            PinProperties record = PinProperties.load(directory.resolve("standards.properties"), "T12 " + group);
            String verdict = record.required("result");
            standards = EvidenceResult.combine(standards, verdict);
            if (!T12Corpus.PROFILE.equals(record.required("profile")) || !exactHash.equals(record.required("input-sha256"))) {
                standards = "indeterminate";
            }
            if ("pass".equals(verdict)) {
                for (String rule : record.required("covered-rules").split(",")) {
                    if (!covered.add(rule)) { throw new IOException("Duplicate T12 standards assignment: " + rule); }
                }
            }
            findings.append(group).append('\n')
                    .append(new String(Files.readAllBytes(directory.resolve("standards.properties")), StandardCharsets.UTF_8))
                    .append(new String(Files.readAllBytes(directory.resolve("findings.txt")), StandardCharsets.UTF_8));
        }
        if (!covered.equals(required)) {
            findings.append("The exact required T12 rule union was not qualified.\n");
            if (!"fail".equals(standards)) { standards = "indeterminate"; }
        }
        result.setProperty("standards", standards);
        result.setProperty("standards-rule-count", Integer.toString(required.size()));
        EvidenceFiles.write(output.resolve("standards.txt"), findings.toString());

        Properties semantic = T12AnnotationSemantics.inspect(root, observed, product, output, execution);
        for (String key : new String[] {"semantic", "execution-profile", "semantic-scope", "public-observation", "finding"}) {
            result.setProperty(key, semantic.getProperty(key));
        }
        T12AnnotationProducts.save(output.resolve("semantic.properties"), semantic);
        Properties visual = recordVisual(root, observed, product, output, release);
        for (String key : visual.stringPropertyNames()) {
            if ("visual".equals(key) || "page-count".equals(key) || key.startsWith("page.")) {
                result.setProperty(key, visual.getProperty(key));
            }
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input)) || !exactHash.equals(EvidenceFiles.sha256(observed))) {
            for (String chain : Arrays.asList("syntax", "standards", "semantic", "visual")) {
                result.setProperty(chain, "indeterminate");
            }
        }
        return result;
    }

    private static Set<String> requiredRules(Path root) throws IOException {
        Path authority = root.resolve("capabilities/profiles/T12-standards/required-rules.txt");
        if (!REQUIRED_RULES_SHA256.equals(EvidenceFiles.sha256(authority))) { throw new IOException("T12 required-rule identity mismatch"); }
        List<String> lines = Files.readAllLines(authority, StandardCharsets.US_ASCII);
        Set<String> rules = new TreeSet<String>();
        for (String rule : lines) {
            if (!rule.matches("[a-z0-9-]+") || !rules.add(rule)) { throw new IOException("Invalid T12 standards rule catalog"); }
        }
        if (rules.size() != 174) { throw new IOException("T12 requires exactly 174 qualified standards rules"); }
        return rules;
    }

    static Properties recordVisual(Path root, Path input, String product, Path output, String release) throws Exception {
        T12Corpus corpus = new T12Corpus(root);
        corpus.product(product);
        int pageCount = Integer.parseInt(corpus.expected("products." + product + ".pages.count"));
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("annotations.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        PdfiumPin pdfium = PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties"));
        ImageMagickPin comparator = ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties"));
        Properties result = new Properties();
        result.setProperty("profile", T12Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("page-count", Integer.toString(pageCount));
        String combined = "pass";
        StringBuilder aggregate = new StringBuilder("Input exact SHA-256: " + exactHash + "\n");
        for (int page = 1; page <= pageCount; page++) {
            VisualProfile profile = VisualProfile.load(root.resolve("capabilities/profiles/T12-annotations/visual/"
                    + product + "-page-" + page + ".properties"));
            requireFrozenProfile(root, corpus, product, page, pageCount, profile);
            VisualEvidenceChain chain = VisualEvidenceChain.t12(root, page);
            VisualEvidence evidence = VisualEvidenceRecorder.record(chain, observed, exactHash, output,
                    pdfium, comparator, profile, release);
            String pageResult = evidence.result().recordValue();
            result.setProperty("page." + page + ".visual", pageResult);
            combined = EvidenceResult.combine(combined, pageResult);
            aggregate.append("page ").append(page).append('=').append(pageResult).append('\n');
            EvidenceFiles.write(output.resolve(chain.findingsName()),
                    "Input exact SHA-256: " + exactHash + "\n" + evidence.rawFindings());
            EvidenceFiles.write(output.resolve(chain.recordName()), evidence.record());
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input)) || !exactHash.equals(EvidenceFiles.sha256(observed))) {
            combined = "indeterminate";
        }
        result.setProperty("visual", combined);
        EvidenceFiles.write(output.resolve("visual.txt"), aggregate.append("result=").append(combined).append('\n').toString());
        return result;
    }

    private static void requireFrozenProfile(Path root, T12Corpus corpus, String product, int page,
            int pageCount, VisualProfile profile) throws IOException {
        String prefix = "products." + product + ".visual." + (page - 1) + ".";
        Path expectedRaster = root.resolve("capabilities/profiles/T12-annotations")
                .resolve(corpus.expected(prefix + "raster")).toAbsolutePath().normalize();
        if (!(T12Corpus.PROFILE + "-" + product + "-page-" + page).equals(profile.profileId())
                || profile.pageNumber() != page || profile.pageCount() != pageCount || profile.dpi() != 144
                || !"effective CropBox [0 0 120 100] points".equals(profile.pageBox())
                || !"sRGB, opaque 8-bit RGB PNG after compositing over opaque white".equals(profile.colorPolicy())
                || !"not applicable; the artifact has no text or font resources and uses no system fonts".equals(profile.fontPolicy())
                || !"pinned PDFium default smoothing; vector edges are axis-aligned".equals(profile.antialiasingPolicy())
                || !"opaque white (#ffffff)".equals(profile.background())
                || profile.rasterWidth() != Integer.parseInt(corpus.expected(prefix + "raster-width"))
                || profile.rasterHeight() != Integer.parseInt(corpus.expected(prefix + "raster-height"))
                || !"AE".equals(profile.comparisonMetric()) || profile.comparisonFuzzPercent() != 0
                || profile.comparisonThreshold() != 0 || profile.rendererAgreementThreshold() != 0
                || !expectedRaster.equals(profile.expectedRaster().toAbsolutePath().normalize())
                || !corpus.expected(prefix + "raster-sha256").equals(profile.expectedRasterSha256())) {
            throw new IOException("T12 visual profile differs from the frozen corpus: " + product + " page " + page);
        }
    }
}

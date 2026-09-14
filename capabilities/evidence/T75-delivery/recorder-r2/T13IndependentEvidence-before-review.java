package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Independent tool observations bound to the exact extraction document. */
final class T13IndependentEvidence {
    private static final String QPDF_SHA256 = "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b";
    private static final String QPDF_WRAPPER_SHA256 = "a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e";
    private T13IndependentEvidence() { }

    static Properties recordSyntax(Path root, Path input, Path output, String release) throws IOException {
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("syntax", "indeterminate");
        try {
            Properties before = syntaxToolIdentity(root);
            result.putAll(before);
            QpdfSyntaxRecorder.Profile syntax = QpdfSyntaxRecorder.Profile.exactDocument("T75",
                    "document.text-structure.extract", T13Corpus.PROFILE,
                    "capabilities/evidence/T13-text-logical-structure.md", "extraction.pdf", "syntax.md", "syntax.txt");
            result.setProperty("syntax", QpdfSyntaxRecorder.record(output, output,
                    QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")), exactHash, release, syntax).recordValue());
            if (!before.equals(syntaxToolIdentity(root))) {
                throw new IOException("T13 qpdf identity changed during observation");
            }
        } catch (IOException unavailable) {
            result.setProperty("syntax", "indeterminate");
            result.setProperty("finding", "The qualified qpdf installation is unavailable or changed.");
            EvidenceFiles.write(output.resolve("syntax.txt"), "Input exact SHA-256: " + exactHash
                    + "\nresult=indeterminate\n" + result.getProperty("finding") + "\n");
        }
        if (!exactHash.equals(EvidenceFiles.sha256(input)) || !exactHash.equals(EvidenceFiles.sha256(observed))) {
            result.setProperty("syntax", "indeterminate");
        }
        return result;
    }

    private static Properties syntaxToolIdentity(Path root) throws IOException {
        Path pinPath = root.resolve("scripts/qpdf-pin.properties");
        PinProperties pin = PinProperties.load(pinPath, "T13 qpdf pin");
        String cache = System.getenv("QPDF_CACHE_DIRECTORY");
        Path binary = (cache == null ? root.resolve(".build-cache/qpdf") : Paths.get(cache)).resolve("12.4.0/bin/qpdf");
        Path wrapper = root.resolve("scripts/container-bin/qpdf");
        if (!"12.4.0".equals(pin.required("QPDF_VERSION"))
                || !"container-bin/qpdf".equals(pin.required("QPDF_EXECUTABLE"))
                || !QPDF_SHA256.equals(pin.required("QPDF_BINARY_SHA256"))
                || !QPDF_WRAPPER_SHA256.equals(EvidenceFiles.sha256(wrapper))
                || !QPDF_SHA256.equals(EvidenceFiles.sha256(binary))) {
            throw new IOException("T13 qpdf identity mismatch");
        }
        Properties identity = new Properties();
        identity.setProperty("qpdf-version", "12.4.0");
        identity.setProperty("qpdf-binary-sha256", QPDF_SHA256);
        identity.setProperty("qpdf-wrapper-sha256", QPDF_WRAPPER_SHA256);
        identity.setProperty("qpdf-pin-sha256", EvidenceFiles.sha256(pinPath));
        return identity;
    }

    static Properties recordVisual(Path root, Path input, String product, Path output, String release) throws Exception {
        T13Corpus corpus = new T13Corpus(root);
        corpus.requireProduct(product);
        int pageCount = Integer.parseInt(corpus.expected("products." + product + ".extraction.pages.count"));
        String exactHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        PdfiumPin pdfium = PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties"));
        ImageMagickPin comparator = ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties"));
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("input-sha256", exactHash);
        result.setProperty("page-count", Integer.toString(pageCount));
        String combined = "pass";
        StringBuilder aggregate = new StringBuilder("Input exact SHA-256: " + exactHash + "\n");
        for (int page = 1; page <= pageCount; page++) {
            VisualProfile profile = VisualProfile.load(root.resolve("capabilities/profiles/T13-text/visual/"
                    + product + "-page-" + page + ".properties"));
            requireFrozenProfile(root, corpus, product, page, pageCount, profile);
            VisualEvidenceChain chain = VisualEvidenceChain.t13(root, page);
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
        corpus.verifySources();
        result.setProperty("visual", combined);
        EvidenceFiles.write(output.resolve("visual.txt"), aggregate.append("result=").append(combined).append('\n').toString());
        return result;
    }

    private static void requireFrozenProfile(Path root, T13Corpus corpus, String product, int page,
            int pageCount, VisualProfile profile) throws IOException {
        String prefix = "products." + product + ".visual." + (page - 1) + ".";
        Path expectedRaster = root.resolve("capabilities/profiles/T13-text")
                .resolve(corpus.expected(prefix + "raster")).toAbsolutePath().normalize();
        String fontPolicy = "embedded-font-kinds".equals(product)
                ? "original embedded Type1C, CID CFF and TrueType rectangle glyphs; no system fonts or substitution"
                : "original embedded Type3 rectangle glyph; no system fonts or substitution";
        String antialiasing = "embedded-font-kinds".equals(product)
                ? "pinned PDFium default font smoothing; independent original PDF reference"
                : "pinned PDFium default smoothing; vector edges are axis-aligned";
        if ("embedded-font-kinds".equals(product)
                && !corpus.expected(prefix + "reference-pdf-sha256").equals(EvidenceFiles.sha256(
                    root.resolve("capabilities/profiles/T13-text").resolve(corpus.expected(prefix + "reference-pdf"))))) {
            throw new IOException("T13 original font reference PDF identity mismatch");
        }
        if (!(T13Corpus.PROFILE + "-" + product + "-page-" + page).equals(profile.profileId())
                || profile.pageNumber() != page || profile.pageCount() != pageCount || profile.dpi() != 144
                || !"effective CropBox [0 0 120 100] points".equals(profile.pageBox())
                || !"sRGB, opaque 8-bit RGB PNG after compositing over opaque white".equals(profile.colorPolicy())
                || !fontPolicy.equals(profile.fontPolicy())
                || !antialiasing.equals(profile.antialiasingPolicy())
                || !"opaque white (#ffffff)".equals(profile.background())
                || profile.rasterWidth() != Integer.parseInt(corpus.expected(prefix + "raster-width"))
                || profile.rasterHeight() != Integer.parseInt(corpus.expected(prefix + "raster-height"))
                || !"AE".equals(profile.comparisonMetric()) || profile.comparisonFuzzPercent() != 0
                || profile.comparisonThreshold() != 0 || profile.rendererAgreementThreshold()
                    != Integer.parseInt(corpus.expected(prefix + "renderer-agreement-threshold"))
                || !expectedRaster.equals(profile.expectedRaster().toAbsolutePath().normalize())
                || !corpus.expected(prefix + "raster-sha256").equals(profile.expectedRasterSha256())) {
            throw new IOException("T13 visual profile differs from the frozen corpus: " + product + " page " + page);
        }
    }
}

package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Independent tool observations bound to the exact extraction document. */
final class T13IndependentEvidence {
    private static final String QPDF_SHA256 = "9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b";
    private static final String QPDF_WRAPPER_SHA256 = "a12d5a4e48fd37e8aefa3b92b30f002c25d2de9f96944fb68efeb64f2b79431e";
    private static final String QPDF_PIN_SHA256 = "62c63de3d888ba08b60df9e3c33c9ebefe4cc0c8ee747cc5269390017b57169c";
    private static final String QPDF_RUNTIME_SHA256 = "a06ee3eb9314e2fa3db4fb475d806f8249f82e0daea85528535c04362fa280ad";
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
        result.setProperty("qpdf-result", "unavailable");
        try {
            Properties before = syntaxToolIdentity(root, output);
            result.putAll(before);
            QpdfSyntaxRecorder.Profile syntax = QpdfSyntaxRecorder.Profile.exactDocument("T75",
                    "document.text-structure.extract", T13Corpus.PROFILE,
                    "capabilities/evidence/T13-text-logical-structure.md", "extraction.pdf", "qpdf-syntax.md", "qpdf-syntax.txt");
            String raw = QpdfSyntaxRecorder.record(output, output,
                    QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")), exactHash, release, syntax).recordValue();
            result.setProperty("qpdf-result", raw);
            result.setProperty("syntax", raw);
            if (!before.equals(syntaxToolIdentity(root, output))) {
                throw new IOException("T13 qpdf identity changed during observation");
            }
            result.setProperty("finding", "The qpdf observation retains its final input and tool identity checks.");
        } catch (IOException unavailable) {
            result.setProperty("syntax", "indeterminate");
            result.setProperty("finding", "The qualified qpdf installation identity is unavailable or changed.");
        }
        try {
            String inputAfter = EvidenceFiles.sha256(input);
            String observedAfter = EvidenceFiles.sha256(observed);
            result.setProperty("input-after-sha256", inputAfter);
            result.setProperty("retained-after-sha256", observedAfter);
            if (!exactHash.equals(inputAfter) || !exactHash.equals(observedAfter)) {
                throw new IOException("T13 PDF identity changed during observation");
            }
        } catch (IOException changed) {
            result.setProperty("syntax", "indeterminate");
            result.setProperty("finding", "The input or retained PDF identity changed or is unavailable.");
        }
        String summary = "# T75 syntax observation\n\nInput exact SHA-256: `" + exactHash
                + "`\n\nFinal determination: `" + result.getProperty("syntax") + "`\n\n"
                + result.getProperty("finding") + "\n\nRaw qpdf result before final identity checks: `"
                + result.getProperty("qpdf-result") + "`\n";
        if (Files.isRegularFile(output.resolve("qpdf-syntax.txt"))) {
            summary += "\nOriginal qpdf output: [findings](qpdf-syntax.txt), [tool record](qpdf-syntax.md).\n";
        }
        EvidenceFiles.write(output.resolve("syntax.txt"), summary);
        EvidenceFiles.write(output.resolve("syntax.md"), summary);
        return result;
    }

    private static Properties syntaxToolIdentity(Path root, Path workingDirectory) throws IOException {
        Path pinPath = root.resolve("scripts/qpdf-pin.properties");
        if (!QPDF_PIN_SHA256.equals(EvidenceFiles.sha256(pinPath))) {
            throw new IOException("T13 qpdf pin identity mismatch");
        }
        PinProperties pin = PinProperties.load(pinPath, "T13 qpdf pin");
        String cache = System.getenv("QPDF_CACHE_DIRECTORY");
        // Match the wrapper's empty-value default and the actual qpdf process cwd.
        Path cacheDirectory = cache == null || cache.isEmpty() ? root.resolve(".build-cache/qpdf") : Paths.get(cache);
        Path binary = workingDirectory.toRealPath().resolve(cacheDirectory).resolve("12.4.0/bin/qpdf");
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
        Path runtime = root.resolve("scripts/t13-qpdf-runtime.sha256");
        byte[] manifest = Files.readAllBytes(runtime);
        if (!QPDF_RUNTIME_SHA256.equals(EvidenceFiles.sha256(manifest))) {
            throw new IOException("T13 qpdf runtime manifest identity mismatch");
        }
        for (String line : new String(manifest, StandardCharsets.US_ASCII).split("\n")) {
            String file = line.substring(66);
            String hash = EvidenceFiles.sha256(binary.getParent().getParent().resolve(file));
            if (!line.substring(0, 64).equals(hash)) {
                throw new IOException("T13 qpdf runtime library identity mismatch");
            }
            identity.setProperty("qpdf-runtime." + file, hash);
        }
        identity.setProperty("qpdf-runtime-sha256", QPDF_RUNTIME_SHA256);
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

package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Fixed PDFium/ImageMagick chain with independent decoding of every actual raster. */
final class T30RasterEvidence {
    private static final Pattern AE = Pattern.compile("([0-9]+(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?)(?: \\([^\\r\\n]*\\))?");
    private T30RasterEvidence() { }

    static Observation record(Path pdf, Path reference, Path artifacts, PdfiumPin pdfium, ImageMagickPin comparator) {
        StringBuilder findings = new StringBuilder();
        int decoded = 0;
        EvidenceResult result = EvidenceResult.PASS;
        try {
            ProcessResult renderer = ExternalProcess.run(pdfium.executable(), artifacts, "--version");
            invocation(findings, "pdfium --version", renderer);
            if (renderer.exitCode != 0 || !renderer.standardOutput.trim().equals("pdfium version " + pdfium.cliVersion())) {
                return new Observation(EvidenceResult.INDETERMINATE, 0, findings + "Pinned PDFium identity unavailable.\n");
            }
            ProcessResult compareVersion = ExternalProcess.run(comparator.executable(), artifacts, "--version");
            invocation(findings, "magick --version", compareVersion);
            if (compareVersion.exitCode != 0 || !compareVersion.standardOutput.startsWith("Version: ImageMagick " + comparator.version() + " ")) {
                return new Observation(EvidenceResult.INDETERMINATE, 0, findings + "Pinned ImageMagick identity unavailable.\n");
            }
            findings.append("PDFium distribution SHA-256: ").append(pdfium.archiveSha256())
                    .append("\nPDFium engine: ").append(pdfium.engineVersion()).append("; SHA-256: ").append(pdfium.engineArchiveSha256())
                    .append("\nImageMagick distribution SHA-256: ").append(comparator.archiveSha256())
                    .append("\nActual PDF SHA-256: ").append(EvidenceFiles.sha256(pdf))
                    .append("\nReference PDF SHA-256: ").append(EvidenceFiles.sha256(reference))
                    .append("\nProfile: 612x792 pt, 288 DPI, 2448x3168, opaque 8-bit white sRGB; AE 0, fuzz 0%.\n");
            String stem = pdf.getFileName().toString().replaceFirst("\\.pdf$", "");
            List<T30BarcodeProfile.Fixture> fixtures = T30BarcodeProfile.fixtures();
            for (int page = 1; page <= fixtures.size(); page++) {
                for (String suffix : new String[] {"-actual-", "-reference-", "-difference-"}) {
                    if (Files.exists(artifacts.resolve(stem + suffix + page + ".png"))) { throw new IOException("T30 raster output already exists"); }
                }
            }
            render(reference, artifacts, stem + "-reference-%d.png", pdfium, fixtures.size(), findings);
            render(pdf, artifacts, stem + "-actual-%d.png", pdfium, fixtures.size(), findings);
            for (int page = 1; page <= fixtures.size(); page++) {
                T30BarcodeProfile.Fixture fixture = fixtures.get(page - 1);
                Path actual = artifacts.resolve(stem + "-actual-" + page + ".png");
                Path expected = artifacts.resolve(stem + "-reference-" + page + ".png");
                Path difference = artifacts.resolve(stem + "-difference-" + page + ".png");
                PngRaster.requireProfileRaster(actual, 2448, 3168);
                PngRaster.requireProfileRaster(expected, 2448, 3168);
                findings.append("\nPage ").append(page).append(" ").append(fixture.id)
                        .append("\nActual PNG SHA-256: ").append(EvidenceFiles.sha256(actual))
                        .append("\nReference PNG SHA-256: ").append(EvidenceFiles.sha256(expected)).append('\n');
                try {
                    findings.append("Raster decode: ").append(T30RasterDecoder.decode(ImageIO.read(actual.toFile()), fixture)).append("; pass\n");
                    decoded++;
                } catch (Exception mismatch) {
                    result = EvidenceResult.FAIL;
                    findings.append("Raster decode: fail; ").append(mismatch.getClass().getSimpleName()).append(": ")
                            .append(mismatch.getMessage()).append('\n');
                }
                ProcessResult comparison = ExternalProcess.run(comparator.executable(), artifacts, "compare", "-metric", "AE", "-fuzz", "0%",
                        "-highlight-color", "#ff0000", "-lowlight-color", "#ffffff", "-define", "png:exclude-chunk=time,date",
                        expected.getFileName().toString(), actual.getFileName().toString(), difference.getFileName().toString());
                invocation(findings, "magick compare -metric AE -fuzz 0% " + expected.getFileName() + " " + actual.getFileName()
                        + " " + difference.getFileName(), comparison);
                Matcher metric = AE.matcher(comparison.standardError.trim());
                if ((comparison.exitCode != 0 && comparison.exitCode != 1) || !metric.matches()) { throw new IOException("Unusable ImageMagick comparison"); }
                BigDecimal error = new BigDecimal(metric.group(1));
                PngRaster.requireDifferenceRaster(difference, 2448, 3168);
                findings.append("Difference PNG SHA-256: ").append(EvidenceFiles.sha256(difference)).append('\n');
                if (error.signum() != 0 || comparison.exitCode != 0) { result = EvidenceResult.FAIL; }
                findings.append("Visual comparison: ").append(error.signum() == 0 && comparison.exitCode == 0 ? "pass" : "fail").append('\n');
                if (page % 18 == 0) { System.out.println("T30 " + stem + ": compared and raster-decoded " + page + "/" + fixtures.size()); }
            }
        } catch (IOException unavailable) {
            if (result != EvidenceResult.FAIL) { result = EvidenceResult.INDETERMINATE; }
            findings.append("Required raster evidence unavailable: ").append(unavailable.getMessage()).append('\n');
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (result != EvidenceResult.FAIL) { result = EvidenceResult.INDETERMINATE; }
            findings.append("Raster tool interrupted.\n");
        }
        return new Observation(result, decoded, findings.toString());
    }

    private static void render(Path pdf, Path artifacts, String output, PdfiumPin pin, int pages, StringBuilder findings)
            throws IOException, InterruptedException {
        ProcessResult rendered = ExternalProcess.run(pin.executable(), artifacts, "render", pdf.toAbsolutePath().toString(), output,
                "--dpi", "288", "--file-type", "png", "--pages", "1-" + pages);
        invocation(findings, "pdfium render " + pdf.getFileName() + " " + output + " --dpi 288 --file-type png --pages 1-" + pages, rendered);
        if (rendered.exitCode != 0) { throw new IOException("PDFium render did not succeed"); }
    }

    private static void invocation(StringBuilder findings, String command, ProcessResult result) {
        findings.append("Command: ").append(command).append("\nExit: ").append(result.exitCode)
                .append("\nstdout:\n").append(result.standardOutput).append("\nstderr:\n").append(result.standardError).append('\n');
    }

    static final class Observation {
        final EvidenceResult result;
        final int decodedPages;
        final String findings;
        Observation(EvidenceResult result, int decodedPages, String findings) {
            this.result = result; this.decodedPages = decodedPages; this.findings = findings;
        }
    }
}

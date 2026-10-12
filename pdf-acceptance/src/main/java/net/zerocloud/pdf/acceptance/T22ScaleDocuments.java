package net.zerocloud.pdf.acceptance;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/** Independent producers read actual scale artifacts, including the full consumed input. */
final class T22ScaleDocuments {
    private T22ScaleDocuments() { }

    static void record(Path root, Path scale) throws Exception {
        record(root, scale.resolve("pages"), "generated-5000-pages.pdf", 5000);
        for (String name : Arrays.asList("concurrent-0.pdf", "concurrent-1.pdf", "concurrent-excess.pdf")) {
            Path directory = Files.createDirectory(scale.resolve("concurrency/" + name.replace(".pdf", "")));
            Files.copy(scale.resolve("concurrency/" + name), directory.resolve(name));
            record(root, directory, name, 1);
        }
        Path input = scale.resolve("input/consumed-input.pdf");
        try {
            try (InputStream source = new GZIPInputStream(Files.newInputStream(scale.resolve("input/consumed-input.pdf.gz")));
                    OutputStream target = Files.newOutputStream(input)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = source.read(buffer)) >= 0) { target.write(buffer, 0, count); }
            }
            Properties observed = load(scale.resolve("input/observations.properties"));
            if (Files.size(input) != 1073741824L || !EvidenceFiles.sha256(input).equals(observed.getProperty("input-sha256"))) {
                throw new java.io.IOException("Retained bytes differ from the actual consumed 1-GiB input");
            }
            record(root, scale.resolve("input"), input.getFileName().toString(), 1);
        } finally { Files.deleteIfExists(input); }
    }

    private static void record(Path root, Path directory, String name, int pages) throws Exception {
        Path pdf = directory.resolve(name);
        String exact = EvidenceFiles.sha256(pdf);
        Properties results = new Properties(); results.setProperty("input-sha256", exact);
        ProcessResult syntax = ExternalProcess.run(QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")).executable(),
                directory, "--check", name);
        EvidenceFiles.write(directory.resolve("syntax.txt"), "input-sha256=" + exact + "\nexit=" + syntax.exitCode + "\n" + syntax.combinedOutput());
        check(syntax.exitCode == 0, "actual scale syntax"); results.setProperty("syntax", "pass");
        Set<String> rules = new TreeSet<String>();
        for (String checker : Arrays.asList("pdfcpu", "arlington")) {
            Path findings = directory.resolve(checker);
            StandardsEvidenceCommand.main(new String[] {findings.toString(), root.resolve("scripts/" + (checker.equals("arlington") ? "t22-arlington" : checker) + "-pin.properties").toString(),
                root.resolve("capabilities/profiles/T03-standards/" + checker + ".properties").toString(), pdf.toString()});
            Properties actual = load(findings.resolve("standards.properties"));
            check("pass".equals(actual.getProperty("result")) && exact.equals(actual.getProperty("input-sha256")), "actual scale standards/rule qualification");
            rules.addAll(Arrays.asList(actual.getProperty("covered-rules").split(",")));
        }
        check(rules.size() == 22, "required 22-rule blank-document union");
        results.setProperty("standards", "pass"); results.setProperty("covered-rules", String.join(",", rules));
        Path observer = root.resolve("scripts/t22_semantics.py");
        ProcessResult semantics = ExternalProcess.run(java.nio.file.Paths.get("/usr/bin/python3.12"), directory,
                observer.toString(), QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")).executable().toString(),
                pdf.toString(), Integer.toString(pages), directory.resolve("semantic").toString());
        EvidenceFiles.write(directory.resolve("semantic.txt"), semantics.combinedOutput());
        check(semantics.exitCode == 0, "all actual blank scale pages"); results.setProperty("semantic", "pass");
        results.setProperty("page-count", Integer.toString(pages));
        if (pages == 5000) {
            ProcessResult negative = ExternalProcess.run(java.nio.file.Paths.get("/usr/bin/python3.12"), directory,
                    observer.toString(), QpdfPin.load(root.resolve("scripts/qpdf-pin.properties")).executable().toString(),
                    pdf.toString(), "1", directory.resolve("semantic-wrong-count").toString());
            EvidenceFiles.write(directory.resolve("semantic-wrong-count.txt"), negative.combinedOutput());
            check(negative.exitCode != 0, "actual 5000-page wrong-count control was not detected");
        }
        for (int page : pages == 5000 ? new int[] {1, 2500, 5000} : new int[] {1}) {
            Path raster = Files.createDirectory(directory.resolve("page-" + page));
            Files.createLink(raster.resolve("blank.pdf"), pdf);
            VisualProfile profile = VisualProfile.load(root.resolve(pages == 5000
                    ? "capabilities/profiles/T22-worker-recovery-scale-pages-" + page + "-visual.properties"
                    : "capabilities/profiles/T03-document-blank-visual.properties"));
            // The giant authored input has no trailer ID; its exact identity is also its neutral identity.
            String neutral = name.equals("consumed-input.pdf") ? exact : EvidenceFiles.idNeutralPdfSha256(pdf);
            VisualEvidence visual = VisualEvidenceRecorder.record(VisualEvidenceChain.t22(root,
                    name.equals("consumed-input.pdf") ? "consumed-input.pdf.gz" : name), raster.resolve("blank.pdf"), neutral, raster,
                    PdfiumPin.load(root.resolve("scripts/pdfium-pin.properties")),
                    ImageMagickPin.load(root.resolve("scripts/imagemagick-pin.properties")), profile, "0.1.0");
            EvidenceFiles.write(raster.resolve("visual.txt"), visual.rawFindings());
            EvidenceFiles.write(raster.resolve("visual.md"), visual.record());
            check(visual.result() == EvidenceResult.PASS, "actual scale independent pixels: " + page);
            results.setProperty("page." + page + ".visual", "pass");
            Files.delete(raster.resolve("blank.pdf"));
        }
        results.setProperty("visual", "pass");
        check(exact.equals(EvidenceFiles.sha256(pdf)), "scale artifact changed during observation");
        try (OutputStream target = Files.newOutputStream(directory.resolve("document.properties"))) { results.store(target, "Actual scale document chains"); }
    }

    private static Properties load(Path path) throws Exception {
        Properties values = new Properties(); try (InputStream input = Files.newInputStream(path)) { values.load(input); } return values;
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}

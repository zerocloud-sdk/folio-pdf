package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Actual document defects observed against unchanged original expectations. */
final class T13NegativeControls {
    private T13NegativeControls() { }

    static Properties record(Path root, Path output, WorkflowExecutionProfile execution, String release) throws Exception {
        T13Corpus corpus = new T13Corpus(root);
        RetainedEvidence retained = new RetainedEvidence(output);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        Path syntax = Files.createDirectory(output.resolve("syntax"));
        Path truncated = syntax.resolve("extraction.pdf");
        retained.write(truncated, "%PDF-2.0\n1 0 obj\n");
        Properties syntaxResult = T13IndependentEvidence.recordSyntax(root, truncated, syntax, release);
        retained.write(syntax.resolve("result.properties"), syntaxResult);
        T13CombinedEvidence.includeManifest(retained, syntax, syntaxResult.getProperty("retained-files-sha256"));
        result.setProperty("syntax", syntaxResult.getProperty("syntax"));

        Path standards = Files.createDirectory(output.resolve("standards"));
        Path malformed = standards.resolve("extraction.pdf");
        retained.write(malformed, replaceOnce(corpus.source("marked-structure"), "/Type /Catalog", "/Type /Bogus  "));
        Properties standardsResult = T13DeclarationStandards.record(root, malformed, standards);
        retained.write(standards.resolve("result.properties"), standardsResult);
        T13CombinedEvidence.includeManifest(retained, standards, standardsResult.getProperty("retained-files-sha256"));
        result.setProperty("standards", standardsResult.getProperty("declarations"));

        Path semantic = Files.createDirectory(output.resolve("semantic"));
        Properties semanticResult = semantic(root, semantic, execution);
        retained.write(semantic.resolve("result.properties"), semanticResult);
        T13CombinedEvidence.includeManifest(retained, semantic, semanticResult.getProperty("retained-files-sha256"));
        result.setProperty("semantic", semanticResult.getProperty("semantic"));
        Path visual = Files.createDirectory(output.resolve("visual"));
        Properties visualResult = visual(root, visual, release);
        retained.write(visual.resolve("result.properties"), visualResult);
        T13CombinedEvidence.includeManifest(retained, visual, visualResult.getProperty("retained-files-sha256"));
        result.setProperty("visual", visualResult.getProperty("visual"));
        corpus.verifySources();
        boolean effective = true;
        for (String chain : new String[] {"syntax", "standards", "semantic", "visual"}) {
            effective &= "fail".equals(result.getProperty(chain));
        }
        result.setProperty("retained-files-sha256", retained.publishManifest(effective ? "fail" : "indeterminate"));
        return result;
    }

    static Properties semantic(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        T13Corpus corpus = new T13Corpus(root);
        RetainedEvidence retained = new RetainedEvidence(output);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("native-execution-profile", execution.name());
        boolean positive = true;
        for (String product : T13Corpus.PRODUCTS) {
            Path directory = Files.createDirectory(output.resolve("positive-" + product));
            Path input = directory.resolve("extraction.pdf");
            retained.write(input, Files.readAllBytes(corpus.source(product)));
            Properties observed = T13ExtractionSemantics.inspect(root, input, product, directory, execution);
            retained.write(directory.resolve("result.properties"), observed);
            T13CombinedEvidence.includeManifest(retained, directory, observed.getProperty("retained-files-sha256"));
            result.setProperty("positive." + product, observed.getProperty("semantic"));
            positive &= "pass".equals(observed.getProperty("semantic"));
        }
        result.setProperty("positive", positive ? "pass" : "indeterminate");
        String[][] controls = {
            {"contents-order", "nested-split-type3", "/Contents [4 0 R 5 0 R]", "/Contents [5 0 R 4 0 R]"},
            {"form-position", "nested-split-type3", "/Matrix [2 0 0 1 5 10]", "/Matrix [2 0 0 1 6 10]"},
            {"font-width", "nested-split-type3", "/Widths [500]", "/Widths [600]"},
            {"replacement", "marked-structure", "/ActualText (Outer)", "/ActualText (Other)"},
            {"alternate", "marked-structure", "/Alt (PageAlt)", "/Alt (PageBad)"},
            {"language", "marked-structure", "/Lang (fr)", "/Lang (de)"},
            {"mcr", "marked-structure", "/Stm 7 0 R /MCID 0", "/Stm 7 0 R /MCID 1"},
            {"parent-tree", "marked-structure", "/Nums [0 [10 0 R] 1 [10 0 R] 2 10 0 R]",
                    "/Nums [0 [11 0 R] 1 [10 0 R] 2 10 0 R]"},
            {"object-reference", "marked-structure", "/Obj 12 0 R", "/Obj 11 0 R"},
            {"role-target", "marked-structure", "/Intermediate [/Document 15 0 R]", "/Intermediate [/Document 14 0 R]"},
            {"empty-replacement", "marked-structure", "/ActualText ()", "              "},
            {"namespace-reference", "marked-structure", "/S /Span /NS 15 0 R", "/S /Span /NS 14 0 R"}
        };
        boolean effective = positive;
        for (String[] control : controls) {
            Path directory = Files.createDirectory(output.resolve(control[0]));
            Path input = directory.resolve("extraction.pdf");
            retained.write(input, replaceOnce(corpus.source(control[1]), control[2], control[3]));
            Properties observed = T13ExtractionSemantics.inspect(root, input, control[1], directory, execution);
            retained.write(directory.resolve("result.properties"), observed);
            T13CombinedEvidence.includeManifest(retained, directory, observed.getProperty("retained-files-sha256"));
            result.setProperty(control[0], observed.getProperty("semantic"));
            effective &= "fail".equals(observed.getProperty("semantic"));
        }
        corpus.verifySources();
        result.setProperty("control-count", Integer.toString(controls.length));
        result.setProperty("semantic", effective ? "fail" : "indeterminate");
        result.setProperty("retained-files-sha256", retained.publishManifest(result.getProperty("semantic")));
        return result;
    }

    static Properties visual(Path root, Path output, String release) throws Exception {
        T13Corpus corpus = new T13Corpus(root);
        RetainedEvidence retained = new RetainedEvidence(output);
        String product = "nested-split-type3";
        Path source = root.resolve("capabilities/profiles/T13-text")
                .resolve(corpus.expected("sources." + product + ".path"));
        String sourceHash = EvidenceFiles.sha256(source);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("source-sha256", sourceHash);
        Path positive = Files.createDirectory(output.resolve("positive"));
        Properties baseline = T13IndependentEvidence.recordVisual(root, source, product, positive, release);
        retained.write(positive.resolve("result.properties"), baseline);
        T13CombinedEvidence.includeManifest(retained, positive, baseline.getProperty("retained-files-sha256"));
        result.setProperty("positive", baseline.getProperty("visual"));
        boolean effective = "pass".equals(baseline.getProperty("visual"));
        Path fontPositive = Files.createDirectory(output.resolve("positive-embedded-font-kinds"));
        Properties fonts = T13IndependentEvidence.recordVisual(root, corpus.source("embedded-font-kinds"),
                "embedded-font-kinds", fontPositive, release);
        retained.write(fontPositive.resolve("result.properties"), fonts);
        T13CombinedEvidence.includeManifest(retained, fontPositive, fonts.getProperty("retained-files-sha256"));
        result.setProperty("positive.embedded-font-kinds", fonts.getProperty("visual"));
        effective &= "pass".equals(fonts.getProperty("visual"));
        result.setProperty("positive", effective ? "pass" : "indeterminate");
        String[][] controls = {
            {"glyph-ink", "nested-split-type3", "0 0 400 600 re f", "0 0 300 600 re f"},
            {"form-position", "nested-split-type3", "/Matrix [2 0 0 1 5 10]", "/Matrix [2 0 0 1 6 10]"},
            {"simple-font-position", "embedded-font-kinds", "1 0 0 1 10 20 Tm", "1 0 0 1 11 20 Tm"},
            {"vertical-font-position", "embedded-font-kinds", "1 0 0 1 40 80 Tm", "1 0 0 1 41 80 Tm"}
        };
        for (String[] control : controls) {
            Path directory = Files.createDirectory(output.resolve(control[0]));
            Path defect = directory.resolve("extraction.pdf");
            retained.write(defect, replaceOnce(corpus.source(control[1]), control[2], control[3]));
            Properties observed = T13IndependentEvidence.recordVisual(root, defect, control[1], directory, release);
            retained.write(directory.resolve("result.properties"), observed);
            T13CombinedEvidence.includeManifest(retained, directory, observed.getProperty("retained-files-sha256"));
            String verdict = observed.getProperty("visual");
            result.setProperty(control[0], verdict);
            effective &= "fail".equals(verdict);
        }
        effective &= rasterControls(root, Files.createDirectory(output.resolve("raster")), result, retained);
        if (!"pass".equals(result.getProperty("raster.edge-only"))) { result.setProperty("positive", "indeterminate"); }
        corpus.verifySources();
        result.setProperty("control-count", Integer.toString(controls.length));
        result.setProperty("visual", effective ? "fail" : "indeterminate");
        result.setProperty("retained-files-sha256", retained.publishManifest(result.getProperty("visual")));
        return result;
    }

    private static byte[] replaceOnce(Path source, String before, String after) throws IOException {
        String original = new String(Files.readAllBytes(source), StandardCharsets.ISO_8859_1);
        if (before.length() != after.length() || !original.contains(before)
                || original.indexOf(before) != original.lastIndexOf(before)) {
            throw new IOException("T13 control does not identify one fixed source mutation");
        }
        return original.replace(before, after).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static boolean rasterControls(Path root, Path output, Properties result, RetainedEvidence retained) throws IOException {
        Path primary = output.resolve("original.png");
        byte[] original = Files.readAllBytes(root.resolve("capabilities/profiles/T13-fonts/embedded-font-kinds-reference.png"));
        String expected = new T13Corpus(root).expected("products.embedded-font-kinds.visual.0.raster-sha256");
        if (!expected.equals(EvidenceFiles.sha256(original))) { throw new IOException("T13 raster control reference identity mismatch"); }
        retained.write(primary, original);
        result.setProperty("raster.primary-sha256", EvidenceFiles.sha256(original));
        boolean effective = true;
        int negativeCount = 0;
        for (String control : new String[] {"edge-only", "missing-glyph", "interior-hole", "outside-ink"}) {
            java.awt.image.BufferedImage raster = javax.imageio.ImageIO.read(primary.toFile());
            if (raster == null) { throw new IOException("T13 original raster control is unavailable"); }
            if ("edge-only".equals(control)) { raster.setRGB(20, 136, 0xffffff); }
            if ("missing-glyph".equals(control)) {
                for (int y = 135; y < 161; y++) {
                    for (int x = 19; x < 37; x++) { raster.setRGB(x, y, 0xffffff); }
                }
            }
            if ("interior-hole".equals(control)) { raster.setRGB(23, 139, 0xffffff); }
            if ("outside-ink".equals(control)) { raster.setRGB(1, 1, 0); }
            Path directory = Files.createDirectory(output.resolve(control));
            Path secondary = directory.resolve("secondary.png");
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(raster, "png", bytes)) { throw new IOException("T13 raster control encoding is unavailable"); }
            retained.write(secondary, bytes.toByteArray());
            Properties observed = T13FontRasterAgreement.inspect(primary, secondary);
            retained.write(directory.resolve("result.properties"), observed);
            String verdict = observed.getProperty("raster-agreement");
            result.setProperty("raster." + control, verdict);
            effective &= ("edge-only".equals(control) ? "pass" : "fail").equals(verdict);
            if (!"edge-only".equals(control)) { negativeCount++; }
        }
        result.setProperty("raster-control-count", Integer.toString(negativeCount));
        return effective;
    }
}

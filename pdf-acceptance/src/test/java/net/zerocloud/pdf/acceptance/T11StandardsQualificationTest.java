package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Qualifies actual independent diagnostics against original metadata defects. */
@Category(IndependentTools.class)
public final class T11StandardsQualificationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void everyFrozenRuleIsQualifiedByItsAssignedCheckerOnTheOriginalPdf20Source() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T11-metadata/fixtures/primary.pdf");
        java.util.Set<String> covered = new java.util.TreeSet<String>();
        for (String group : new String[] {"pdfcpu", "arlington-core", "arlington-metadata"}) {
            Path output = temporary.getRoot().toPath().resolve("all-" + group);
            Path pin = root.resolve("scripts/" + ("pdfcpu".equals(group) ? "pdfcpu" : "t11-arlington") + "-pin.properties");
            Path profile = root.resolve("capabilities/profiles/T11-standards/" + group + ".properties");
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            PinProperties result = PinProperties.load(output.resolve("standards.properties"), "T11 " + group);
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    "pass", result.required("result"));
            assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
            for (String rule : result.required("covered-rules").split(",")) {
                assertTrue("A rule has one assigned checker: " + rule, covered.add(rule));
            }
        }
        java.util.Set<String> required = new java.util.TreeSet<String>(Files.readAllLines(
                root.resolve("capabilities/profiles/T11-standards/required-rules.txt"), StandardCharsets.US_ASCII));
        assertEquals(170, required.size());
        assertEquals(required, covered);
    }

    @Test
    public void fitRNullOperandsAreRejectedWhileNullableFitCoordinatesRemainLegal() throws Exception {
        qualify("destination-fitr-operand", "Error: FitR destination operands must all be numbers");
    }

    @Test
    public void nameTreeLimitsAndCyclesHaveSpecificBoundedDiagnostics() throws Exception {
        for (String[] rule : new String[][] {
            {"nametree-child-limits-array", "Error: name tree Limits must be an array"},
            {"nametree-child-limits-count", "Error: name tree Limits must contain two entries"},
            {"nametree-child-limits-string", "Error: name tree Limits entries must be strings"},
            {"nametree-child-limits-value", "Error: name tree Limits do not match descendant keys"},
            {"nametree-kids-cycle", "Error: name tree contains a cycle or repeated child"}
        }) {
            qualify(rule[0], rule[1]);
        }
    }

    @Test
    public void outlineHierarchyCountsAndNamedTargetsRequireMatchingDiagnostics() throws Exception {
        for (String[] rule : new String[][] {
            {"outline-parent-link", "Error: outline Parent does not identify the actual parent"},
            {"outline-prev-link", "Error: outline Prev does not identify the previous sibling"},
            {"outline-prev-required", "Error: outline Prev is required after the first sibling"},
            {"outline-next-cycle", "Error: outline contains a cycle or repeated item"},
            {"outline-first-required", "Error: outline First is required for a nonempty hierarchy"},
            {"outline-last-required", "Error: outline Last is required for a nonempty hierarchy"},
            {"outline-last-link", "Error: outline Last does not identify the last sibling"},
            {"outline-child-last-link", "Error: outline Last does not identify the last sibling"},
            {"outline-count-required", "Error: outline Count is required for visible descendants"},
            {"outline-child-count-required", "Error: outline Count is required for visible descendants"},
            {"outline-count-value", "Error: outline Count does not match visible descendants"},
            {"outline-child-count-value", "Error: outline Count does not match visible descendants"},
            {"outline-named-target", "Error: outline named destination does not resolve"}
        }) {
            qualify(rule[0], rule[1]);
        }
    }

    @Test
    public void embeddedFileDataSizesChecksumsAndMimeKindsAreIndependentlyChecked() throws Exception {
        for (String[] rule : new String[][] {
            {"embedded-size-value", "Error: embedded file Size does not match decoded bytes"},
            {"embedded-checksum-value", "Error: embedded file CheckSum does not match decoded bytes"},
            {"embedded-subtype-mime", "Error: embedded file Subtype is not a MIME media type or prefixed name"},
            {"filespec-type-required", "Error: file specification Type is required when EF is present"}
        }) {
            qualify(rule[0], rule[1]);
        }
    }

    @Test
    public void metadataStreamsRequireIndependentlyParsedWellFormedXml() throws Exception {
        qualify("metadata-xml-wellformed", "Error: metadata stream is not well-formed XML");
    }

    @Test
    public void pinnedPdfCpuPdf20OutputRetainsItsBannerAndRequiresRealIllegalControls() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path fixture = root.resolve("capabilities/profiles/T03-standards/fixtures/catalog-type.pdf");
        Path profile = temporary.getRoot().toPath().resolve("pdfcpu-pdf20.properties");
        Files.write(profile, ("profile=T11-metadata-outlines-destinations-attachments\npdf-version=2.0\n"
                + "required-rules=catalog-type\ncovered-rules=catalog-type\nnegative.catalog-type.path=" + fixture
                + "\nnegative.catalog-type.sha256=" + EvidenceFiles.sha256(fixture)
                + "\nnegative.catalog-type.finding=document catalog: dict=rootDict entry=Type invalid dict entry: Bogus\n")
                .getBytes(StandardCharsets.UTF_8));
        Path output = temporary.getRoot().toPath().resolve("pdfcpu-pdf20");
        StandardsEvidenceCommand.main(new String[] {output.toString(), root.resolve("scripts/pdfcpu-pin.properties").toString(),
            profile.toString(), root.resolve("capabilities/profiles/T11-metadata/fixtures/primary.pdf").toString()});
        PinProperties result = PinProperties.load(output.resolve("standards.properties"), "PDF 2.0 strict observation");
        String findings = new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8);
        assertEquals(findings, "pass", result.required("result"));
        assertEquals("catalog-type", result.required("covered-rules"));
        assertTrue(findings, findings.contains("PDF 2.0 features are supported on a need basis."));
        assertTrue(findings, findings.contains("detected=true"));
        assertTrue(Files.size(output.resolve("negative-catalog-type.txt")) > 0);
    }

    private void qualify(String rule, String finding) throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path fixture = root.resolve("capabilities/profiles/T11-standards/fixtures/" + rule + ".pdf");
        Path profile = temporary.getRoot().toPath().resolve(rule + ".properties");
        Files.write(profile, ("profile=T11-metadata-outlines-destinations-attachments\npdf-version=2.0\n"
                + "required-rules=" + rule + "\ncovered-rules=" + rule + "\nnegative." + rule + ".path=" + fixture
                + "\nnegative." + rule + ".sha256=" + EvidenceFiles.sha256(fixture)
                + "\nnegative." + rule + ".finding=" + finding + "\n").getBytes(StandardCharsets.UTF_8));
        Path pin = root.resolve(System.getProperty("folio.t11.arlingtonPin", "scripts/t11-arlington-pin.properties"));
        for (String source : new String[] {"T11-metadata/fixtures/primary.pdf", "T11-metadata/fixtures/appendix.pdf",
                "T11-standards/fixtures/positive-kids.pdf", "T11-standards/fixtures/positive-closed-outline.pdf",
                "T11-standards/fixtures/positive-filtered-attachment.pdf"}) {
            Path input = root.resolve("capabilities/profiles/" + source);
            Path output = temporary.getRoot().toPath().resolve(rule + "-" + input.getFileName());
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            PinProperties record = PinProperties.load(output.resolve("standards.properties"), "T11 qualification");
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    "pass", record.required("result"));
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
            assertEquals(rule, record.required("covered-rules"));
            assertTrue(new String(Files.readAllBytes(output.resolve("negative-" + rule + ".txt")),
                    StandardCharsets.UTF_8).contains(finding));
        }
    }
}

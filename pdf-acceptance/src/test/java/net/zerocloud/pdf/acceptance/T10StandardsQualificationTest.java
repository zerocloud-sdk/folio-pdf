package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Real external-tool qualification, explicitly selected by independent-certification. */
@Category(IndependentTools.class)
public final class T10StandardsQualificationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void theFullFrozenRuleUnionIsQualifiedByIndependentCheckers() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T10-pages/fixtures/primary.pdf");
        Set<String> covered = new HashSet<String>();
        for (String checker : new String[] {"pdfcpu", "arlington"}) {
            Path output = temporary.getRoot().toPath().resolve(checker);
            Path pin = root.resolve("scripts/" + ("arlington".equals(checker) ? "t10-arlington" : checker) + "-pin.properties");
            Path profile = root.resolve("capabilities/profiles/T10-standards/" + checker + ".properties");
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            String record = read(output.resolve("standards.properties"));
            assertTrue(record + read(output.resolve("findings.txt")), record.contains("result=pass"));
            PinProperties values = PinProperties.load(output.resolve("standards.properties"), "T10 qualification");
            assertEquals(EvidenceFiles.sha256(input), values.required("input-sha256"));
            covered.addAll(Arrays.asList(values.required("covered-rules").split(",")));
        }
        assertEquals("22 core structure, 6 Contents/Filter and 56 page preservation controls", 84, covered.size());
        assertTrue(covered.containsAll(Arrays.asList("pages-count-value", "page-contents-array-member", "stream-filter-name-value",
                "page-rotate-value", "form-bbox-required", "annotation-rect-numbers", "destination-fit-count",
                "destination-page-type", "nametree-key-order", "nametree-key-unique")));
    }

    @Test
    public void destinationAndNameTreeRulesRequireMatchingRealNegativeFindings() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path fixtures = root.resolve("capabilities/profiles/T10-standards/fixtures");
        String[][] rules = {
            {"destination-fit-count", "destination array length is not 2 for Dest0Array"},
            {"destination-page-type", "Page object was already identified as a page-tree node"},
            {"nametree-key-order", "name tree keys are not in ascending byte order"},
            {"nametree-key-order-high-bytes", "name tree keys are not in ascending byte order"},
            {"nametree-key-unique", "name tree key is duplicated"},
            {"nametree-key-unique-encoding", "name tree key is duplicated"}
        };
        StringBuilder profile = new StringBuilder("profile=T10-page-manipulation-merge-split\npdf-version=1.7\n");
        StringBuilder ids = new StringBuilder();
        for (String[] rule : rules) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(rule[0]);
            Path control = fixtures.resolve(rule[0] + ".pdf");
            profile.append("negative.").append(rule[0]).append(".path=").append(control).append('\n')
                    .append("negative.").append(rule[0]).append(".sha256=").append(EvidenceFiles.sha256(control)).append('\n')
                    .append("negative.").append(rule[0]).append(".finding=").append(rule[1]).append('\n');
        }
        profile.append("required-rules=").append(ids).append("\ncovered-rules=").append(ids).append('\n');
        Path ruleProfile = temporary.getRoot().toPath().resolve("rules.properties");
        Files.write(ruleProfile, profile.toString().getBytes(StandardCharsets.UTF_8));
        Path pin = root.resolve(System.getProperty("folio.t10.arlingtonPin", "scripts/t10-arlington-pin.properties"));
        Path[] positives = {fixtures.resolve("valid.pdf"), fixtures.resolve("positive-name-byte-order.pdf"),
            root.resolve("capabilities/profiles/T10-pages/fixtures/primary.pdf"),
            root.resolve("capabilities/profiles/T10-pages/fixtures/cover.pdf"),
            root.resolve("capabilities/profiles/T10-pages/fixtures/appendix.pdf")};
        for (Path input : positives) {
            Path output = temporary.getRoot().toPath().resolve(input.getFileName().toString());
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), ruleProfile.toString(), input.toString()});
            String observed = read(output.resolve("standards.properties"));
            assertTrue(observed + read(output.resolve("findings.txt")), observed.contains("result=pass"));
            for (String[] rule : rules) {
                assertTrue(read(output.resolve("negative-" + rule[0] + ".txt")).contains(rule[1]));
            }
        }
    }

    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}

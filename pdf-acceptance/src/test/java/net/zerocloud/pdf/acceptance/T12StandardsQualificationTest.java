package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

/** Qualifies independent standards diagnostics against original annotation defects. */
@Category(IndependentTools.class)
public final class T12StandardsQualificationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void graphChecksAcceptPageLocalNamesAndAbsentOrNullOptionalBindings() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path pin = root.resolve(System.getProperty("folio.t12.arlingtonPin", "scripts/t12-arlington-pin.properties"));
        Path profile = root.resolve("capabilities/profiles/T12-standards/arlington-annotations.properties");
        for (String boundary : new String[] {"same-name-other-page", "optional-bindings", "null-bindings"}) {
            Path input = root.resolve("capabilities/profiles/T12-standards/fixtures/positive-" + boundary + ".pdf");
            Path output = temporary.getRoot().toPath().resolve(boundary);
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            PinProperties record = PinProperties.load(output.resolve("standards.properties"), "T12 legal boundary");
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    "pass", record.required("result"));
            assertEquals(EvidenceFiles.sha256(input), record.required("input-sha256"));
        }
    }

    @Test
    public void all174FrozenRulesHaveEffectiveAssignedControlsOnTheOriginalSixFamilyPdf() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot")).toAbsolutePath();
        Path input = root.resolve("capabilities/profiles/T12-standards/fixtures/valid.pdf");
        Set<String> covered = new TreeSet<String>();
        for (String group : new String[] {"pdfcpu", "arlington-core", "arlington-annotations"}) {
            Path output = temporary.getRoot().toPath().resolve(group);
            Path pin = root.resolve("pdfcpu".equals(group) ? "scripts/pdfcpu-pin.properties"
                    : System.getProperty("folio.t12.arlingtonPin", "scripts/t12-arlington-pin.properties"));
            Path profile = root.resolve("capabilities/profiles/T12-standards/" + group + ".properties");
            StandardsEvidenceCommand.main(new String[] {output.toString(), pin.toString(), profile.toString(), input.toString()});
            PinProperties result = PinProperties.load(output.resolve("standards.properties"), "T12 " + group);
            assertEquals(new String(Files.readAllBytes(output.resolve("findings.txt")), StandardCharsets.UTF_8),
                    "pass", result.required("result"));
            assertEquals(EvidenceFiles.sha256(input), result.required("input-sha256"));
            for (String rule : result.required("covered-rules").split(",")) {
                assertTrue("One assigned checker per rule: " + rule, covered.add(rule));
            }
        }
        Set<String> required = new TreeSet<String>(Files.readAllLines(
                root.resolve("capabilities/profiles/T12-standards/required-rules.txt"), StandardCharsets.US_ASCII));
        assertEquals(174, required.size());
        assertEquals(required, covered);
    }
}

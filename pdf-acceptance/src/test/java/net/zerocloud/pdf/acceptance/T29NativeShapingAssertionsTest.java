package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Exact source GIDs and input ranges are observed at the real external Provider boundary. */
public final class T29NativeShapingAssertionsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void everyFrozenRunMatchesTheRealNativeProvider() throws Exception {
        T29NativeShapingAssertions.Observation observed = T29NativeShapingAssertions.inspect(
                Paths.get(System.getProperty("folio.harfBuzzHelper")), temporary.newFolder().toPath());
        assertEquals(observed.findings, EvidenceResult.PASS, observed.result);
        for (String profile : new String[] {"arabic", "hebrew", "devanagari", "thai"}) {
            assertTrue(observed.findings.contains(profile + " line=6 run=1"));
        }
    }

    @Test
    public void missingEngineIsIndeterminateAndDoesNotInventNativeEvidence() throws Exception {
        Path staging = temporary.newFolder().toPath();
        T29NativeShapingAssertions.Observation observed = T29NativeShapingAssertions.inspect(
                temporary.getRoot().toPath().resolve("missing-engine"), staging);
        assertEquals(EvidenceResult.INDETERMINATE, observed.result);
        assertTrue(observed.findings.contains("PROVIDER_UNAVAILABLE"));
        assertEquals(0, staging.toFile().list().length);
    }
}

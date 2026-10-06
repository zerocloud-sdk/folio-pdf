package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public final class T21ContractTestCommandTest {
    @Test public void missingOrDuplicateDeclaredCasesCannotPass() throws Exception {
        assertEquals(0, run(Passing.class, Passing.class));
        assertEquals(1, run(Passing.class, Different.class));
        assertEquals(1, run(Passing.class, Passing.class, Passing.class));
    }

    @Test public void skippedIgnoredOrFailedMandatoryCasesCannotPass() throws Exception {
        assertEquals(1, run(Skipped.class, Skipped.class));
        assertEquals(1, run(Ignored.class, Ignored.class));
        assertEquals(1, run(Failing.class, Failing.class));
    }

    @Test public void actualNamedCaseAndCompleteCountsAreRetained() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, "UTF-8")) {
            assertEquals(0, T21ContractTestCommand.run(output, required(Passing.class), Passing.class));
        }
        assertTrue(bytes.toString("UTF-8").contains("T21 CASE " + Passing.class.getName() + "#caseOne = PASS"));
        assertTrue(bytes.toString("UTF-8").contains("failures=0, ignored=0, assumptions=0, duplicates=0"));
    }

    private static Set<String> required(Class<?> type) { return Collections.singleton(type.getName() + "#caseOne"); }
    private static int run(Class<?> expected, Class<?>... actual) throws Exception {
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8")) {
            return T21ContractTestCommand.run(output, required(expected), actual);
        }
    }

    public static final class Passing { @Test public void caseOne() { } }
    public static final class Different { @Test public void anotherCase() { } }
    public static final class Skipped { @Test public void caseOne() { Assume.assumeTrue(false); } }
    public static final class Ignored { @Ignore("Collector negative control") @Test public void caseOne() { } }
    public static final class Failing { @Test public void caseOne() { throw new AssertionError("Required negative control"); } }
}

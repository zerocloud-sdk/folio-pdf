package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public final class T20ContractTestCommandTest {

    @Test
    public void passingCountMustMatchTheRequiredSuite() throws Exception {
        assertEquals(0, run(1, Passing.class));
        assertEquals(1, run(2, Passing.class));
    }

    @Test
    public void assumptionSkipCannotCertifyAPassingJUnitCount() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, "UTF-8")) {
            assertEquals(1, T20ContractTestCommand.run(output, 1, AssumptionSkip.class));
        }
        String transcript = bytes.toString("UTF-8");
        assertTrue(transcript.contains("OK (1 test)"));
        assertTrue(transcript.contains("assumptions=1"));
    }

    @Test
    public void ignoredTestsCannotBeHiddenBehindTheExpectedRunCount() throws Exception {
        assertEquals(1, run(1, Passing.class, Ignored.class));
    }

    private static int run(int expected, Class<?>... classes) throws Exception {
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8")) {
            return T20ContractTestCommand.run(output, expected, classes);
        }
    }

    public static final class Passing {
        @Test
        public void passes() {
        }
    }

    public static final class AssumptionSkip {
        @Test
        public void skips() {
            Assume.assumeTrue(false);
        }
    }

    public static final class Ignored {
        @Ignore("Negative control for certification; never part of the required suite")
        @Test
        public void ignored() {
        }
    }
}

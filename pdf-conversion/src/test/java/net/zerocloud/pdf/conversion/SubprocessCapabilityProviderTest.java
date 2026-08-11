package net.zerocloud.pdf.conversion;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SubprocessCapabilityProviderTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void boundedSubprocessExchangesPayloadAndCleansStaging()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("staging").toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 128L, Duration.ofSeconds(2L)),
                        helperCommand("echo"),
                        stagingRoot);
        ProviderRequest request = ProviderRequest.builder(
                        "conversion.test.subprocess",
                        new byte[] {10, 20, 30})
                .timeout(Duration.ofSeconds(1L))
                .build();

        ProviderResult result = provider.execute(request);

        assertArrayEquals(new byte[] {10, 20, 30}, result.getOutput());
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void inputLimitFailsBeforeSubprocessOrStagingStarts()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("input-limit-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(2L, 128L, Duration.ofSeconds(2L)),
                        helperCommand("echo"),
                        stagingRoot);

        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.subprocess",
                            new byte[] {1, 2, 3})
                    .timeout(Duration.ofSeconds(1L))
                    .build());
            fail("Expected the input byte limit to fail");
        } catch (ProviderFailure failure) {
            assertEquals(ProviderFailureCode.INPUT_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void outputLimitReturnsNoOversizedResultAndCleansStaging()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("output-limit-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 8L, Duration.ofSeconds(2L)),
                        helperCommand("oversized-output"),
                        stagingRoot);
        long started = System.nanoTime();

        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.subprocess",
                            new byte[] {1})
                    .timeout(Duration.ofSeconds(1L))
                    .build());
            fail("Expected the output byte limit to fail");
        } catch (ProviderFailure failure) {
            assertEquals(ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED,
                    failure.getCode());
        }
        long elapsedMillis = Duration.ofNanos(
                System.nanoTime() - started).toMillis();
        assertFalse("output-limit termination took " + elapsedMillis + " ms",
                elapsedMillis >= 1500L);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void deadlineTerminatesSubprocessAndCleansStaging()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("deadline-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 128L, Duration.ofSeconds(2L)),
                        helperCommand("sleep"),
                        stagingRoot);
        long started = System.nanoTime();

        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.subprocess",
                            new byte[] {1})
                    .timeout(Duration.ofMillis(100L))
                    .build());
            fail("Expected the subprocess deadline to expire");
        } catch (ProviderFailure failure) {
            assertEquals(ProviderFailureCode.DEADLINE_EXCEEDED,
                    failure.getCode());
        }
        long elapsedMillis = Duration.ofNanos(
                System.nanoTime() - started).toMillis();
        assertFalse("deadline termination took " + elapsedMillis + " ms",
                elapsedMillis >= 1500L);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void deadlineBoundsIoWhenExitedProviderLeavesInheritedPipeOpen()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("inherited-pipe-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 128L, Duration.ofSeconds(2L)),
                        helperCommand("exit-with-inherited-stdout"),
                        stagingRoot);
        long started = System.nanoTime();

        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.subprocess",
                            new byte[] {1})
                    .timeout(Duration.ofMillis(500L))
                    .build());
            fail("Expected inherited-pipe I/O to remain deadline bounded");
        } catch (ProviderFailure failure) {
            assertEquals(ProviderFailureCode.DEADLINE_EXCEEDED,
                    failure.getCode());
        }
        long elapsedMillis = Duration.ofNanos(
                System.nanoTime() - started).toMillis();
        assertFalse("inherited-pipe deadline took " + elapsedMillis + " ms",
                elapsedMillis >= 1500L);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void startupFailureIsNormalizedAndCleansStaging()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("startup-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 128L, Duration.ofSeconds(2L)),
                        Collections.singletonList(
                                stagingRoot.resolve("missing-engine").toString()),
                        stagingRoot);

        assertFailure(provider, ProviderFailureCode.STARTUP_FAILED);

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    @Test
    public void nonzeroExitAndCrashAreNormalizedAndCleanStaging()
            throws Exception {
        String[] modes = new String[] {"nonzero", "crash"};
        for (String mode : modes) {
            Path stagingRoot = temporaryFolder.newFolder(mode + "-staging")
                    .toPath();
            SubprocessCapabilityProvider provider =
                    new SubprocessCapabilityProvider(
                            metadata(64L, 128L, Duration.ofSeconds(2L)),
                            helperCommand(mode),
                            stagingRoot);

            assertFailure(provider, ProviderFailureCode.EXECUTION_FAILED);

            try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
                assertFalse(entries.iterator().hasNext());
            }
        }
    }

    @Test
    public void malformedOutputIsNormalizedAndCleansStaging()
            throws Exception {
        Path stagingRoot = temporaryFolder.newFolder("malformed-staging")
                .toPath();
        SubprocessCapabilityProvider provider =
                new SubprocessCapabilityProvider(
                        metadata(64L, 128L, Duration.ofSeconds(2L)),
                        helperCommand("malformed"),
                        stagingRoot);

        assertFailure(provider, ProviderFailureCode.MALFORMED_OUTPUT);

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(stagingRoot)) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    private static ProviderMetadata metadata(
            long maximumInputBytes,
            long maximumOutputBytes,
            Duration maximumDuration) {
        return ProviderMetadata.builder("test.subprocess", "fixture-1")
                .capability("conversion.test.subprocess")
                .executionMode(ProviderExecutionMode.SUBPROCESS)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        maximumInputBytes,
                        maximumOutputBytes,
                        maximumDuration))
                .engineLicense("LicenseRef-Test", "Project test fixture")
                .distribution(ProviderDistribution.CALLER_SUPPLIED)
                .build();
    }

    private static void assertFailure(
            SubprocessCapabilityProvider provider,
            ProviderFailureCode expectedCode) throws Exception {
        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.subprocess",
                            new byte[] {1})
                    .timeout(Duration.ofSeconds(1L))
                    .build());
            fail("Expected Provider failure " + expectedCode);
        } catch (ProviderFailure failure) {
            assertEquals(expectedCode, failure.getCode());
            assertEquals(expectedCode == ProviderFailureCode.STARTUP_FAILED
                            ? "The Provider engine could not be started."
                            : expectedCode == ProviderFailureCode.MALFORMED_OUTPUT
                                    ? "The Provider engine returned malformed output."
                                    : "The Provider engine did not complete successfully.",
                    failure.getDiagnostic());
            assertEquals(null, failure.getCause());
        }
    }

    private static List<String> helperCommand(String mode) {
        List<String> command = new ArrayList<String>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ProviderProcessFixture.class.getName());
        command.add(mode);
        return command;
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name")
                .toLowerCase()
                .contains("win") ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", executable);
    }
}

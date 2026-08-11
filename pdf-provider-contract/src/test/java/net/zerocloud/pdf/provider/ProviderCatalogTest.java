package net.zerocloud.pdf.provider;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class ProviderCatalogTest {

    @Test
    public void deterministicProviderSelectionExecutesAndReportsMetadata()
            throws Exception {
        ProviderLimits limits = ProviderLimits.bounded(
                64L,
                128L,
                Duration.ofSeconds(2L));
        ProviderMetadata metadata = ProviderMetadata.builder(
                        "test.echo",
                        "1.2.3")
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.IN_PROCESS)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(limits)
                .engineLicense("Apache-2.0", "Apache License 2.0")
                .distribution(ProviderDistribution.SEPARATELY_INSTALLED)
                .build();
        ProviderMetadata laterMetadata = ProviderMetadata.builder(
                        "test.echo-later",
                        "9.9.9")
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.IN_PROCESS)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(limits)
                .engineLicense("Apache-2.0", "Apache License 2.0")
                .distribution(ProviderDistribution.CALLER_SUPPLIED)
                .build();
        ProviderCatalog catalog = ProviderCatalog.of(
                Arrays.asList(
                        new EchoProvider(metadata),
                        new EchoProvider(laterMetadata)));
        ProviderRequest request = ProviderRequest.builder(
                        "conversion.test.echo",
                        new byte[] {1, 2, 3})
                .timeout(Duration.ofSeconds(1L))
                .build();
        ProviderPreference preference = ProviderPreference.any(
                "conversion.test.echo");

        ProviderExecution first = catalog.execute(request, preference);
        ProviderExecution repeated = catalog.execute(request, preference);

        assertArrayEquals(new byte[] {1, 2, 3}, first.getResult().getOutput());
        assertEquals("test.echo", first.getSelection().getProviderId());
        assertEquals("test.echo", repeated.getSelection().getProviderId());
        assertEquals("1.2.3", first.getSelection().getMetadata().getEngineVersion());
        assertEquals(ProviderExecutionMode.IN_PROCESS,
                first.getSelection().getMetadata().getExecutionMode());
        assertEquals(ProviderAvailability.AVAILABLE,
                first.getSelection().getMetadata().getAvailability());
        assertEquals(limits, first.getSelection().getMetadata().getLimits());
        assertEquals("Apache-2.0",
                first.getSelection().getMetadata().getEngineLicenseSpdxIdentifier());
        assertEquals("Apache License 2.0",
                first.getSelection().getMetadata().getEngineLicenseName());
        assertEquals(ProviderDistribution.SEPARATELY_INSTALLED,
                first.getSelection().getMetadata().getDistribution());
    }

    @Test
    public void remoteExecutionRequiresExplicitDisclosureAuthorization()
            throws Exception {
        ProviderMetadata metadata = ProviderMetadata.builder(
                        "test.remote",
                        "4.0.0")
                .capability("conversion.test.remote")
                .executionMode(ProviderExecutionMode.REMOTE)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("LicenseRef-Test", "Project test license")
                .distribution(ProviderDistribution.REMOTE_SERVICE)
                .build();
        CountingRemoteProvider remote = new CountingRemoteProvider(metadata);
        ProviderCatalog catalog = ProviderCatalog.of(Arrays.asList(remote));
        ProviderPreference preference = ProviderPreference.prefer(
                "conversion.test.remote",
                "test.remote");

        try {
            catalog.execute(
                    ProviderRequest.builder(
                                    "conversion.test.remote",
                                    new byte[] {7})
                            .timeout(Duration.ofSeconds(1L))
                            .build(),
                    preference);
            fail("Expected remote disclosure refusal");
        } catch (ProviderFailure failure) {
            assertEquals(
                    ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                    failure.getCode());
            assertEquals(
                    "Remote document disclosure was not explicitly authorized.",
                    failure.getDiagnostic());
        }
        assertEquals(0, remote.invocations.get());

        ProviderExecution authorized = catalog.execute(
                ProviderRequest.builder(
                                "conversion.test.remote",
                                new byte[] {8})
                        .timeout(Duration.ofSeconds(1L))
                        .authorizeRemoteDisclosure()
                        .build(),
                preference);

        assertArrayEquals(new byte[] {8}, authorized.getResult().getOutput());
        assertEquals(1, remote.invocations.get());
    }

    @Test
    public void providerRequestAndResultDetachMutableByteArrays() {
        byte[] callerInput = new byte[] {3, 4};
        ProviderRequest request = ProviderRequest.builder(
                        "conversion.test.echo",
                        callerInput)
                .timeout(Duration.ofSeconds(1L))
                .build();
        callerInput[0] = 99;
        byte[] exposedInput = request.getInput();
        exposedInput[1] = 99;

        byte[] providerOutput = new byte[] {5, 6};
        ProviderResult result = ProviderResult.of(providerOutput);
        providerOutput[0] = 99;
        byte[] exposedOutput = result.getOutput();
        exposedOutput[1] = 99;

        assertArrayEquals(new byte[] {3, 4}, request.getInput());
        assertArrayEquals(new byte[] {5, 6}, result.getOutput());
    }

    @Test
    public void providerExecutionModesRepresentEverySupportedEngineLocation() {
        assertArrayEquals(
                new ProviderExecutionMode[] {
                    ProviderExecutionMode.IN_PROCESS,
                    ProviderExecutionMode.NATIVE,
                    ProviderExecutionMode.SUBPROCESS,
                    ProviderExecutionMode.REMOTE
                },
                ProviderExecutionMode.values());
    }

    @Test
    public void metadataRejectsExecutionAndDistributionThatDisagreeAboutRemoteness() {
        try {
            metadataWith(
                    ProviderExecutionMode.IN_PROCESS,
                    ProviderDistribution.REMOTE_SERVICE);
            fail("Expected remote-service distribution mismatch");
        } catch (IllegalStateException expected) {
            // The engine cannot be both in the caller process and remote.
        }

        try {
            metadataWith(
                    ProviderExecutionMode.REMOTE,
                    ProviderDistribution.CALLER_SUPPLIED);
            fail("Expected remote execution mismatch");
        } catch (IllegalStateException expected) {
            // A remote engine must be reported as a remote service.
        }
    }

    @Test
    public void providerFailuresCannotRetainRawCauses() {
        ProviderFailure failure = ProviderFailure.forProvider(
                ProviderFailureCode.EXECUTION_FAILED,
                "test.failure",
                "conversion.test.failure");

        assertEquals(null, failure.getCause());
        try {
            failure.initCause(new IllegalArgumentException("engine detail"));
            fail("Expected raw cause injection to be rejected");
        } catch (IllegalStateException expected) {
            // The exception is constructed with cause initialization disabled.
        }
        failure.addSuppressed(new IllegalStateException("transport detail"));
        assertEquals(null, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void providerBoundaryRebuildsAdapterFailuresWithRegisteredIdentity()
            throws Exception {
        CapabilityProvider provider = new ForgingFailureProvider(metadataWith(
                ProviderExecutionMode.IN_PROCESS,
                ProviderDistribution.CALLER_SUPPLIED));

        try {
            provider.execute(ProviderRequest.builder(
                            "conversion.test.metadata",
                            new byte[] {1})
                    .timeout(Duration.ofSeconds(1L))
                    .build());
            fail("Expected adapter failure");
        } catch (ProviderFailure failure) {
            assertEquals(ProviderFailureCode.EXECUTION_FAILED, failure.getCode());
            assertEquals("test.metadata", failure.getProviderId());
            assertEquals("conversion.test.metadata", failure.getCapabilityId());
            assertEquals(null, failure.getCause());
            assertEquals(0, failure.getSuppressed().length);
        }
    }

    private static ProviderMetadata metadataWith(
            ProviderExecutionMode executionMode,
            ProviderDistribution distribution) {
        return ProviderMetadata.builder("test.metadata", "1.0.0")
                .capability("conversion.test.metadata")
                .executionMode(executionMode)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("LicenseRef-Test", "Project test license")
                .distribution(distribution)
                .build();
    }

    private static final class EchoProvider extends CapabilityProvider {

        private EchoProvider(ProviderMetadata metadata) {
            super(metadata);
        }

        @Override
        protected ProviderResult perform(ProviderRequest request)
                throws ProviderFailure {
            return ProviderResult.of(request.getInput());
        }
    }

    private static final class CountingRemoteProvider extends CapabilityProvider {

        private final AtomicInteger invocations = new AtomicInteger();

        private CountingRemoteProvider(ProviderMetadata metadata) {
            super(metadata);
        }

        @Override
        protected ProviderResult perform(ProviderRequest request)
                throws ProviderFailure {
            invocations.incrementAndGet();
            return ProviderResult.of(request.getInput());
        }
    }

    private static final class ForgingFailureProvider extends CapabilityProvider {

        private ForgingFailureProvider(ProviderMetadata metadata) {
            super(metadata);
        }

        @Override
        protected ProviderResult perform(ProviderRequest request)
                throws ProviderFailure {
            ProviderFailure failure = ProviderFailure.forProvider(
                    ProviderFailureCode.EXECUTION_FAILED,
                    "forged.provider",
                    request.getCapabilityId());
            failure.addSuppressed(new IllegalStateException("engine detail"));
            throw failure;
        }
    }
}

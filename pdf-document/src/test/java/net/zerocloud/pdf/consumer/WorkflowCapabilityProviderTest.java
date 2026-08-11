package net.zerocloud.pdf.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowCapabilityProviderTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void workflowSelectsExplicitProviderPreferenceAndReportsMetadata()
            throws Exception {
        CountingProvider first = provider("test.first", "1.0.0");
        CountingProvider preferred = provider("test.preferred", "2.4.0");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(first)
                .provider(preferred)
                .build();
        WorkflowRequest request = WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(
                        "conversion.test.echo",
                        "test.preferred"))
                .target(
                        "output",
                        PublicationTarget.path(
                                temporaryFolder.newFile("provider.pdf").toPath()))
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(1, outcome.getProviderSelections().size());
        assertEquals("test.preferred",
                outcome.getProviderSelections().get(0).getProviderId());
        assertEquals("2.4.0",
                outcome.getProviderSelections().get(0).getMetadata().getEngineVersion());
        assertEquals(ProviderExecutionMode.IN_PROCESS,
                outcome.getProviderSelections().get(0).getMetadata().getExecutionMode());
        assertEquals(ProviderAvailability.AVAILABLE,
                outcome.getProviderSelections().get(0).getMetadata().getAvailability());
        assertEquals(128L,
                outcome.getProviderSelections().get(0).getMetadata()
                        .getLimits().getMaximumOutputBytes());
        assertEquals(0, first.invocations.get());
        assertEquals(0, preferred.invocations.get());
    }

    @Test
    public void rejectedDuplicateProviderPreferencePreservesOriginalSelection()
            throws Exception {
        CountingProvider first = provider("test.first", "1.0.0");
        CountingProvider replacement = provider("test.replacement", "2.0.0");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(first)
                .provider(replacement)
                .build();
        WorkflowRequest.Builder request = WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(
                        "conversion.test.echo",
                        "test.first"));

        try {
            request.providerPreference(ProviderPreference.prefer(
                    "conversion.test.echo",
                    "test.replacement"));
            fail("Expected duplicate Provider preference rejection");
        } catch (IllegalArgumentException expected) {
            // A rejected declaration must not mutate the reusable builder.
        }

        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(
                request.target(
                                "output",
                                PublicationTarget.path(temporaryFolder
                                        .newFile("duplicate-preference.pdf")
                                        .toPath()))
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals("test.first",
                outcome.getProviderSelections().get(0).getProviderId());
    }

    @Test
    public void environmentExposesImmutableProviderMetadataOnly() {
        CountingProvider provider = provider("test.discoverable", "5.0.0");
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(provider)
                .build();

        assertEquals(1, environment.getProviderMetadata().size());
        assertEquals("test.discoverable",
                environment.getProviderMetadata().get(0).getProviderId());
        assertEquals("5.0.0",
                environment.getProviderMetadata().get(0).getEngineVersion());
        try {
            environment.getProviderMetadata().clear();
            fail("Expected immutable Provider metadata discovery");
        } catch (UnsupportedOperationException expected) {
            // The environment never exposes its executable Provider instances.
        }
        assertEquals(1, environment.getProviderMetadata().size());
    }

    @Test
    public void offlineSystemDefaultsHaveNoProviderRegistrationOrSelection()
            throws Exception {
        WorkflowEnvironment environment = WorkflowEnvironment.systemDefaults();
        WorkflowRequest request = WorkflowRequest.builder()
                .target(
                        "output",
                        PublicationTarget.path(
                                temporaryFolder.newFile("offline.pdf").toPath()))
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(0, environment.getProviderMetadata().size());
        assertEquals(0, outcome.getProviderSelections().size());
    }

    @Test
    public void unavailablePreferredProviderProducesStableFailure()
            throws Exception {
        CountingProvider unavailable = new CountingProvider(ProviderMetadata.builder(
                        "test.unavailable",
                        "6.0.0")
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.NATIVE)
                .availability(ProviderAvailability.UNAVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("LicenseRef-Test", "Project test license")
                .distribution(ProviderDistribution.SEPARATELY_INSTALLED)
                .build());
        WorkflowRequest request = WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(
                        "conversion.test.echo",
                        "test.unavailable"))
                .target(
                        "output",
                        PublicationTarget.path(
                                temporaryFolder.newFile("unavailable.pdf").toPath()))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow(WorkflowEnvironment.builder()
                    .provider(unavailable)
                    .build()).execute(request, session -> null);
            fail("Expected unavailable Provider failure");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE,
                    failure.getCode());
            assertEquals("The selected Capability Provider is unavailable.",
                    failure.getDiagnostic());
        }
        assertEquals(0, unavailable.invocations.get());
    }

    @Test
    public void workflowRefusesUnauthorizedRemoteDisclosureBeforeInvocation()
            throws Exception {
        CountingProvider remote = new CountingProvider(ProviderMetadata.builder(
                        "test.remote",
                        "3.0.0")
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.REMOTE)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("LicenseRef-Test", "Project test license")
                .distribution(ProviderDistribution.REMOTE_SERVICE)
                .build());
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(remote)
                .build();
        WorkflowRequest request = WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(
                        "conversion.test.echo",
                        "test.remote"))
                .target(
                        "output",
                        PublicationTarget.path(
                                temporaryFolder.newFile("remote.pdf").toPath()))
                .saveMode(SaveMode.REWRITE)
                .build();

        try {
            new DocumentWorkflow(environment).execute(request, session -> null);
            fail("Expected unauthorized remote disclosure to fail");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED,
                    failure.getCode());
            assertEquals("conversion.test.echo", failure.getCapabilityId());
            assertEquals(
                    "Remote document disclosure was not explicitly authorized.",
                    failure.getDiagnostic());
            assertEquals(1, failure.getPublicationReceipts().size());
            assertEquals(PublicationStatus.NOT_ATTEMPTED,
                    failure.getPublicationReceipts().get(0).getStatus());
        }
        assertEquals(0, remote.invocations.get());
    }

    @Test
    public void workflowSelectsRemoteProviderOnlyWithCapabilityAuthorization()
            throws Exception {
        CountingProvider remote = new CountingProvider(ProviderMetadata.builder(
                        "test.authorized-remote",
                        "3.1.0")
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.REMOTE)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("LicenseRef-Test", "Project test license")
                .distribution(ProviderDistribution.REMOTE_SERVICE)
                .build());
        WorkflowEnvironment environment = WorkflowEnvironment.builder()
                .provider(remote)
                .build();
        WorkflowRequest request = WorkflowRequest.builder()
                .providerPreference(ProviderPreference.prefer(
                        "conversion.test.echo",
                        "test.authorized-remote"))
                .authorizeRemoteDisclosure("conversion.test.echo")
                .target(
                        "output",
                        PublicationTarget.path(
                                temporaryFolder.newFile("authorized-remote.pdf")
                                        .toPath()))
                .saveMode(SaveMode.REWRITE)
                .build();

        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(
                request,
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    return null;
                });

        assertEquals(1, outcome.getProviderSelections().size());
        assertEquals("test.authorized-remote",
                outcome.getProviderSelections().get(0).getProviderId());
        assertEquals(ProviderExecutionMode.REMOTE,
                outcome.getProviderSelections().get(0).getMetadata().getExecutionMode());
        assertEquals(0, remote.invocations.get());
    }

    private static CountingProvider provider(String providerId, String version) {
        return new CountingProvider(ProviderMetadata.builder(providerId, version)
                .capability("conversion.test.echo")
                .executionMode(ProviderExecutionMode.IN_PROCESS)
                .availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(
                        64L,
                        128L,
                        Duration.ofSeconds(2L)))
                .engineLicense("Apache-2.0", "Apache License 2.0")
                .distribution(ProviderDistribution.CALLER_SUPPLIED)
                .build());
    }

    private static final class CountingProvider extends CapabilityProvider {

        private final AtomicInteger invocations = new AtomicInteger();

        private CountingProvider(ProviderMetadata metadata) {
            super(metadata);
        }

        @Override
        protected ProviderResult perform(ProviderRequest request)
                throws ProviderFailure {
            invocations.incrementAndGet();
            return ProviderResult.of(request.getInput());
        }
    }
}

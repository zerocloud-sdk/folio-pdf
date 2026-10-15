package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.provider.CapabilityProvider;
import net.zerocloud.pdf.provider.ProviderAvailability;
import net.zerocloud.pdf.provider.ProviderCatalog;
import net.zerocloud.pdf.provider.ProviderDistribution;
import net.zerocloud.pdf.provider.ProviderExecution;
import net.zerocloud.pdf.provider.ProviderExecutionMode;
import net.zerocloud.pdf.provider.ProviderFailure;
import net.zerocloud.pdf.provider.ProviderFailureCode;
import net.zerocloud.pdf.provider.ProviderLimits;
import net.zerocloud.pdf.provider.ProviderMetadata;
import net.zerocloud.pdf.provider.ProviderPreference;
import net.zerocloud.pdf.provider.ProviderRequest;
import net.zerocloud.pdf.provider.ProviderResult;

/** Acceptance-only public Provider, loopback and process observations for issue #84. */
final class T05ProviderContracts {
    static final String CAPABILITY = "conversion.test.t05";
    static final byte[] PAYLOAD = {10, 20, 30, 40};
    static final byte[] RESPONSE = {40, 30, 20, 10};
    static final String[] PRODUCTS = {"native-offline", "native-registration", "native-first", "native-repeat",
            "native-exact", "native-remote-selection", "native-remote-execution", "native-stream", "native-before-failure",
            "facade-stream", "facade-before-failure", "preview/facade-stream", "preview/facade-before-failure"};
    private final Path output;
    private final WorkflowExecutionProfile execution;
    private final RetainedEvidence retained;
    private final Properties observed = new Properties();
    private final Set<String> cases = new TreeSet<String>();

    private T05ProviderContracts(Path output, WorkflowExecutionProfile execution) {
        this.output = output; this.execution = execution; this.retained = new RetainedEvidence(output);
    }

    static RetainedEvidence record(Path output, WorkflowExecutionProfile execution) throws Exception {
        T05ProviderContracts recorder = new T05ProviderContracts(output, execution);
        recorder.workflowAndEndpoint(); recorder.providerValues(); recorder.subprocesses(); recorder.publication();
        recorder.retained.include(T20FacadeContracts.record(output));
        recorder.observed.setProperty("execution-profile", execution.name());
        recorder.observed.setProperty("case-count", Integer.toString(recorder.cases.size()));
        recorder.retained.write(output.resolve("observations.properties"), recorder.observed);
        try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
            for (Path path : (Iterable<Path>) paths.filter(Files::isRegularFile)::iterator) {
                recorder.retained.retain(path, EvidenceFiles.sha256(path));
            }
        }
        recorder.retained.verify();
        return recorder.retained;
    }

    private void workflowAndEndpoint() throws Exception {
        check(WorkflowEnvironment.systemDefaults().getProviderMetadata().isEmpty(), "offline metadata");
        WorkflowOutcome<Void> offline = create("native-offline", WorkflowEnvironment.systemDefaults(), null, null, null);
        check(offline.getProviderSelections().isEmpty(), "offline selection");
        observe("offline", null, "metadata-count", "0", "selection-count", "0");
        Echo first = new Echo(metadata("test.t05-first", ProviderExecutionMode.IN_PROCESS, ProviderAvailability.AVAILABLE, 4, 4), "echo");
        Echo later = new Echo(metadata("test.t05-later", ProviderExecutionMode.IN_PROCESS, ProviderAvailability.AVAILABLE, 4, 4), "echo");
        Echo unavailable = new Echo(metadata("test.t05-unavailable", ProviderExecutionMode.NATIVE, ProviderAvailability.UNAVAILABLE, 4, 4), "echo");
        try (Endpoint endpoint = new Endpoint(output)) {
            Remote remote = new Remote(metadata("test.t05-remote", ProviderExecutionMode.REMOTE, ProviderAvailability.AVAILABLE, 4, 4), endpoint);
            WorkflowEnvironment.Builder builder = WorkflowEnvironment.builder().provider(unavailable).provider(remote).provider(first).provider(later);
            WorkflowEnvironment environment = builder.build();
            builder.provider(new Echo(metadata("test.t05-after", ProviderExecutionMode.IN_PROCESS, ProviderAvailability.AVAILABLE, 4, 4), "echo"));
            check(environment.getProviderMetadata().size() == 4, "environment snapshot");
            rejects(() -> environment.getProviderMetadata().clear(), UnsupportedOperationException.class);
            rejects(() -> first.getMetadata().getCapabilityIds().clear(), UnsupportedOperationException.class);
            recordMetadata(environment.getProviderMetadata());
            observe("immutable-registration", null, "metadata-count", "4", "immutable", "true");
            WorkflowOutcome<Void> registered = create("native-registration", environment, null, null, null);
            check(registered.getProviderSelections().isEmpty() && endpoint.requests() == 0, "registration disclosure");
            observe("registration-only", null, "requests", "0", "disclosed-bytes", "0", "selection-count", "0");
            WorkflowOutcome<Void> selected = create("native-first", environment, ProviderPreference.any(CAPABILITY), null, null);
            selection(selected, "test.t05-first");
            observe("declaration-order", null, "selected", "test.t05-first", "invocations", "0");
            selection(create("native-repeat", environment, ProviderPreference.any(CAPABILITY), null, null), "test.t05-first");
            observe("repeated-selection", null, "selected", "test.t05-first", "invocations", "0");
            selection(create("native-exact", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-later"), null, null), "test.t05-later");
            observe("exact-preference", null, "selected", "test.t05-later", "invocations", "0");
            workflowFailure("missing-preference", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-missing"), null,
                    DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND);
            workflowFailure("wrong-capability", environment, ProviderPreference.prefer("conversion.test.other", "test.t05-first"), null,
                    DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND);
            workflowFailure("unavailable-preference", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-unavailable"), null,
                    DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE);
            workflowFailure("remote-preference-denied", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-remote"), null,
                    DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED);
            workflowFailure("remote-wrong-scope", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-remote"), "conversion.test.other",
                    DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED);
            check(endpoint.requests() == 0 && remote.invocations == 0, "unauthorized endpoint requests");
            observe("remote-zero-disclosure", null, "requests", "0", "disclosed-bytes", "0");
            selection(create("native-remote-selection", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-remote"), CAPABILITY, null), "test.t05-remote");
            check(endpoint.requests() == 0, "selection invoked remote adapter");
            observe("authorized-selection-only", null, "requests", "0", "disclosed-bytes", "0", "selected", "test.t05-remote");
            providerFailure("remote-direct-denied", remote, request(PAYLOAD, false), ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED);
            check(endpoint.requests() == 0, "direct authorization missing");
            ProviderCatalog catalog = ProviderCatalog.of(Arrays.asList(remote));
            ProviderExecution transferred = catalog.execute(request(PAYLOAD, true), ProviderPreference.prefer(CAPABILITY, "test.t05-remote"));
            check(Arrays.equals(RESPONSE, transferred.getResult().getOutput()), "remote response identity");
            check(endpoint.requests() == 1 && Arrays.equals(PAYLOAD, endpoint.payloads.get(0)), "authorized request identity");
            observe("remote-exact-transfer", null, "requests", "1", "disclosed-bytes", "4", "input-sha256", EvidenceFiles.sha256(PAYLOAD), "output-sha256", EvidenceFiles.sha256(RESPONSE));
            providerFailure("remote-no-inherited-consent", remote, request(PAYLOAD, false), ProviderFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED);
            check(endpoint.requests() == 1, "consent leaked between requests");
            workflowFailure("workflow-no-inherited-consent", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-remote"), null,
                    DocumentFailureCode.REMOTE_DISCLOSURE_NOT_AUTHORIZED);
            final String parentPid = pid(); final long parentThread = Thread.currentThread().getId();
            create("native-remote-execution", environment, ProviderPreference.prefer(CAPABILITY, "test.t05-remote"), CAPABILITY, () -> {
                check(parentPid.equals(pid()) && Thread.currentThread().getId() == parentThread, "Provider was not parent-brokered");
                // Generic execution is an explicit public Provider call in caller work.
                // Scoped Workflow selection does not supply direct ProviderRequest consent.
                check(Arrays.equals(RESPONSE, remote.execute(request(PAYLOAD, true)).getOutput()), "workflow remote result");
            });
            check(endpoint.requests() == 2 && remote.invocations == 2 && first.invocations == 0 && later.invocations == 0 && unavailable.invocations == 0,
                    "unexpected adapter invocation");
            observe("parent-brokered-execution", null, "broker", "parent", "requests", "2", "disclosed-bytes", "8", "direct-authorization", "explicit");
            Properties actual = new Properties();
            actual.setProperty("address", endpoint.server.getInetAddress().getHostAddress());
            actual.setProperty("port", Integer.toString(endpoint.server.getLocalPort()));
            actual.setProperty("requests", Integer.toString(endpoint.requests())); actual.setProperty("disclosed-bytes", "8");
            actual.setProperty("adapter-pid", remote.executingPid); actual.setProperty("parent-pid", parentPid);
            actual.setProperty("adapter-java-build", System.getProperty("java.runtime.version"));
            actual.setProperty("protocol", "t05-synthetic-length-framed-v1");
            retained.write(output.resolve("endpoint.properties"), actual);
        }
    }

    private WorkflowOutcome<Void> create(String product, WorkflowEnvironment environment, ProviderPreference preference,
            String authorization, Action adapter) throws Exception {
        Path pdf = Files.createDirectory(output.resolve(product)).resolve("blank.pdf");
        WorkflowRequest.Builder request = base().target("result", PublicationTarget.path(pdf));
        if (preference != null) { request.providerPreference(preference); }
        if (authorization != null) { request.authorizeRemoteDisclosure(authorization); }
        WorkflowOutcome<Void> outcome = new DocumentWorkflow(environment).execute(request.build(), session -> {
            if (adapter != null) {
                try { adapter.run(); } catch (Exception failed) { throw new AssertionError("Acceptance adapter failed", failed); }
            }
            session.execute(AddBlankPage.INSTANCE); return null;
        });
        check(outcome.getExecutionProfile() == execution && receipts(outcome.getPublicationReceipts()).equals("result:COMMITTED:false"), "public outcome profile/receipt");
        check(T03BlankSemantics.inspect(pdf, execution) == EvidenceResult.PASS, "public reopened Provider workflow");
        retained.retain(pdf, EvidenceFiles.sha256(pdf));
        return outcome;
    }

    private void selection(WorkflowOutcome<?> outcome, String provider) throws Exception {
        check(outcome.getProviderSelections().size() == 1 && provider.equals(outcome.getProviderSelections().get(0).getProviderId()), "selection mismatch");
        rejects(() -> outcome.getProviderSelections().clear(), UnsupportedOperationException.class);
    }

    private void workflowFailure(String name, WorkflowEnvironment environment, ProviderPreference preference,
            String authorization, DocumentFailureCode code) throws Exception {
        Path sentinel = output.resolve(name + ".sentinel"); retained.write(sentinel, PAYLOAD);
        WorkflowRequest.Builder request = base().target("result", PublicationTarget.path(sentinel)).providerPreference(preference);
        if (authorization != null) { request.authorizeRemoteDisclosure(authorization); }
        try { new DocumentWorkflow(environment).execute(request.build(), session -> { throw new AssertionError("refused selection invoked work"); });
            throw new AssertionError("missing workflow refusal"); }
        catch (DocumentFailure failure) {
            check(failure.getCode() == code && preference.getCapabilityId().equals(failure.getCapabilityId()) && failure.getCause() == null
                    && failure.getSuppressed().length == 0, "unsafe or wrong Workflow failure");
            String diagnostic = code == DocumentFailureCode.CAPABILITY_PROVIDER_NOT_FOUND ? "No eligible Capability Provider is registered."
                    : code == DocumentFailureCode.CAPABILITY_PROVIDER_UNAVAILABLE ? "The selected Capability Provider is unavailable."
                    : "Remote document disclosure was not explicitly authorized.";
            check(diagnostic.equals(failure.getDiagnostic()) && receipts(failure.getPublicationReceipts()).equals("result:NOT_ATTEMPTED:false")
                    && Arrays.equals(PAYLOAD, Files.readAllBytes(sentinel)), "Workflow receipt/sentinel/diagnostic");
            observe(name, code.name(), "diagnostic", failure.getDiagnostic(), "receipts", receipts(failure.getPublicationReceipts()), "sentinel-sha256", EvidenceFiles.sha256(sentinel));
        }
    }

    private void providerValues() throws Exception {
        Echo echo = new Echo(metadata("test.t05-direct", ProviderExecutionMode.IN_PROCESS, ProviderAvailability.AVAILABLE, 4, 4), "echo");
        byte[] input = PAYLOAD.clone(); ProviderRequest detached = request(input, false); input[0] = 99; detached.getInput()[1] = 99;
        byte[] resultBytes = RESPONSE.clone(); ProviderResult detachedResult = ProviderResult.of(resultBytes); resultBytes[0] = 99; detachedResult.getOutput()[1] = 99;
        check(Arrays.equals(PAYLOAD, detached.getInput()) && Arrays.equals(RESPONSE, detachedResult.getOutput()), "mutable Provider values");
        observe("detached-values", null, "input-sha256", EvidenceFiles.sha256(detached.getInput()), "output-sha256", EvidenceFiles.sha256(detachedResult.getOutput()));
        check(Arrays.equals(PAYLOAD, echo.execute(detached).getOutput()), "exact bound failed");
        observe("exact-byte-bounds", null, "input-length", "4", "output-length", "4");
        providerFailure("input-first-excess", echo, request(new byte[5], false), ProviderFailureCode.INPUT_LIMIT_EXCEEDED);
        check(echo.invocations == 1, "input first excess executed adapter");
        Echo oversize = new Echo(echo.getMetadata(), "oversize");
        providerFailure("output-first-excess", oversize, detached, ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED);
        check(oversize.invocations == 1, "output boundary was not executed");
        ProviderCatalog catalog = ProviderCatalog.of(Arrays.asList(echo));
        rejects(() -> catalog.execute(detached, ProviderPreference.any("conversion.test.other")), IllegalArgumentException.class);
        check(echo.invocations == 1, "preference mismatch executed adapter");
        observe("capability-preference-agreement", "IllegalArgumentException", "invocations", "0");
        rejects(() -> ProviderRequest.builder(CAPABILITY, PAYLOAD).timeout(Duration.ZERO).build(), IllegalArgumentException.class);
        rejects(() -> ProviderRequest.builder(CAPABILITY, PAYLOAD).timeout(Duration.ofMillis(-1)).build(), IllegalArgumentException.class);
        observe("positive-timeout", "IllegalArgumentException", "zero-rejected", "true", "negative-rejected", "true");
        echo.execute(ProviderRequest.builder(CAPABILITY, PAYLOAD).timeout(Duration.ofSeconds(5)).build());
        observe("maximum-timeout", null, "timeout", "PT5S", "accepted", "true");
        rejects(() -> echo.execute(ProviderRequest.builder(CAPABILITY, PAYLOAD).timeout(Duration.ofMillis(5001)).build()), IllegalArgumentException.class);
        check(echo.invocations == 2, "above-maximum timeout executed adapter");
        observe("above-maximum-timeout", "IllegalArgumentException", "invocations", "0");
        providerFailure("direct-wrong-capability", echo, ProviderRequest.builder("conversion.test.other", PAYLOAD).timeout(Duration.ofSeconds(1)).build(), ProviderFailureCode.PROVIDER_NOT_FOUND);
        providerFailure("direct-unavailable", new Echo(metadata("test.t05-unavailable-direct", ProviderExecutionMode.IN_PROCESS, ProviderAvailability.UNAVAILABLE, 4, 4), "echo"),
                detached, ProviderFailureCode.PROVIDER_UNAVAILABLE);
        providerFailure("null-result", new Echo(echo.getMetadata(), "null"), detached, ProviderFailureCode.MALFORMED_OUTPUT);
        providerFailure("runtime-failure", new Echo(echo.getMetadata(), "runtime"), detached, ProviderFailureCode.EXECUTION_FAILED);
        providerFailure("forged-failure", new Echo(echo.getMetadata(), "forged"), detached, ProviderFailureCode.EXECUTION_FAILED);
        rejects(() -> metadataBuilder("test.t05-bad").executionMode(ProviderExecutionMode.REMOTE).distribution(ProviderDistribution.CALLER_SUPPLIED).build(), IllegalStateException.class);
        rejects(() -> metadataBuilder("test.t05-bad").executionMode(ProviderExecutionMode.IN_PROCESS).distribution(ProviderDistribution.REMOTE_SERVICE).build(), IllegalStateException.class);
        observe("consistent-remote-metadata", "IllegalStateException", "both-mismatches-rejected", "true");
        List<CapabilityProvider> registrations = new ArrayList<CapabilityProvider>(); registrations.add(echo);
        ProviderCatalog copied = ProviderCatalog.of(registrations); registrations.clear();
        rejects(() -> copied.getMetadata().clear(), UnsupportedOperationException.class);
        check(copied.getMetadata().size() == 1, "mutable catalog registration");
        observe("immutable-catalog", null, "metadata-count", "1", "immutable", "true");
    }

    private void subprocesses() throws Exception {
        List<String> modes = Arrays.asList("echo", "pre-start-input", "first-excess-output", "wrong-magic", "wrong-version", "negative-length",
                "oversized-length", "truncated-header", "truncated-payload", "trailing-output", "startup", "nonzero", "crash", "stalled-input", "stalled-output", "inherited-pipe");
        Path boundary = Files.createDirectory(output.resolve("subprocess"));
        for (String mode : modes) {
            Path staging = Files.createDirectory(boundary.resolve(mode + "-root"));
            Path caller = staging.resolve("caller-owned.sentinel"); Files.write(caller, PAYLOAD);
            Path actual = boundary.resolve(mode + "-child.properties");
            String fixtureMode = "first-excess-output".equals(mode) ? "echo" : mode;
            List<String> command = new ArrayList<String>(Arrays.asList(Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), T05ProcessFixture.class.getName(), fixtureMode, actual.toString()));
            if ("startup".equals(mode)) { command = Collections.singletonList(staging.resolve("missing-program").toString()); }
            long maxInput = "pre-start-input".equals(mode) ? 3 : "stalled-input".equals(mode) ? 1048576 : 4;
            long maxOutput = "first-excess-output".equals(mode) ? 3 : 4;
            net.zerocloud.pdf.conversion.SubprocessCapabilityProvider provider = new net.zerocloud.pdf.conversion.SubprocessCapabilityProvider(
                    metadata("test.t05-process", ProviderExecutionMode.SUBPROCESS, ProviderAvailability.AVAILABLE, maxInput, maxOutput), command, staging);
            Properties invocation = new Properties(); invocation.setProperty("argument-count", Integer.toString(command.size()));
            for (int index = 0; index < command.size(); index++) { invocation.setProperty("argument." + index, command.get(index)); }
            invocation.setProperty("maximum-input", Long.toString(provider.getMetadata().getLimits().getMaximumInputBytes()));
            invocation.setProperty("maximum-output", Long.toString(provider.getMetadata().getLimits().getMaximumOutputBytes()));
            invocation.setProperty("maximum-duration", provider.getMetadata().getLimits().getMaximumDuration().toString());
            invocation.setProperty("mode", provider.getMetadata().getExecutionMode().name());
            retained.write(boundary.resolve(mode + "-command.properties"), invocation);
            ProviderFailureCode expected = "echo".equals(mode) ? null : "pre-start-input".equals(mode) ? ProviderFailureCode.INPUT_LIMIT_EXCEEDED
                    : "first-excess-output".equals(mode) || "oversized-length".equals(mode) ? ProviderFailureCode.OUTPUT_LIMIT_EXCEEDED
                    : "startup".equals(mode) ? ProviderFailureCode.STARTUP_FAILED
                    : "nonzero".equals(mode) || "crash".equals(mode) ? ProviderFailureCode.EXECUTION_FAILED
                    : mode.startsWith("stalled-") || "inherited-pipe".equals(mode) ? ProviderFailureCode.DEADLINE_EXCEEDED : ProviderFailureCode.MALFORMED_OUTPUT;
            Set<String> before = pipes(); long started = System.nanoTime();
            ProviderRequest input = request("stalled-input".equals(mode) ? new byte[524288] : PAYLOAD, false);
            if (expected == null) {
                check(Arrays.equals(PAYLOAD, provider.execute(input).getOutput()), "framed process exchange");
                observe("process-" + mode, null, "input-length", "4", "output-length", "4");
            } else { providerFailure("process-" + mode, provider, input, expected); }
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            check(elapsed >= 0 && elapsed < 6000, "finite subprocess elapsed bound");
            Properties lifecycle = new Properties(); lifecycle.setProperty("elapsed-millis", Long.toString(elapsed));
            lifecycle.setProperty("elapsed-upper-bound-millis", "6000"); lifecycle.setProperty("timeout", input.getTimeout().toString());
            lifecycle.setProperty("mode", mode); lifecycle.setProperty("result", expected == null ? "pass" : expected.name());
            boolean startedChild = Files.isRegularFile(actual);
            check(startedChild != ("startup".equals(mode) || "pre-start-input".equals(mode)), "pre-start child observation");
            if (startedChild) {
                Properties child = load(actual);
                String processPid = child.getProperty("pid");
                check(terminated(processPid), "direct child remains alive");
                lifecycle.setProperty("direct-child-pid", processPid); lifecycle.setProperty("direct-child-state", "terminated");
                check(!Files.exists(Paths.get(child.getProperty("staging"))), "owned per-run staging remains");
                if (!"stalled-input".equals(mode)) {
                    check(CAPABILITY.equals(child.getProperty("capability")) && "4".equals(child.getProperty("input-length"))
                            && EvidenceFiles.sha256(PAYLOAD).equals(child.getProperty("input-sha256")), "process request frame identity");
                }
            } else { lifecycle.setProperty("direct-child-state", "not-started"); }
            try (DirectoryStream<Path> contents = Files.newDirectoryStream(staging)) {
                List<Path> paths = new ArrayList<Path>(); for (Path path : contents) { paths.add(path); }
                check(paths.equals(Arrays.asList(caller)) && Arrays.equals(PAYLOAD, Files.readAllBytes(caller)), "caller staging ownership");
            }
            Set<String> after = pipes(); after.removeAll(before); check(after.isEmpty(), "subprocess pipe descriptors remain open");
            lifecycle.setProperty("new-open-pipes", Integer.toString(after.size()));
            lifecycle.setProperty("owned-staging-removed", "true"); lifecycle.setProperty("caller-root-preserved", "true");
            lifecycle.setProperty("caller-sentinel-sha256", EvidenceFiles.sha256(caller));
            retained.write(boundary.resolve(mode + "-lifecycle.properties"), lifecycle);
            if ("inherited-pipe".equals(mode)) {
                Path descendant = Paths.get(actual.toString() + ".descendant.properties");
                check(Files.isRegularFile(descendant), "inherited pipe was not exercised");
                Properties child = load(descendant);
                long until = System.nanoTime() + Duration.ofSeconds(6).toNanos();
                while (!terminated(child.getProperty("pid")) && System.nanoTime() < until) { Thread.sleep(10); }
                check(terminated(child.getProperty("pid")), "acceptance-owned descendant did not release its pipe");
            }
        }
    }

    private void publication() throws Exception {
        BorrowedOutput stream = new BorrowedOutput();
        WorkflowOutcome<Void> success = new DocumentWorkflow().execute(base().target("result", PublicationTarget.stream(stream)).build(), session -> {
            session.execute(AddBlankPage.INSTANCE); return null;
        });
        check(!stream.closed && receipts(success.getPublicationReceipts()).equals("result:COMMITTED:false"), "caller stream ownership");
        Path pdf = Files.createDirectory(output.resolve("native-stream")).resolve("blank.pdf"); retained.write(pdf, stream.toByteArray());
        BorrowedInput source = new BorrowedInput(stream.toByteArray());
        WorkflowOutcome<Integer> reopened = new DocumentWorkflow().execute(base().source("source", DocumentSource.stream(source, stream.size())).primarySource("source").build(),
                session -> session.query(net.zerocloud.pdf.query.PageCount.INSTANCE));
        check(!source.closed && reopened.getResult().longValue() == 1, "caller input ownership/reopen");
        observe("native-ownership", null, "input-closed", "false", "output-closed", "false", "pages", "1", "receipts", receipts(success.getPublicationReceipts()));
        Path first = Files.createDirectory(output.resolve("native-before-failure")).resolve("blank.pdf");
        Path later = output.resolve("native-later.sentinel"); retained.write(later, PAYLOAD); BrokenOutput broken = new BrokenOutput();
        try { new DocumentWorkflow().execute(base().target("first", PublicationTarget.path(first)).target("current", PublicationTarget.stream(broken))
                    .target("later", PublicationTarget.path(later)).build(), session -> { session.execute(AddBlankPage.INSTANCE); return null; });
            throw new AssertionError("failed publication passed"); }
        catch (DocumentFailure failure) {
            check(failure.getCode() == DocumentFailureCode.PUBLICATION_FAILED && failure.getCause() == null
                    && "The validated document could not be written to its stream target.".equals(failure.getDiagnostic())
                    && receipts(failure.getPublicationReceipts()).equals("first:COMMITTED:false,current:FAILED:true,later:NOT_ATTEMPTED:false"), "safe ordered publication failure");
            observe("native-partial-publication", failure.getCode().name(), "diagnostic", failure.getDiagnostic(), "receipts", receipts(failure.getPublicationReceipts()),
                    "sentinel-sha256", EvidenceFiles.sha256(later));
        }
        check(broken.count == 1 && !broken.closed && Arrays.equals(PAYLOAD, Files.readAllBytes(later))
                && T03BlankSemantics.inspect(first, execution) == EvidenceResult.PASS, "uncommitted output/ownership");
        retained.retain(first, EvidenceFiles.sha256(first));
    }

    private void providerFailure(String name, CapabilityProvider provider, ProviderRequest request, ProviderFailureCode code) throws Exception {
        try { provider.execute(request); throw new AssertionError("expected Provider failure " + name); }
        catch (ProviderFailure failure) {
            ProviderFailure safe = ProviderFailure.forProvider(code, provider.getMetadata().getProviderId(), request.getCapabilityId());
            check(failure.getCode() == code && safe.getProviderId().equals(failure.getProviderId()) && safe.getCapabilityId().equals(failure.getCapabilityId())
                    && safe.getDiagnostic().equals(failure.getDiagnostic()) && failure.getCause() == null && failure.getSuppressed().length == 0,
                    "unsafe Provider failure " + name + ": expected " + code + ", observed " + failure.getCode());
            rejects(() -> failure.initCause(new IOException("private-transport")), IllegalStateException.class);
            failure.addSuppressed(new IOException("private-engine")); check(failure.getSuppressed().length == 0, "suppressed engine details");
            observe(name, code.name(), "provider", failure.getProviderId(), "capability", failure.getCapabilityId(), "diagnostic", failure.getDiagnostic(), "cause", "none", "suppressed", "0");
        }
    }

    private void observe(String name, String code, String... facts) {
        check(cases.add(name), "duplicate mandatory observation " + name); observed.setProperty(name + ".result", "pass");
        if (code != null) { observed.setProperty(name + ".code", code); }
        for (int index = 0; index < facts.length; index += 2) { observed.setProperty(name + "." + facts[index], facts[index + 1]); }
    }

    private void recordMetadata(List<ProviderMetadata> metadata) throws IOException {
        Properties facts = new Properties(); facts.setProperty("count", Integer.toString(metadata.size()));
        for (int index = 0; index < metadata.size(); index++) {
            ProviderMetadata value = metadata.get(index); String prefix = "provider." + index + ".";
            facts.setProperty(prefix + "id", value.getProviderId()); facts.setProperty(prefix + "capabilities", String.join(",", value.getCapabilityIds()));
            facts.setProperty(prefix + "version", value.getEngineVersion()); facts.setProperty(prefix + "mode", value.getExecutionMode().name());
            facts.setProperty(prefix + "availability", value.getAvailability().name()); facts.setProperty(prefix + "distribution", value.getDistribution().name());
            facts.setProperty(prefix + "license-spdx", value.getEngineLicenseSpdxIdentifier()); facts.setProperty(prefix + "license-name", value.getEngineLicenseName());
            facts.setProperty(prefix + "maximum-input", Long.toString(value.getLimits().getMaximumInputBytes()));
            facts.setProperty(prefix + "maximum-output", Long.toString(value.getLimits().getMaximumOutputBytes()));
            facts.setProperty(prefix + "maximum-duration", value.getLimits().getMaximumDuration().toString());
        }
        retained.write(output.resolve("metadata.properties"), facts);
    }

    private WorkflowRequest.Builder base() { return WorkflowRequest.builder().executionProfile(execution).saveMode(SaveMode.REWRITE); }
    private static ProviderRequest request(byte[] bytes, boolean authorized) {
        ProviderRequest.Builder builder = ProviderRequest.builder(CAPABILITY, bytes).timeout(Duration.ofSeconds(1));
        if (authorized) { builder.authorizeRemoteDisclosure(); } return builder.build();
    }
    private static ProviderMetadata.Builder metadataBuilder(String id) {
        return ProviderMetadata.builder(id, "t05-synthetic-v1").capability(CAPABILITY).availability(ProviderAvailability.AVAILABLE)
                .limits(ProviderLimits.bounded(4, 4, Duration.ofSeconds(5))).engineLicense("Apache-2.0", "Apache License 2.0");
    }
    private static ProviderMetadata metadata(String id, ProviderExecutionMode mode, ProviderAvailability availability, long input, long result) {
        return metadataBuilder(id).executionMode(mode).availability(availability).limits(ProviderLimits.bounded(input, result, Duration.ofSeconds(5)))
                .distribution(mode == ProviderExecutionMode.REMOTE ? ProviderDistribution.REMOTE_SERVICE : ProviderDistribution.CALLER_SUPPLIED).build();
    }
    private static String receipts(List<PublicationReceipt> receipts) {
        List<String> values = new ArrayList<String>(); for (PublicationReceipt value : receipts) {
            values.add(value.getTargetName() + ":" + value.getStatus().name() + ":" + value.isPartialOutputPossible());
        } return String.join(",", values);
    }
    private static String pid() { return ManagementFactory.getRuntimeMXBean().getName().split("@")[0]; }
    private static Properties load(Path path) throws IOException {
        Properties values = new Properties(); try (java.io.InputStream stream = Files.newInputStream(path)) { values.load(stream); } return values;
    }
    private static boolean terminated(String pid) throws IOException {
        check(pid != null && pid.matches("[1-9][0-9]*"), "missing actual child PID");
        Path stat = Paths.get("/proc", pid, "stat"); if (!Files.exists(stat)) { return true; }
        try { String value = new String(Files.readAllBytes(stat), java.nio.charset.StandardCharsets.UTF_8);
            return value.substring(value.lastIndexOf(')') + 2).startsWith("Z "); }
        catch (java.nio.file.NoSuchFileException exited) { return true; }
    }
    private static Set<String> pipes() throws IOException {
        Set<String> result = new HashSet<String>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(Paths.get("/proc/self/fd"))) {
            for (Path path : paths) { try { String value = Files.readSymbolicLink(path).toString(); if (value.startsWith("pipe:[")) { result.add(value); } }
                catch (java.nio.file.NoSuchFileException closed) { /* A completed stream disappeared during the observation. */ } }
        } return result;
    }
    private static void rejects(Action action, Class<? extends Throwable> expected) throws Exception {
        try { action.run(); } catch (Throwable failure) {
            if (expected.isInstance(failure)) { return; }
            if (failure instanceof Exception) { throw (Exception) failure; } throw (Error) failure;
        } throw new AssertionError("Missing rejection " + expected.getSimpleName());
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private interface Action { void run() throws Exception; }

    private static final class Echo extends CapabilityProvider {
        private final String behavior; private int invocations;
        Echo(ProviderMetadata metadata, String behavior) { super(metadata); this.behavior = behavior; }
        @Override protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
            invocations++;
            if ("null".equals(behavior)) { return null; }
            if ("runtime".equals(behavior)) { throw new IllegalStateException("private-engine/path/token"); }
            if ("forged".equals(behavior)) { throw ProviderFailure.forProvider(ProviderFailureCode.EXECUTION_FAILED, "private.engine", "private.capability"); }
            return ProviderResult.of("oversize".equals(behavior) ? new byte[5] : request.getInput());
        }
    }
    private static final class Remote extends CapabilityProvider {
        private final Endpoint endpoint; private int invocations; private String executingPid;
        Remote(ProviderMetadata metadata, Endpoint endpoint) { super(metadata); this.endpoint = endpoint; }
        @Override protected ProviderResult perform(ProviderRequest request) throws ProviderFailure {
            invocations++; executingPid = pid();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), endpoint.server.getLocalPort()), 500);
                socket.setSoTimeout(500);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream()); output.writeInt(Math.toIntExact(request.getInputLength())); output.write(request.getInput()); output.flush();
                DataInputStream input = new DataInputStream(socket.getInputStream()); int length = input.readInt();
                if (length < 0 || length > 4) { throw ProviderFailure.forProvider(ProviderFailureCode.MALFORMED_OUTPUT, getMetadata().getProviderId(), CAPABILITY); }
                byte[] result = new byte[length]; input.readFully(result); return ProviderResult.of(result);
            } catch (IOException failure) { throw ProviderFailure.forProvider(ProviderFailureCode.EXECUTION_FAILED, getMetadata().getProviderId(), CAPABILITY); }
        }
    }
    private static final class Endpoint implements AutoCloseable {
        private final ServerSocket server; private final Thread thread; private final Path output;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final CopyOnWriteArrayList<byte[]> payloads = new CopyOnWriteArrayList<byte[]>();
        private volatile Throwable failure;
        Endpoint(Path output) throws IOException {
            this.output = output; server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")); server.setSoTimeout(100);
            thread = new Thread(() -> {
                while (running.get()) { try (Socket socket = server.accept()) {
                    socket.setSoTimeout(500); DataInputStream input = new DataInputStream(socket.getInputStream()); int length = input.readInt();
                    check(length >= 0 && length <= 4 && socket.getInetAddress().isLoopbackAddress(), "endpoint synthetic input bound");
                    byte[] payload = new byte[length]; input.readFully(payload); int index = payloads.size(); payloads.add(payload);
                    Files.write(this.output.resolve("endpoint-request-" + index + ".bin"), payload);
                    Files.write(this.output.resolve("endpoint-response-" + index + ".bin"), RESPONSE);
                    DataOutputStream result = new DataOutputStream(socket.getOutputStream()); result.writeInt(RESPONSE.length); result.write(RESPONSE); result.flush();
                } catch (SocketTimeoutException idle) { /* Bounded accept, no polling outside the local fixture. */ }
                catch (Throwable problem) { if (running.get()) { failure = problem; } break; } }
            }, "t05-controlled-loopback"); thread.setDaemon(true); thread.start();
        }
        int requests() { check(failure == null, "controlled endpoint failed"); return payloads.size(); }
        @Override public void close() throws Exception { running.set(false); server.close(); thread.join(2000);
            check(!thread.isAlive() && failure == null, "controlled endpoint cleanup"); }
    }
    private static final class BorrowedInput extends ByteArrayInputStream {
        private boolean closed; BorrowedInput(byte[] bytes) { super(bytes); } @Override public void close() { closed = true; }
    }
    private static final class BorrowedOutput extends ByteArrayOutputStream {
        private boolean closed; @Override public void close() { closed = true; }
    }
    private static final class BrokenOutput extends OutputStream {
        private int count; private boolean closed; @Override public void write(int value) throws IOException { if (count == 1) { throw new IOException("private-output"); } count++; }
        @Override public void close() { closed = true; }
    }
}

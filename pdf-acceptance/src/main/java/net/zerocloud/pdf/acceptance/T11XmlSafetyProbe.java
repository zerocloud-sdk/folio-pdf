package net.zerocloud.pdf.acceptance;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.T11WorkerSafetyObserver;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.SetXmpMetadata;
import net.zerocloud.pdf.itext7.kernel.exceptions.PdfException;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.query.XmpMetadata;

/** Isolated public-API probe for the T11 XMP external-resource contract. */
public final class T11XmlSafetyProbe {
    private static final byte[] BASELINE = packet("<safe>baseline</safe>");
    private static final String REJECTION_DIAGNOSTIC =
            "The XMP packet is not a well-formed XMP metadata packet.";
    private static final int CANARY_PORT = 46531;

    private T11XmlSafetyProbe() {
    }

    static Properties record(
            Path output,
            WorkflowExecutionProfile nativeExecution) throws Exception {
        Properties overall = new Properties();
        overall.setProperty("profile", T11Corpus.PROFILE);
        overall.setProperty("native-execution-profile",
                nativeExecution.name());
        overall.setProperty("facade-execution-profile",
                WorkflowExecutionProfile.IN_PROCESS.name());
        overall.setProperty("result", "pass");
        for (String api : new String[] {"native", "facade"}) {
            Path apiOutput = Files.createDirectory(output.resolve(api));
            Path log = apiOutput.resolve("child.log");
            List<String> command = new ArrayList<String>();
            command.add(javaExecutable().toString());
            if (requiresSecurityManagerAllowOption()) {
                command.add("-Djava.security.manager=allow");
            }
            command.add("-cp");
            command.add(System.getProperty(
                    "surefire.test.class.path",
                    System.getProperty("java.class.path")));
            command.add(T11XmlSafetyProbe.class.getName());
            command.add("child");
            command.add(api);
            command.add(apiOutput.toString());
            command.add(nativeExecution.name());
            Process child = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            try {
                if (!child.waitFor(60L, TimeUnit.SECONDS)) {
                    throw new IOException("T11 XML safety child timed out for " + api);
                }
                if (child.exitValue() != 0) {
                    throw new IOException("T11 XML safety child failed for " + api
                            + ": " + boundedLog(log));
                }
            } finally {
                child.destroyForcibly();
            }
            PinProperties result = PinProperties.load(
                    apiOutput.resolve("safety.properties"),
                    "T11 " + api + " XML safety");
            overall.setProperty(api, result.required("result"));
            if (!"pass".equals(result.required("result"))) {
                overall.setProperty("result", "fail");
            }
        }
        T11MetadataProducts.save(output.resolve("result.properties"), overall);
        return overall;
    }

    /** Runs one isolated child probe. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"child".equals(arguments[0])
                || !("native".equals(arguments[1])
                        || "facade".equals(arguments[1]))) {
            throw new IllegalArgumentException(
                    "Usage: T11XmlSafetyProbe child <native|facade> <output> <native-execution-profile>");
        }
        runChild(
                arguments[1],
                Paths.get(arguments[2]).toAbsolutePath().normalize(),
                WorkflowExecutionProfile.valueOf(arguments[3]));
    }

    @SuppressWarnings("removal")
    private static void runChild(
            String api,
            Path output,
            WorkflowExecutionProfile nativeExecution) throws Exception {
        Path canary = output.resolve("external-entity-canary.txt");
        Files.write(canary, "external entity must stay unread\n".getBytes(StandardCharsets.US_ASCII));
        String canaryUri = canary.toUri().toASCIIString();
        CountingSecurityManager guard = new CountingSecurityManager(
                canary.toAbsolutePath().normalize().toString(), CANARY_PORT);
        Properties result = new Properties();
        result.setProperty("api", api);
        WorkflowExecutionProfile actual = "native".equals(api)
                ? nativeExecution : WorkflowExecutionProfile.IN_PROCESS;
        result.setProperty("execution-profile", actual.name());
        result.setProperty("result", "pass");
        System.setSecurityManager(guard);
        try {
            proveFileObserver(canary, guard, result);
            proveNetworkObserver(guard, result);

            byte[] externalFile = packet("<!DOCTYPE x:xmpmeta [<!ENTITY leak SYSTEM \""
                    + canaryUri + "\">]><probe>&leak;</probe>");
            byte[] externalNetwork = packet("<!DOCTYPE x:xmpmeta [<!ENTITY % remote SYSTEM \"http://127.0.0.1:"
                    + CANARY_PORT + "/entity.dtd\">%remote;]><probe>network</probe>");
            byte[] malformed = ("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                    + "<broken></x:xmpmeta>").getBytes(StandardCharsets.UTF_8);
            byte[] inertXInclude = packet("<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\""
                    + canaryUri + "\" parse=\"text\"/>");

            rejectedScenario(api, actual, output, guard, result,
                    "external-file", externalFile);
            rejectedScenario(api, actual, output, guard, result,
                    "external-network", externalNetwork);
            rejectedScenario(api, actual, output, guard, result,
                    "malformed", malformed);
            acceptedScenario(api, actual, output, guard, result,
                    "inert-xinclude", inertXInclude);
            T11MetadataProducts.save(output.resolve("safety.properties"), result);
        } finally {
            System.setSecurityManager(null);
        }
    }

    private static void rejectedScenario(
            String api,
            WorkflowExecutionProfile execution,
            Path output,
            CountingSecurityManager guard,
            Properties evidence,
            String name,
            byte[] packet) throws Exception {
        guard.reset();
        ScenarioResult result = scenario(
                api, execution, output.resolve(name + ".pdf"), packet, true);
        int fileAccesses = guard.fileAttempts();
        int networkAccesses = guard.networkAttempts();
        evidence.setProperty(name + ".code", result.failure.getCode().name());
        evidence.setProperty(name + ".diagnostic", result.failure.getDiagnostic());
        evidence.setProperty(name + ".file-accesses", Integer.toString(fileAccesses));
        evidence.setProperty(name + ".network-accesses", Integer.toString(networkAccesses));
        evidence.setProperty(name + ".unmutated", exact(BASELINE, result.observed) ? "pass" : "fail");
        evidence.setProperty(name + ".reopened", exact(BASELINE, result.reopened) ? "pass" : "fail");
        evidence.setProperty(name + ".pdf-sha256", EvidenceFiles.sha256(output.resolve(name + ".pdf")));
        recordWorkerObservation(evidence, name, result.workerObservation);
        require(result.failure.getCode() == DocumentFailureCode.COMMAND_REJECTED
                        && REJECTION_DIAGNOSTIC.equals(result.failure.getDiagnostic()),
                "T11 XML " + name + " did not retain the safe rejection");
        require(fileAccesses == 0 && networkAccesses == 0,
                "T11 XML " + name + " attempted an external resource");
        require(exact(BASELINE, result.observed) && exact(BASELINE, result.reopened),
                "T11 XML " + name + " partially mutated XMP");
    }

    private static void acceptedScenario(
            String api,
            WorkflowExecutionProfile execution,
            Path output,
            CountingSecurityManager guard,
            Properties evidence,
            String name,
            byte[] packet) throws Exception {
        guard.reset();
        ScenarioResult result = scenario(
                api, execution, output.resolve(name + ".pdf"), packet, false);
        int fileAccesses = guard.fileAttempts();
        int networkAccesses = guard.networkAttempts();
        evidence.setProperty(name + ".accepted", result.failure == null ? "pass" : "fail");
        evidence.setProperty(name + ".exact", exact(packet, result.observed) ? "pass" : "fail");
        evidence.setProperty(name + ".reopened", exact(packet, result.reopened) ? "pass" : "fail");
        evidence.setProperty(name + ".file-accesses", Integer.toString(fileAccesses));
        evidence.setProperty(name + ".network-accesses", Integer.toString(networkAccesses));
        evidence.setProperty(name + ".pdf-sha256", EvidenceFiles.sha256(output.resolve(name + ".pdf")));
        recordWorkerObservation(evidence, name, result.workerObservation);
        require(result.failure == null && exact(packet, result.observed)
                        && exact(packet, result.reopened),
                "T11 inert XInclude packet was not retained exactly");
        require(fileAccesses == 0 && networkAccesses == 0,
                "T11 inert XInclude attempted an external resource");
    }

    private static ScenarioResult scenario(
            String api,
            WorkflowExecutionProfile execution,
            Path target,
            byte[] candidate,
            boolean reject) throws Exception {
        return "native".equals(api)
                ? nativeScenario(target, candidate, reject, execution)
                : facadeScenario(target, candidate, reject);
    }

    private static ScenarioResult nativeScenario(
            Path target,
            byte[] candidate,
            boolean reject,
            WorkflowExecutionProfile execution) throws Exception {
        final DocumentFailure[] rejected = new DocumentFailure[1];
        final T11WorkerSafetyObserver.Observation[] workerObservation =
                new T11WorkerSafetyObserver.Observation[1];
        WorkflowOutcome<byte[]> outcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .target("result", PublicationTarget.path(target))
                        .executionProfile(execution)
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> {
                    session.execute(AddBlankPage.INSTANCE);
                    session.execute(SetXmpMetadata.version1(BASELINE));
                    try {
                        session.execute(SetXmpMetadata.version1(candidate));
                        if (reject) {
                            throw new IllegalStateException("T11 Native accepted hostile XMP");
                        }
                    } catch (DocumentFailure failure) {
                        if (!reject) {
                            throw failure;
                        }
                        rejected[0] = failure;
                    }
                    if (execution
                            == WorkflowExecutionProfile.HARDENED_WORKER) {
                        workerObservation[0] =
                                T11WorkerSafetyObserver.observe(session);
                    }
                    return session.query(XmpMetadata.version1(1L << 20));
                });
        require(outcome.getExecutionProfile() == execution,
                "T11 Native XML probe changed execution identity");
        WorkflowOutcome<byte[]> reopenedOutcome = new DocumentWorkflow().execute(
                WorkflowRequest.builder()
                        .source("input", DocumentSource.path(target))
                        .primarySource("input")
                        .executionProfile(execution)
                        .saveMode(SaveMode.REWRITE)
                        .build(),
                session -> session.query(XmpMetadata.version1(1L << 20)));
        require(reopenedOutcome.getExecutionProfile() == execution,
                "T11 Native XML reopen changed execution identity");
        return new ScenarioResult(
                rejected[0],
                outcome.getResult(),
                reopenedOutcome.getResult(),
                workerObservation[0]);
    }

    private static ScenarioResult facadeScenario(
            Path target,
            byte[] candidate,
            boolean reject) throws Exception {
        DocumentFailure rejected = null;
        byte[] observed;
        try (PdfDocument document = new PdfDocument(new PdfWriter(target.toString()))) {
            document.addNewPage();
            document.setXmpMetadata(BASELINE);
            try {
                document.setXmpMetadata(candidate);
                if (reject) {
                    throw new IOException("T11 Facade accepted hostile XMP");
                }
            } catch (PdfException failure) {
                if (!reject || !(failure.getCause() instanceof DocumentFailure)) {
                    throw failure;
                }
                rejected = (DocumentFailure) failure.getCause();
            }
            observed = document.getXmpMetadata(1L << 20);
        }
        byte[] reopened;
        try (PdfDocument document = new PdfDocument(new PdfReader(target.toString()))) {
            reopened = document.getXmpMetadata(1L << 20);
        }
        return new ScenarioResult(rejected, observed, reopened, null);
    }

    private static void recordWorkerObservation(
            Properties evidence,
            String scenario,
            T11WorkerSafetyObserver.Observation observation)
            throws IOException {
        if (observation == null) {
            return;
        }
        String file = observation.isFileAccessDenied()
                ? "denied" : "allowed";
        String network = observation.isNetworkAccessDenied()
                ? "denied" : "allowed";
        String process = observation.isProcessIsolated()
                ? "pass" : "fail";
        evidence.setProperty(
                scenario + ".worker-file-positive-control", file);
        evidence.setProperty(
                scenario + ".worker-network-positive-control", network);
        evidence.setProperty(
                scenario + ".worker-process-isolated", process);
        evidence.setProperty("worker-file-positive-control", file);
        evidence.setProperty("worker-network-positive-control", network);
        evidence.setProperty("worker-process-isolated", process);
        require("denied".equals(file)
                        && "denied".equals(network)
                        && "pass".equals(process),
                "T11 Hardened Worker isolation observer did not pass");
    }

    private static byte[] packet(String content) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
                + content + "</x:xmpmeta>").getBytes(StandardCharsets.UTF_8);
    }

    private static void proveFileObserver(
            Path canary,
            CountingSecurityManager guard,
            Properties result) throws IOException {
        try {
            Files.readAllBytes(canary);
            throw new IOException("T11 file-access observer did not reject its positive control");
        } catch (SecurityException expected) {
            // The positive control proves the exact-path observer is active.
        }
        require(guard.fileAttempts() == 1,
                "T11 file-access observer did not count exactly one positive control");
        result.setProperty("observer.file-attempts", Integer.toString(guard.fileAttempts()));
        guard.reset();
    }

    private static void proveNetworkObserver(
            CountingSecurityManager guard,
            Properties result) throws IOException {
        try {
            new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), CANARY_PORT).close();
            throw new IOException("T11 network observer did not reject its positive control");
        } catch (SecurityException expected) {
            // The positive control proves the exact-port observer is active.
        }
        require(guard.networkAttempts() == 1,
                "T11 network observer did not count exactly one positive control");
        result.setProperty("observer.network-attempts", Integer.toString(guard.networkAttempts()));
        guard.reset();
    }

    private static Path javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin",
                File.separatorChar == '\\' ? "java.exe" : "java");
    }

    private static boolean requiresSecurityManagerAllowOption() {
        String version = System.getProperty("java.specification.version", "");
        String major = version.startsWith("1.") ? version.substring(2) : version;
        int separator = major.indexOf('.');
        if (separator >= 0) {
            major = major.substring(0, separator);
        }
        try {
            return Integer.parseInt(major) >= 17;
        } catch (NumberFormatException failure) {
            return false;
        }
    }

    private static String boundedLog(Path log) throws IOException {
        byte[] bytes = Files.readAllBytes(log);
        int count = Math.min(bytes.length, 8192);
        return new String(bytes, 0, count, StandardCharsets.UTF_8);
    }

    private static boolean exact(byte[] expected, byte[] actual) {
        return Arrays.equals(expected, actual);
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static final class ScenarioResult {
        private final DocumentFailure failure;
        private final byte[] observed;
        private final byte[] reopened;
        private final T11WorkerSafetyObserver.Observation workerObservation;

        private ScenarioResult(
                DocumentFailure failure,
                byte[] observed,
                byte[] reopened,
                T11WorkerSafetyObserver.Observation workerObservation) {
            this.failure = failure;
            this.observed = observed;
            this.reopened = reopened;
            this.workerObservation = workerObservation;
        }
    }

    @SuppressWarnings("removal")
    private static final class CountingSecurityManager extends SecurityManager {
        private final String canary;
        private final int port;
        private int fileAttempts;
        private int networkAttempts;

        private CountingSecurityManager(String canary, int port) {
            this.canary = canary;
            this.port = port;
        }

        @Override
        public void checkPermission(Permission permission) {
            // All ambient operations except the two exact canaries remain available.
        }

        @Override
        public void checkRead(String file) {
            if (canary.equals(Paths.get(file).toAbsolutePath().normalize().toString())) {
                fileAttempts++;
                throw new SecurityException("T11 external-file canary access");
            }
        }

        @Override
        public void checkConnect(String host, int targetPort) {
            if (targetPort == port) {
                networkAttempts++;
                throw new SecurityException("T11 external-network canary access");
            }
        }

        private int fileAttempts() {
            return fileAttempts;
        }

        private int networkAttempts() {
            return networkAttempts;
        }

        private void reset() {
            fileAttempts = 0;
            networkAttempts = 0;
        }
    }
}

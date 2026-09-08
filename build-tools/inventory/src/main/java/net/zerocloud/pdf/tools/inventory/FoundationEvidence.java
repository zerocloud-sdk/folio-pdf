package net.zerocloud.pdf.tools.inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Binds independent chain records to the exact candidate, contract and observed environment. */
final class FoundationEvidence {
    private final FoundationReadiness readiness;
    private final FoundationInventory foundation;
    private final Map<String, ObservedEnvironment> environments = new LinkedHashMap<String, ObservedEnvironment>();
    private final Map<String, InventoryYaml> certifications = new LinkedHashMap<String, InventoryYaml>();

    FoundationEvidence(FoundationReadiness readiness) {
        this.readiness = readiness;
        this.foundation = readiness.foundation;
    }

    void loadEnvironments(List<InventoryYaml> observations) {
        for (InventoryYaml observation : observations) {
            observation.keys("profile", "record");
            String profile = observation.string("profile");
            FoundationInventory.Environment expected = foundation.environments.get(profile);
            if (expected == null) {
                observation.error("unknown environment " + profile + "; architecture alone cannot certify it");
                continue;
            }
            InventoryYaml reference = observation.object("record");
            Path file = FoundationHashes.verify(foundation.inventory.repositoryRoot, reference, readiness.global);
            if (file == null) {
                continue;
            }
            InventoryYaml record = InventoryYaml.load(file, readiness.global);
            record.keys("schema-version", "profile", "identity", "host", "native-engine", "tools");
            if (record.integer("schema-version") != 1 || !profile.equals(record.string("profile"))) {
                record.error("environment profile metadata mismatch");
            }
            if (!expected.identity.equals(record.object("identity").values)) {
                record.error("environment identity mismatch for " + profile
                        + "; require the exact OS/image, architecture and JDK vendor/build/hashes");
            }
            InventoryYaml host = record.object("host");
            host.keys("kernel", "architecture");
            host.string("kernel");
            if (!"x86-64".equals(host.string("architecture"))) {
                host.error("host architecture mismatch");
            }
            InventoryYaml nativeEngine = record.object("native-engine");
            nativeEngine.keys("name", "version", "helper-sha256", "library-sha256", "installation-sha256");
            if (!"harfbuzz".equals(nativeEngine.string("name")) || !"10.2.0".equals(nativeEngine.string("version"))) {
                nativeEngine.error("native engine must match the Foundation HarfBuzz 10.2.0 profile");
            }
            for (String key : Arrays.asList("helper-sha256", "library-sha256", "installation-sha256")) {
                FoundationHashes.requireHash(nativeEngine.string(key), nativeEngine.location + "." + key, readiness.global);
            }
            ObservedEnvironment environment = new ObservedEnvironment(reference.string("sha256"));
            for (InventoryYaml tool : record.objects("tools")) {
                tool.keys("id", "kind", "version", "sha256", "chains");
                String id = tool.string("id");
                String kind = tool.string("kind");
                if (!Arrays.asList("external-tool", "project-test", "human-review").contains(kind)) {
                    tool.error("unknown producer kind");
                }
                tool.string("version");
                FoundationHashes.requireHash(tool.string("sha256"), tool.location, readiness.global);
                for (String chain : tool.strings("chains")) {
                    if (!FoundationInventory.CHAINS.contains(chain)) {
                        tool.error("unknown evidence chain " + chain);
                    }
                }
                if (environment.tools.put(id, tool) != null) {
                    tool.error("duplicate tool identity " + id);
                }
            }
            if (environment.tools.isEmpty()) {
                record.error("missing actual tool identities");
            }
            if (environments.put(profile, environment) != null) {
                observation.error("duplicate observed environment " + profile);
            }
        }
        for (String profile : foundation.environments.keySet()) {
            if (!environments.containsKey(profile)) {
                readiness.global.add("missing actual environment evidence for " + profile);
            }
        }
    }

    void loadCertifications(List<InventoryYaml> records) {
        for (InventoryYaml record : records) {
            record.keys("obligation", "environment", "execution-profile", "configuration", "records");
            String obligation = record.string("obligation");
            String environment = record.string("environment");
            String execution = record.string("execution-profile");
            FoundationInventory.Obligation expected = foundation.obligations.get(obligation);
            if (expected == null) {
                record.error("unknown obligation " + obligation);
            } else if (!expected.environments.contains(environment) || !expected.executions.contains(execution)) {
                record.error("unknown or mismatched environment/execution profile for obligation " + obligation);
            }
            if (certifications.put(key(obligation, environment, execution), record) != null) {
                record.error("duplicate certification for " + obligation + "/" + environment + "/" + execution);
            }
        }
    }

    void verify(FoundationInventory.Obligation obligation, List<String> errors) {
        for (String environment : obligation.environments) {
            for (String execution : obligation.executions) {
                String scope = key(obligation.id, environment, execution);
                InventoryYaml certification = certifications.get(scope);
                if (certification == null) {
                    errors.add("missing certification " + environment + "/" + execution
                            + " (required chains: " + String.join(", ", obligation.chains) + ")");
                } else {
                    verifyChains(obligation, environment, execution, certification, errors);
                }
            }
        }
    }

    private void verifyChains(FoundationInventory.Obligation obligation, String environment, String execution,
            InventoryYaml certification, List<String> errors) {
        Map<String, InventoryYaml> chains = new LinkedHashMap<String, InventoryYaml>();
        Set<Path> files = new HashSet<Path>();
        Set<String> reportHashes = new HashSet<String>();
        Set<String> independentProducers = new HashSet<String>();
        String configurationHash = verifyConfiguration(obligation, environment, execution, certification, errors);
        for (InventoryYaml reference : certification.objects("records")) {
            Path file = FoundationHashes.verify(foundation.inventory.repositoryRoot, reference, errors);
            if (file == null) {
                continue;
            }
            if (!files.add(file)) {
                errors.add("independent chains require distinct record files: " + file);
            }
            InventoryYaml record = InventoryYaml.load(file, errors);
            record.keys("schema-version", "obligation", "acceptance-profile", "release", "candidate-sha256",
                    "contract-sha256", "environment-sha256", "execution-profile", "chain", "result", "producer",
                    "configuration", "execution-configuration-sha256", "report", "negative-controls");
            if (record.integer("schema-version") != 1) {
                record.error("unsupported chain record schema-version");
            }
            match(record, "obligation", obligation.id);
            match(record, "acceptance-profile", obligation.profile);
            match(record, "release", foundation.release);
            match(record, "execution-profile", execution);
            match(record, "candidate-sha256", readiness.candidateIdentity);
            match(record, "contract-sha256", readiness.contractIdentity);
            ObservedEnvironment observed = environments.get(environment);
            match(record, "environment-sha256", observed == null ? "" : observed.sha256);
            match(record, "execution-configuration-sha256", configurationHash);
            String chain = record.string("chain");
            if (!obligation.chains.contains(chain)) {
                record.error("unexpected chain " + chain + " for " + obligation.id);
            }
            if (chains.put(chain, record) != null) {
                record.error("duplicate evidence chain " + chain);
            }
            match(record, "result", "pass");
            InventoryYaml configuration = record.object("configuration");
            match(configuration, "path", obligation.profileContract);
            FoundationHashes.verify(foundation.inventory.repositoryRoot, configuration, errors);
            InventoryYaml report = record.object("report");
            Path reportFile = FoundationHashes.verify(foundation.inventory.repositoryRoot, report, errors);
            if (reportFile != null && (!files.add(reportFile) || !reportHashes.add(report.string("sha256")))) {
                record.error("independent chains require distinct actual report artifacts");
            }
            verifyProducer(record, observed, chain, independentProducers);
            List<InventoryYaml> controls = record.objects("negative-controls");
            if (obligation.kind.requiresPdfEvidence() && controls.isEmpty()) {
                record.error("missing required negative-control evidence for " + chain);
            }
            for (InventoryYaml control : controls) {
                Path controlFile = FoundationHashes.verify(foundation.inventory.repositoryRoot, control, errors);
                if (controlFile != null && (controlFile.equals(reportFile) || controlFile.equals(file))) {
                    record.error("negative-control evidence cannot be its positive report or chain record");
                }
            }
        }
        for (String chain : obligation.chains) {
            if (!chains.containsKey(chain)) {
                errors.add("missing required independent evidence chain " + chain + " for "
                        + obligation.id + "/" + environment + "/" + execution);
            }
        }
    }

    private String verifyConfiguration(FoundationInventory.Obligation obligation, String environment, String execution,
            InventoryYaml certification, List<String> errors) {
        InventoryYaml reference = certification.object("configuration");
        Path file = FoundationHashes.verify(foundation.inventory.repositoryRoot, reference, errors);
        if (file == null) {
            errors.add("missing actual execution configuration for " + obligation.id + "/" + environment + "/" + execution);
            return "";
        }
        InventoryYaml configuration = InventoryYaml.load(file, errors);
        configuration.keys("schema-version", "candidate-sha256", "environment-sha256", "acceptance-profile",
                "execution-profile", "command", "java-options", "locale", "timezone", "settings", "inputs");
        if (configuration.integer("schema-version") != 1) {
            configuration.error("unsupported execution configuration schema-version");
        }
        match(configuration, "candidate-sha256", readiness.candidateIdentity);
        ObservedEnvironment observed = environments.get(environment);
        match(configuration, "environment-sha256", observed == null ? "" : observed.sha256);
        match(configuration, "acceptance-profile", obligation.profile);
        match(configuration, "execution-profile", execution);
        List<String> command = configuration.arguments("command");
        if (command.isEmpty() || command.get(0).trim().isEmpty()) {
            configuration.error("actual execution command must be recorded");
        }
        configuration.arguments("java-options");
        configuration.string("locale");
        configuration.string("timezone");
        InventoryYaml settings = configuration.object("settings");
        for (String field : Arrays.asList("workflow-policy", "fonts", "providers")) {
            settings.string(field);
        }
        List<InventoryYaml> inputs = configuration.objects("inputs");
        if (inputs.isEmpty()) {
            configuration.error("execution configuration requires the actual corpus/font/policy/tool configuration inputs");
        }
        for (InventoryYaml input : inputs) {
            FoundationHashes.verify(foundation.inventory.repositoryRoot, input, errors);
        }
        return reference.string("sha256");
    }

    private void verifyProducer(InventoryYaml record, ObservedEnvironment environment, String chain,
            Set<String> independentProducers) {
        String producer = record.string("producer");
        InventoryYaml tool = environment == null ? null : environment.tools.get(producer);
        if (tool == null) {
            record.error("missing actual producer identity " + producer);
            return;
        }
        if (!tool.strings("chains").contains(chain)) {
            record.error("producer " + producer + " is not qualified for " + chain);
        }
        String kind = tool.string("kind");
        AcceptanceChain pdfChain = AcceptanceChain.from(chain);
        if (pdfChain != null && !pdfChain.requiredProducerKind().toString().equals(kind)) {
            record.error("wrong producer kind for independent " + chain + " chain");
        }
        if (FoundationInventory.PDF_CHAINS.contains(chain)
                && !independentProducers.add(tool.string("sha256"))) {
            record.error("independent PDF chains cannot reuse a producer executable identity");
        }
        if (("standards".equals(chain) && producer.toLowerCase(java.util.Locale.ROOT).contains("qpdf"))
                || ("visual".equals(chain) && producer.toLowerCase(java.util.Locale.ROOT).contains("pdfbox"))) {
            record.error("producer cannot substitute for the required independent " + chain + " chain");
        }
    }

    private static void match(InventoryYaml record, String field, String expected) {
        if (expected.isEmpty() || !expected.equals(record.string(field))) {
            record.error("missing or mismatched " + field + "; expected " + (expected.isEmpty() ? "an established identity" : expected));
        }
    }

    private static String key(String obligation, String environment, String execution) {
        return obligation + "/" + environment + "/" + execution;
    }

    private static final class ObservedEnvironment {
        final String sha256;
        final Map<String, InventoryYaml> tools = new LinkedHashMap<String, InventoryYaml>();

        ObservedEnvironment(String sha256) {
            this.sha256 = sha256;
        }
    }
}

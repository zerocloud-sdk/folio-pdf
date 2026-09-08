package net.zerocloud.pdf.tools.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Synthetic repository contracts exercised only through the actual command process. */
public final class FoundationReadinessCommandTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void realInventoryIsValidButFoundationRemainsNotReady() throws Exception {
        Path root = Paths.get(System.getProperty("repositoryRoot"));
        Result valid = command("validate", root);
        assertEquals(valid.output, 0, valid.exit);
        Result result = command("readiness", root);
        assertTrue(result.output, result.exit != 0);
        for (String finding : Arrays.asList("Foundation 0.1.0: NOT READY",
                "BLOCKED password-clear-metadata (#79)", "BLOCKED password-attachments (#80)",
                "BLOCKED tables-base (#91)", "BLOCKED tables-pagination (#92)",
                "incompatible Dependency Gate",
                "ubuntu-24.04-linux-x86-64-jdk8", "ubuntu-24.04-linux-x86-64-jdk11",
                "ubuntu-24.04-linux-x86-64-jdk17", "ubuntu-24.04-linux-x86-64-jdk21")) {
            assertTrue("Missing " + finding + "\n" + result.output, result.output.contains(finding));
        }
    }

    @Test
    public void completeControlledContractPassesAndGeneratesTheSameScope() throws Exception {
        Fixture fixture = fixture(false);
        Result result = command("readiness", fixture.root);
        assertEquals(result.output, 0, result.exit);
        assertTrue(result.output, result.output.contains("Foundation 0.1.0: READY"));
        Result generated = command("generate", fixture.root);
        assertEquals(generated.output, 0, generated.exit);
        String document = read(fixture.root.resolve("docs/generated/foundation-readiness.md"));
        assertTrue(document, document.contains("**READY**"));
        assertTrue(document, document.contains("Windows x86-64 and macOS x86-64/arm64 are uncertified"));
        assertTrue(document, document.contains("not required release gates for Foundation 0.1.0"));
        for (String environment : fixture.environmentIds) {
            assertTrue(document, document.contains(environment));
        }
        Result current = command("check", fixture.root);
        assertEquals(current.output, 0, current.exit);
        assertEquals(document, read(fixture.root.resolve("docs/generated/foundation-readiness.md")));
        Files.write(fixture.root.resolve("docs/generated/foundation-readiness.md"), bytes("stale\n"));
        assertFailure(command("check", fixture.root), "generated documentation is stale");
    }

    @Test
    public void independentlyCertifiedChildDoesNotCompleteItsAggregate() throws Exception {
        Fixture fixture = fixture(true);
        Result valid = command("validate", fixture.root);
        assertEquals(valid.output, 0, valid.exit);
        Result result = command("readiness", fixture.root);
        assertFailure(result, "BLOCKED aggregate (#78)");
        assertTrue(result.output, result.output.contains("SATISFIED sample (#70)"));
        assertFalse(result.output, result.output.contains("BLOCKED sample (#70)"));
    }

    @Test
    public void controlledIncompleteContractsFailAtTheCommandBoundary() throws Exception {
        List<Negative> negatives = Arrays.asList(
                negative("unknown environment", "unknown environment windows-x86-64", fixture -> {
                    map(list(fixture.evidence, "environments").get(0)).put("profile", "windows-x86-64");
                    fixture.saveEvidence();
                }),
                negative("unknown capability platform", "unknown or uncertified environment label", fixture -> {
                    fixture.capability.put("certified-platforms", strings("linux-x86-64"));
                    fixture.saveMatrix();
                }),
                negative("OS identity mismatch", "environment identity mismatch", fixture -> {
                    fixture.changeEnvironment("os-version", "26.04");
                }),
                negative("JDK build mismatch", "environment identity mismatch", fixture -> {
                    fixture.changeEnvironment("jdk-build", "different-build");
                }),
                negative("missing JDK identity", "environment identity mismatch", fixture -> {
                    fixture.changeEnvironment("jdk-vendor", null);
                }),
                negative("missing native identity", "expected a nonblank string", fixture -> {
                    Map<String, Object> environment = fixture.load("evidence/environment-8.yaml");
                    map(environment.get("native-engine")).remove("library-sha256");
                    fixture.write("evidence/environment-8.yaml", environment);
                }),
                negative("missing tools", "missing actual tool identities", fixture -> {
                    Map<String, Object> environment = fixture.load("evidence/environment-8.yaml");
                    environment.put("tools", objects());
                    fixture.write("evidence/environment-8.yaml", environment);
                }),
                negative("missing required JDK", "missing actual environment evidence for ubuntu-24.04-linux-x86-64-jdk21", fixture -> {
                    list(fixture.evidence, "environments").remove(3);
                    fixture.saveEvidence();
                }),
                negative("missing execution run", "missing certification", fixture -> {
                    list(fixture.evidence, "certifications").remove(0);
                    fixture.saveEvidence();
                }),
                negative("unknown certification environment", "unknown or mismatched environment/execution profile", fixture -> {
                    map(list(fixture.evidence, "certifications").get(0)).put("environment", "same-architecture-elsewhere");
                    fixture.saveEvidence();
                }),
                negative("missing standards chain", "missing required independent evidence chain standards", fixture -> {
                    list(map(list(fixture.evidence, "certifications").get(0)), "records").remove(1);
                    fixture.saveEvidence();
                }),
                negative("failed chain", "missing or mismatched result", fixture -> fixture.changeChain("result", "fail")),
                negative("indeterminate chain", "missing or mismatched result", fixture -> fixture.changeChain("result", "indeterminate")),
                negative("stale output bytes", "stale or mismatched artifact identity output.jar", fixture -> fixture.text("output.jar", "changed product")),
                negative("changed candidate", "missing or mismatched candidate-sha256", fixture -> {
                    fixture.text("output.jar", "new final product");
                    map(fixture.evidence.get("candidate")).put("artifacts", objects(fixture.reference("output.jar")));
                    fixture.saveEvidence();
                }),
                negative("changed source", "stale or mismatched artifact identity input.txt", fixture -> fixture.text("input.txt", "new source")),
                negative("changed referenced profile", "stale or mismatched artifact identity contracts/reference.properties", fixture ->
                        fixture.text("contracts/reference.properties", "dpi=72\n")),
                negative("changed public guide", "stale or mismatched artifact identity README.md", fixture ->
                        fixture.text("README.md", "Changed public certification claim.\n")),
                negative("new unrecorded profile", "source identity coverage mismatch", fixture ->
                        fixture.text("contracts/added.properties", "new mandatory input\n")),
                negative("missing source identity", "source identity coverage mismatch", fixture -> {
                    map(fixture.evidence.get("candidate")).put("inputs", objects());
                    fixture.saveEvidence();
                }),
                negative("missing candidate artifact", "missing or unexpected required candidate artifacts", fixture -> {
                    map(fixture.evidence.get("candidate")).put("artifacts", objects());
                    fixture.saveEvidence();
                }),
                negative("stale report", "stale or mismatched artifact identity", fixture -> fixture.text(fixture.firstReport, "changed report")),
                negative("missing negative controls", "missing required negative-control evidence", fixture -> fixture.changeChain("negative-controls", objects())),
                negative("missing execution configuration", "missing actual execution configuration", fixture -> {
                    map(list(fixture.evidence, "certifications").get(0)).remove("configuration");
                    fixture.saveEvidence();
                }),
                negative("stale execution configuration", "stale or mismatched artifact identity", fixture -> {
                    String path = (String) map(map(list(fixture.evidence, "certifications").get(0)).get("configuration")).get("path");
                    Map<String, Object> configuration = fixture.load(path);
                    configuration.put("locale", "different-locale");
                    fixture.write(path, configuration);
                }),
                negative("wrong profile", "missing or mismatched acceptance-profile", fixture -> fixture.changeChain("acceptance-profile", "unrelated-profile")),
                negative("stale contract", "missing or mismatched contract-sha256", fixture -> {
                    fixture.obligation.put("summary", "Changed mandatory behavior");
                    fixture.saveFoundation();
                }),
                negative("unqualified producer", "is not qualified for syntax", fixture -> fixture.changeChain("producer", "fixture-standards")),
                negative("missing mapping", "missing required Stable Facade mapping surface.compatible", fixture -> {
                    fixture.capability.put("migration-facade", object("stable", strings(), "preview", strings()));
                    map(fixture.facade.get("surfaces")).put("stable", objects());
                    fixture.facade.put("excluded-capabilities", objects(object("id", "sample.compatible", "ticket", "T32",
                            "reason", "A blanket exclusion cannot replace required mappings.")));
                    fixture.saveMatrix();
                    fixture.write("capabilities/facade-surface.yaml", fixture.facade);
                    assertEquals(0, command("validate", fixture.root).exit);
                }),
                negative("empty family mapping set", "missing required Facade mapping set", fixture -> {
                    map(list(fixture.obligation, "facade-families").get(0)).put("mappings", strings());
                    fixture.saveFoundation();
                }),
                negative("incompatible dependency", "requires Dependency Gate sample.dependency to be compatible", fixture -> {
                    Map<String, Object> dependency = fixture.planned("sample.dependency");
                    list(fixture.matrix, "capabilities").add(dependency);
                    fixture.capability.put("dependency-gates", objects(object("capability", "sample.dependency", "required-status", "compatible")));
                    fixture.saveMatrix();
                }),
                negative("missing requirement mapping", "required Foundation requirement has no obligation", fixture -> {
                    Map<String, Object> requirements = fixture.load("capabilities/requirements.yaml");
                    map(list(requirements, "requirements").get(0)).put("obligations", strings());
                    fixture.write("capabilities/requirements.yaml", requirements);
                }),
                negative("unclassified limitation", "Foundation limitation missing classification", fixture -> {
                    fixture.capability.put("limitations", strings("A newly restricted required case."));
                    fixture.saveMatrix();
                }),
                negative("required limitation", "unresolved retained limitation", fixture -> {
                    fixture.capability.put("limitations", strings("A missing required successful case."));
                    fixture.foundation.put("limitations", objects(object("capability", "sample.compatible",
                            "sha256", hash(bytes("A missing required successful case.")), "disposition", "release-blocker",
                            "reason", "Implement the required behavior.", "obligation", "sample")));
                    fixture.saveMatrix();
                    fixture.saveFoundation();
                }),
                negative("fake Native Interface only", "Foundation behavior requires a corresponding Migration Facade family", fixture -> {
                    fixture.obligation.put("facade-families", objects());
                    fixture.obligation.put("native-only-reason", "Blanket exclusion.");
                    fixture.saveFoundation();
                }),
                negative("shared producer identity", "independent PDF chains cannot reuse a producer executable identity", fixture -> {
                    Map<String, Object> environment = fixture.load("evidence/environment-8.yaml");
                    List<Object> tools = list(environment, "tools");
                    map(tools.get(1)).put("sha256", map(tools.get(0)).get("sha256"));
                    fixture.write("evidence/environment-8.yaml", environment);
                }),
                negative("evidence path escape", "missing evidence/artifact file", fixture -> {
                    fixture.changeChain("report", object("path", "../outside-report", "sha256", hash(bytes("outside"))));
                }));
        for (Negative negative : negatives) {
            Fixture fixture = fixture(false);
            negative.change.apply(fixture);
            Result result = command("readiness", fixture.root);
            assertTrue(negative.name + " unexpectedly passed:\n" + result.output, result.exit != 0);
            assertTrue(negative.name + " did not diagnose " + negative.expected + ":\n" + result.output,
                    result.output.contains(negative.expected));
        }
    }

    private Fixture fixture(boolean aggregate) throws Exception {
        Fixture fixture = new Fixture(temporary.newFolder().toPath());
        fixture.prepare(aggregate);
        fixture.certify();
        return fixture;
    }

    private static void assertFailure(Result result, String finding) {
        assertTrue(result.output, result.exit != 0);
        assertTrue(result.output, result.output.contains(finding));
    }

    private static final class Fixture {
        final Path root;
        final List<String> environmentIds = strings();
        Map<String, Object> matrix;
        Map<String, Object> capability;
        Map<String, Object> facade;
        Map<String, Object> foundation;
        Map<String, Object> obligation;
        Map<String, Object> evidence;
        String firstChain;
        String firstReport;

        Fixture(Path root) {
            this.root = root;
        }

        void prepare(boolean aggregate) throws Exception {
            Path base = Paths.get(FoundationReadinessCommandTest.class.getClassLoader().getResource("fixtures/base").toURI());
            try (Stream<Path> paths = Files.walk(base)) {
                for (java.util.Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
                    Path from = iterator.next();
                    Path to = root.resolve(base.relativize(from).toString());
                    if (Files.isDirectory(from)) {
                        Files.createDirectories(to);
                    } else {
                        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
                        text(root.relativize(to).toString(), read(to).replace("9.9.9-TEST", "0.1.0"));
                    }
                }
            }
            matrix = load("capabilities/capability-matrix.yaml");
            capability = map(list(matrix, "capabilities").get(2));
            matrix.put("capabilities", objects(capability));
            matrix.put("foundation-release", "capabilities/foundation.yaml");
            facade = load("capabilities/facade-surface.yaml");
            map(facade.get("surfaces")).put("preview", objects());
            facade.put("excluded-capabilities", objects());
            text("decision.md", "Synthetic project-owned Foundation platform decision.\n");
            text("profile.md", "Synthetic public-command Acceptance Profile with four independent chains.\n");
            text("input.txt", "Synthetic source input.\n");
            text("contracts/reference.properties", "dpi=144\n");
            text("README.md", "Synthetic public contract.\n");
            text("output.jar", "Synthetic product bytes, not a deployable artifact.\n");
            List<Object> profiles = objects();
            List<Object> actualEnvironments = objects();
            for (int jdk : Arrays.asList(8, 11, 17, 21)) {
                String id = "ubuntu-24.04-linux-x86-64-jdk" + jdk;
                environmentIds.add(id);
                Map<String, Object> identity = object("os", "ubuntu", "os-version", "24.04", "architecture", "x86-64",
                        "image", "example.invalid/synthetic@sha256:" + hash(bytes("image" + jdk)),
                        "os-release-sha256", hash(bytes("Ubuntu 24.04 fixture")), "jdk-major", jdk,
                        "jdk-vendor", "Synthetic vendor", "jdk-build", jdk + ".fixture+1", "java-sha256", hash(bytes("java" + jdk)));
                profiles.add(object("id", id, "identity", identity));
                List<Object> tools = objects();
                for (String chain : strings("syntax", "standards", "semantic", "visual")) {
                    tools.add(object("id", "fixture-" + chain, "kind", chain.equals("semantic") ? "project-test" : "external-tool",
                            "version", "1.0", "sha256", hash(bytes("producer" + chain)), "chains", strings(chain)));
                }
                String path = "evidence/environment-" + jdk + ".yaml";
                write(path, object("schema-version", 1, "profile", id, "identity", identity,
                        "host", object("kernel", "synthetic-kernel", "architecture", "x86-64"),
                        "native-engine", object("name", "harfbuzz", "version", "10.2.0", "helper-sha256", hash(bytes("helper")),
                                "library-sha256", hash(bytes("engine")), "installation-sha256", hash(bytes("installation"))), "tools", tools));
                actualEnvironments.add(object("profile", id, "record", reference(path)));
            }
            capability.put("certified-platforms", new ArrayList<String>(environmentIds));
            obligation = object("id", "sample", "summary", "Synthetic complete observable behavior.", "capability", "sample.compatible",
                    "acceptance-profile", "sample-compatible-profile", "profile-contract", "profile.md", "kind", "behavior", "slice", 70,
                    "environments", new ArrayList<String>(environmentIds), "execution-profiles", strings("IN_PROCESS", "HARDENED_WORKER"),
                    "chains", strings("syntax", "standards", "semantic", "visual"), "dependencies", strings(), "members", strings(),
                    "facade-families", objects(object("family", "Fixture API", "reference-prefix", "com.itextpdf.fixture.", "mappings", strings("surface.compatible"))));
            foundation = object("schema-version", 1, "release", "0.1.0", "platform-decision", "decision.md",
                    "environments", "capabilities/environments.yaml", "requirements", "capabilities/requirements.yaml",
                    "evidence", "capabilities/evidence.yaml", "source-roots", strings("input.txt", "contracts", "README.md"), "required-artifacts", strings("output.jar"),
                    "obligations", objects(obligation), "limitations", objects());
            List<String> owners = strings("sample");
            if (aggregate) {
                capability.put("parent-capability", "sample.aggregate");
                obligation.put("parent-capability", "sample.aggregate");
                list(matrix, "capabilities").add(planned("sample.aggregate"));
                list(facade, "excluded-capabilities").add(object("id", "sample.aggregate", "ticket", "T32", "reason", "Aggregate remains incomplete."));
                list(foundation, "obligations").add(object("id", "aggregate", "summary", "Aggregate preserves original scope.",
                        "capability", "sample.aggregate", "acceptance-profile", "sample-aggregate-profile", "profile-contract", "profile.md",
                        "kind", "aggregate", "slice", 78, "environments", strings(), "execution-profiles", strings(), "chains", strings(),
                        "dependencies", strings(), "members", strings("sample"), "facade-families", objects()));
                owners.add("aggregate");
            }
            saveMatrix();
            write("capabilities/facade-surface.yaml", facade);
            write("capabilities/environments.yaml", object("schema-version", 1,
                    "uncertified-not-required", strings("windows-x86-64", "macos-x86-64", "macos-arm64"), "profiles", profiles));
            write("capabilities/requirements.yaml", object("schema-version", 1, "sources", object("source", "synthetic fixture"),
                    "requirements", objects(object("id", "fixture-requirement", "source", "project-owned fixture contract",
                            "requirement", "Complete synthetic behavior with matching Facade and independent evidence.",
                            "disposition", "required", "reason", "Controlled command boundary fixture.", "obligations", owners))));
            saveFoundation();
            evidence = object("schema-version", 1, "candidate", object("release", "0.1.0",
                    "inputs", objects(reference("input.txt"), reference("contracts/reference.properties"), reference("README.md")),
                    "artifacts", objects(reference("output.jar"))),
                    "environments", actualEnvironments, "certifications", objects());
            saveEvidence();
        }

        Map<String, Object> planned(String id) {
            return object("id", id, "context", "test-context", "summary", "Synthetic planned aggregate or dependency.",
                    "reference-suite", object("source", "fixture contract", "role", "test only"),
                    "native-interface", object("entry-point", "example.Planned#execute"),
                    "migration-facade", object("stable", strings(), "preview", strings()), "limitations", strings(),
                    "dependency-gates", objects(), "promotion-gates", objects(object("ticket", "T32", "requirement", "Complete evidence.")),
                    "acceptance-profile", object("id", id.replace('.', '-') + "-profile", "state", "planned",
                            "mandatory-evidence", strings("syntax", "standards", "semantic", "visual")),
                    "evidence", objects(), "acceptance-evidence", objects(),
                    "provenance", object("path", "PROVENANCE.md", "record", "Fixture record"), "certified-platforms", strings(), "status", "planned");
        }

        void certify() throws Exception {
            // Obtain the documented identities from the public command, not from checker internals.
            Result incomplete = command("readiness", root);
            assertFailure(incomplete, "missing certification");
            String contract = outputValue(incomplete.output, "Contract identity: ");
            String candidate = outputValue(incomplete.output, "Candidate identity: ");
            List<Object> certifications = objects();
            for (Object observed : list(evidence, "environments")) {
                String environment = (String) map(observed).get("profile");
                String environmentHash = (String) map(map(observed).get("record")).get("sha256");
                for (String execution : strings("IN_PROCESS", "HARDENED_WORKER")) {
                    List<Object> records = objects();
                    String configurationPath = "evidence/" + environment + "-" + execution + "-configuration.yaml";
                    write(configurationPath, object("schema-version", 1, "candidate-sha256", candidate,
                            "environment-sha256", environmentHash, "acceptance-profile", "sample-compatible-profile",
                            "execution-profile", execution, "command", strings("fixture-observer",
                                    "--input", "input.txt", "--input", "contracts/reference.properties", "--label", ""),
                            "java-options", strings("-Xmx128m", "-ea", "-ea"), "locale", "ROOT", "timezone", "UTC",
                            "settings", object("workflow-policy", "bounded fixture policy", "fonts", "no text in synthetic corpus",
                                    "providers", "explicit synthetic observers"), "inputs", objects(reference("profile.md"))));
                    Map<String, Object> configurationReference = reference(configurationPath);
                    for (String chain : strings("syntax", "standards", "semantic", "visual")) {
                        String base = "evidence/" + environment + "-" + execution + "-" + chain;
                        text(base + ".txt", "Synthetic passing " + base + " observation.\n");
                        text(base + "-negative.txt", "Synthetic known-invalid " + base + " rejected.\n");
                        write(base + ".yaml", object("schema-version", 1, "obligation", "sample",
                                "acceptance-profile", "sample-compatible-profile", "release", "0.1.0",
                                "candidate-sha256", candidate, "contract-sha256", contract, "environment-sha256", environmentHash,
                                "execution-configuration-sha256", configurationReference.get("sha256"),
                                "execution-profile", execution, "chain", chain, "result", "pass", "producer", "fixture-" + chain,
                                "configuration", reference("profile.md"), "report", reference(base + ".txt"),
                                "negative-controls", objects(reference(base + "-negative.txt"))));
                        records.add(reference(base + ".yaml"));
                        if (firstChain == null) {
                            firstChain = base + ".yaml";
                            firstReport = base + ".txt";
                        }
                    }
                    certifications.add(object("obligation", "sample", "environment", environment,
                            "execution-profile", execution, "configuration", configurationReference, "records", records));
                }
            }
            evidence.put("certifications", certifications);
            saveEvidence();
        }

        void changeEnvironment(String field, Object value) throws Exception {
            Map<String, Object> environment = load("evidence/environment-8.yaml");
            if (value == null) {
                map(environment.get("identity")).remove(field);
            } else {
                map(environment.get("identity")).put(field, value);
            }
            write("evidence/environment-8.yaml", environment);
        }

        void changeChain(String field, Object value) throws Exception {
            Map<String, Object> chain = load(firstChain);
            chain.put(field, value);
            write(firstChain, chain);
            list(map(list(evidence, "certifications").get(0)), "records").set(0, reference(firstChain));
            saveEvidence();
        }

        Map<String, Object> reference(String path) throws Exception {
            return object("path", path, "sha256", hash(Files.readAllBytes(root.resolve(path))));
        }

        Map<String, Object> load(String path) throws Exception {
            return map(new Yaml(new SafeConstructor(new LoaderOptions())).load(read(root.resolve(path))));
        }

        void write(String path, Object value) throws Exception {
            text(path, new Yaml().dump(value));
        }

        void text(String path, String value) throws Exception {
            Files.createDirectories(root.resolve(path).getParent());
            Files.write(root.resolve(path), bytes(value));
        }

        void saveEvidence() throws Exception { write("capabilities/evidence.yaml", evidence); }
        void saveMatrix() throws Exception { write("capabilities/capability-matrix.yaml", matrix); }
        void saveFoundation() throws Exception { write("capabilities/foundation.yaml", foundation); }
    }

    private static Result command(String action, Path root) throws Exception {
        Path java = Paths.get(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                InventoryCommand.class.getName(), action, root.toString()).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); }
        }
        return new Result(process.waitFor(), new String(output.toByteArray(), StandardCharsets.UTF_8));
    }

    private static String outputValue(String output, String prefix) {
        for (String line : output.split("\n")) {
            if (line.startsWith(prefix)) { return line.substring(prefix.length()).trim(); }
        }
        throw new AssertionError("Missing " + prefix + "\n" + output);
    }

    private static String hash(byte[] value) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte item : MessageDigest.getInstance("SHA-256").digest(value)) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        }
        return result.toString();
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String read(Path path) throws Exception { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> map, String key) { return (List<Object>) map.get(key); }
    private static List<Object> objects(Object... values) { return new ArrayList<Object>(Arrays.asList(values)); }
    private static List<String> strings(String... values) { return new ArrayList<String>(Arrays.asList(values)); }
    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < pairs.length; index += 2) { result.put((String) pairs[index], pairs[index + 1]); }
        return result;
    }

    private interface Change { void apply(Fixture fixture) throws Exception; }
    private static Negative negative(String name, String expected, Change change) { return new Negative(name, expected, change); }
    private static final class Negative {
        final String name;
        final String expected;
        final Change change;
        Negative(String name, String expected, Change change) { this.name = name; this.expected = expected; this.change = change; }
    }
    private static final class Result {
        final int exit;
        final String output;
        Result(int exit, String output) { this.exit = exit; this.output = output; }
    }
}

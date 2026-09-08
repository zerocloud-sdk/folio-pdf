package net.zerocloud.pdf.tools.inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The release contract, distinct from observations claiming to satisfy it. */
final class FoundationInventory {
    static final List<String> PDF_CHAINS = Arrays.asList("syntax", "standards", "semantic", "visual");
    static final List<String> CHAINS = Arrays.asList("syntax", "standards", "semantic", "visual",
            "contract", "artifact", "supply-chain", "reproducibility", "signature", "review", "human");
    final InventoryModel inventory;
    final String path;
    String release;
    String decision;
    String evidencePath;
    String requirementsPath;
    String environmentsPath;
    final List<String> sourceRoots = new ArrayList<String>();
    final List<String> sourceExclusions = new ArrayList<String>();
    final List<String> requiredArtifacts = new ArrayList<String>();
    final Map<String, Environment> environments = new LinkedHashMap<String, Environment>();
    final Map<String, Obligation> obligations = new LinkedHashMap<String, Obligation>();
    final List<Requirement> requirements = new ArrayList<Requirement>();
    final List<Limitation> limitations = new ArrayList<Limitation>();

    private FoundationInventory(InventoryModel inventory, String path) {
        this.inventory = inventory;
        this.path = path;
    }

    static FoundationInventory load(InventoryModel inventory, String path, List<String> errors) {
        FoundationInventory result = new FoundationInventory(inventory, path);
        InventoryYaml root = result.read(path, errors);
        root.keys("schema-version", "release", "platform-decision", "environments", "requirements",
                "evidence", "source-roots", "source-exclusions", "required-artifacts", "obligations", "limitations");
        version(root);
        result.release = root.string("release");
        if (!"0.1.0".equals(result.release)) {
            root.error("release: this Foundation contract is specific to 0.1.0");
        }
        if (!result.release.equals(inventory.releaseTrain)
                && !(result.release + "-SNAPSHOT").equals(inventory.releaseTrain)) {
            root.error("Foundation release does not match the Capability Matrix Release Train");
        }
        result.decision = root.string("platform-decision");
        RepositoryFileResolver.validate(inventory.repositoryRoot, result.decision, "platform-decision", errors);
        result.environmentsPath = root.string("environments");
        result.requirementsPath = root.string("requirements");
        result.evidencePath = root.string("evidence");
        result.sourceRoots.addAll(root.strings("source-roots"));
        if (root.values.containsKey("source-exclusions")) {
            result.sourceExclusions.addAll(root.strings("source-exclusions"));
        }
        for (String exclusion : result.sourceExclusions) {
            if (!FoundationMarkdown.OUTPUT.equals(exclusion)) {
                root.error("only the generated readiness result may be excluded from source identity: " + exclusion);
            }
        }
        result.requiredArtifacts.addAll(root.strings("required-artifacts"));
        if (result.sourceRoots.isEmpty() || result.requiredArtifacts.isEmpty()) {
            root.error("source-roots and required-artifacts must declare the candidate identity boundary");
        }
        result.loadEnvironments(errors);
        for (InventoryYaml item : root.objects("obligations")) {
            Obligation obligation = new Obligation(item);
            put(result.obligations, obligation.id, obligation, item);
        }
        for (InventoryYaml item : root.objects("limitations")) {
            result.limitations.add(new Limitation(item));
        }
        result.loadRequirements(errors);
        result.validateLinks(errors);
        return result;
    }

    InventoryYaml read(String relative, List<String> errors) {
        Path file = RepositoryFileResolver.resolveForRead(inventory.repositoryRoot, relative);
        if (file == null) {
            RepositoryFileResolver.validate(inventory.repositoryRoot, relative, "Foundation authority", errors);
            return InventoryYaml.object(new LinkedHashMap<String, Object>(), relative, errors);
        }
        return InventoryYaml.load(file, errors);
    }

    private void loadEnvironments(List<String> errors) {
        InventoryYaml root = read(environmentsPath, errors);
        root.keys("schema-version", "uncertified-not-required", "profiles");
        version(root);
        if (!new HashSet<String>(root.strings("uncertified-not-required")).equals(
                new HashSet<String>(Arrays.asList("windows-x86-64", "macos-x86-64", "macos-arm64")))) {
            root.error("Windows x86-64 and macOS x86-64/arm64 must remain explicitly uncertified and not required");
        }
        Set<Integer> jdks = new HashSet<Integer>();
        for (InventoryYaml item : root.objects("profiles")) {
            Environment environment = new Environment(item);
            put(environments, environment.id, environment, item);
            if (!jdks.add(environment.jdk)) {
                item.error("duplicate required JDK profile");
            }
        }
        if (!jdks.equals(new HashSet<Integer>(Arrays.asList(8, 11, 17, 21)))) {
            root.error("required profiles must cover exactly JDK 8, 11, 17 and 21");
        }
    }

    private void loadRequirements(List<String> errors) {
        InventoryYaml root = read(requirementsPath, errors);
        root.keys("schema-version", "sources", "requirements");
        version(root);
        root.object("sources");
        Set<String> ids = new HashSet<String>();
        for (InventoryYaml item : root.objects("requirements")) {
            item.keys("id", "source", "requirement", "disposition", "reason", "obligations");
            Requirement requirement = new Requirement();
            requirement.id = item.string("id");
            requirement.source = item.string("source");
            requirement.text = item.string("requirement");
            requirement.disposition = item.string("disposition");
            requirement.reason = item.string("reason");
            requirement.obligations.addAll(item.strings("obligations"));
            if (!ids.add(requirement.id)) {
                item.error("duplicate requirement " + requirement.id);
            }
            if (!Arrays.asList("required", "later-release", "non-goal", "platform-amended", "historical")
                    .contains(requirement.disposition)) {
                item.error("unknown requirement disposition");
            }
            if ("required".equals(requirement.disposition) && requirement.obligations.isEmpty()) {
                item.error("required Foundation requirement has no obligation");
            }
            for (String id : requirement.obligations) {
                if (!obligations.containsKey(id)) {
                    item.error("unknown obligation " + id);
                } else {
                    obligations.get(id).requirements.add(requirement);
                }
            }
            requirements.add(requirement);
        }
        if (requirements.isEmpty()) {
            root.error("source requirement catalogue must not be empty");
        }
    }

    private void validateLinks(List<String> errors) {
        Map<String, InventoryModel.Capability> capabilities = capabilities();
        Set<String> covered = new HashSet<String>();
        for (Obligation obligation : obligations.values()) {
            String at = "Foundation obligation " + obligation.id + ": ";
            RepositoryFileResolver.validate(inventory.repositoryRoot, obligation.profileContract,
                    at + "Acceptance Profile contract", errors);
            if (obligation.requirements.isEmpty()) {
                errors.add(at + "missing source requirement mapping");
            }
            InventoryModel.Capability owner = capabilities.get(obligation.capability);
            if (owner == null && (obligation.parent.isEmpty() || !capabilities.containsKey(obligation.parent))) {
                errors.add(at + "unknown owning capability or subcapability parent " + obligation.capability);
            }
            if (owner != null && !obligation.parent.equals(owner.parentCapability)) {
                errors.add(at + "subcapability parent does not match Capability Matrix");
            }
            covered.add(obligation.parent.isEmpty() ? obligation.capability : obligation.parent);
            for (String dependency : obligation.dependencies) {
                if (!obligations.containsKey(dependency)) {
                    errors.add(at + "unknown dependent obligation " + dependency);
                }
            }
            for (String environment : obligation.environments) {
                if (!environments.containsKey(environment)) {
                    errors.add(at + "unknown required environment " + environment);
                }
            }
            if (obligation.environments.isEmpty() && !(obligation.kind == ObligationKind.AGGREGATE)) {
                errors.add(at + "at least one required environment is necessary");
            }
            for (String member : obligation.members) {
                Obligation child = obligations.get(member);
                if (child == null || !obligation.capability.equals(child.parent)) {
                    errors.add(at + "aggregate member must retain its parent capability: " + member);
                }
            }
        }
        for (InventoryModel.Capability capability : inventory.capabilities) {
            if (!covered.contains(capability.id) && capability.parentCapability.isEmpty()) {
                errors.add("Foundation has no obligation for capability " + capability.id);
            }
        }
        for (String id : obligations.keySet()) {
            visit(id, new HashSet<String>(), new HashSet<String>(), errors);
        }
        validateLimitations(capabilities, errors);
    }

    private void visit(String id, Set<String> active, Set<String> done, List<String> errors) {
        if (done.contains(id) || !obligations.containsKey(id)) {
            return;
        }
        if (!active.add(id)) {
            errors.add("Foundation obligation dependency cycle at " + id);
            return;
        }
        Obligation item = obligations.get(id);
        List<String> edges = new ArrayList<String>(item.dependencies);
        edges.addAll(item.members);
        for (String edge : edges) {
            visit(edge, active, done, errors);
        }
        active.remove(id);
        done.add(id);
    }

    private void validateLimitations(Map<String, InventoryModel.Capability> capabilities, List<String> errors) {
        Map<String, Set<String>> seen = new LinkedHashMap<String, Set<String>>();
        for (Limitation limitation : limitations) {
            InventoryModel.Capability capability = capabilities.get(limitation.capability);
            if (capability == null || !obligations.containsKey(limitation.obligation)) {
                errors.add("Foundation limitation has an unknown capability or obligation: " + limitation.capability);
                continue;
            }
            if (!seen.containsKey(limitation.capability)) {
                seen.put(limitation.capability, new HashSet<String>());
            }
            if (!seen.get(limitation.capability).add(limitation.sha256)) {
                errors.add("Foundation limitation classified twice: " + limitation.capability + " " + limitation.sha256);
            }
            boolean found = false;
            for (String text : capability.limitations) {
                found |= FoundationHashes.text(text).equals(limitation.sha256);
            }
            if (!found) {
                errors.add("Foundation limitation classification is stale: " + limitation.capability + " " + limitation.sha256);
            }
        }
        for (InventoryModel.Capability capability : capabilities.values()) {
            for (String text : capability.limitations) {
                if (!seen.containsKey(capability.id) || !seen.get(capability.id).contains(FoundationHashes.text(text))) {
                    errors.add("Foundation limitation missing classification: " + capability.id + " — " + text);
                }
            }
        }
    }

    Map<String, InventoryModel.Capability> capabilities() {
        Map<String, InventoryModel.Capability> result = new LinkedHashMap<String, InventoryModel.Capability>();
        for (InventoryModel.Capability capability : inventory.capabilities) {
            result.put(capability.id, capability);
        }
        return result;
    }

    private static void version(InventoryYaml item) {
        if (item.integer("schema-version") != 1) {
            item.error("unsupported schema-version; expected 1");
        }
    }

    private static <T> void put(Map<String, T> map, String id, T value, InventoryYaml item) {
        if (!id.matches("[a-z][a-z0-9.-]*")) {
            item.error("invalid identifier " + id);
        }
        if (map.put(id, value) != null) {
            item.error("duplicate identifier " + id);
        }
    }

    static final class Environment {
        final String id;
        final int jdk;
        final Map<String, Object> identity;

        Environment(InventoryYaml item) {
            item.keys("id", "identity");
            id = item.string("id");
            InventoryYaml fields = item.object("identity");
            fields.keys("os", "os-version", "architecture", "image", "os-release-sha256", "jdk-major",
                    "jdk-vendor", "jdk-build", "java-sha256");
            jdk = fields.integer("jdk-major");
            if (!"ubuntu".equals(fields.string("os")) || !"24.04".equals(fields.string("os-version"))
                    || !"x86-64".equals(fields.string("architecture"))) {
                item.error("Foundation 0.1.0 requires actual Ubuntu 24.04 / Linux x86-64 profiles");
            }
            String image = fields.string("image");
            if (!image.matches("[^\\s]+@sha256:[0-9a-f]{64}")) {
                item.error("image must be an immutable repository@sha256 identity");
            }
            fields.string("jdk-vendor");
            fields.string("jdk-build");
            FoundationHashes.requireHash(fields.string("java-sha256"), item.location, item.errors);
            FoundationHashes.requireHash(fields.string("os-release-sha256"), item.location, item.errors);
            identity = fields.values;
        }
    }

    static final class Obligation {
        final String id;
        final String summary;
        final String capability;
        final String parent;
        final String profile;
        final String profileContract;
        final ObligationKind kind;
        final int slice;
        final List<String> environments;
        final List<String> executions;
        final List<String> chains;
        final List<String> dependencies;
        final List<String> members;
        final List<FacadeFamily> facades = new ArrayList<FacadeFamily>();
        final String nativeOnlyReason;
        final List<Requirement> requirements = new ArrayList<Requirement>();
        final Map<String, Object> contract;

        Obligation(InventoryYaml item) {
            item.keys("id", "summary", "capability", "parent-capability", "acceptance-profile", "profile-contract",
                    "kind", "slice", "environments", "execution-profiles", "chains", "dependencies", "members",
                    "facade-families", "native-only-reason");
            id = item.string("id");
            summary = item.string("summary");
            capability = item.string("capability");
            parent = item.optional("parent-capability");
            profile = item.string("acceptance-profile");
            profileContract = item.string("profile-contract");
            kind = ObligationKind.from(item.string("kind"));
            slice = item.integer("slice");
            if (slice < 69 || slice > 97) {
                item.error("slice must be one of the approved #69–#97 slices");
            }
            if (kind == null) {
                item.error("unknown obligation kind");
            }
            environments = item.strings("environments");
            executions = item.strings("execution-profiles");
            chains = item.strings("chains");
            dependencies = item.strings("dependencies");
            members = item.strings("members");
            for (String chain : chains) {
                if (!CHAINS.contains(chain)) {
                    item.error("unknown mandatory evidence chain " + chain);
                }
            }
            if ((kind != null && kind.requiresPdfEvidence()) && !chains.containsAll(PDF_CHAINS)) {
                item.error("PDF behavior and controls must retain all four independent PDF evidence chains");
            }
            if (!(kind == ObligationKind.AGGREGATE) && (chains.isEmpty() || executions.isEmpty())) {
                item.error("evidence chains and execution profiles must not be empty");
            }
            for (String execution : executions) {
                if (!Arrays.asList("IN_PROCESS", "HARDENED_WORKER", "REPOSITORY").contains(execution)) {
                    item.error("unknown execution profile " + execution);
                }
            }
            if ((kind == ObligationKind.AGGREGATE) != !members.isEmpty()) {
                item.error("only aggregates must declare member obligations");
            }
            for (InventoryYaml family : item.objects("facade-families")) {
                facades.add(new FacadeFamily(family));
            }
            nativeOnlyReason = item.optional("native-only-reason");
            if ((kind == ObligationKind.BEHAVIOR) && facades.isEmpty()) {
                item.error("Foundation behavior requires a corresponding Migration Facade family");
            }
            if (((kind == ObligationKind.CONTROL) || (kind == ObligationKind.RELEASE))
                    && facades.isEmpty() && nativeOnlyReason.isEmpty()) {
                item.error("Native Interface only requires a reason identifying the project-specific control");
            }
            contract = item.values;
        }
    }

    static final class FacadeFamily {
        final String name;
        final String referencePrefix;
        final List<String> mappings;

        FacadeFamily(InventoryYaml item) {
            item.keys("family", "reference-prefix", "mappings");
            name = item.string("family");
            referencePrefix = item.string("reference-prefix");
            mappings = item.strings("mappings");
            if (!referencePrefix.startsWith("com.itextpdf.")) {
                item.error("facade family must name its Reference Suite namespace");
            }
        }
    }

    static final class Requirement {
        String id;
        String source;
        String text;
        String disposition;
        String reason;
        final List<String> obligations = new ArrayList<String>();
    }

    static final class Limitation {
        final String capability;
        final String sha256;
        final String disposition;
        final String reason;
        final String obligation;

        Limitation(InventoryYaml item) {
            item.keys("capability", "sha256", "disposition", "reason", "obligation");
            capability = item.string("capability");
            sha256 = item.string("sha256");
            disposition = item.string("disposition");
            reason = item.string("reason");
            obligation = item.string("obligation");
            FoundationHashes.requireHash(sha256, item.location, item.errors);
            if (!Arrays.asList("retained-contract", "release-blocker", "later-release", "platform-amended")
                    .contains(disposition)) {
                item.error("unknown limitation disposition");
            }
        }
    }
}

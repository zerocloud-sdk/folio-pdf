package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Evaluates retained evidence. It never runs acceptance tools or promotes a capability. */
final class FoundationReadiness {
    final FoundationInventory foundation;
    final List<String> global = new ArrayList<String>();
    final Map<String, List<String>> blockers = new LinkedHashMap<String, List<String>>();
    String candidateIdentity = "";
    String contractIdentity = "";

    FoundationReadiness(FoundationInventory foundation) {
        this.foundation = foundation;
    }

    void evaluate() throws IOException {
        evaluate(foundation.evidencePath);
    }

    void evaluate(String evidencePath) throws IOException {
        contractIdentity = contractIdentity();
        InventoryYaml evidence = foundation.read(evidencePath, global);
        evidence.keys("schema-version", "candidate", "environments", "certifications");
        if (evidence.integer("schema-version") != 1) {
            evidence.error("unsupported evidence schema-version");
        }
        verifyCandidate(evidence.object("candidate"));
        FoundationEvidence observations = new FoundationEvidence(this);
        observations.loadEnvironments(evidence.objects("environments"));
        observations.loadCertifications(evidence.objects("certifications"));
        Set<String> requiredSurfaces = new HashSet<String>();
        for (FoundationInventory.Obligation obligation : foundation.obligations.values()) {
            for (FoundationInventory.FacadeFamily family : obligation.facades) {
                requiredSurfaces.addAll(family.mappings);
            }
        }
        for (InventoryModel.Surface surface : foundation.inventory.stableSurfaces) {
            if (!requiredSurfaces.contains(surface.id)) {
                global.add("Stable Facade surface has no Foundation obligation mapping: " + surface.id);
            }
        }
        Map<String, InventoryModel.Capability> capabilities = foundation.capabilities();
        for (FoundationInventory.Obligation obligation : foundation.obligations.values()) {
            List<String> errors = new ArrayList<String>();
            blockers.put(obligation.id, errors);
            InventoryModel.Capability capability = capabilities.get(obligation.capability);
            if (!(obligation.kind == ObligationKind.RELEASE)) {
                if (capability == null) {
                    errors.add("missing independently certifiable subcapability " + obligation.capability
                            + " (aggregate " + obligation.parent + ")");
                } else {
                    verifyCapability(obligation, capability, capabilities, errors);
                }
            }
            verifyFacades(obligation, errors);
            for (FoundationInventory.Limitation limitation : foundation.limitations) {
                if (obligation.id.equals(limitation.obligation) && "release-blocker".equals(limitation.disposition)) {
                    errors.add("unresolved retained limitation: " + limitation.reason);
                }
            }
            if (!(obligation.kind == ObligationKind.AGGREGATE)) {
                observations.verify(obligation, errors);
            }
        }
        Set<String> visited = new HashSet<String>();
        for (String id : foundation.obligations.keySet()) {
            propagateDependencies(id, visited);
        }
    }

    boolean ready() {
        if (!global.isEmpty()) {
            return false;
        }
        for (List<String> errors : blockers.values()) {
            if (!errors.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void verifyCapability(FoundationInventory.Obligation obligation, InventoryModel.Capability capability,
            Map<String, InventoryModel.Capability> capabilities, List<String> errors) {
        if (capability.status != CapabilityState.COMPATIBLE) {
            errors.add("capability " + capability.id + " is " + capability.status + ", requires compatible");
        }
        if (!obligation.profile.equals(capability.acceptanceProfile.id)) {
            errors.add("Acceptance Profile mismatch: expected " + obligation.profile
                    + ", found " + capability.acceptanceProfile.id);
        }
        for (InventoryModel.DependencyGate dependency : capability.dependencyGates) {
            InventoryModel.Capability required = capabilities.get(dependency.capability);
            if (required == null || required.status != CapabilityState.COMPATIBLE) {
                errors.add("incompatible Dependency Gate " + dependency.capability);
            }
        }
        for (String environment : obligation.environments) {
            if (!capability.certifiedPlatforms.contains(environment)) {
                errors.add("missing certified environment " + environment + " on " + capability.id);
            }
        }
        for (String platform : capability.certifiedPlatforms) {
            if (!foundation.environments.containsKey(platform)) {
                errors.add("unknown or uncertified environment label " + platform + " on " + capability.id);
            }
        }
    }

    private void verifyFacades(FoundationInventory.Obligation obligation, List<String> errors) {
        for (FoundationInventory.FacadeFamily family : obligation.facades) {
            if (family.mappings.isEmpty()) {
                errors.add("missing required Facade mapping set for " + family.name);
            }
            for (String id : family.mappings) {
                InventoryModel.Surface match = null;
                for (InventoryModel.Surface surface : foundation.inventory.stableSurfaces) {
                    if (surface.id.equals(id)) {
                        match = surface;
                        break;
                    }
                }
                if (match == null || !match.capabilities.contains(obligation.capability)
                        || !match.referenceType.startsWith(family.referencePrefix)) {
                    errors.add("missing required Stable Facade mapping " + id + " for " + family.name);
                }
            }
        }
    }

    private void propagateDependencies(String id, Set<String> visited) {
        if (!visited.add(id)) {
            return;
        }
        FoundationInventory.Obligation obligation = foundation.obligations.get(id);
        List<String> dependencies = new ArrayList<String>(obligation.dependencies);
        dependencies.addAll(obligation.members);
        for (String dependency : dependencies) {
            propagateDependencies(dependency, visited);
            if (!blockers.get(dependency).isEmpty()) {
                blockers.get(id).add("incomplete prerequisite obligation " + dependency);
            }
        }
    }

    private String contractIdentity() throws IOException {
        Map<String, String> files = new TreeMap<String, String>();
        List<String> paths = new ArrayList<String>();
        Collections.addAll(paths, foundation.path, foundation.requirementsPath, foundation.environmentsPath,
                foundation.decision, relative(foundation.inventory.matrixPath), relative(foundation.inventory.facadePath));
        for (FoundationInventory.Obligation obligation : foundation.obligations.values()) {
            paths.add(obligation.profileContract);
        }
        for (String path : paths) {
            Path resolved = RepositoryFileResolver.resolveForRead(foundation.inventory.repositoryRoot, path);
            if (resolved == null) {
                global.add("missing Acceptance Profile or contract file " + path);
            } else {
                files.put(path, FoundationHashes.file(resolved));
            }
        }
        return FoundationHashes.text(identityLines("contract", files));
    }

    private void verifyCandidate(InventoryYaml candidate) throws IOException {
        candidate.keys("release", "inputs", "artifacts");
        if (candidate.values.isEmpty()) {
            global.add("missing final candidate source and artifact identity");
            return;
        }
        if (!foundation.release.equals(candidate.string("release"))) {
            candidate.error("candidate release must equal " + foundation.release);
        }
        Map<String, String> inputs = verifyFiles(candidate.objects("inputs"));
        Map<String, String> artifacts = verifyFiles(candidate.objects("artifacts"));
        Set<String> expectedInputs = sourceFiles();
        if (!inputs.keySet().equals(expectedInputs)) {
            Set<String> missing = new HashSet<String>(expectedInputs);
            missing.removeAll(inputs.keySet());
            Set<String> unexpected = new HashSet<String>(inputs.keySet());
            unexpected.removeAll(expectedInputs);
            candidate.error("source identity coverage mismatch; missing " + missing + "; unexpected " + unexpected);
        }
        if (!artifacts.keySet().equals(new HashSet<String>(foundation.requiredArtifacts))) {
            candidate.error("missing or unexpected required candidate artifacts; expected " + foundation.requiredArtifacts);
        }
        candidateIdentity = FoundationHashes.text("release " + foundation.release + "\n"
                + identityLines("input", inputs) + identityLines("artifact", artifacts));
    }

    private Map<String, String> verifyFiles(List<InventoryYaml> references) {
        Map<String, String> result = new TreeMap<String, String>();
        for (InventoryYaml reference : references) {
            FoundationHashes.verify(foundation.inventory.repositoryRoot, reference, global);
            String path = reference.string("path");
            if (result.put(path, reference.string("sha256")) != null) {
                reference.error("duplicate candidate file " + path);
            }
        }
        return result;
    }

    private Set<String> sourceFiles() throws IOException {
        Set<String> result = new HashSet<String>();
        Path root = foundation.inventory.repositoryRoot.toRealPath();
        for (String source : foundation.sourceRoots) {
            Path directory = root.resolve(source).normalize();
            if (!directory.startsWith(root) || !Files.exists(directory) || !directory.toRealPath().startsWith(root)) {
                global.add("missing or invalid candidate source root " + source);
                continue;
            }
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).filter(path -> !generatedCache(path))
                        .forEach(path -> result.add(root.relativize(path).toString().replace('\\', '/')));
            }
        }
        result.removeAll(foundation.sourceExclusions);
        return result;
    }

    private static boolean generatedCache(Path path) {
        for (Path component : path) {
            if ("__pycache__".equals(component.toString())) {
                return true;
            }
        }
        return false;
    }

    private String relative(Path path) {
        return foundation.inventory.repositoryRoot.relativize(path).toString().replace('\\', '/');
    }

    private static String identityLines(String kind, Map<String, String> files) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> file : files.entrySet()) {
            result.append(kind).append(' ').append(file.getKey()).append(' ').append(file.getValue()).append('\n');
        }
        return result.toString();
    }
}

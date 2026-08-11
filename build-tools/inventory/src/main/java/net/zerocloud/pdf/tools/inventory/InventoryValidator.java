package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

final class InventoryValidator {

    private static final Pattern STABLE_ID =
            Pattern.compile("^[a-z][a-z0-9]*(?:[.-][a-z0-9][a-z0-9-]*)*$");
    private static final Pattern PROFILE_ID =
            Pattern.compile("^[A-Za-z][A-Za-z0-9]*(?:[.-][A-Za-z0-9][A-Za-z0-9-]*)*$");
    private static final Pattern RELEASE_TRAIN =
            Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?$");
    private static final Pattern TICKET = Pattern.compile("^T[0-9]+$");
    private static final String REFERENCE_FACADE_PREFIX = "com.itextpdf.";
    private static final String FOLIO_PDF_FACADE_PREFIX = "net.zerocloud.pdf.itext7.";

    private static final Set<AcceptanceChain> BASE_ACCEPTANCE_CHAINS =
            Collections.unmodifiableSet(EnumSet.of(
                    AcceptanceChain.SYNTAX,
                    AcceptanceChain.STANDARDS,
                    AcceptanceChain.SEMANTIC,
                    AcceptanceChain.VISUAL));

    ValidationResult validate(Path repositoryRoot, Path matrixPath, Path facadePath) {
        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        Path normalizedMatrix = matrixPath.toAbsolutePath().normalize();
        Path normalizedFacade = facadePath.toAbsolutePath().normalize();
        InventoryModel model = new InventoryModel(normalizedRoot, normalizedMatrix, normalizedFacade);
        List<String> errors = new ArrayList<String>();

        Map<String, Object> matrix = loadYaml(normalizedMatrix, "capability-matrix", errors);
        Map<String, Object> facade = loadYaml(normalizedFacade, "facade-surface", errors);
        parseMatrix(matrix, model, errors);
        parseFacade(facade, model, errors);
        ReleaseTrainValidator.validate(model, errors);
        EvidenceRecordValidator.validate(model, errors);
        InventoryCrossValidator.validate(model, errors);

        return new ValidationResult(model, errors);
    }

    private static Map<String, Object> loadYaml(
            Path path, String authorityName, List<String> errors) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(50);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(3_000_000);
        Yaml yaml = new Yaml(new SafeConstructor(options));

        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            return map(loaded, authorityName, errors, true);
        } catch (IOException e) {
            errors.add(authorityName + ": cannot read " + path + ": " + e.getMessage());
        } catch (YAMLException e) {
            errors.add(authorityName + ": invalid YAML in " + path + ": " + compact(e.getMessage()));
        }
        return Collections.emptyMap();
    }

    private static void parseMatrix(
            Map<String, Object> root, InventoryModel model, List<String> errors) {
        checkKeys(root, setOf(
                "schema-version", "release-train", "authority", "capabilities"),
                "capability-matrix", errors);

        model.matrixSchemaVersion = requiredInteger(
                root, "schema-version", "capability-matrix", errors);
        if (model.matrixSchemaVersion != 1) {
            errors.add("capability-matrix.schema-version: unsupported value "
                    + model.matrixSchemaVersion + "; expected 1");
        }

        model.releaseTrain = requiredString(
                root, "release-train", "capability-matrix", errors);
        if (model.releaseTrain != null && !RELEASE_TRAIN.matcher(model.releaseTrain).matches()) {
            errors.add("capability-matrix.release-train: must be a semantic release-train version");
        }

        String authority = requiredString(root, "authority", "capability-matrix", errors);
        if (authority != null && !"behavioral-capability".equals(authority)) {
            errors.add("capability-matrix.authority: expected behavioral-capability");
        }

        List<Object> capabilities = requiredList(
                root, "capabilities", "capability-matrix", errors);
        if (capabilities.isEmpty()) {
            errors.add("capability-matrix.capabilities: at least one capability is required");
        }
        for (int index = 0; index < capabilities.size(); index++) {
            String path = "capability-matrix.capabilities[" + index + "]";
            Map<String, Object> value = map(capabilities.get(index), path, errors, true);
            model.capabilities.add(parseCapability(value, path, model.repositoryRoot, errors));
        }

        validateUniqueCapabilityIds(model.capabilities, errors);
    }

    private static InventoryModel.Capability parseCapability(
            Map<String, Object> value,
            String path,
            Path repositoryRoot,
            List<String> errors) {
        checkKeys(value, setOf(
                "id", "context", "summary", "reference-suite", "native-interface",
                "migration-facade", "limitations", "dependency-gates", "promotion-gates",
                "acceptance-profile", "evidence", "acceptance-evidence", "provenance",
                "certified-platforms", "status"), path, errors);

        InventoryModel.Capability capability = new InventoryModel.Capability();
        capability.id = requiredString(value, "id", path, errors);
        validateStableId(capability.id, path + ".id", errors);
        capability.context = requiredString(value, "context", path, errors);
        validateStableId(capability.context, path + ".context", errors);
        capability.summary = requiredString(value, "summary", path, errors);

        Map<String, Object> reference = requiredMap(value, "reference-suite", path, errors);
        checkKeys(reference, setOf("source", "role"), path + ".reference-suite", errors);
        capability.referenceSource = requiredString(
                reference, "source", path + ".reference-suite", errors);
        capability.referenceRole = requiredString(
                reference, "role", path + ".reference-suite", errors);

        Map<String, Object> nativeInterface = requiredMap(
                value, "native-interface", path, errors);
        if (nativeInterface.isEmpty()) {
            errors.add(path + ".native-interface: at least one mapping is required");
        }
        for (Map.Entry<String, Object> entry : nativeInterface.entrySet()) {
            String fieldPath = path + ".native-interface." + entry.getKey();
            validateStableId(entry.getKey(), fieldPath + " (field name)", errors);
            String mapping = string(entry.getValue(), fieldPath, errors, true);
            if (mapping != null) {
                capability.nativeInterface.put(entry.getKey(), mapping);
            }
        }

        Map<String, Object> migration = requiredMap(value, "migration-facade", path, errors);
        checkKeys(migration, setOf("stable", "preview"), path + ".migration-facade", errors);
        capability.stableFacadeIds.addAll(stringList(
                requiredList(migration, "stable", path + ".migration-facade", errors),
                path + ".migration-facade.stable", errors));
        capability.previewFacadeIds.addAll(stringList(
                requiredList(migration, "preview", path + ".migration-facade", errors),
                path + ".migration-facade.preview", errors));
        validateStableIds(capability.stableFacadeIds, path + ".migration-facade.stable", errors);
        validateStableIds(capability.previewFacadeIds, path + ".migration-facade.preview", errors);
        validateNoDuplicates(capability.stableFacadeIds, path + ".migration-facade.stable", errors);
        validateNoDuplicates(capability.previewFacadeIds, path + ".migration-facade.preview", errors);

        capability.limitations.addAll(stringList(
                requiredList(value, "limitations", path, errors), path + ".limitations", errors));

        List<Object> dependencies = requiredList(value, "dependency-gates", path, errors);
        for (int index = 0; index < dependencies.size(); index++) {
            String dependencyPath = path + ".dependency-gates[" + index + "]";
            Map<String, Object> dependency = map(
                    dependencies.get(index), dependencyPath, errors, true);
            checkKeys(dependency, setOf("capability", "required-status"), dependencyPath, errors);
            InventoryModel.DependencyGate gate = new InventoryModel.DependencyGate();
            gate.capability = requiredString(dependency, "capability", dependencyPath, errors);
            validateStableId(gate.capability, dependencyPath + ".capability", errors);
            String requiredStatus = requiredString(
                    dependency, "required-status", dependencyPath, errors);
            gate.requiredStatus = CapabilityState.from(requiredStatus);
            if (requiredStatus != null
                    && gate.requiredStatus != CapabilityState.COMPATIBLE) {
                errors.add(dependencyPath
                        + ".required-status: Dependency Gates must require compatible");
            }
            capability.dependencyGates.add(gate);
        }
        validateUniqueDependencies(capability, path, errors);

        List<Object> promotions = requiredList(value, "promotion-gates", path, errors);
        for (int index = 0; index < promotions.size(); index++) {
            String promotionPath = path + ".promotion-gates[" + index + "]";
            Map<String, Object> promotion = map(
                    promotions.get(index), promotionPath, errors, true);
            checkKeys(promotion, setOf("ticket", "requirement"), promotionPath, errors);
            InventoryModel.PromotionGate gate = new InventoryModel.PromotionGate();
            gate.ticket = requiredString(promotion, "ticket", promotionPath, errors);
            validateTicket(gate.ticket, promotionPath + ".ticket", errors);
            gate.requirement = requiredString(promotion, "requirement", promotionPath, errors);
            capability.promotionGates.add(gate);
        }

        Map<String, Object> profile = requiredMap(value, "acceptance-profile", path, errors);
        checkKeys(profile, setOf("id", "state", "mandatory-evidence", "evidence-record"),
                path + ".acceptance-profile", errors);
        capability.acceptanceProfile = new InventoryModel.AcceptanceProfile();
        capability.acceptanceProfile.id = requiredString(
                profile, "id", path + ".acceptance-profile", errors);
        if (capability.acceptanceProfile.id != null
                && !PROFILE_ID.matcher(capability.acceptanceProfile.id).matches()) {
            errors.add(path + ".acceptance-profile.id: invalid stable identifier");
        }
        String profileState = requiredString(
                profile, "state", path + ".acceptance-profile", errors);
        capability.acceptanceProfile.state = CapabilityState.from(profileState);
        if (profileState != null && capability.acceptanceProfile.state == null) {
            errors.add(path + ".acceptance-profile.state: unsupported capability state "
                    + profileState);
        }
        List<String> mandatoryEvidence = stringList(
                requiredList(profile, "mandatory-evidence", path + ".acceptance-profile", errors),
                path + ".acceptance-profile.mandatory-evidence", errors);
        for (String chainName : mandatoryEvidence) {
            AcceptanceChain chain = AcceptanceChain.from(chainName);
            if (chain == null) {
                errors.add(path + ".acceptance-profile.mandatory-evidence: unsupported "
                        + "Acceptance Evidence chain " + chainName);
            } else {
                capability.acceptanceProfile.mandatoryEvidence.add(chain);
            }
        }
        capability.acceptanceProfile.evidenceRecord = optionalString(
                profile, "evidence-record", path + ".acceptance-profile", errors);
        validateNoDuplicates(capability.acceptanceProfile.mandatoryEvidence,
                path + ".acceptance-profile.mandatory-evidence", errors);
        for (AcceptanceChain requiredChain : BASE_ACCEPTANCE_CHAINS) {
            if (!capability.acceptanceProfile.mandatoryEvidence.contains(requiredChain)) {
                errors.add(path + ".acceptance-profile.mandatory-evidence: missing required chain "
                        + requiredChain);
            }
        }
        validateRelativeFile(repositoryRoot, capability.acceptanceProfile.evidenceRecord,
                path + ".acceptance-profile.evidence-record", errors);

        List<Object> implementationEvidence = requiredList(value, "evidence", path, errors);
        for (int index = 0; index < implementationEvidence.size(); index++) {
            String evidencePath = path + ".evidence[" + index + "]";
            Map<String, Object> evidence = map(
                    implementationEvidence.get(index), evidencePath, errors, true);
            checkKeys(evidence, setOf("kind", "path", "assertion"), evidencePath, errors);
            InventoryModel.ImplementationEvidence item =
                    new InventoryModel.ImplementationEvidence();
            item.kind = requiredString(evidence, "kind", evidencePath, errors);
            validateStableId(item.kind, evidencePath + ".kind", errors);
            item.path = requiredString(evidence, "path", evidencePath, errors);
            item.assertion = requiredString(evidence, "assertion", evidencePath, errors);
            validateRelativeFile(repositoryRoot, item.path, evidencePath + ".path", errors);
            capability.implementationEvidence.add(item);
        }

        List<Object> acceptanceEvidence = requiredList(value, "acceptance-evidence", path, errors);
        Set<Path> acceptanceRecords = new HashSet<Path>();
        for (int index = 0; index < acceptanceEvidence.size(); index++) {
            String evidencePath = path + ".acceptance-evidence[" + index + "]";
            Map<String, Object> evidence = map(
                    acceptanceEvidence.get(index), evidencePath, errors, true);
            checkKeys(evidence, setOf("chain", "result", "record", "producer"),
                    evidencePath, errors);
            InventoryModel.AcceptanceEvidence item = new InventoryModel.AcceptanceEvidence();
            String chainName = requiredString(evidence, "chain", evidencePath, errors);
            item.chain = AcceptanceChain.from(chainName);
            if (chainName != null && item.chain == null) {
                errors.add(evidencePath + ".chain: unsupported Acceptance Evidence chain "
                        + chainName);
            }
            String resultName = requiredString(evidence, "result", evidencePath, errors);
            item.result = EvidenceResult.from(resultName);
            if (resultName != null && item.result == null) {
                errors.add(evidencePath + ".result: unsupported result " + resultName);
            }
            item.record = requiredString(evidence, "record", evidencePath, errors);
            validateRelativeFile(repositoryRoot, item.record, evidencePath + ".record", errors);
            Path canonicalRecord = RepositoryFileResolver.resolveForRead(
                    repositoryRoot, item.record);
            if (canonicalRecord != null && !acceptanceRecords.add(canonicalRecord)) {
                errors.add(evidencePath
                        + ".record: independent evidence chains require distinct records");
            }
            Map<String, Object> producer = requiredMap(
                    evidence, "producer", evidencePath, errors);
            checkKeys(producer, setOf("kind", "name", "version"),
                    evidencePath + ".producer", errors);
            String producerKind = requiredString(
                    producer, "kind", evidencePath + ".producer", errors);
            item.producerKind = ProducerKind.from(producerKind);
            if (producerKind != null && item.producerKind == null) {
                errors.add(evidencePath + ".producer.kind: unsupported producer kind "
                        + producerKind);
            }
            item.producerName = requiredString(
                    producer, "name", evidencePath + ".producer", errors);
            validateStableId(item.producerName, evidencePath + ".producer.name", errors);
            item.producerVersion = requiredString(
                    producer, "version", evidencePath + ".producer", errors);
            validateProducerKind(item, evidencePath, errors);
            capability.acceptanceEvidence.add(item);
        }
        validateUniqueAcceptanceChains(capability, path, errors);
        validateIndependentEvidenceProducers(capability, path, errors);
        validateIndependentEvidencePaths(repositoryRoot, capability, path, errors);

        Map<String, Object> provenance = requiredMap(value, "provenance", path, errors);
        checkKeys(provenance, setOf("path", "record"), path + ".provenance", errors);
        capability.provenancePath = requiredString(
                provenance, "path", path + ".provenance", errors);
        capability.provenanceRecord = requiredString(
                provenance, "record", path + ".provenance", errors);
        validateRelativeFile(repositoryRoot, capability.provenancePath,
                path + ".provenance.path", errors);

        capability.certifiedPlatforms.addAll(stringList(
                requiredList(value, "certified-platforms", path, errors),
                path + ".certified-platforms", errors));
        validateNoDuplicates(capability.certifiedPlatforms,
                path + ".certified-platforms", errors);

        String status = requiredString(value, "status", path, errors);
        capability.status = CapabilityState.from(status);
        if (status != null && capability.status == null) {
            errors.add(path + ".status: unsupported capability state " + status);
        }
        validateCapabilityState(capability, path, errors);
        return capability;
    }

    private static void validateCapabilityState(
            InventoryModel.Capability capability, String path, List<String> errors) {
        if (capability.acceptanceProfile != null
                && capability.status != null
                && capability.acceptanceProfile.state != null
                && !capability.status.equals(capability.acceptanceProfile.state)) {
            errors.add(path + ".acceptance-profile.state: must equal capability status "
                    + capability.status);
        }
        if (capability.status == null) {
            return;
        }

        boolean hasProfileRecord = capability.acceptanceProfile != null
                && capability.acceptanceProfile.evidenceRecord != null;
        if (capability.status == CapabilityState.PLANNED) {
            if (!capability.implementationEvidence.isEmpty()) {
                errors.add(path + ".evidence: planned capabilities cannot claim implementation evidence");
            }
            if (!capability.acceptanceEvidence.isEmpty()) {
                errors.add(path
                        + ".acceptance-evidence: planned capabilities cannot claim Acceptance Evidence");
            }
            if (hasProfileRecord) {
                errors.add(path + ".acceptance-profile.evidence-record: planned capabilities cannot "
                        + "claim an evidence record");
            }
            if (!capability.certifiedPlatforms.isEmpty()) {
                errors.add(path
                        + ".certified-platforms: planned capabilities cannot claim certified platforms");
            }
            return;
        }

        if (capability.implementationEvidence.isEmpty()) {
            errors.add(path + ".evidence: " + capability.status
                    + " capabilities require implementation evidence");
        }
        if (!hasProfileRecord) {
            errors.add(path + ".acceptance-profile.evidence-record: " + capability.status
                    + " capabilities require an evidence record");
        }

        if (capability.status == CapabilityState.EXPERIMENTAL) {
            if (!capability.certifiedPlatforms.isEmpty()) {
                errors.add(path + ".certified-platforms: experimental capabilities cannot claim "
                        + "certified platforms");
            }
            return;
        }

        Map<AcceptanceChain, InventoryModel.AcceptanceEvidence> evidenceByChain =
                new HashMap<AcceptanceChain, InventoryModel.AcceptanceEvidence>();
        for (InventoryModel.AcceptanceEvidence item : capability.acceptanceEvidence) {
            if (item.chain != null) {
                evidenceByChain.put(item.chain, item);
            }
        }
        for (AcceptanceChain mandatory : capability.acceptanceProfile.mandatoryEvidence) {
            InventoryModel.AcceptanceEvidence item = evidenceByChain.get(mandatory);
            if (item == null || item.result != EvidenceResult.PASS) {
                errors.add(path + ".acceptance-evidence: " + capability.status
                        + " requires passing mandatory chain " + mandatory);
            }
        }
        if (!capability.promotionGates.isEmpty()) {
            errors.add(path + ".promotion-gates: " + capability.status
                    + " capabilities cannot retain an open promotion gate");
        }
        if (capability.status == CapabilityState.LIMITED && capability.limitations.isEmpty()) {
            errors.add(path + ".limitations: limited capabilities require an explicit limitation");
        }
    }

    private static void parseFacade(
            Map<String, Object> root, InventoryModel model, List<String> errors) {
        checkKeys(root, setOf(
                "schema-version", "release-train", "authority", "surfaces",
                "excluded-capabilities"), "facade-surface", errors);

        model.facadeSchemaVersion = requiredInteger(root, "schema-version", "facade-surface", errors);
        if (model.facadeSchemaVersion != 1) {
            errors.add("facade-surface.schema-version: unsupported value "
                    + model.facadeSchemaVersion + "; expected 1");
        }
        String releaseTrain = requiredString(root, "release-train", "facade-surface", errors);
        if (releaseTrain != null && !RELEASE_TRAIN.matcher(releaseTrain).matches()) {
            errors.add("facade-surface.release-train: must be a semantic release-train version");
        }
        if (releaseTrain != null && model.releaseTrain != null
                && !releaseTrain.equals(model.releaseTrain)) {
            errors.add("facade-surface.release-train: must equal Capability Matrix release train "
                    + model.releaseTrain);
        }
        String authority = requiredString(root, "authority", "facade-surface", errors);
        if (authority != null && !"migration-source-surface".equals(authority)) {
            errors.add("facade-surface.authority: expected migration-source-surface");
        }

        Map<String, Object> surfaces = requiredMap(root, "surfaces", "facade-surface", errors);
        checkKeys(surfaces, setOf("stable", "preview"), "facade-surface.surfaces", errors);
        parseSurfaces(requiredList(surfaces, "stable", "facade-surface.surfaces", errors),
                FacadeAvailability.STABLE, model.stableSurfaces, errors);
        parseSurfaces(requiredList(surfaces, "preview", "facade-surface.surfaces", errors),
                FacadeAvailability.PREVIEW, model.previewSurfaces, errors);

        List<Object> exclusions = requiredList(
                root, "excluded-capabilities", "facade-surface", errors);
        for (int index = 0; index < exclusions.size(); index++) {
            String path = "facade-surface.excluded-capabilities[" + index + "]";
            Map<String, Object> value = map(exclusions.get(index), path, errors, true);
            checkKeys(value, setOf("id", "ticket", "reason"), path, errors);
            InventoryModel.Exclusion exclusion = new InventoryModel.Exclusion();
            exclusion.capability = requiredString(value, "id", path, errors);
            validateStableId(exclusion.capability, path + ".id", errors);
            exclusion.ticket = requiredString(value, "ticket", path, errors);
            validateTicket(exclusion.ticket, path + ".ticket", errors);
            exclusion.reason = requiredString(value, "reason", path, errors);
            model.exclusions.add(exclusion);
        }

        validateUniqueSurfaceIds(model, errors);
        validateUniqueExclusions(model.exclusions, errors);
    }

    private static void parseSurfaces(
            List<Object> values,
            FacadeAvailability availability,
            List<InventoryModel.Surface> destination,
            List<String> errors) {
        for (int index = 0; index < values.size(); index++) {
            String path = "facade-surface.surfaces." + availability + "[" + index + "]";
            Map<String, Object> value = map(values.get(index), path, errors, true);
            checkKeys(value, setOf(
                    "id", "reference", "folio-pdf", "generic-contract", "exception-contract",
                    "capabilities"), path, errors);
            InventoryModel.Surface surface = new InventoryModel.Surface();
            surface.availability = availability;
            surface.id = requiredString(value, "id", path, errors);
            validateStableId(surface.id, path + ".id", errors);

            Map<String, Object> reference = requiredMap(value, "reference", path, errors);
            checkKeys(reference, setOf("type", "member"), path + ".reference", errors);
            surface.referenceType = requiredString(reference, "type", path + ".reference", errors);
            surface.referenceMember = requiredString(
                    reference, "member", path + ".reference", errors);

            Map<String, Object> folioPdf = requiredMap(value, "folio-pdf", path, errors);
            checkKeys(folioPdf, setOf("type", "member"), path + ".folio-pdf", errors);
            surface.folioPdfType = requiredString(folioPdf, "type", path + ".folio-pdf", errors);
            surface.folioPdfMember = requiredString(folioPdf, "member", path + ".folio-pdf", errors);
            validateFacadeTypeMapping(surface, path, errors);

            surface.genericContract = requiredString(value, "generic-contract", path, errors);
            surface.exceptionContract = requiredString(value, "exception-contract", path, errors);
            surface.capabilities.addAll(stringList(
                    requiredList(value, "capabilities", path, errors),
                    path + ".capabilities", errors));
            if (surface.capabilities.isEmpty()) {
                errors.add(path + ".capabilities: at least one capability reference is required");
            }
            validateStableIds(surface.capabilities, path + ".capabilities", errors);
            validateNoDuplicates(surface.capabilities, path + ".capabilities", errors);
            destination.add(surface);
        }
    }

    private static void validateUniqueCapabilityIds(
            List<InventoryModel.Capability> capabilities, List<String> errors) {
        Set<String> ids = new HashSet<String>();
        Set<String> profileIds = new HashSet<String>();
        for (InventoryModel.Capability capability : capabilities) {
            if (capability.id != null && !ids.add(capability.id)) {
                errors.add("capability-matrix.capabilities: duplicate capability id " + capability.id);
            }
            if (capability.acceptanceProfile != null
                    && capability.acceptanceProfile.id != null
                    && !profileIds.add(capability.acceptanceProfile.id)) {
                errors.add("capability-matrix.capabilities: duplicate Acceptance Profile id "
                        + capability.acceptanceProfile.id);
            }
        }
    }

    private static void validateUniqueSurfaceIds(InventoryModel model, List<String> errors) {
        Set<String> ids = new HashSet<String>();
        List<InventoryModel.Surface> all = new ArrayList<InventoryModel.Surface>();
        all.addAll(model.stableSurfaces);
        all.addAll(model.previewSurfaces);
        for (InventoryModel.Surface surface : all) {
            if (surface.id != null && !ids.add(surface.id)) {
                errors.add("facade-surface.surfaces: duplicate surface id " + surface.id);
            }
        }
    }

    private static void validateUniqueExclusions(
            List<InventoryModel.Exclusion> exclusions, List<String> errors) {
        Set<String> ids = new HashSet<String>();
        for (InventoryModel.Exclusion exclusion : exclusions) {
            if (exclusion.capability != null && !ids.add(exclusion.capability)) {
                errors.add("facade-surface.excluded-capabilities: duplicate exclusion for "
                        + exclusion.capability);
            }
        }
    }

    private static void validateUniqueDependencies(
            InventoryModel.Capability capability, String path, List<String> errors) {
        Set<String> ids = new HashSet<String>();
        for (InventoryModel.DependencyGate gate : capability.dependencyGates) {
            if (gate.capability != null && !ids.add(gate.capability)) {
                errors.add(path + ".dependency-gates: duplicate gate for " + gate.capability);
            }
        }
    }

    private static void validateUniqueAcceptanceChains(
            InventoryModel.Capability capability, String path, List<String> errors) {
        Set<AcceptanceChain> chains = new HashSet<AcceptanceChain>();
        for (InventoryModel.AcceptanceEvidence evidence : capability.acceptanceEvidence) {
            if (evidence.chain != null && !chains.add(evidence.chain)) {
                errors.add(path + ".acceptance-evidence: duplicate chain " + evidence.chain);
            }
        }
    }

    private static void validateProducerKind(
            InventoryModel.AcceptanceEvidence evidence,
            String path,
            List<String> errors) {
        if (evidence.chain == null || evidence.producerKind == null) {
            return;
        }
        ProducerKind expected = evidence.chain.requiredProducerKind();
        if (expected != evidence.producerKind) {
            errors.add(path + ".producer.kind: " + evidence.chain
                    + " evidence requires producer kind " + expected);
        }
    }

    private static void validateIndependentEvidenceProducers(
            InventoryModel.Capability capability, String path, List<String> errors) {
        Set<String> producers = new HashSet<String>();
        for (InventoryModel.AcceptanceEvidence evidence : capability.acceptanceEvidence) {
            if (evidence.producerName != null && !producers.add(evidence.producerName)) {
                errors.add(path + ".acceptance-evidence: independent chains require distinct "
                        + "producer names; repeated " + evidence.producerName);
            }
        }
    }

    private static void validateIndependentEvidencePaths(
            Path repositoryRoot,
            InventoryModel.Capability capability,
            String path,
            List<String> errors) {
        Set<Path> nonAcceptanceRecords = new HashSet<Path>();
        if (capability.acceptanceProfile != null
                && capability.acceptanceProfile.evidenceRecord != null) {
            Path profileRecord = RepositoryFileResolver.resolveForRead(
                    repositoryRoot, capability.acceptanceProfile.evidenceRecord);
            if (profileRecord != null) {
                nonAcceptanceRecords.add(profileRecord);
            }
        }
        for (InventoryModel.ImplementationEvidence evidence : capability.implementationEvidence) {
            Path implementationRecord = RepositoryFileResolver.resolveForRead(
                    repositoryRoot, evidence.path);
            if (implementationRecord != null) {
                nonAcceptanceRecords.add(implementationRecord);
            }
        }
        for (InventoryModel.AcceptanceEvidence evidence : capability.acceptanceEvidence) {
            Path acceptanceRecord = RepositoryFileResolver.resolveForRead(
                    repositoryRoot, evidence.record);
            if (acceptanceRecord != null
                    && nonAcceptanceRecords.contains(acceptanceRecord)) {
                errors.add(path + ".acceptance-evidence: independent chain record "
                        + evidence.record + " cannot reuse implementation or profile evidence");
            }
        }
    }

    private static void validateFacadeTypeMapping(
            InventoryModel.Surface surface, String path, List<String> errors) {
        if (surface.referenceType == null || surface.folioPdfType == null) {
            return;
        }
        if (!surface.referenceType.startsWith(REFERENCE_FACADE_PREFIX)
                || surface.referenceType.length() == REFERENCE_FACADE_PREFIX.length()) {
            errors.add(path + ".reference.type: expected a type below "
                    + REFERENCE_FACADE_PREFIX);
            return;
        }
        String suffix = surface.referenceType.substring(REFERENCE_FACADE_PREFIX.length());
        String expected = FOLIO_PDF_FACADE_PREFIX + suffix;
        if (!expected.equals(surface.folioPdfType)) {
            errors.add(path + ".folio-pdf.type: must preserve the reference suffix as "
                    + expected);
        }
    }

    private static void validateStableIds(
            List<String> ids, String path, List<String> errors) {
        for (int index = 0; index < ids.size(); index++) {
            validateStableId(ids.get(index), path + "[" + index + "]", errors);
        }
    }

    private static void validateStableId(String id, String path, List<String> errors) {
        if (id != null && !STABLE_ID.matcher(id).matches()) {
            errors.add(path + ": invalid stable identifier " + id);
        }
    }

    private static void validateTicket(String ticket, String path, List<String> errors) {
        if (ticket != null && !TICKET.matcher(ticket).matches()) {
            errors.add(path + ": expected a ticket identifier such as T04");
        }
    }

    private static void validateRelativeFile(
            Path repositoryRoot, String value, String path, List<String> errors) {
        RepositoryFileResolver.validate(repositoryRoot, value, path, errors);
    }

    private static <T> void validateNoDuplicates(
            List<T> values, String path, List<String> errors) {
        Set<T> unique = new HashSet<T>();
        for (T value : values) {
            if (!unique.add(value)) {
                errors.add(path + ": duplicate value " + value);
            }
        }
    }

    private static void checkKeys(
            Map<String, Object> value,
            Set<String> allowed,
            String path,
            List<String> errors) {
        for (String key : value.keySet()) {
            if (!allowed.contains(key)) {
                errors.add(path + ": unsupported field " + key);
            }
        }
    }

    private static Map<String, Object> requiredMap(
            Map<String, Object> parent, String key, String path, List<String> errors) {
        return map(parent.get(key), path + "." + key, errors, true);
    }

    private static List<Object> requiredList(
            Map<String, Object> parent, String key, String path, List<String> errors) {
        Object value = parent.get(key);
        String valuePath = path + "." + key;
        if (value == null) {
            errors.add(valuePath + ": field is required");
            return Collections.emptyList();
        }
        if (!(value instanceof List<?>)) {
            errors.add(valuePath + ": expected a sequence");
            return Collections.emptyList();
        }
        return new ArrayList<Object>((List<?>) value);
    }

    private static String requiredString(
            Map<String, Object> parent, String key, String path, List<String> errors) {
        return string(parent.get(key), path + "." + key, errors, true);
    }

    private static String optionalString(
            Map<String, Object> parent, String key, String path, List<String> errors) {
        if (!parent.containsKey(key)) {
            return null;
        }
        return string(parent.get(key), path + "." + key, errors, true);
    }

    private static String string(
            Object value, String path, List<String> errors, boolean required) {
        if (value == null) {
            if (required) {
                errors.add(path + ": field is required");
            }
            return null;
        }
        if (!(value instanceof String)) {
            errors.add(path + ": expected a string");
            return null;
        }
        String result = ((String) value).trim();
        if (result.isEmpty()) {
            errors.add(path + ": value cannot be blank");
            return null;
        }
        return result;
    }

    private static int requiredInteger(
            Map<String, Object> parent, String key, String path, List<String> errors) {
        Object value = parent.get(key);
        String valuePath = path + "." + key;
        if (!(value instanceof Number)) {
            errors.add(valuePath + ": expected an integer");
            return -1;
        }
        Number number = (Number) value;
        int result = number.intValue();
        if (number.doubleValue() != (double) result) {
            errors.add(valuePath + ": expected an integer");
            return -1;
        }
        return result;
    }

    private static List<String> stringList(
            List<Object> values, String path, List<String> errors) {
        List<String> result = new ArrayList<String>();
        for (int index = 0; index < values.size(); index++) {
            String value = string(values.get(index), path + "[" + index + "]", errors, true);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static Map<String, Object> map(
            Object value, String path, List<String> errors, boolean required) {
        if (value == null) {
            if (required) {
                errors.add(path + ": field is required");
            }
            return Collections.emptyMap();
        }
        if (!(value instanceof Map<?, ?>)) {
            errors.add(path + ": expected a mapping");
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                errors.add(path + ": mapping keys must be strings");
                continue;
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static String compact(String value) {
        if (value == null) {
            return "unknown parser error";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

}

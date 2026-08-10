package net.zerocloud.pdf.tools.inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InventoryModel {

    final Path repositoryRoot;
    final Path matrixPath;
    final Path facadePath;
    int matrixSchemaVersion;
    int facadeSchemaVersion;
    String releaseTrain;
    final List<Capability> capabilities = new ArrayList<Capability>();
    final List<Surface> stableSurfaces = new ArrayList<Surface>();
    final List<Surface> previewSurfaces = new ArrayList<Surface>();
    final List<Exclusion> exclusions = new ArrayList<Exclusion>();

    InventoryModel(Path repositoryRoot, Path matrixPath, Path facadePath) {
        this.repositoryRoot = repositoryRoot;
        this.matrixPath = matrixPath;
        this.facadePath = facadePath;
    }

    static final class Capability {
        String id;
        String context;
        String summary;
        CapabilityState status;
        String referenceSource;
        String referenceRole;
        final Map<String, String> nativeInterface = new LinkedHashMap<String, String>();
        final List<String> stableFacadeIds = new ArrayList<String>();
        final List<String> previewFacadeIds = new ArrayList<String>();
        final List<String> limitations = new ArrayList<String>();
        final List<DependencyGate> dependencyGates = new ArrayList<DependencyGate>();
        final List<PromotionGate> promotionGates = new ArrayList<PromotionGate>();
        AcceptanceProfile acceptanceProfile;
        final List<ImplementationEvidence> implementationEvidence =
                new ArrayList<ImplementationEvidence>();
        final List<AcceptanceEvidence> acceptanceEvidence =
                new ArrayList<AcceptanceEvidence>();
        String provenancePath;
        String provenanceRecord;
        final List<String> certifiedPlatforms = new ArrayList<String>();
    }

    static final class DependencyGate {
        String capability;
        CapabilityState requiredStatus;
    }

    static final class PromotionGate {
        String ticket;
        String requirement;
    }

    static final class AcceptanceProfile {
        String id;
        CapabilityState state;
        String evidenceRecord;
        final List<AcceptanceChain> mandatoryEvidence = new ArrayList<AcceptanceChain>();
    }

    static final class ImplementationEvidence {
        String kind;
        String path;
        String assertion;
    }

    static final class AcceptanceEvidence {
        AcceptanceChain chain;
        EvidenceResult result;
        String record;
        ProducerKind producerKind;
        String producerName;
        String producerVersion;
    }

    static final class Surface {
        FacadeAvailability availability;
        String id;
        String referenceType;
        String referenceMember;
        String openPdfType;
        String openPdfMember;
        String genericContract;
        String exceptionContract;
        final List<String> capabilities = new ArrayList<String>();
    }

    static final class Exclusion {
        String capability;
        String ticket;
        String reason;
    }
}

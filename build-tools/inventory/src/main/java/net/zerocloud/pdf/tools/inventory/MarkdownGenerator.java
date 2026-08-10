package net.zerocloud.pdf.tools.inventory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MarkdownGenerator {

    static final String CAPABILITY_OUTPUT = "docs/generated/capability-matrix.md";
    static final String FACADE_OUTPUT = "docs/generated/facade-surface.md";

    Map<Path, String> generate(InventoryModel model) {
        Map<Path, String> documents = new HashMap<Path, String>();
        Path capabilityOutput = model.repositoryRoot.resolve(CAPABILITY_OUTPUT).normalize();
        Path facadeOutput = model.repositoryRoot.resolve(FACADE_OUTPUT).normalize();
        documents.put(capabilityOutput, capabilityDocument(model, capabilityOutput, facadeOutput));
        documents.put(facadeOutput, facadeDocument(model, facadeOutput, capabilityOutput));
        return documents;
    }

    private static String capabilityDocument(
            InventoryModel model, Path output, Path facadeOutput) {
        StringBuilder text = new StringBuilder();
        generatedHeader(text);
        text.append("# Capability Matrix\n\n");
        text.append("Behavioral authority: [")
                .append(code(relative(output, model.matrixPath)))
                .append("](").append(relative(output, model.matrixPath)).append(")\n\n");
        text.append("- Schema version: \u0060").append(model.matrixSchemaVersion).append("\u0060\n");
        text.append("- Release train: \u0060").append(codeText(model.releaseTrain)).append("\u0060\n");
        text.append("- Capabilities: \u0060").append(model.capabilities.size()).append("\u0060\n\n");

        text.append("## Capability summary\n\n");
        text.append("| Capability | Context | Status | Migration facade |\n");
        text.append("| --- | --- | --- | --- |\n");
        List<InventoryModel.Capability> capabilities =
                new ArrayList<InventoryModel.Capability>(model.capabilities);
        Collections.sort(capabilities, capabilityComparator());
        for (InventoryModel.Capability capability : capabilities) {
            text.append("| [").append(code(capability.id)).append("](#")
                    .append(capabilityAnchor(capability.id)).append(") | ")
                    .append(code(capability.context)).append(" | ")
                    .append(code(capability.status)).append(" | ")
                    .append(facadeSummary(capability, model, facadeOutput.getFileName().toString()))
                    .append(" |\n");
        }
        text.append('\n');

        for (InventoryModel.Capability capability : capabilities) {
            text.append("<a id=\"").append(capabilityAnchor(capability.id)).append("\"></a>\n");
            text.append("## ").append(code(capability.id)).append("\n\n");
            text.append(capability.summary).append("\n\n");
            text.append("- Context: \u0060").append(codeText(capability.context)).append("\u0060\n");
            text.append("- Status: \u0060").append(codeText(capability.status)).append("\u0060\n");
            text.append("- Reference Suite source: ")
                    .append(code(capability.referenceSource)).append("\n");
            text.append("- Reference role: ").append(capability.referenceRole).append("\n");
            text.append("- Acceptance Profile: \u0060")
                    .append(codeText(capability.acceptanceProfile.id)).append("\u0060\n");
            text.append("- Mandatory evidence chains: ")
                    .append(codeList(capability.acceptanceProfile.mandatoryEvidence)).append("\n");
            if (capability.acceptanceProfile.evidenceRecord == null) {
                text.append("- Evidence record: none while planned\n");
            } else {
                text.append("- Evidence record: [")
                        .append(code(capability.acceptanceProfile.evidenceRecord)).append("](")
                        .append(relative(output, model.repositoryRoot.resolve(
                                capability.acceptanceProfile.evidenceRecord))).append(")\n");
            }
            text.append("- Certified platforms: ")
                    .append(capability.certifiedPlatforms.isEmpty()
                            ? "none" : codeList(capability.certifiedPlatforms))
                    .append("\n\n");

            text.append("### Native Interface mapping\n\n");
            List<String> nativeKeys = new ArrayList<String>(capability.nativeInterface.keySet());
            Collections.sort(nativeKeys);
            for (String key : nativeKeys) {
                text.append("- ").append(code(key)).append(": ")
                        .append(code(capability.nativeInterface.get(key))).append("\n");
            }
            text.append('\n');

            text.append("### Migration Facade coverage\n\n");
            appendSurfaceLinks(text, "Stable", capability.stableFacadeIds,
                    facadeOutput.getFileName().toString());
            appendSurfaceLinks(text, "Preview", capability.previewFacadeIds,
                    facadeOutput.getFileName().toString());
            InventoryModel.Exclusion exclusion = exclusionFor(model, capability.id);
            if (exclusion != null) {
                text.append("- Explicit exclusion: [").append(code(exclusion.ticket)).append("](")
                        .append(facadeOutput.getFileName()).append("#")
                        .append(exclusionAnchor(capability.id)).append(") — ")
                        .append(exclusion.reason).append("\n");
            }
            text.append('\n');

            text.append("### Gates and limitations\n\n");
            if (capability.dependencyGates.isEmpty()) {
                text.append("- Dependency Gates: none\n");
            } else {
                for (InventoryModel.DependencyGate gate : capability.dependencyGates) {
                    text.append("- Dependency Gate: [").append(code(gate.capability)).append("](#")
                            .append(capabilityAnchor(gate.capability)).append(") must be ")
                            .append(code(gate.requiredStatus)).append("\n");
                }
            }
            if (capability.promotionGates.isEmpty()) {
                text.append("- Promotion gates: none\n");
            } else {
                for (InventoryModel.PromotionGate gate : capability.promotionGates) {
                    text.append("- Promotion gate ").append(code(gate.ticket)).append(": ")
                            .append(gate.requirement).append("\n");
                }
            }
            if (capability.limitations.isEmpty()) {
                text.append("- Limitations: none declared\n");
            } else {
                for (String limitation : capability.limitations) {
                    text.append("- Limitation: ").append(limitation).append("\n");
                }
            }
            text.append('\n');

            text.append("### Evidence\n\n");
            if (capability.implementationEvidence.isEmpty()) {
                text.append("Implementation evidence: none while planned.\n\n");
            } else {
                text.append("Implementation evidence:\n\n");
                for (InventoryModel.ImplementationEvidence evidence
                        : capability.implementationEvidence) {
                    text.append("- \u0060").append(codeText(evidence.kind)).append("\u0060: [")
                            .append(code(evidence.path)).append("](")
                            .append(relative(output, model.repositoryRoot.resolve(evidence.path)))
                            .append(") — ").append(evidence.assertion).append("\n");
                }
                text.append('\n');
            }

            if (capability.acceptanceEvidence.isEmpty()) {
                text.append("Acceptance Evidence: incomplete; no independent chain has been "
                        + "recorded as passing.\n\n");
            } else {
                text.append("Acceptance Evidence:\n\n");
                List<InventoryModel.AcceptanceEvidence> evidence =
                        new ArrayList<InventoryModel.AcceptanceEvidence>(
                                capability.acceptanceEvidence);
                Collections.sort(evidence, acceptanceEvidenceComparator());
                for (InventoryModel.AcceptanceEvidence item : evidence) {
                    text.append("- \u0060").append(codeText(item.chain)).append("\u0060: \u0060")
                            .append(codeText(item.result)).append("\u0060 — [")
                            .append(code(item.record)).append("](")
                            .append(relative(output, model.repositoryRoot.resolve(item.record)))
                            .append("); producer \u0060")
                            .append(codeText(item.producerName)).append("@")
                            .append(codeText(item.producerVersion)).append("\u0060 (")
                            .append(code(item.producerKind)).append(")\n");
                }
                text.append('\n');
            }

            text.append("Provenance: [").append(code(capability.provenancePath)).append("](")
                    .append(relative(output, model.repositoryRoot.resolve(
                            capability.provenancePath))).append("), record ")
                    .append(code(capability.provenanceRecord)).append(".\n\n");
        }
        return finishDocument(text);
    }

    private static String facadeDocument(
            InventoryModel model, Path output, Path capabilityOutput) {
        StringBuilder text = new StringBuilder();
        generatedHeader(text);
        text.append("# Facade Surface Manifest\n\n");
        text.append("Source-surface authority: [")
                .append(code(relative(output, model.facadePath)))
                .append("](").append(relative(output, model.facadePath)).append(")\n\n");
        text.append("- Schema version: \u0060").append(model.facadeSchemaVersion).append("\u0060\n");
        text.append("- Release train: \u0060").append(codeText(model.releaseTrain)).append("\u0060\n");
        text.append("- Stable entries: \u0060").append(model.stableSurfaces.size()).append("\u0060\n");
        text.append("- Preview entries: \u0060").append(model.previewSurfaces.size()).append("\u0060\n");
        text.append("- Explicit capability exclusions: \u0060").append(model.exclusions.size())
                .append("\u0060\n\n");

        appendSurfaceSection(text, "Stable surfaces", model.stableSurfaces,
                capabilityOutput.getFileName().toString());
        appendSurfaceSection(text, "Preview surfaces", model.previewSurfaces,
                capabilityOutput.getFileName().toString());

        text.append("## Explicit capability exclusions\n\n");
        List<InventoryModel.Exclusion> exclusions =
                new ArrayList<InventoryModel.Exclusion>(model.exclusions);
        Collections.sort(exclusions, exclusionComparator());
        if (exclusions.isEmpty()) {
            text.append("No capabilities are explicitly excluded.\n");
        } else {
            for (InventoryModel.Exclusion exclusion : exclusions) {
                text.append("<a id=\"").append(exclusionAnchor(exclusion.capability))
                        .append("\"></a>\n");
                text.append("### ").append(code(exclusion.capability)).append("\n\n");
                text.append("- Behavioral capability: [").append(code(exclusion.capability))
                        .append("](").append(capabilityOutput.getFileName()).append("#")
                        .append(capabilityAnchor(exclusion.capability)).append(")\n");
                text.append("- Deferred ticket: \u0060").append(codeText(exclusion.ticket))
                        .append("\u0060\n");
                text.append("- Reason: ").append(exclusion.reason).append("\n\n");
            }
        }
        return finishDocument(text);
    }

    private static void appendSurfaceSection(
            StringBuilder text,
            String title,
            List<InventoryModel.Surface> values,
            String capabilityDocument) {
        text.append("## ").append(title).append("\n\n");
        List<InventoryModel.Surface> surfaces = new ArrayList<InventoryModel.Surface>(values);
        Collections.sort(surfaces, surfaceComparator());
        if (surfaces.isEmpty()) {
            text.append("No ").append(title.toLowerCase(Locale.ROOT)).append(" are declared.\n\n");
            return;
        }
        for (InventoryModel.Surface surface : surfaces) {
            text.append("<a id=\"").append(surfaceAnchor(surface.id)).append("\"></a>\n");
            text.append("### ").append(code(surface.id)).append("\n\n");
            text.append("- Availability: \u0060").append(codeText(surface.availability))
                    .append("\u0060\n");
            text.append("- Reference member: \u0060").append(codeText(surface.referenceType))
                    .append("#").append(codeText(surface.referenceMember)).append("\u0060\n");
            text.append("- Open PDF mapping: \u0060").append(codeText(surface.openPdfType))
                    .append("#").append(codeText(surface.openPdfMember)).append("\u0060\n");
            text.append("- Generic contract: ").append(surface.genericContract).append("\n");
            text.append("- Exception contract: ").append(surface.exceptionContract).append("\n");
            text.append("- Behavioral capabilities: ");
            List<String> capabilityIds = new ArrayList<String>(surface.capabilities);
            Collections.sort(capabilityIds);
            for (int index = 0; index < capabilityIds.size(); index++) {
                if (index > 0) {
                    text.append(", ");
                }
                String capabilityId = capabilityIds.get(index);
                text.append("[").append(code(capabilityId)).append("](")
                        .append(capabilityDocument).append("#")
                        .append(capabilityAnchor(capabilityId)).append(")");
            }
            text.append("\n\n");
        }
    }

    private static void appendSurfaceLinks(
            StringBuilder text, String label, List<String> ids, String facadeDocument) {
        text.append("- ").append(label).append(": ");
        if (ids.isEmpty()) {
            text.append("none\n");
            return;
        }
        List<String> sorted = new ArrayList<String>(ids);
        Collections.sort(sorted);
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            String id = sorted.get(index);
            text.append("[").append(code(id)).append("](").append(facadeDocument)
                    .append("#").append(surfaceAnchor(id)).append(")");
        }
        text.append('\n');
    }

    private static String facadeSummary(
            InventoryModel.Capability capability, InventoryModel model, String facadeDocument) {
        List<String> links = new ArrayList<String>();
        for (String id : capability.stableFacadeIds) {
            links.add("[" + code(id) + "](" + facadeDocument + "#" + surfaceAnchor(id) + ")");
        }
        for (String id : capability.previewFacadeIds) {
            links.add("[" + code(id) + "](" + facadeDocument + "#" + surfaceAnchor(id) + ")");
        }
        Collections.sort(links);
        if (!links.isEmpty()) {
            return String.join(", ", links);
        }
        InventoryModel.Exclusion exclusion = exclusionFor(model, capability.id);
        if (exclusion != null) {
            return "[excluded by " + code(exclusion.ticket) + "](" + facadeDocument + "#"
                    + exclusionAnchor(capability.id) + ")";
        }
        return "none";
    }

    private static InventoryModel.Exclusion exclusionFor(InventoryModel model, String capabilityId) {
        for (InventoryModel.Exclusion exclusion : model.exclusions) {
            if (capabilityId.equals(exclusion.capability)) {
                return exclusion;
            }
        }
        return null;
    }

    private static String relative(Path fromDocument, Path target) {
        return fromDocument.getParent().relativize(target.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    private static String capabilityAnchor(String id) {
        return "capability-" + anchorToken(id);
    }

    private static String surfaceAnchor(String id) {
        return "facade-surface-" + anchorToken(id);
    }

    private static String exclusionAnchor(String id) {
        return "excluded-capability-" + anchorToken(id);
    }

    private static String anchorToken(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')) {
                result.append(character);
            } else if (character == '.') {
                result.append("_dot_");
            } else if (character == '-') {
                result.append("_dash_");
            } else {
                result.append("_").append(Integer.toHexString(character)).append("_");
            }
        }
        return result.toString();
    }

    private static String code(Object value) {
        return "\u0060" + codeText(value) + "\u0060";
    }

    private static String codeText(Object value) {
        return value == null ? "" : value.toString()
                .replace("\u0060", "&#96;").replace("|", "&#124;");
    }

    private static String codeList(List<?> values) {
        List<String> rendered = new ArrayList<String>();
        for (Object value : values) {
            rendered.add(code(value));
        }
        return String.join(", ", rendered);
    }

    private static void generatedHeader(StringBuilder text) {
        text.append("<!-- Generated by ./scripts/inventory generate. Do not edit. -->\n\n");
    }

    private static String finishDocument(StringBuilder text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\n') {
            end--;
        }
        return text.substring(0, end) + "\n";
    }

    private static Comparator<InventoryModel.Capability> capabilityComparator() {
        return new Comparator<InventoryModel.Capability>() {
            @Override
            public int compare(
                    InventoryModel.Capability left, InventoryModel.Capability right) {
                return left.id.compareTo(right.id);
            }
        };
    }

    private static Comparator<InventoryModel.Surface> surfaceComparator() {
        return new Comparator<InventoryModel.Surface>() {
            @Override
            public int compare(InventoryModel.Surface left, InventoryModel.Surface right) {
                return left.id.compareTo(right.id);
            }
        };
    }

    private static Comparator<InventoryModel.Exclusion> exclusionComparator() {
        return new Comparator<InventoryModel.Exclusion>() {
            @Override
            public int compare(InventoryModel.Exclusion left, InventoryModel.Exclusion right) {
                return left.capability.compareTo(right.capability);
            }
        };
    }

    private static Comparator<InventoryModel.AcceptanceEvidence> acceptanceEvidenceComparator() {
        return new Comparator<InventoryModel.AcceptanceEvidence>() {
            @Override
            public int compare(
                    InventoryModel.AcceptanceEvidence left,
                    InventoryModel.AcceptanceEvidence right) {
                return left.chain.compareTo(right.chain);
            }
        };
    }
}

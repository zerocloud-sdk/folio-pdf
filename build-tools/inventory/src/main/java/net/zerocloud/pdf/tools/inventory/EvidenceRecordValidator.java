package net.zerocloud.pdf.tools.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class EvidenceRecordValidator {

    private EvidenceRecordValidator() {
    }

    static void validate(InventoryModel model, List<String> errors) {
        for (InventoryModel.Capability capability : model.capabilities) {
            if (capability.acceptanceProfile == null) {
                continue;
            }
            if (capability.acceptanceProfile.evidenceRecord != null) {
                validateRecordMetadata(
                        model.repositoryRoot,
                        capability.acceptanceProfile.evidenceRecord,
                        "capability " + capability.id + " profile evidence record",
                        Arrays.asList(
                                metadata("Status", capability.status),
                                metadata("Capability", capability.id),
                                metadata("Acceptance Profile", capability.acceptanceProfile.id),
                                metadata("Release train", model.releaseTrain)),
                        errors);
            }
            for (InventoryModel.AcceptanceEvidence evidence : capability.acceptanceEvidence) {
                if (evidence.record == null) {
                    continue;
                }
                validateRecordMetadata(
                        model.repositoryRoot,
                        evidence.record,
                        "capability " + capability.id + " " + evidence.chain
                                + " Acceptance Evidence record",
                        Arrays.asList(
                                metadata("Capability", capability.id),
                                metadata("Acceptance Profile", capability.acceptanceProfile.id),
                                metadata("Profile record",
                                        capability.acceptanceProfile.evidenceRecord),
                                metadata("Release train", model.releaseTrain),
                                metadata("Chain", evidence.chain),
                                metadata("Result", evidence.result),
                                metadata("Producer kind", evidence.producerKind),
                                metadata("Producer", evidence.producerName),
                                metadata("Producer version", evidence.producerVersion)),
                        errors);
            }
        }
    }

    private static void validateRecordMetadata(
            Path repositoryRoot,
            String relativePath,
            String description,
            List<String> expectedLines,
            List<String> errors) {
        Path path = RepositoryFileResolver.resolveForRead(repositoryRoot, relativePath);
        if (path == null) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            errors.add(description + ": cannot read metadata: " + e.getMessage());
            return;
        }
        List<String> normalized = new ArrayList<String>();
        for (String line : lines) {
            normalized.add(line.trim());
        }
        for (String expected : expectedLines) {
            String label = expected.substring(0, expected.indexOf(':') + 1);
            List<String> matchingLabels = new ArrayList<String>();
            for (String line : normalized) {
                if (line.startsWith(label)) {
                    matchingLabels.add(line);
                }
            }
            if (matchingLabels.isEmpty()) {
                errors.add(description + ": missing or mismatched metadata line " + expected);
            } else if (matchingLabels.size() > 1) {
                errors.add(description + ": duplicate or conflicting metadata label " + label);
            } else if (!expected.equals(matchingLabels.get(0))) {
                errors.add(description + ": missing or mismatched metadata line " + expected);
            }
        }
    }

    private static String metadata(String label, Object value) {
        String tick = Character.toString((char) 96);
        return label + ": " + tick + (value == null ? "" : value.toString()) + tick;
    }
}

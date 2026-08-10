package net.zerocloud.pdf.tools.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InventoryCrossValidator {

    private InventoryCrossValidator() {
    }

    static void validate(InventoryModel model, List<String> errors) {
        Map<String, InventoryModel.Capability> capabilities =
                new LinkedHashMap<String, InventoryModel.Capability>();
        for (InventoryModel.Capability capability : model.capabilities) {
            if (capability.id != null && !capabilities.containsKey(capability.id)) {
                capabilities.put(capability.id, capability);
            }
        }

        validateDependencyReferences(capabilities, errors);
        validateDependencyCycles(capabilities, errors);

        Map<String, Set<String>> stableLinks = new HashMap<String, Set<String>>();
        Map<String, Set<String>> previewLinks = new HashMap<String, Set<String>>();
        for (InventoryModel.Surface surface : model.stableSurfaces) {
            validateSurfaceReferences(surface, capabilities, stableLinks, errors);
        }
        for (InventoryModel.Surface surface : model.previewSurfaces) {
            validateSurfaceReferences(surface, capabilities, previewLinks, errors);
        }

        Map<String, InventoryModel.Exclusion> exclusions =
                new HashMap<String, InventoryModel.Exclusion>();
        for (InventoryModel.Exclusion exclusion : model.exclusions) {
            if (exclusion.capability == null) {
                continue;
            }
            if (!capabilities.containsKey(exclusion.capability)) {
                errors.add("facade-surface.excluded-capabilities: unknown capability "
                        + exclusion.capability);
            }
            if (!exclusions.containsKey(exclusion.capability)) {
                exclusions.put(exclusion.capability, exclusion);
            }
        }

        for (InventoryModel.Capability capability : model.capabilities) {
            if (capability.id == null) {
                continue;
            }
            Set<String> actualStable = valueOrEmpty(stableLinks.get(capability.id));
            Set<String> actualPreview = valueOrEmpty(previewLinks.get(capability.id));
            if (!actualStable.equals(new LinkedHashSet<String>(capability.stableFacadeIds))) {
                errors.add("capability " + capability.id
                        + ": migration-facade.stable does not match Facade Surface references; "
                        + "expected " + sorted(actualStable));
            }
            if (!actualPreview.equals(new LinkedHashSet<String>(capability.previewFacadeIds))) {
                errors.add("capability " + capability.id
                        + ": migration-facade.preview does not match Facade Surface references; "
                        + "expected " + sorted(actualPreview));
            }

            boolean hasSurface = !actualStable.isEmpty() || !actualPreview.isEmpty();
            boolean excluded = exclusions.containsKey(capability.id);
            if (hasSurface && excluded) {
                errors.add("capability " + capability.id
                        + ": cannot be both facade-covered and excluded");
            } else if (!hasSurface && !excluded) {
                errors.add("capability " + capability.id
                        + ": must have a facade surface or an explicit exclusion");
            }
        }
    }

    private static void validateDependencyReferences(
            Map<String, InventoryModel.Capability> capabilities, List<String> errors) {
        for (InventoryModel.Capability capability : capabilities.values()) {
            for (InventoryModel.DependencyGate gate : capability.dependencyGates) {
                if (gate.capability == null) {
                    continue;
                }
                InventoryModel.Capability required = capabilities.get(gate.capability);
                if (required == null) {
                    errors.add("capability " + capability.id
                            + ": Dependency Gate references unknown capability " + gate.capability);
                    continue;
                }
                if ((capability.status == CapabilityState.COMPATIBLE
                        || capability.status == CapabilityState.LIMITED)
                        && required.status != CapabilityState.COMPATIBLE) {
                    errors.add("capability " + capability.id + ": status " + capability.status
                            + " requires Dependency Gate " + gate.capability
                            + " to be compatible, but it is " + required.status);
                }
            }
        }
    }

    private static void validateDependencyCycles(
            Map<String, InventoryModel.Capability> capabilities, List<String> errors) {
        Map<String, Integer> colors = new HashMap<String, Integer>();
        Deque<String> stack = new ArrayDeque<String>();
        Set<String> reportedCycles = new HashSet<String>();
        for (String capability : capabilities.keySet()) {
            if (!Integer.valueOf(2).equals(colors.get(capability))) {
                visitDependency(capability, capabilities, colors, stack, reportedCycles, errors);
            }
        }
    }

    private static void visitDependency(
            String id,
            Map<String, InventoryModel.Capability> capabilities,
            Map<String, Integer> colors,
            Deque<String> stack,
            Set<String> reportedCycles,
            List<String> errors) {
        Integer color = colors.get(id);
        if (Integer.valueOf(2).equals(color)) {
            return;
        }
        colors.put(id, Integer.valueOf(1));
        stack.addLast(id);
        InventoryModel.Capability capability = capabilities.get(id);
        if (capability != null) {
            for (InventoryModel.DependencyGate gate : capability.dependencyGates) {
                if (!capabilities.containsKey(gate.capability)) {
                    continue;
                }
                if (Integer.valueOf(1).equals(colors.get(gate.capability))) {
                    List<String> cycle = cycleFrom(stack, gate.capability);
                    cycle.add(gate.capability);
                    String message = String.join(" -> ", cycle);
                    if (reportedCycles.add(message)) {
                        errors.add("Capability dependency cycle: " + message);
                    }
                } else if (!Integer.valueOf(2).equals(colors.get(gate.capability))) {
                    visitDependency(gate.capability, capabilities, colors, stack,
                            reportedCycles, errors);
                }
            }
        }
        stack.removeLast();
        colors.put(id, Integer.valueOf(2));
    }

    private static List<String> cycleFrom(Deque<String> stack, String start) {
        List<String> result = new ArrayList<String>();
        boolean copy = false;
        for (String item : stack) {
            if (item.equals(start)) {
                copy = true;
            }
            if (copy) {
                result.add(item);
            }
        }
        return result;
    }

    private static void validateSurfaceReferences(
            InventoryModel.Surface surface,
            Map<String, InventoryModel.Capability> capabilities,
            Map<String, Set<String>> links,
            List<String> errors) {
        for (String capabilityId : surface.capabilities) {
            InventoryModel.Capability capability = capabilities.get(capabilityId);
            if (capability == null) {
                errors.add("facade surface " + surface.id
                        + ": references unknown capability " + capabilityId);
                continue;
            }
            if (surface.availability == FacadeAvailability.STABLE
                    && capability.status != CapabilityState.COMPATIBLE) {
                errors.add("stable facade surface " + surface.id + ": capability " + capabilityId
                        + " must be compatible, but it is " + capability.status);
            }
            if (surface.availability == FacadeAvailability.PREVIEW
                    && !(capability.status == CapabilityState.COMPATIBLE
                    || capability.status == CapabilityState.EXPERIMENTAL)) {
                errors.add("preview facade surface " + surface.id + ": capability " + capabilityId
                        + " must be compatible or experimental, but it is " + capability.status);
            }
            Set<String> ids = links.get(capabilityId);
            if (ids == null) {
                ids = new LinkedHashSet<String>();
                links.put(capabilityId, ids);
            }
            if (surface.id != null) {
                ids.add(surface.id);
            }
        }
    }

    private static Set<String> valueOrEmpty(Set<String> value) {
        return value == null ? Collections.<String>emptySet() : value;
    }

    private static List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<String>(values);
        Collections.sort(result);
        return result;
    }
}

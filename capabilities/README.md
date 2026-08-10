# Compatibility inventories

Open PDF keeps two versioned YAML inventories.

## Capability Matrix

`capability-matrix.yaml` is the behavioral authority. Each entry has a stable capability ID, owning context, Reference Suite source, Native Interface mapping, Stable and Experimental Migration Facade coverage, limitations, Dependency Gates, Acceptance Profile, evidence references, certified platforms, and one status: `planned`, `experimental`, `compatible`, or `limited`.

## Facade Surface Manifest

`facade-surface.yaml` is the source-surface authority. Each entry records its reference fully qualified name and member descriptor, Open PDF mapping, generic and exception contract, linked capability IDs, and stable or preview availability.

Generated documentation links both inventories. A method mapping without passing behavioral evidence is not compatible, and behavioral coverage without a surface entry does not lower source-migration effort.

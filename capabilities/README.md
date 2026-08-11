# Compatibility inventories

Folio PDF keeps two versioned YAML authorities. The checked-in YAML is
normative; files below `docs/generated/` are deterministic views and must
never be edited by hand.

## Commands

Validate both authorities and every referenced repository file:

```text
./scripts/inventory validate
```

Regenerate the human-readable views:

```text
./scripts/inventory generate
```

The outputs are:

- `docs/generated/capability-matrix.md`
- `docs/generated/facade-surface.md`

Record the built-in T06 blank-document Acceptance Profile with the pinned
external syntax validator and the project semantic assertions:

```text
./scripts/provision-qpdf /path/to/qpdf-12.4.0-bin-linux-x86_64.zip
./scripts/acceptance capabilities/evidence
```

Provisioning is deliberately offline. The operator supplies the official
qpdf 12.4.0 Linux x86-64 archive; `scripts/provision-qpdf` verifies SHA-256
`a3bca240f3bb61efdc3a90be89d1da4ed5e125326c3458c4e62df53ff4f153e3`
and verifies the extracted `bin/qpdf` SHA-256
`9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b`
before placing it in the ignored `.build-cache/qpdf/12.4.0` directory.
`scripts/qpdf-pin.properties` is the single operational pin authority, and
the canonical acceptance profile fixes both the repository wrapper and that
pin file. The normal build, tests, and inventory commands neither download
nor require qpdf. A missing, unreadable, incorrectly versioned, or
digest-unmarked validator records the syntax chain as `indeterminate`, never
`pass`.

The acceptance command creates the T06 blank PDF through
`DocumentWorkflow.execute`, hashes that artifact, runs `qpdf --check` on it,
and then reopens the same artifact through the public workflow to assert the
committed one-page sequence and readable object graph; text order is not
applicable because the profile emits no text. It also drives all six T10 page,
merge, and split Commands to create two independently reopenable products and
runs the same pinned qpdf syntax check on both. It records tool identity,
version and distribution digest, input SHA-256 values, raw findings,
chain-level results, and the applicable determination beneath the requested
output directory. qpdf is syntax evidence only: its success is not a PDF
standards-conformance result and cannot satisfy the independent standards,
semantic, or visual chains.

`./scripts/inventory check` validates the authorities and fails if either
generated file is missing or stale. The repository root command
`./mvnw -B -ntp verify` invokes that check through the internal
`pdf-inventory-tool` build module. The tool is skipped for Maven install and
deploy and is not part of the Native Interface, BOM, or published product
artifacts.

## Capability Matrix schema

`capability-matrix.yaml` is the behavioral authority. Its top level fixes
`schema-version`, `release-train`, and the literal authority
`behavioral-capability`. Each capability requires:

- a unique lowercase `id` and owning `context`;
- a summary and Reference Suite source/role that identify the inventory input
  without treating another implementation as an oracle;
- at least one Native Interface mapping;
- exact `stable` and `preview` facade-surface ID lists;
- limitations, Dependency Gates, promotion gates, and certified platforms;
- one uniquely identified Acceptance Profile, implementation `evidence`, independent
  `acceptance-evidence`, provenance, and one declared state.

Repository paths are relative, use forward slashes, cannot escape the
repository lexically or through symbolic links, and must name existing regular
files. Unknown schema fields, duplicate identifiers, and mismatched release
trains fail validation. The inventory Release Train must also equal the project
version declared by the repository root `pom.xml`.

Every Dependency Gate has `capability` and `required-status: compatible`.
Targets must exist, the dependency graph must be acyclic, and a `compatible`
or `limited` capability cannot pass while a required capability is not
`compatible`.

## Capability states and evidence

Every Acceptance Profile declares the mandatory independent evidence chains.
`syntax`, `standards`, `semantic`, and `visual` are always mandatory; `human`
is additionally mandatory where the profile requires human review. Each
recorded chain has one result (`pass`, `fail`, or `indeterminate`) and its own
repository evidence record plus a versioned producer `kind`, `name`, and
`version`. Syntax, standards, and visual chains require external-tool
producers; semantic chains require project-test producers; human chains
require human-review producers. Independent chains cannot share a producer
name or canonical record, and cannot reuse an implementation or profile file
through an alternate relative spelling or in-repository symbolic link.

Profile evidence records carry exact `Status`, `Capability`, and
`Acceptance Profile` and `Release train` metadata. Independent chain records
carry exact `Capability`, `Acceptance Profile`, `Profile record`,
`Release train`, `Chain`, `Result`, and producer metadata. Each metadata label
must occur exactly once. Validation rejects missing, duplicate, or conflicting
metadata and values that disagree with the Capability Matrix.

| State | Required evidence | Claims and gates |
| --- | --- | --- |
| `planned` | No implementation evidence, Acceptance Evidence, or evidence record. | No certified platform claim. Future mandatory chains are still declared. |
| `experimental` | At least one implementation evidence item and an evidence record. Acceptance chains may be absent, partial, failing, or indeterminate. | No certified platform claim. Open Dependency or promotion gates may remain. |
| `compatible` | Implementation evidence plus `pass` records for every mandatory Acceptance Evidence chain. Implementation tests alone are insufficient. | Every Dependency Gate is `compatible` and no promotion gate remains open. |
| `limited` | The same complete mandatory Acceptance Evidence required for `compatible`. | At least one explicit limitation, every Dependency Gate is `compatible`, and no promotion gate remains open. |

The Acceptance Profile `state` must equal the capability `status`. Promotion is
conservative: complete evidence permits a state but never automatically
changes it.

## Facade Surface Manifest schema

`facade-surface.yaml` is the source-surface authority. It fixes the same schema
version and Release Train and uses the literal authority
`migration-source-surface`. Each unique stable surface ID records:

- Reference Suite type and member descriptor;
- Folio PDF type and member mapping;
- generic and exception contracts;
- one or more behavioral capability IDs;
- stable or preview availability through its containing list.

Stable surfaces may reference only `compatible` capabilities. Preview surfaces
may reference only `compatible` or `experimental` capabilities. A surface
cannot appear in both lists.

Reference types must be below `com.itextpdf.*`. Their Folio PDF types must
preserve the exact suffix below `net.zerocloud.pdf.itext7.*`, so the declared
mapping remains eligible for mechanical import-prefix replacement.

The Capability Matrix facade ID lists and manifest references must agree
exactly. Every capability must either have at least one corresponding surface
or one explicit `excluded-capabilities` entry with a ticket and reason; it
cannot have both. This rule gives every generated capability entry an accurate
facade backlink while every generated surface or exclusion links to its
behavioral capability.

## Authority boundaries

The Capability Matrix answers whether behavior has the required evidence. The
Facade Surface Manifest answers which migration source shapes exist. A method
mapping without passing behavioral evidence is not compatible, and behavioral
coverage without a surface entry does not lower source-migration effort.
Generated Markdown is review material, not a third authority.

The T02 validator and generator evidence is recorded in
`capabilities/evidence/T02-inventory-authorities.md`.

The current T06 syntax, semantic, and overall records are
`capabilities/evidence/T06-document-blank-syntax.md`,
`capabilities/evidence/T06-document-blank-semantic.md`, and
`capabilities/evidence/T06-document-blank-determination.md`. Syntax and
semantic pass for their shared pinned artifact. Standards and visual evidence
are absent, so the overall profile remains `indeterminate` and the capability
remains `experimental`.

The current T10 syntax record is
`capabilities/evidence/T10-page-manipulation-merge-split-syntax.md`. Its two
project-produced artifacts pass pinned qpdf 12.4.0 syntax checks. Standards,
semantic, and visual Acceptance Evidence remain absent, so the T10 capability
also remains `experimental`.

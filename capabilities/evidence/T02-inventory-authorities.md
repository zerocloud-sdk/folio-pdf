# T02 inventory-authority evidence

Status: repository-verified.

Release train: `0.1.0-SNAPSHOT`

Authorities:

- `capabilities/capability-matrix.yaml`
- `capabilities/facade-surface.yaml`

The repository-owned `./scripts/inventory` command validates schema shape,
stable identifiers, capability state and evidence rules, Dependency Gates,
cycles, facade references and availability, bidirectional coverage, referenced
files, and Release Train agreement. Its `generate` action produces both
cross-linked Markdown views, while `check` compares the expected bytes with the
checked-in outputs.

`InventoryCommandTest` runs valid and invalid project-owned inventories through
the command process. Its cases cover every capability state, unsupported and
missing IDs, unknown and cyclic dependencies, blocked compatibility promotion,
unknown facade capability references, invalid stable facade coverage,
incomplete evidence for each state, deterministic bidirectional generation,
and stale-output rejection.

No Migration Facade implementation or product artifact is introduced. The
live blank-document capability remains `experimental`, has no certified
platform claim, and retains T06 as its promotion gate.

## Execution record

Executed on 2026-08-10:

- `./scripts/inventory validate` accepted the checked-in authorities: one
  capability, zero facade surfaces, and one explicit exclusion.
- `./mvnw -B -ntp -pl build-tools/inventory
  -Dtest=InventoryCommandTest test` passed all 6 command-boundary test methods,
  including 17 invalid fixture cases and valid fixtures spanning all four
  capability states.
- `./scripts/inventory generate` followed by `./scripts/inventory check`
  confirmed byte-for-byte deterministic, current generated documentation.
- `./mvnw -B -ntp clean verify` passed all 13 repository test methods and the
  bound compatibility-inventory check.
- `./scripts/verify-jdk-matrix.sh` passed the full repository verification on
  JDK 8, 11, 17, and 21.
- `git diff --check` passed.

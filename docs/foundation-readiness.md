# Foundation release readiness

`./scripts/inventory readiness` evaluates the required Foundation 0.1.0
obligations and retained observations. Exit zero means every declared requirement
has compatible behavior/dependencies, exact environment evidence, independent
chains and required Stable Facade mappings, with current source/artifact/report
identities. It neither runs the acceptance tools nor grants publication approval.
Use `validate` for structural consistency and `generate` / `check` for the
[generated public report](generated/foundation-readiness.md). Regular Maven
`verify` checks a valid, currently incomplete inventory and the checker fixtures.

The sources are:

- [foundation-release.yaml](../capabilities/foundation-release.yaml): mandatory
  obligations, owning capabilities or proposed subcapabilities, Acceptance
  Profiles, execution/environment coverage, chains, mapping families/sets,
  dependency/member links, assigned #69–#97 slices and limitation classifications.
- [foundation-requirements.yaml](../capabilities/foundation-requirements.yaml):
  verbatim numbered #1 user stories, implementation/testing decisions and
  non-goals, #33 acceptance criteria and the specific approved slice requirements.
  Every required source maps to an obligation; later scope and historical
  observations carry reasons and do not erase retained Foundation requirements.
- [foundation-environments.yaml](../capabilities/foundation-environments.yaml):
  actual observed Noble image digests, OS-release hashes and Temurin JDK identities.
  `./scripts/inventory environments` emits `FOUNDATION_ENVIRONMENT JDK IMAGE`
  records; the JDK matrix consumes these immutable images. No floating tag is an
  authority. A future image/build needs reviewed profile changes and new evidence.
- [foundation-evidence.yaml](../capabilities/foundation-evidence.yaml): final
  candidate inputs/artifacts, actual environment records and certifications.
  Each completed slice records the same exact candidate and retains only
  certifications whose candidate, contract, environment, configuration, and
  recursively referenced report identities still match. Historical T70/T71
  records remain immutable when T72 refreshes transactions, values, and pages.

Windows x86-64 and macOS x86-64/arm64 are explicitly uncertified and are not
required Foundation 0.1.0 gates under [ADR-0040](adr/0040-certify-only-observed-foundation-environments.md).
Only the actual required Ubuntu 24.04/Linux x86-64 JDK 8/11/17/21 profiles may
satisfy this release contract; the same architecture cannot certify another OS,
image, JDK vendor or build. Other #1/#33 contracts and non-goals remain unchanged.

## Recording evidence in a certification slice

The following YAML shapes document the format; placeholder values are not valid
evidence. Every file reference has repository-relative `path` and lowercase
64-digit `sha256`; the checker reads and hashes the actual file, rejects escapes
and missing files, and does not trust filenames as identity.

```yaml
schema-version: 1
candidate:
  release: 0.1.0
  inputs:
    - {path: pom.xml, sha256: ACTUAL_SHA256}
    # Every file beneath every declared source-root, with the exclusions below.
  artifacts:
    - {path: target/foundation-0.1.0/central-bundle.zip, sha256: ACTUAL_SHA256}
    # Exactly the declared complete binary/POM/sources/Javadoc artifact set.
environments:
  - profile: ubuntu-24.04-linux-x86-64-jdk17
    record: {path: capabilities/evidence/foundation/environment-17.yaml, sha256: ACTUAL_SHA256}
certifications:
  - obligation: transactions
    environment: ubuntu-24.04-linux-x86-64-jdk17
    execution-profile: IN_PROCESS
    configuration: {path: capabilities/evidence/foundation/transactions-17-run.yaml, sha256: ACTUAL_SHA256}
    records:
      - {path: capabilities/evidence/foundation/transactions-17-syntax.yaml, sha256: ACTUAL_SHA256}
      # Separate record for every required chain and execution/environment tuple.
```

The candidate identity printed by `readiness` is SHA-256 of UTF-8 lines:
`release VERSION\n`, then each `input PATH SHA256\n`, then each
`artifact PATH SHA256\n`, sorted by path within each category. Candidate inputs
must exactly cover the declared source roots, including build/test tools, public
contracts, guides, actual reference profiles and expected rasters. Python
`__pycache__` is ignored. The sole permitted declared `source-exclusions` entry
is `docs/generated/foundation-readiness.md`, because that file is the result of
the evidence being evaluated; including it would create a self-reference.
Other generated documentation remains in the identity boundary. Changed
sources, normative thresholds, public guides or compiled artifacts invalidate
previous records. Rehearsal/signature
and Central bundle validators retain their existing separate responsibilities.

The contract identity is SHA-256 of `contract PATH SHA256\n` lines sorted by
path, once for each obligation, source-requirement, environment, Capability
Matrix and Facade authority, platform ADR and referenced profile-contract file.
It excludes the mutable evidence index to avoid self-reference. Thus a changed
requirement, mapping set, profile or platform boundary invalidates old evidence.

Each environment record contains:

```yaml
schema-version: 1
profile: ubuntu-24.04-linux-x86-64-jdk17
identity: # Exact fields and values from the approved environment profile.
  os: ubuntu
  os-version: '24.04'
  architecture: x86-64
  image: docker.io/library/eclipse-temurin@sha256:ACTUAL_IMAGE_DIGEST
  os-release-sha256: ACTUAL_SHA256
  jdk-major: 17
  jdk-vendor: ACTUAL_VENDOR
  jdk-build: ACTUAL_RUNTIME_BUILD
  java-sha256: ACTUAL_SHA256
host: {kernel: ACTUAL_KERNEL, architecture: x86-64}
native-engine:
  name: harfbuzz
  version: 10.2.0
  helper-sha256: ACTUAL_SHA256
  library-sha256: ACTUAL_LOADED_LIBRARY_SHA256
  installation-sha256: ACTUAL_INSTALLATION_RECEIPT_SHA256
tools:
  - id: qpdf
    kind: external-tool
    version: ACTUAL_VERSION
    sha256: ACTUAL_EXECUTABLE_SHA256
    chains: [syntax]
  # Include every actual producer, with separately qualified roles and identities.
```

Collect observations during the actual run; copying the expected profile is not
an observation. Retain original OS/JDK/process/native observation reports and
profile configuration in the evidence reports. The existing HarfBuzz live-engine
observer proves more than an installer receipt; ambiguous or unavailable loaded
engine observations cannot certify shaping. Tool role qualification and actual
rule/semantic/raster findings are owned by the named acceptance slices, beginning
with the independent standards tool in #70; this checker does not qualify a tool
by its name. External tools and native installations remain unbundled.

Each chain file contains:

```yaml
schema-version: 1
obligation: transactions
acceptance-profile: T03-document-workflow-transaction
release: 0.1.0
candidate-sha256: PRINTED_CANDIDATE_IDENTITY
contract-sha256: PRINTED_CONTRACT_IDENTITY
environment-sha256: ACTUAL_ENVIRONMENT_RECORD_SHA256
execution-configuration-sha256: ACTUAL_RUN_CONFIGURATION_SHA256
execution-profile: IN_PROCESS
chain: syntax
result: pass
producer: qpdf
configuration: {path: capabilities/evidence/T03-document-workflow-transaction.md, sha256: ACTUAL_SHA256}
report: {path: capabilities/evidence/foundation/transactions-17-qpdf.txt, sha256: ACTUAL_SHA256}
negative-controls:
  - {path: capabilities/evidence/foundation/transactions-17-invalid-qpdf.txt, sha256: ACTUAL_SHA256}
```

Each certification also references a separate actual execution-configuration
record. Its `schema-version: 1`, `candidate-sha256`, `environment-sha256`,
`acceptance-profile` and `execution-profile` must match the certification.
Record the exact `command` argument sequence, `java-options` sequence, explicit
`locale` and `timezone`, and nonblank `settings` for `workflow-policy`, `fonts`
and `providers`. List the actual corpus, font, policy and tool-configuration
files under `inputs`, each with its path and SHA-256. An explicit absence (for
example a repository-only gate with no Workflow or fonts) must be stated in
settings, not inferred. Every chain binds this configuration's actual hash;
a changed setting or missing run configuration fails. The Acceptance Profile
Markdown in the chain's separate `configuration` field describes the expected
contract and is insufficient as a record of the actual execution.

Mandatory chains must all pass. Distinct PDF chains require distinct record and
report files and producer identities; syntax is not standards evidence, and
PDFBox cannot be the independent visual oracle. Missing, FAIL or INDETERMINATE
records fail readiness. Behavioral and control profiles also require retained
negative controls. Configuration must be the obligation's current profile
contract. Repository release controls use their declared contract, artifact,
supply-chain, reproducibility, signature and review chains, with reasoned
Native-Interface-only treatment and no artificial Reference Suite APIs.

## Mapping and limitations

A required Facade family with an empty mapping list is an explicit blocker.
The owning slice must freeze the full matching member set, implement it, and
prove Native/Facade behavior and compiled artifact surface agreement. Every
listed ID must be Stable, reference that capability/subcapability and belong to
the declared Reference Suite family. Every actual Stable entry must belong to a
Foundation obligation. A blanket `excluded-capabilities` entry never satisfies
this requirement. Repository controls with no Reference Suite counterpart have
individual reasons; the controls cannot exempt the PDF behavior they execute.

Password baseline/metadata-clear/embedded-files-only scopes and base/paginated
tables are independently certifiable. The proposed subject ID in an obligation
is intentionally absent from the Capability Matrix until its implementation
slice adds it with `parent-capability`, a matching profile, inherited external
Dependency Gates and independent evidence. The parent is an aggregate, not a
child prerequisite. Foundation still requires the original aggregate capability
and every member; partial child certification cannot complete the aggregate.

Each retained limitation is linked by the SHA-256 of its exact UTF-8 text to an
obligation and classified as `retained-contract`, `release-blocker`,
`later-release` or `platform-amended`. Every current limitation must have exactly
one current classification. Required missing behavior and unresolved scope
restrictions remain release blockers, even if a safe unsupported failure exists.
Future slices resolve the actual behavior/evidence gap and update the limitation,
classification and source traceability together.

The command-boundary fixtures are synthetic project-owned data. Their success
proves the checker accepts a controlled complete contract, not that the real
Foundation Release is certified. Run them with
`./mvnw -B -ntp -pl build-tools/inventory test`.


## Annotation certification recorder

The Foundation runner accepts `--obligation annotations` for the frozen T12
profile. It executes all eight approved Linux/JDK/Native-mode combinations,
with the inherited Stable Facade observed as IN_PROCESS, and retains 16 product
cases per tuple. In addition to the four independent chains, its collector
requires the complete 174-rule assignment, exact independent qpdf graphs,
page-level AP projections, all committed receipts, 18 detected semantic defects,
actual PDF visual defects, the exact one-pixel comparator control and qualified
cross-process Action safety observations. Missing or inconsistent supporting
records cannot be repaired by an aggregate PASS label.

Use [the T12 procedure](t12-certification.md) after freezing and staging the
candidate. Refresh transactions, values, pages and metadata for the same
candidate; their prior identities become stale when source/contracts change.
The five obligations can be satisfied while unrelated Foundation slices keep
overall readiness NOT READY. The approved environment matrix and deferred
Windows/macOS scope remain those in ADR-0040.

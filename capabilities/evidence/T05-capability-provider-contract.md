# T05 Capability Provider contract evidence

Status: `compatible`

Historical implementation status: `experimental`

Capability: `conversion.capability-provider.select-execute`

Acceptance Profile: `T05-capability-provider-contract`

Release train: `0.1.0-SNAPSHOT`

T05 establishes the project-owned Capability Provider seam independently of
the Document Workflow execution profile. Provider metadata reports a stable
Provider ID, supported capability IDs, engine version, execution mode,
availability, request/result/time limits, engine license, and distribution.
The four Provider execution modes are in-process Java, native linkage, a local
subprocess, and a remote service. They do not imply whether the enclosing
Document Workflow uses trusted in-process execution or the future Hardened
Worker Profile.

## Historical implementation evidence

The account below describes the original T05 phase. Its prerequisite and
certification observations are historical; the current #84 account follows.

- `ProviderCatalogTest` exercises immutable project-owned request/result
  values, deterministic declaration-ordered selection, metadata disclosure,
  all four execution modes, consistent remote metadata, declared limits,
  sanitized adapter failures, and the remote disclosure boundary. Its
  deterministic remote Provider is in memory and contacts no network service.
  Byte-limit enforcement is exercised by the subprocess contract tests below.
- `WorkflowCapabilityProviderTest` exercises metadata-only discovery through
  an immutable Workflow Environment, explicit Provider preferences,
  workflow-visible selections, unavailable-Provider failures, empty offline
  defaults, and capability-scoped remote authorization through
  `DocumentWorkflow.execute`. Workflow selection does not invoke Provider
  adapter code or expose a generic Provider locator through Document Session.
- `SubprocessCapabilityProviderTest` runs a project-authored Java fixture over
  the real external Provider seam. It covers bounded protocol exchange,
  pre-start input rejection, output rejection with no oversized result,
  deadline termination, startup failure, non-zero exit, abrupt process halt,
  malformed output, an exited parent whose inherited output pipe remains open,
  stable safe diagnostics, and cleanup after every tested success and failure
  exit.
- `PublicApiLeakageIT` reflectively checks both T05 jars for signatures outside
  project-owned and JDK types and specifically rejects PDFBox,
  process-implementation, and network-client types.
- `JarContractIT` checks the two intentional Automatic-Module-Name values,
  Java 8 class-file version, notices, unbundled dependencies, and BOM entries.
- `./scripts/inventory check`, `./mvnw -B -ntp verify`, and
  `./scripts/verify-jdk-matrix.sh` are the repository gates for inventory
  drift, the full build, and JDK 8/11/17/21 execution.

The default Workflow Environment contains no Provider registration, so it
cannot perform implicit network access. A Provider registration never grants
remote disclosure permission. A request must authorize disclosure for the
same capability before a remote Provider is eligible, and the Provider
execution contract checks authorization again before invoking its adapter.

`SubprocessCapabilityProvider` launches a fixed argument list without shell
expansion, exchanges a versioned length-framed payload, discards child stderr,
terminates the child at the request timeout or an output-policy failure, and
confirms direct-child termination before returning. Failure to confirm
termination or remove its private per-run staging directory is normalized to
a stable execution failure. It is a generic adapter only; no external engine
or downstream OCR, Office, shaping, rendering, commercial, or remote
implementation is bundled or claimed.

This record is implementation evidence, not independent Acceptance Evidence.
No syntax, standards, semantic, or visual Acceptance Evidence chain has been
recorded as passing. T03 is also still `experimental`, so T05's declared
Dependency Gate is open. T05 therefore remains `experimental`; T06 is still
required before any compatibility claim or certified-platform claim.

## Current issue #84 certification account

Foundation obligation: `providers`; dependency: `transactions`.
[T05 certification contract](../../docs/t05-certification.md),
[frozen 51-case inventory](../profiles/T05-capability-provider-contract/coverage.json),
[59 mandatory named tests](../profiles/T05-capability-provider-contract/mandatory-tests.txt).
The current [Foundation evidence index](../foundation-evidence.yaml) owns the
exact staged candidate, four Ubuntu 24.04/Linux x86-64 Temurin builds, two Native
profiles and 40 separate syntax/standards/semantic/visual/contract records.
Compatibility is valid only with that complete dependency-satisfied index and
current actual source, artifacts, configuration, tools and observations.

Actual public observations cover declaration-order and exact preferences,
metadata-only selection and immutable snapshots, offline defaults, byte bounds,
timeout policy, safe adapter failures, framed subprocess faults/lifecycle and
controlled loopback disclosure. Registration/preference does not disclose;
wrong-capability consent and later unauthorized requests remain refused.
Explicit Workflow selection and explicit direct Provider Request authorization
are separate controls, with parent-brokered execution in both Native profiles.

All applicable native, Stable and isolated Preview published outcomes are
reopened and checked with original T03 blank-PDF predicates and controls.
Provider byte transfer does not replace PDF standards or visual evidence.
Facades execute IN_PROCESS and Provider-specific controls retain the individual
Native-only mapping rationale. Strict collection reexecutes the exact candidate
and rejects omitted/skipped/repeated cases, tuples/chains, altered payloads,
frames, findings or documents, stale identities and resealed invalid controls.

The current four PDF narratives are T84-provider-syntax/standards/semantic/visual;
the additional T84-provider-contract narrative owns boundary controls. Windows
and macOS remain uncertified. Generic cleanup supervises only the direct child
and owned per-run staging; arbitrary in-process termination, Provider descendant
containment and hard OS isolation are not claimed. Other open slices remain
Foundation blockers. The original T05 record and prior failed observations keep
their historical identities.

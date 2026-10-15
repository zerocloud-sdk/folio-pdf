# Capability Provider certification (#84)

The `providers` Foundation control certifies
`conversion.capability-provider.select-execute` through both Native Workflow
profiles on the four exact Ubuntu 24.04 / Linux x86-64 Temurin environments in
[the environment authority](../capabilities/foundation-environments.yaml).
The current candidate and all eight tuples are defined by
[the evidence index](../capabilities/foundation-evidence.yaml). Each tuple must
retain separate syntax, standards, semantic, visual and contract records.
Windows and macOS remain uncertified under ADR-0040.

The [frozen inventory](../capabilities/profiles/T05-capability-provider-contract/coverage.json)
traces all nine issue criteria and the assigned specification requirements.
Its [59 mandatory named tests](../capabilities/profiles/T05-capability-provider-contract/mandatory-tests.txt)
must execute exactly once per tuple with zero failures, ignored cases,
assumption skips or duplicates. The original recorder additionally retains 51
closed public cases; an aggregate test count cannot replace their observations.

Selection occurs through public Workflow Environment, Request and detached
Outcome values. It reports immutable version, mode, availability, limits,
license and distribution facts without invoking an adapter. Declaration-order
selection filters unavailable or unauthorized registrations. Exact preferences
never fall back. Empty defaults remain offline; registration does not activate
a downstream capability or discover an engine. Availability is a snapshot.

Actual public Provider calls qualify inclusive request/result byte boundaries,
first excess, positive and maximum timeout policy, capability/preference
agreement, copied values and fixed safe failures. The base checks the requested
timeout; adapters own elapsed enforcement. Arbitrary in-process code cannot be
forcibly terminated by that contract.

The project-authored loopback fixture exchanges only four synthetic bytes over
`t05-synthetic-length-framed-v1`. Original endpoint payloads, request counts,
disclosed lengths, parent identity and response bytes are retained. Registration,
preference, wrong-capability Workflow authorization and absent direct request
consent produce no transfer. Explicit authorized selection remains non-invoking;
explicit direct request consent permits the exact exchange. A later request
inherits no consent. Generic execution is an explicit public Provider call in
caller work, independent of scoped Workflow selection. No remote-service adapter
or endpoint fixture is shipped in a product artifact.

The real generic subprocess seam retains successful and malformed response
frames, actual Java child/PID and staging observations, fixed command arguments,
request identities and monotonic elapsed bounds. It qualifies pre-start input
refusal, output excess, wrong magic/version, negative/oversized/truncated/trailing
output, startup failure, nonzero exit/crash, stalled input/output and a direct
child that exits while a descendant holds its pipe. Boundary observations check
termination, closed caller pipe descriptors, removed owned staging and intact
caller roots/sentinels. The acceptance-owned descendant exits after cleanup;
this does not certify descendant containment by the generic adapter.

Every actual successful publication, including the committed prefix of a failed
multi-Target publication, is reopened through public observations. Borrowed
resources remain open, failure identities are safe, ordered receipts preserve
COMMITTED/FAILED/NOT_ATTEMPTED and uncommitted sentinels remain unchanged.
Existing Stable and Preview Facade operations retain matching outcomes; Preview
runs in a separate JVM containing its actual jar without the Stable edition.
Both Facades execute IN_PROCESS. Provider controls retain their individual
Native-only reason and gain no fabricated Facade counterpart.

The PDF workload consists exclusively of actual resource-free, one-page,
612 by 792 point documents. Four independent chains inspect those exact bytes:
pinned qpdf syntax; qualified pdfcpu/Arlington standards rule union and original
rule-specific controls; public page/Catalog/Pages/Resources semantics; and pinned
PDFium/ImageMagick against the authored opaque white 144-DPI raster at zero AE
and zero fuzz. The reused T03 predicate identities remain explicit; they are not
renamed to invent Provider PDF rules. Synthetic Provider exchange alone supplies
none of these PDF chains. The separate contract chain owns Provider boundaries.

The strict collector validates original manifests, raw payloads/frames, all
closed cases, runtime/configuration identities and actual tools. It reexecutes
the exact candidate and compares original documents, pixels and findings with
fresh observations. Only separately qualified volatile PIDs, endpoint ports,
private staging suffixes and monotonic timings may vary. Rehashing or relabeling
invalid evidence, omitting cases/tuples/chains, replacing a workload or changing
a fixture/tool/configuration cannot produce PASS.

Run with the declared observer Python environment (PyYAML 6.0.2 for host
orchestration, pinned Ubuntu Python 3.12 for container observers) and explicit
`FOLIO_FOUNDATION_PYTHON_ROOT` / `FOLIO_HARFBUZZ_HELPER` inputs:

```sh
python3 -B scripts/t03-foundation.py stage
python3 -B scripts/t03-foundation.py plan <fresh-provider-plan> --obligation providers
python3 -B scripts/t03-foundation.py certify <fresh-provider-output> --obligation providers
python3 -B scripts/t03-foundation.py collect <tuple>/observations --obligation providers --execution-profile IN_PROCESS
python3 -B scripts/t03-foundation.py collect <tuple>/observations --obligation providers --execution-profile HARDENED_WORKER
```

Every output directory is fresh; `collect` selects the tuple's actual profile.
Container networking remains `none`, with the acceptance endpoint reachable
only over loopback. Provider execution mode is distinct from Workflow profile;
PDF Worker networking/runtime-closure restrictions continue to apply.

Freeze source, contracts, fixtures and tools before final staging. Refresh all
invalidated previously certified obligations in dependency order, starting with
transactions, before collecting Providers. Historical and failed observations
keep their identities. Generate/validate/check the public views, run focused and
full Maven verification and fixed-baseline Standards/Spec review, then evaluate
live readiness with current staged/cache bytes. Every previously satisfied
obligation must remain satisfied. Other outstanding Foundation slices may keep
global readiness false and remain outside #84. Certification grants no release
publication, issue mutation or external-service authority.

For an inherited refresh, `certify --defer-index` records a complete obligation
in its own fresh directory while leaving the prior evidence index unchanged.
Independent sibling obligations may run in disjoint directories only after
their recorded prerequisites complete. Their shared candidate, contract and
unchanged prior-index identities must agree before strict atomic combination;
all original reports and observations remain in their original directories.
No deferred run is considered current evidence until the combined index passes
the same completeness, transitive-byte and stale-authority checks. Provider
collection follows the complete inherited refresh.

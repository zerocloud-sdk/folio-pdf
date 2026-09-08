# T71 delivery receipt

This change completes the implementation and current-candidate certification for
`document.value.inspect-patch` and the Foundation `values` obligation (#71),
including fresh `transactions` prerequisite evidence. The reviewed change remains
uncommitted on `main` relative to `543c582cb41104f7da43b9d801c629894dcec34a`.
The complete Maven reactor, unchanged default four-JDK matrix and final inventory
checks have passed, and the independent Standards and Spec final gates both pass
with no remaining findings. The owned candidate worktree has been safely removed.
All 23 approved Goal criteria are satisfied and recorded in the checklist below.

## Implemented scope

The [source traceability and frozen operations](../../../docs/t09-implementation-plan.md)
cover nine PDF Value kinds, session-stable Object References, bounded views,
resource ownership, safe failures, rollback and preservation. The seven Patch
operations set/remove dictionary entries, set/insert/remove array elements,
replace values and replace decoded stream data with explicit encoding. Public
Native and Facade workflows cover inspect/change/publish/reopen outcomes and
Publication Receipts, including valid publication after a caught Patch failure.

Stable and Preview each expose the frozen 17 public types and 89 declared
members. Preview includes Stable mappings; actual-jar tests reject mixed
classpaths in both orders. The dictionary-only and missing stream-mutation
release blockers are resolved by implemented success behavior. Remaining
stream/filter and protected-structure rules are documented retained contracts.
See the [English values contract](../../../docs/document-values.md),
[Chinese usage](../../../docs/zh-CN/getting-started.md),
[capability inventory](../../capability-matrix.yaml),
[Facade inventory](../../facade-surface.yaml) and
[clean-room provenance](../../../PROVENANCE.md).

## Certified candidate

- Candidate: `ea1d430215b3bcaf50c29021ae2a9c74a36c6002f90e065d5abf1d52c280e7f0`.
- Contract: `717a4baa6f11807aa8044c8306813b627b7e120b06bcf88f3b5ab1e7acebd750`.
- [Build receipt](build-inputs.json): 955 source inputs, 30 contract inputs,
  23 actual unsigned artifacts and 24 harness/runtime files.
- Build receipt SHA-256: `9b3b86b3824a7f83e308c173eecfbb7995ee125aa3d4143230121b4ea12f4821`.
- Authority: [current Foundation evidence](../../foundation-evidence.yaml).
- Actual observations: [eight values scopes](../foundation/T71-values-r3/) and
  [eight transactions scopes](../foundation/T71-transactions/).

All 16 scopes bind the same current candidate and contract. Each obligation has
Ubuntu 24.04/Linux x86-64, JDK 8/11/17/21 × IN_PROCESS/HARDENED_WORKER observations.
The Native execution mode is selected explicitly; Facade observations accurately
record IN_PROCESS. Values executes 83 public/actual-jar tests per scope (664 total),
transactions 34 per scope (272 total), without skips. Refreshing transactions
retains the exact eight values certification objects.

The 64 PASS chain records retain actual products, reports, tools, configurations,
environments and negative controls. Independent review checked 5,544 referenced
files; 40 products; 1,576 rule-specific negative diagnostics; all 62 new-rule
qualification invocations; 48 matching positive rasters; 16 one-pixel negative
controls; and eight raw-stream preservation controls. T09 explicitly qualifies
51 required standards rules. The final effective encoded streams and page content
match the Source; the re-encoded incremental control fails despite preserving
the old prefix and decoded bytes. The 144 DPI, opaque sRGB, zero-fuzz, AE 0 visual
contract is unchanged. Missing tools/rules or uncertain execution do not become
PASS.

## Delivery validation

| Validation | Result | Record |
| --- | --- | --- |
| `./mvnw -B -ntp verify` | PASS; 1,332 test entries, 0 failures/errors, 4 opt-in skips; 38:34 min | [Log](full-verify.txt), [invocation and input checks](full-verify-result.json), [log/XML totals](full-verify-tests.json), [75 raw XML reports](full-verify-reports.zip) |
| `./scripts/verify-jdk-matrix.sh` | PASS; all four default JDKs, 5,328 entries, 0 failures/errors, 16 opt-in skips | [Complete log](jdk-matrix.txt), [invocation and input checks](jdk-matrix-result.json), [per-JDK totals](jdk-matrix-tests.json), [final JDK 21 XML reports](jdk21-matrix-reports.zip) |
| `./scripts/inventory generate` | PASS after matrix | [Log](post-matrix-inventory-generate.txt) |
| `./scripts/inventory validate` | PASS after matrix | [Log](post-matrix-inventory-validate.txt) |
| `./scripts/inventory check` | PASS after matrix; generated documents current | [Log](post-matrix-inventory-check.txt) |
| `./scripts/inventory readiness` | Expected exit 1: values and transactions SATISFIED; global NOT READY | [Full report](post-matrix-inventory-readiness.txt) |
| `git diff --check` | PASS | [Command record](git-diff-check.txt), [Git state](final-git-state.json) |

| Default matrix JDK | Test entries | Failures / errors | Opt-in skips | Reactor duration |
| --- | ---: | --- | ---: | --- |
| 8 | 1,332 | 0 / 0 | 4 | 41:56 min |
| 11 | 1,332 | 0 / 0 | 4 | 35:36 min |
| 17 | 1,332 | 0 / 0 | 4 | 33:40 min |
| 21 | 1,332 | 0 / 0 | 4 | 34:16 min |

Full verify and the default matrix use the explicit Folio HarfBuzz installation
recorded in their invocation receipts, SHA-256
`169389e19e28bc96e3e878a9468671c31ccc6d5e7abbc3d38c4b8470526cdc65`.
Full verify runs in the isolated candidate worktree; the unchanged default matrix
runs in the delivery workspace using all four immutable Foundation images.
All 1,032 staged references match before and after both complete verification
commands. The [post-matrix checks](post-matrix-checks.json) also compare the complete
source membership with the staged build before and after serial inventory commands;
the candidate and contract identities remain unchanged.
The [workspace copy receipt](workspace-copy.json) records protection of the 577
original task paths and exact source/artifact/harness transfer.

Each full verification run has the same four existing opt-in skips: Worker tests
`configuredConcurrencyLimitAdmitsLimitAndRejectsFirstExcess`,
`generatedFiveThousandPageWorkloadCompletesAndReopens`,
`generatedExactOneGiBInputCompletesWithinStagingProfile`, and T30 test
`pinnedRastersDecodeAndMatchEveryDeclaredVariant`. They are not counted as executed
tests: full verify executes 1,328 tests, and the four-JDK matrix executes 5,312.
All T09 and transactions certification tests execute. The XML archives retain
full verify and the final matrix JDK 21 reports; the complete matrix log retains
all four runs. Earlier JDK XML files were overwritten by subsequent default runs.

## Independent review and history

The clean-context [Standards review](standards-review.md) and
[Spec review](spec-review.md) cover the complete fixed-baseline working-tree scope,
including untracked implementation and actual evidence. Both report no remaining
source or evidence findings. Both final gate reviews independently confirm the
completed verification, current identities, retained evidence, workspace cleanup,
uncommitted Git state and this receipt. The [Goal checklist](completion-checklist.md)
maps all 23 completed criteria to the retained evidence. The
[final delivery check](final-delivery-checks.json) records the closing mechanical
identity, file-retention and Git checks after the review verdicts were recorded.

Actual RED/GREEN/refactor observations and review corrections remain in
[development history](../../../docs/t09-development.md),
[the core-structure correction](../T71-review-correction/README.md) and
[the raw-observation index correction](../T71-index-correction/README.md).
The [prequalification](../T71-prequalification/README.md),
[interrupted first candidate](../T71-review-attempt-1/README.md) and
[second candidate's failed index publication](../T71-index-attempt/README.md)
retain their original identities. They are not relabeled as current certification.
The second attempt's complete replay graph and earlier staged artifacts are
retained in the delivery workspace's ignored `.build-cache/t71` archives.
All 29 T71 observer logs are visible for delivery through a scoped ignore-rule
exception; hash-bound raw bytes remain unchanged.

## Workspace disposition

The [cleanup receipt](final-workspace-checks.json) records exact content and
executable-mode equality for all 10,860 delivery paths before the owned
`open-pdf-t71-candidate` worktree was removed. Every delivered path remained
present afterward, and the complete 1,032-reference staged build still matches.
The other worktrees are unchanged. The [copy manifest](worktree-copy-manifest.json)
is the observation made before cleanup; subsequent review verdict and receipt-only
updates remain separate from the frozen candidate and contract inputs.

Historical artifacts, the complete 4,428-file r2 replay graph and 56 development
logs/probes/snapshots remain in the delivery workspace's ignored `.build-cache/t71`
archives. These are retained validation history. The delivery contains no active
temporary candidate worktree. Branch `main` remains at the fixed baseline, with
an empty index and the reviewed implementation/evidence as uncommitted changes.

## Scope and remaining limits

Readiness satisfies values and transactions, while other Foundation obligations
keep the global result NOT READY. The standards evidence covers the frozen T09
profile, not all ISO 32000 requirements or PDF/A/PDF/UA conformance. Windows
x86-64 and macOS x86-64/arm64 remain uncertified. Facade certification records its
actual IN_PROCESS mode. Stream/filter, finite-resource, ownership and protected
structure restrictions remain the documented contract. Acceptance tools and
fixtures do not enter product runtime.

No commit, push, PR, tracker change, tag, signing, publication or Central operation
has been performed.

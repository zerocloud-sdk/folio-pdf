# Issue #79 delivery receipt

Completed locally under the sole execution contract. The clear-metadata child is compatible and independently certified; aggregate security and #80 remain incomplete.

- Comparison and execution baseline: `7420a656d27d17b82c7632be7c6214f8a657a22f` on `main`.
- Candidate: `b9b31a8df2a137446b49da766cb97ed124c6a29efb637faec7e542029d371197`.
- Foundation contract: `5b8bc8bc32eb8a0ece2c320ba16f1a395afd107f7b1d7eb35a6d75a5448ce68f`.
- Optional local commit: omitted. The worktree contains this ticket's changes.
- [Machine receipt](receipt.json), [source freeze](source-freeze.json), [final identity/evidence audit](validation/final-evidence-audit.json), [command ledger](validation/commands.jsonl).

This execution resumed the existing dirty worktree. The interrupted `full-verify-final.txt` remains unchanged and makes no passing claim. The successful replacement is [full-verify-resumed-r3.txt](validation/full-verify-resumed-r3.txt). The [resume snapshot](validation/resume-start.json) binds the starting files and unchanged contract. Java and the container runtime were restored; missing image layers were downloaded by exact digest into a separate task-local store. [Environment probes](validation/resume-environments.json) and subsequent certification records verify actual identities. The first resumed full run found stale generated readiness; the prior view and failing log remain retained, followed by regeneration and a passing focused inventory regression.

Native `ALL_EXCEPT_METADATA` and Facade `DO_NOT_ENCRYPT_METADATA=8` cover the audited R4 RC4-128/AES-128 and R5/R6 AES-256 inputs, R4 legacy and R6 outputs, PDF-version rules, exact credentials, independent owner authority, permissions, protected rewrite and incremental publication. Catalog XMP bytes remain clear; Info and stream-dictionary strings, component metadata, content and embedded data remain protected. All-content AES-256 stays the default. See the [profile audit](../../../docs/research/T79-clear-metadata-profile-audit.md) and [public contract](../../../docs/pdf-version-password-security.md).

## Observed certification

| Ubuntu 24.04 Linux x86-64 | Actual vendor/build | Native IN_PROCESS and HARDENED_WORKER | Facade IN_PROCESS |
| --- | --- | --- | --- |
| JDK 8 | Eclipse Adoptium / `1.8.0_502-b07` | Pass | Pass |
| JDK 11 | Eclipse Adoptium / `11.0.32+9` | Pass | Pass |
| JDK 17 | Eclipse Adoptium / `17.0.20+8` | Pass | Pass |
| JDK 21 | Eclipse Adoptium / `21.0.12+8-LTS` | Pass | Pass |

Every tuple passed 23 public/artifact tests and four separate syntax, standards, semantic and visual chains. Each retained 78 randomized products, 37 original encrypted inputs, 117 scope controls and 95 baseline security controls, plus core-document and visual controls. The eight new tuples retain 624 product artifacts. Exact images, Java executables, native/tool hashes and settings are in the [fresh certification records](../foundation/T79-final/password-clear-metadata/).

Transactions, values, pages, metadata, annotations, text, images, incremental and password-baseline were refreshed first on the same candidate: 80 current certifications and 320 passing chain records in total. The baseline retains 544 product artifacts. Windows and macOS remain explicitly uncertified and nonblocking.

## Validation and review

The smallest baseline command passed 31 tests; [Native/Facade regressions](validation/review-regressions-1.txt) and [Worker regressions](validation/review-worker-regressions-1.txt) passed all applicable baseline and new scope tests. The exact smallest command was `./mvnw -B -ntp -pl pdf-document -am -Dtest=PdfVersionPasswordSecurityWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`.

`./mvnw -B -ntp verify`, `./scripts/verify-jdk-matrix.sh`, the real-tool independent profile, all 84 Foundation collector tests, live replay with isolated resealed-finding/Native-mode/Facade-mode rejection, unsigned staging, all ten certification routes, and inventory validate/generate/check passed. [Exact commands, environments, durations, exit codes and log hashes](validation/commands.jsonl) and the [final audit](validation/final-evidence-audit.json) retain the evidence. Ordinary Maven's opt-in skips do not substitute for the passing independent profile or mandatory #79 chains.

The [verification summary](validation/verification-summary.json) records actual test counts and omissions. Ordinary verification leaves the three unrelated `HardenedWorkerScaleProfileTest` cases disabled unless `folio.pdf.t22.scale` is selected: concurrency stress, 5,000 pages and a 1-GiB input. It also omits the unrelated `T30BarcodeEvidenceCommandTest.pinnedRastersDecodeAndMatchEveryDeclaredVariant` unless `t30.raster` is selected. Required password-security tests and chains passed; the independent T78/T79 profile ran all nine tests without skips.

[Standards](reviews/standards-resume.md) and [Spec](reviews/spec-resume.md) reviews have no unresolved findings. Their source-review reports explicitly identified later validation gates; the final audit now proves those gates. Regression evidence covers preflight, explicit Crypt normalization, catalog/component separation, Identity defaults and unexpected checker diagnostics. Historical development failures remain retained as failures and are not final certification evidence.

The first text-certification attempt stopped on three JDK 8 worker timeouts. The unchanged focused replay, complete 126-test suite, and 20 repeated short-method runs all passed; source, staged artifacts, harness and environment identities matched. The exact cause was not established. No source or time limit changed. The failed attempt remains retained, and the complete fresh [text-r2 route](../foundation/T79-final/text-r2/) subsequently passed every required tuple. The [diagnosis](validation/diagnosis-text-timeouts/README.md) and [selected certification runs](validation/certification-selection.json) distinguish the attempts.

## Completion criteria

All 21 criteria pass. Their complete authoritative text and evidence hashes are in [receipt.json](receipt.json).

| Contract criterion | Result | Evidence |
| --- | --- | --- |
| 1 | Pass | [profile](../../../docs/research/T79-clear-metadata-profile-audit.md), [certification](../../../docs/t79-certification.md), [freeze](../../../capabilities/evidence/T79-delivery/source-freeze.json) |
| 2 | Pass | [contract](../../../docs/pdf-version-password-security.md), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [index](../../../capabilities/foundation-evidence.yaml) |
| 3 | Pass | [profile](../../../docs/research/T79-clear-metadata-profile-audit.md), [certification](../../../docs/t79-certification.md), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 4 | Pass | [profile](../../../docs/research/T79-clear-metadata-profile-audit.md), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [collector](../../../capabilities/evidence/T79-delivery/validation/collector-all-final.txt) |
| 5 | Pass | [contract](../../../docs/pdf-version-password-security.md), [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [worker](../../../capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 6 | Pass | [contract](../../../docs/pdf-version-password-security.md), [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [worker](../../../capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 7 | Pass | [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [worker](../../../capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 8 | Pass | [facade](../../../capabilities/facade-surface.yaml), [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 9 | Pass | [certification](../../../docs/t79-certification.md), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [index](../../../capabilities/foundation-evidence.yaml) |
| 10 | Pass | [collector](../../../capabilities/evidence/T79-delivery/validation/collector-all-final.txt), [live](../../../capabilities/evidence/T79-delivery/validation/live-qualification.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [commands](../../../capabilities/evidence/T79-delivery/validation/commands.jsonl) |
| 11 | Pass | [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [index](../../../capabilities/foundation-evidence.yaml) |
| 12 | Pass | [inventories](../../../capabilities/capability-matrix.yaml), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 13 | Pass | [freeze](../../../capabilities/evidence/T79-delivery/source-freeze.json), [facade](../../../capabilities/facade-surface.yaml), [inventories](../../../capabilities/capability-matrix.yaml), [provenance](../../../PROVENANCE.md), [commands](../../../capabilities/evidence/T79-delivery/validation/commands.jsonl) |
| 14 | Pass | [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [index](../../../capabilities/foundation-evidence.yaml) |
| 15 | Pass | [native](../../../capabilities/evidence/T79-delivery/validation/native-baseline-resumed.txt), [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [worker](../../../capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt) |
| 16 | Pass | [commands](../../../capabilities/evidence/T79-delivery/validation/commands.jsonl), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [collector](../../../capabilities/evidence/T79-delivery/validation/collector-all-final.txt), [live](../../../capabilities/evidence/T79-delivery/validation/live-qualification.txt) |
| 17 | Pass | [standards](../../../capabilities/evidence/T79-delivery/reviews/standards-resume.md), [spec](../../../capabilities/evidence/T79-delivery/reviews/spec-resume.md), [freeze](../../../capabilities/evidence/T79-delivery/source-freeze.json), [worktree](../../../capabilities/evidence/T79-delivery/validation/final-worktree-status.txt) |
| 18 | Pass | [standards](../../../capabilities/evidence/T79-delivery/reviews/standards-resume.md), [spec](../../../capabilities/evidence/T79-delivery/reviews/spec-resume.md), [regression](../../../capabilities/evidence/T79-delivery/validation/review-regressions-1.txt), [worker](../../../capabilities/evidence/T79-delivery/validation/review-worker-regressions-1.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 19 | Pass | [worktree](../../../capabilities/evidence/T79-delivery/validation/final-worktree-status.txt), [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json) |
| 20 | Pass | [worktree](../../../capabilities/evidence/T79-delivery/validation/final-worktree-status.txt), [freeze](../../../capabilities/evidence/T79-delivery/source-freeze.json) |
| 21 | Pass | [audit](../../../capabilities/evidence/T79-delivery/validation/final-evidence-audit.json), [freeze](../../../capabilities/evidence/T79-delivery/source-freeze.json), [commands](../../../capabilities/evidence/T79-delivery/validation/commands.jsonl) |

## Remaining boundaries

Global Foundation readiness is **NOT READY** for unrelated obligations; the selected member, its dependencies and all previously certified members are satisfied. Remaining obligation IDs: `acceptance`, `barcodes-1d`, `barcodes-2d`, `candidate`, `canvas`, `docs`, `environments`, `facade-artifacts`, `fonts`, `graphics`, `integration`, `java-artifacts`, `limits`, `pagination`, `paragraphs`, `password-attachments`, `provenance`, `providers`, `publication-controls`, `recovery`, `rendering`, `reproducibility`, `security`, `shaping`, `supply-chain`, `tables`, `tables-base`, `tables-pagination`, `worker`. The [readiness report](validation/inventory-final-readiness.txt) retains every blocker.

The [known supplemental checker limitation](qualification-limitations/all-content-metadata-stdcf/classification.json) concerns a baseline all-content original outside the owned clear input matrix. Its exact XMP rewrite regression remains implemented, and both APIs' emitted rewrites pass all four chains. No required #79 case is waived. The [provenance](../../../PROVENANCE.md) records original sources, fixtures, licenses and tool limits.

The final worktree contains only ticket changes. No local commit, push, PR, tracker mutation, external message or release publication occurred. The orchestrator retains review and publication authority.

# SPEC EXECUTION RECEIPT — issue #78

Completed locally against the sole execution contract. The baseline member is independently certified; the parent security aggregate remains incomplete. This delivery makes no release publication or tracker-completion claim.

- Source: `/workspace/contracts/issue-78-contract.md` (SHA-256 `864e2c12dde0b6f2a3977d23b8f3fe367fd16c244fd62fd17d8b26a1ddad33ef`).
- Review fixed point and unchanged HEAD: `861c4ba81c7aecf9fba15052859f8cc80fd0259d` on `main`.
- Local commit: omitted (optional). The worktree contains this ticket’s uncommitted changes.
- Candidate: `7e22418b8fa557d1128131bea5ae6c0893b9846aefe82315a83572d42d98e36a`.
- Foundation contract: `7c93911015a20b6bfc15a98a59001ba003ed6eb4269ae03e97c816c995b7e964`.
- [Machine receipt](receipt.json), [source freeze](source-freeze.json), [artifact identities](validation/staged-candidate.json), [final evidence audit](validation/final-evidence-audit.json), and [review findings and resolutions](review.md).

## Delivered behavior

The Native Interface and matching Migration Facade now cover the frozen PDF-version and all-content password baseline: PDF 1.0–1.7/2.0 input declarations, 1.7/2.0 output, AES-256 defaults, admitted legacy profiles with request-scoped output opt-in, R5 input, credential preparation and boundaries, owner authority and permissions, protected Sources/rewrite/incremental publication, and Existing Signature intersection. The source audit records required successes and source-grounded exclusions.

The baseline links 61 Facade mappings (48 added, 13 existing lifecycle members), with matching Stable/Preview signatures and public resource/credential ownership. The [profile audit](../../../docs/research/T78-baseline-profile-audit.md), [security contract](../../../docs/pdf-version-password-security.md), [certification contract](../../../docs/t78-certification.md), and [surface manifest](../../../capabilities/facade-surface.yaml) contain the exact member and behavior tables.

Independent syntax, standards, semantic and visual observations cover 45 successful original inputs, 68 products per Native tuple, 95 security controls and 19 core-document controls. The eight baseline tuples retain 544 actual product artifacts and 32 passing chain records. PDF 1.7 R6/ADBE Level 8 remains qualified interoperability; PDF 2.0 R6 is the normative profile. These are the documented closed-profile predicates.

## Actual certification

| Ubuntu 24.04 Linux x86-64 | Observed JDK vendor/build | Native modes | Facade mode |
| --- | --- | --- | --- |
| [JDK 8](../foundation/T78-final/password-baseline/jdk8-environment/environment.yaml) | Eclipse Adoptium / `1.8.0_502-b07` | IN_PROCESS and HARDENED_WORKER: pass | IN_PROCESS: pass |
| [JDK 11](../foundation/T78-final/password-baseline/jdk11-environment/environment.yaml) | Eclipse Adoptium / `11.0.32+9` | IN_PROCESS and HARDENED_WORKER: pass | IN_PROCESS: pass |
| [JDK 17](../foundation/T78-final/password-baseline/jdk17-environment/environment.yaml) | Eclipse Adoptium / `17.0.20+8` | IN_PROCESS and HARDENED_WORKER: pass | IN_PROCESS: pass |
| [JDK 21](../foundation/T78-final/password-baseline/jdk21-environment/environment.yaml) | Eclipse Adoptium / `21.0.12+8-LTS` | IN_PROCESS and HARDENED_WORKER: pass | IN_PROCESS: pass |

The same candidate also has eight refreshed certifications each for transactions, values, pages, metadata, annotations, text, images and incremental: 72 current certifications and 288 separate passing chain records in total. Immutable images, Java executables, native/tool identities, execution commands, actual randomized artifact hashes and raw reports are retained in the [evidence index](../../foundation-evidence.yaml) and [fresh run directories](../foundation/T78-final/).

## Validation

| Check | Final result and evidence |
| --- | --- |
| Smallest Native security command | 31 tests passed; [exact command and result](validation/commands.json), [log](validation/native-focused.txt) |
| Affected Worker/signature, Facade and artifact contracts | Passed; [development commands](validation/commands.json); each baseline tuple also passed its complete 44-test public/artifact suite |
| `./mvnw -B -ntp verify` | Passed; [full validation record](validation/full-validation.json), [log](validation/full-verify.txt) |
| `./scripts/verify-jdk-matrix.sh` | JDK 8/11/17/21 passed; [log](validation/jdk-matrix.txt) |
| `independent-certification` profile | All 6 T78 tests passed, including the real-tool case; [exact command](validation/independent-profile-command.json), [log](validation/independent-profile-final.txt) |
| Foundation Python collectors | All 73 tests passed; [log](validation/foundation-collector-frozen.txt) |
| Live collector qualification (68 products) | Passed on a copy of certified evidence; changed/resealed findings and isolated false Native/Facade modes were rejected; [record](validation/live-qualification.json), [driver](validation/live-qualification.py) |
| Unsigned stage and all 9 certification routes | Passed; [commands, environment, times, exit codes and log hashes](validation/certification-validation.json) |
| Inventory validate/generate/check | Passed; [records](validation/inventory-final.json) |
| Live readiness | Expected NOT READY for obligations outside this ticket; no baseline or retained-obligation regression; [report](validation/inventory-readiness-final.txt), [audited blockers](validation/final-evidence-audit.json) |

The smallest command was `./mvnw -B -ntp -pl pdf-document -am -Dtest=PdfVersionPasswordSecurityWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test`. Exact environment settings and every other command are in the linked records. Historical development failures remain labeled as failures and are separate from the final passing gates.

Successful final full verify runs retained three opt-in HardenedWorkerScaleProfileTest skips and one opt-in T30 barcode raster skip. No #78 test was skipped; the independent-certification profile passed all six T78 tests, including its real-tool case.

## Completion criteria

All 20 criteria from the sole execution contract pass. The machine receipt retains their complete text and evidence paths.

| # | Criterion | Result |
| --- | --- | --- |
| 1 | Auditable source/profile/member table | Pass |
| 2 | Version declarations, precedence, outputs and transitions | Pass |
| 3 | Secure defaults and admitted legacy profiles | Pass |
| 4 | Credentials, authority and permission observations | Pass |
| 5 | Protected Sources, publication and signature intersection | Pass |
| 6 | Complete matching Facade and compiled surface | Pass |
| 7 | Baseline child, inherited gates and separate downstream members | Pass |
| 8 | Eight Native tuples and actual Facade execution modes | Pass |
| 9 | Candidate, source, contract, environment, product and tool identities | Pass |
| 10 | Qualification and collector fail-closed behavior | Pass |
| 11 | Coordinated authorities, generated docs, provenance and migration notes | Pass |
| 12 | Same-candidate previous obligations and live readiness | Pass |
| 13 | Smallest public validation and affected tests | Pass |
| 14 | Full Maven, JDK matrix, real-tool profile and certification route | Pass |
| 15 | Java 8, clean-room license, ownership, isolation and non-goals | Pass |
| 16 | Final diff review against the fixed baseline | Pass |
| 17 | Standards/Spec findings resolved and verified | Pass |
| 18 | Optional commit boundary respected | Pass |
| 19 | Only ticket changes in the worktree | Pass |
| 20 | Delivery receipt with identities, results, blockers and review | Pass |

## Changed files and review

The implementation delta is in `pdf-document` (password preparation/parser/security, protected Workflow behavior and Worker catalog), `pdf-migration-itext7` (Reader/Writer properties, version/security observations, constants, ownership and shared Preview contracts), and `pdf-acceptance` (T78 products, recorder and bounded coordinator lifecycle). Original fixtures, qualified tool supplements, observers, collectors and provisioning live under `capabilities/profiles`, `build-tools/acceptance` and `scripts`. Authorities, generated views, English/Chinese documentation and `PROVENANCE.md` were updated together. The source freeze records every input hash; [final worktree status](validation/final-worktree-status.txt) identifies the delivery delta.

Standards findings concerning credential workspace ownership, partial UTF-8/property-copy cleanup, file-key lifetime and coordinator descendant/private-file cleanup were resolved. Spec findings concerning R5 owner preparation, a legacy replacement-byte alias and explicit Facade output-version inspection were resolved. The final Standards review and primary-agent Spec review are recorded in [review.md](review.md), with regression evidence and final validation. No unresolved in-scope finding remains.

## Remaining boundaries

Global Foundation readiness remains NOT READY. `document.version-password-security.baseline` is compatible; `document.version-password-security` stays experimental. The separate #79 metadata-clear and #80 embedded-files-only obligations remain incomplete, including their required mappings and certification. Existing fixture-proven metadata-clear legacy input behavior is preserved.

Remaining obligation IDs reported by live readiness: `acceptance`, `barcodes-1d`, `barcodes-2d`, `candidate`, `canvas`, `docs`, `environments`, `facade-artifacts`, `fonts`, `graphics`, `integration`, `java-artifacts`, `limits`, `pagination`, `paragraphs`, `password-attachments`, `password-clear-metadata`, `provenance`, `providers`, `publication-controls`, `recovery`, `rendering`, `reproducibility`, `security`, `shaping`, `supply-chain`, `tables`, `tables-base`, `tables-pagination`, `worker`.

Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified and are not F0.1.0 blockers. Java 8 compatibility, Apache-2.0 clean-room provenance, backend-neutral public types and acceptance-only tool isolation remain intact. No downstream Forms, Trust signing, Conformance, SVG/XML, OCR, Sanitization or Office work was added.

The optional local commit was omitted. No push, PR creation/merge, tracker mutation or release publication occurred. There is no further planning decision needed to complete this selected scope; the orchestrator retains review and publication authority.

```text
SPEC EXECUTION RECEIPT
Conclusion: completed locally — issue #78
Spec source: /workspace/contracts/issue-78-contract.md
Review fixed point: 861c4ba81c7aecf9fba15052859f8cc80fd0259d
Acceptance criteria: all 20 pass; exact criteria and evidence in receipt.json
Main changes: certified version/password baseline; 61 Facade mappings
Changed files: implementation, acceptance tools/fixtures, authorities/docs and fresh evidence
Branch / commit / review: main; uncommitted; Standards/Spec findings resolved
Validation: full verify, JDK 8/11/17/21 matrix, real-tool profile, 73 collector tests, live qualification, 72 certifications and inventory checks pass
Not executed: optional commit; Windows/macOS certification; remote mutations/publication
Risks and remaining work: closed baseline profile; #79/#80 and other Foundation obligations remain incomplete
Planning-thread decision needed: none for selected scope; publication belongs to orchestrator
Final worktree state: only this ticket’s uncommitted changes
External effects: no remote mutations or messages; local validation and unsigned staging only
```

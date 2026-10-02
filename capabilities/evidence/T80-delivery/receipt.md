# T80 delivery receipt

Conclusion: **COMPLETED — all 22 contract criteria PASS.**

Owned capability: `document.version-password-security.attachments`; Foundation
member `password-attachments`; profile `T32-password-embedded-files-only`.
The execution authority is `/workspace/contracts/issue-80-contract.md`, SHA-256
`2d57acbb1f84f7d83d2dfa2d6512f84e15dcc82151d456d9db06fdd273c2d871`.
The [criterion authority](criteria-authority.json) retains all 22 exact criteria.

Comparison baseline and unchanged HEAD:
`08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.
Unsigned candidate: `58efc286fa1b4134024d452a1ecfed78111a81a41c5c117b4837d90ffb353231`.
Foundation contract: `4dff6a8ccc1e6923bbe5142b8da5eca0f2f779e4ccf2b18b504b9bd79282ee8d`.
Attachment acceptance pin:
`7c7ed55bbb3b1a1f898e50e381bb193e1df796b92a6a20677ced978e20df170a`.
The [identity audit](identity-summary.json) confirms 88 same-candidate Native
certifications and 352 passing chain records across all eleven obligations.
It retains exact environment, native/tool, corpus, configuration, source,
contract, harness and artifact identities. The [staged build receipt](staged-build-inputs.json)
binds 2,829 candidate source inputs, 23 artifacts, 32 contract inputs and 644 harness inputs.

The implementation changes Native crypt-filter routing, attachment authentication
and protected access, matching Facade constants/observations, public consumer and
artifact tests, independent fixtures/observers/collectors, certification authorities
and documentation. The [worktree audit](worktree-audit.json) lists tracked changes;
the [criterion evidence map](evidence-map.json) identifies the new source, test,
profile and evidence files. All changes belong to issue #80.

All certified environments use Ubuntu 24.04 Linux x86-64 and Eclipse Adoptium.
Each row ran both Native IN_PROCESS and HARDENED_WORKER, with actual Facade
IN_PROCESS. Full immutable image, Java executable, native/tool and execution
configuration hashes are retained in [identity-summary.json](identity-summary.json).

| JDK | Exact build | Native tuples | Facade mode | Result |
| --- | --- | --- | --- | --- |
| 8 | `1.8.0_502-b07` | IN_PROCESS; HARDENED_WORKER | IN_PROCESS | PASS |
| 11 | `11.0.32+9` | IN_PROCESS; HARDENED_WORKER | IN_PROCESS | PASS |
| 17 | `17.0.20+8` | IN_PROCESS; HARDENED_WORKER | IN_PROCESS | PASS |
| 21 | `21.0.12+8-LTS` | IN_PROCESS; HARDENED_WORKER | IN_PROCESS | PASS |

Validation commands and modes are retained in the linked records and each
tuple's `contract-tests-command.json` and `execution.yaml`:

| Executed validation | Environment / result |
| --- | --- |
| `./mvnw -B -ntp -pl pdf-document -am -Dtest=PdfVersionPasswordSecurityWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test` | Smallest initial public Native baseline check; [record](validation/baseline-native.txt). |
| Native/Facade affected consumer regressions | Host Linux / Temurin 17, IN_PROCESS: 123 Native and 33 Facade tests passed; [record](validation/public-regressions-r10.txt). |
| Native T78/T79/T80 worker regressions | Host Debian Linux x86-64 / Temurin 17, actual HARDENED_WORKER: 61 tests passed; [record](validation/worker-regressions-r6.txt). This is development validation. |
| `./mvnw -B -ntp verify` | Frozen final source, host Linux / Temurin 17: passed; [record](validation/full-verify-r3.txt). |
| `./scripts/verify-jdk-matrix.sh` | Actual pinned Ubuntu 24.04 Linux x86-64 images / JDK 8, 11, 17, 21: passed; [record](validation/jdk-matrix-r1.json). |
| T80 independent-certification Maven profile | Actual pinned Ubuntu 24.04 Linux x86-64 / Temurin 17: all three real-tool tests passed, zero skips; [record](validation/independent-t80-ubuntu-r2.txt). |
| `PYTHONPATH=.build-cache/foundation-host-python python3 -B -m unittest discover -s scripts/tests -p 'test_*foundation.py'` | 96 collector/negative-control tests passed; [record](validation/collector-all-r4.txt). |
| Pinned scope/control qualification and live collector controls | 111 new scope controls, 95 retained baseline controls, and six live controls passed; [qualification](validation/scope-control-qualification-r1.txt), [live checks](validation/live-collector-r1.txt). |
| `python3 -B .build-cache/t80-resume-security-r7.py` | Passed with exit 0. Unchanged original certification implementation; all eight baseline collectors independently replayed, then fresh clear-metadata and attachment routes; [identity audit](identity-summary.json). |
| `./scripts/inventory validate`, `generate`, `check` | All passed with exit 0; generated documentation is current: 26 capabilities, 217 Facade surfaces and 14 exclusions. |
| `./scripts/inventory readiness` | Executed live against the final candidate: security and all eleven refreshed obligations are SATISFIED. Expected exit 1 because 27 unrelated Foundation obligations remain blocked; [actual result](validation/foundation-readiness-final-r1.json). |

The unrelated opt-in skips (`HardenedWorkerScaleProfileTest`, three of three
tests, and `T30BarcodeEvidenceCommandTest`, one of six tests) are recorded separately in
[unrelated-opt-in-skips.json](validation/unrelated-opt-in-skips.json).
No mandatory T80 behavior test, tuple, rule or chain is waived.

The implementation routes protection through actual EF relationships, effective
EFF selectors and admitted explicit Crypt filters. Native and Stable/inherited
Preview Facade consumers preserve clear ordinary document content, require the
declared credentials for protected attachments, enforce permissions, and retain
secure all-content defaults. The source-traceable profile distinguishes legacy,
qualified PDF 1.7 extension and normative PDF 2.0 behavior.

| Criterion | Result | Observable evidence |
| --- | --- | --- |
| 1. Source-traceable profile | PASS | [Profile audit](../../../docs/research/T80-embedded-files-only-profile-audit.md) and [English contract](../../../docs/pdf-version-password-security.md) identify required successes, extension qualifications and source-grounded exclusions. |
| 2. Actual embedded-file coverage | PASS | [Original corpus](../../profiles/T80-embedded-files-only/cases.json), actual products and independent original-byte predicates prove clear ordinary content and object-specific EF/Crypt selection. All eight tuples retain passing four-chain [attachment evidence](../foundation/T80-final/password-attachments/observed-index.json). |
| 3. Native/Facade credentials and payloads | PASS | [Native public tests](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/EmbeddedFilesPasswordWorkflowTest.java), [Facade public tests](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/EmbeddedFilesPasswordFacadeTest.java), and final scope tuples cover creation, reopening, listing, exact extraction, absent/wrong/correct and destroyed credentials. |
| 4. Facade selection and ownership | PASS | Public consumer and compiled artifact tests cover both scope constants, documented selector combinations, copied properties, borrowed credentials and caller streams; [surface authority](../../facade-surface.yaml) inventories Stable and inherited Preview behavior. |
| 5. Fixtures and controls | PASS | Independently authored originals, 111 [scope controls](../../profiles/T80-controls/rules.json), and 95 retained baseline controls cover dictionary types, routes, versions, credentials, permissions, owner proof, Perms and tampering. [Qualification result](validation/scope-control-qualification-r1.txt). |
| 6. Access and preservation | PASS | Native public tests exercise PDF Value access, extraction, mixed primary/named Sources, replacement, page mutation, rewrite, donor strength, version, incremental and existing-signature restrictions. [Regressions](validation/public-regressions-r10.txt). |
| 7. Profiles, limits and lifecycle | PASS | [Worker regressions](validation/worker-regressions-r6.txt) passed 61 public tests. All eight attachment tuples passed 24 public/artifact tests each, covering both actual Native profiles, bounded input/decoding/memory/temp/elapsed work, cleanup and detached returns. |
| 8. Public outcomes and publication | PASS | Native tests assert reopened outcomes, exact payloads, ordered NOT_ATTEMPTED receipts, preserved Targets, partial publication and caller ownership through DocumentWorkflow. |
| 9. Four qualified chains | PASS | Every required original and emitted product retains separate passing syntax, standards, semantic and visual chains; unauthenticated original clear-content observations precede credentials. [Certification contract](../../../docs/t80-certification.md), [final attachment index](../foundation/T80-final/password-attachments/observed-index.json), and [all record/report hashes](identity-summary.json). |
| 10. Collector and privacy | PASS | [96 collector tests](validation/collector-all-r4.txt) and [six live resealed/mode/missing-chain controls](validation/live-collector-r1.txt) passed. Reports retain categorical findings and hashes; private credential intermediates are removed. |
| 11. Actual required environments | PASS | Eight passing attachment tuples cover actual Ubuntu 24.04 / Linux x86-64 × JDK 8/11/17/21 × both Native modes, with actual Facade IN_PROCESS on every JDK; immutable image/JDK/Java/settings/native/tool identities are bound in the [identity audit](identity-summary.json). Windows/macOS remain explicitly uncertified and non-blocking for F0.1.0. |
| 12. Current ordered refresh | PASS | Same unsigned candidate; transactions, values, pages, metadata, annotations, text, images, incremental, password-baseline, password-clear-metadata, password-attachments. All eleven obligations have exactly eight current tuples in the [current authority](../../foundation-evidence.yaml). Successful pages use fresh `pages-r3`; failed `pages` remains unindexed. [Ordered identity audit](identity-summary.json). |
| 13. Inventories agree | PASS | Capability Matrix, Facade Surface, Foundation member/mapping records and evidence index agree; attachment dependencies remain password-baseline and metadata with inherited external gates. Final validate/generate/check passed; [current disposition](foundation-disposition.json). |
| 14. Existing aggregate security | PASS | Exactly password-baseline, password-clear-metadata and password-attachments remain required. All three members and aggregate security are SATISFIED; [live result](validation/foundation-readiness-final-r1.json) and [27 concrete unrelated blockers](foundation-disposition.json). |
| 15. Documentation and provenance | PASS | English/Javadoc, [README](../../../README.md), [Chinese usage](../../../docs/zh-CN/getting-started.md), [0.x migration](../../../docs/migrations/0.x-t03-document-workflow.md), generated inventories and [provenance](../../../PROVENANCE.md) are updated together. Historical evidence retains its original meaning. |
| 16. Defaults, predecessors and compatibility | PASS | 156 Native/Facade regressions passed; secure AES-256 all-content defaults and request-scoped Legacy Security Mode remain. Full four-JDK verification proves Java 8/API compatibility. Baseline, clear-metadata and attachments each have eight current passing tuples. Acceptance tools/assets remain outside product runtime. |
| 17. Smallest-first development checks | PASS | [Initial baseline selection](validation/baseline-native.txt), [new scope checks](validation/scope-public-r3.txt), public Native/Facade and actual Worker regressions retain the executed selections and modes. |
| 18. Mandatory gates | PASS | [Full verify](validation/full-verify-r3.txt), [four-JDK matrix](validation/jdk-matrix-r1.json), [actual Ubuntu independent tests](validation/independent-t80-ubuntu-r2.txt), collectors, live controls, complete final attachment route and final inventory check passed. [Retained gate audit](validation/retained-gate-audit-r1.json). [Unrelated opt-ins](validation/unrelated-opt-in-skips.json) are separately recorded; no mandatory scope behavior/tuple/rule/chain is waived. |
| 19. Final reviews | PASS | Final [Standards review](reviews/standards-delivery-final.md) and [Spec review](reviews/spec-delivery-final.md) passed against the exact contract and recorded baseline, with zero unresolved in-scope findings. Source R3 and recovery R7 reviews remain retained; final tracked-diff and evidence hashes agree. |
| 20. Optional commit | PASS | Omitted; no local commit has been created. |
| 21. Worktree protection | PASS | [Final audit](worktree-audit.json) confirms unchanged baseline HEAD on main, empty Git index, no new commits, passing diff-check, 35 tracked ticket paths and ticket-owned untracked additions. Initial compilation was clean; resumed ticket work and historical evidence were preserved. No unrelated work was changed. |
| 22. Delivery receipt | PASS | This receipt, [all 22 exact criteria](criteria-authority.json), [evidence map](evidence-map.json), identity summary, validation results, two final reviews, aggregate disposition, concrete remaining blockers and worktree audit are complete. The optional local commit is omitted; no release-publication claim is made. |

The initial final-route setup stopped before observations because its parent
directory was missing; staging had passed and the same candidate was retained.
The first page attempt was not accepted after one JDK 21 hardened split test
exceeded the workflow elapsed-time limit. Three isolated exact public replays
passed in 3–4 seconds, and the fresh complete page route subsequently passed all
eight tuples without source or setting changes. [Failure/replay/current-page
record](validation/pages-jdk21-replay-r1.json). The cause of that earlier elapsed
failure was not established; its partial observations remain retained and unindexed.

The first baseline JDK 21 worker recorder passed all 44 public/artifact tests
but was not accepted after one independent PDFium visual subprocess exceeded
the unchanged 30-second tool bound. Three exact visual replays on its original
product passed in 3.866, 3.235 and 3.221 seconds. The fresh worker tuple passed
all 44 tests, full recording and independent collection; baseline index
finalization retained all eight current tuples. The failed original remains
unindexed. [Failure and exact replay record](validation/baseline-worker-visual-timeout-r1.json).
The cause of that earlier tool-bound failure was not established.

The unexecuted R6 proposal to reuse collector reports was dropped after a
Standards review identified the missing prior digest anchor. R7 reran every
unchanged baseline collector and equality-guarded retained reports/records;
[Standards correction review](reviews/standards-recovery-r7.md) and
[Spec correction review](reviews/spec-recovery-r7.md) both passed. No cached
collector verdict was used by R7.

Final aggregate security status: **SATISFIED**, including all existing three members.
Full Foundation 0.1.0 readiness: **NOT READY**, with 27 unrelated blocked obligations:
limits, worker, recovery, canvas, graphics, fonts, rendering, providers,
paragraphs, pagination, tables, shaping, barcodes-1d, barcodes-2d, tables-base,
tables-pagination, environments, java-artifacts, facade-artifacts, acceptance,
docs, provenance, supply-chain, reproducibility, candidate, publication-controls
and integration. Their exact owners, slices and concrete diagnostics are
retained in [foundation-disposition.json](foundation-disposition.json) and the
[live readiness result](validation/foundation-readiness-final-r1.json).
Final [Standards review](reviews/standards-delivery-final.md): **PASS**.
Final [Spec review](reviews/spec-delivery-final.md): **PASS**.
Both report zero unresolved in-scope findings.
Final worktree audit: **PASS**; [worktree-audit.json](worktree-audit.json).

No commit, push, PR, tracker mutation, signing/upload or release publication was
performed. This is local implementation and certification delivery.

# Spec review against baseline 42fe5c797711070b319b9419e14fcb7adfa98735

Source: /workspace/contracts/issue-83-contract.md and the issue #83 snapshot,
read in full. Reviewer: spec_review; independent of Standards review.

One P2 finding was resolved before the final freeze: JSON normalization had
removed /ID from arbitrary qpdf object values, allowing a resealed Catalog
mutation to hide altered raw findings. Normalization now preserves arbitrary
object /ID fields and allows only the trailer and its exact physical XRef-stream
duplicate. The new resealed Catalog mutation guard fails altered findings while
allowing legitimate paired trailer IDs to vary. The reviewer independently
reran that test and confirmed the fix.

No further implementation gaps or scope creep found. The 165-case mandatory
inventory, actual workload artifacts, public recovery outcomes, independent
observations, actual IN_PROCESS Facade mode and bounded claims match the
contract. Observer rename/imports/Java path/pins are consistent. Final review
conclusion: zero outstanding Spec findings. Final source-bound certification,
validation and execution receipt remain separate required gates.

Follow-up review accepts the test-only front-object fixture: it preserves
exactly 1,073,741,824 consumed bytes and the near-end xref. The collector binds
the frozen prefix, newline fill, tail, offsets, length and hash; the reviewer
independently reran the miniature-input rejection guard. No new Spec finding.

A follow-up P2 concerning cross-product child finding hash swaps after
canonical comparison was resolved. Before comparison, original and fresh runs
bind every recovery/Facade child standards hash and all four raw-chain exact
hash prefixes to its actual PDF. Positive and negative semantic records bind
raw JSON, input, command, tool and observer identities before normalization.
Cross-product reseal and producer/graph mutation guards pass. The reviewer
confirms zero outstanding Spec findings in the final inspected source.

A confirmed P2 concerning Python equality of boolean/integer/float JSON scalars
was resolved with canonical JSON serialization and typed Count/MediaBox
predicates. Resealed scalar mutations fail comparison. Relative visual authority
links compare their resolved files only outside scale observations; the
wrong-authority guard fails. The reviewer confirms both fixes and no outstanding
Spec finding. The 34-test r8 suite passed before restaging.

The final baseline-relative source review independently verifies every final-r2
hash (2,887 source / 32 contract inputs), baseline HEAD, unchanged T82 evidence
and verbatim historical T22. It includes the new delivery orchestration, audit
and receipt renderer. No shipped runtime source or POM changed; the conditional
build matrix is not triggered. Spec conclusion: zero outstanding findings.
Final certification, scale, verification, readiness and evidence-completeness
are assessed separately after execution.

The continuation review after the retained password-baseline Worker exit failure
found no applicable Spec findings. It preserves the eight passing obligations,
requires five complete unchanged diagnostic product replays, freshly certifies
all eight password-baseline tuples without reusing partial records, and resumes
dependency order. Audit and receipt checks preserve the failed attempt and
require complete final evidence without claiming a root cause or runtime fix.
All five replays subsequently passed with 68 products and 68 completed public
records each. Continuation gates and final evidence-completeness review remain
required.

The diagnostic archive follow-up found no applicable Spec findings. Read-only
verification checked all 1,236 archived files against their retained originals
and manifest hashes. All six product runs retain 68 PDFs with matching output
hashes and COMMITTED, reopened, source-preserved publication records. The r1
writer gate and its original log remain failed; diagnostics remain outside the
certification index. Final evidence-completeness review is still pending.

The second continuation review found zero applicable Spec findings. Read-only
verification checked all 1,250 attachment diagnostic archive entries against
their original files and manifest hashes; all five runs retain 62 PDFs with
matching output hashes, COMMITTED, reopened and source-preserved public records,
and complete clear observations. All 2,887 frozen source and 32 contract-input
hashes remain exact. The failed attachment attempt is unindexed and its prior
80-tuple / 320-chain index is unchanged. The continuation requires fresh
certification of all eight attachment tuples, then limits, worker and recovery
in dependency order. Audit and receipt checks preserve both shutdown failures
without claiming a root cause, runtime fix or failure-rate bound. The extracted
archive helper passed its live-data gate; follow-up review found zero regression
and confirms all product/public-record/clear-observation bindings remain checked.
Final evidence-completeness review remains pending until all final gates pass.

The third-failure diagnostic follow-up found zero applicable fidelity findings.
Read-only review verified the original image/options and unchanged staged method;
the added helper is parent-only, while Worker classpath construction still uses
the staged runtime closure. The first eight recorded command/helper/log hashes
matched during review; all ten invocations subsequently passed. The frozen
inputs and 80-tuple / 320-chain index remain unchanged. Successful diagnostics
do not certify attachments or establish a fix, cause or failure-rate bound.
The contract permits a fresh all-eight attachment qualification after preserving
the completed diagnostic gate and reviewing a new continuation that treats r3
as failed, preserves its partial tree, then reruns attachments before limits,
worker and recovery. Completion still requires the fresh full certifications,
updated audit and receipt disclosure of all three shutdown failures, and final
evidence-completeness review.

The concrete r4 continuation/archive/audit/receipt review found zero applicable
Spec findings. Read-only checks confirm every prior-index SHA link, the unchanged
80-tuple / 320-chain index, exclusion of failed and diagnostic scopes, and exact
archived bytes across all three ZIPs (2,498 entries). The ten method replays and
Java 8 helper remain retained; the archive validation log binds current audit
code. R4 requires all eight fresh attachment tuples, then limits, worker and
recovery; the final audit requires fourteen ordered successes and 100 / 412
counts while retaining all three failures. Receipt disclosure limits diagnostic
conclusions, including the absent prior-suite context of isolated method runs.
The strengthened current-audit-SHA preflight also passes. Qualification, final
audit and evidence-completeness review remain pending.

The fourth-attempt comparison follow-up found the bounded aggregate-memory
allowance Spec-compatible: both actual peaks match their own executions and
finite policies; exact retained values and hashes stay intact. One P2 source
binding gap was identified and resolved before the next freeze. Exact producer
inventories now reject missing files, empty directories, extra files, nonfiles
and direct directory/file symlink aliases before enabling normalization. The
deletion regression first failed at the intended seam; all final r12 guard
tests pass, including deletion/aliasing of each source file on both sides.
Read-only review verified red/r12/actual replay-r2 log hashes, unchanged captured
properties and the archived original r4 audit hash. The concrete r5 plan
requires a distinct frozen/staged candidate and all fourteen fresh obligations;
the final audit separates candidate2 diagnostics from candidate3 certification.
Resolution review concludes zero remaining applicable Spec findings. Freeze,
staging, refreshed certification and final evidence-completeness are pending.

Final delivery preflight review found zero actionable Spec findings. Read-only
checks confirmed all 2,887 frozen source and 32 contract inputs, matching
staged/portable candidate3 receipts, helper3's current audit-code binding and
unchanged archived r4 audit bytes. The audit preserves all four failed histories,
separates candidate2 diagnostics and requires fourteen fresh ordered r5 successes
for candidate3 with exact 100/412 scope. The receipt discloses failures and
diagnostic limits; ordinary Maven counts stay separate from zero-skip mandatory
certification. Verification, certification, final inventory/readiness/audit and
evidence-completeness review remain pending.

The r5 final-validation handoff preparation review found zero applicable Spec
findings. `validate-final-r5.py` requires all fourteen ordered r5 successes,
the staged candidate, exact 100/412 counts and the final index hash before
inventory validation, and rechecks frozen inputs after each action. The final
audit separately requires recovery and every prerequisite to be SATISFIED
before accepting overall NOT READY. Handoff helpers preserve all four failures,
separate candidate2 diagnostics from candidate3 certification, and distinguish
ordinary skips from mandatory execution. Final evidence completeness, receipt,
ownership and final review remain pending before commit.

### Fifth failure and recorder replay preparation (2026-10-08)

The Spec reviewer reported zero applicable findings in the retained failure or diagnostic probe. The recorder stack identifies the unchanged 900,000-ms coordinator bound. Original failed bytes remain intact; the index remains 80 certifications / 320 chain records with SHA-256 `f073a34f04162a29a39bfb2d1ab126449a2801f8c701a013992fe3cc3fc03a70`; frozen inputs and staged candidate match. Replay changes only the output mount/path. Outcomes were pending at this review.

The prospective continuation can preserve the first ten same-candidate successes, freshly certify all eight attachment tuples in a new root, then run limits/worker/recovery in unused r5 roots. This meets the contract's same-candidate and historical-preservation requirements. This is a preparation review, not final completeness or a successful recorder qualification.

Before continuation, inspect each diagnostic outcome: its wrapper intentionally permits expected timeout results and can finish successfully despite those failures. Its overall exit alone proves no successful recorder qualification. Preserve both runs and cleanup evidence; update final audit/receipt to include the fifth failed attempt and ordered effective successes. Any source fix requires a new freeze, staging and fresh affected certification.

### Draft r6 guard correction (2026-10-08)

The Spec reviewer found a concrete P2: diagnostic original-file records are ordered by `sorted(Path)` while the draft compared sorted relative strings. At `authentication/user/...` and `authentication/user-proof/...`, these orders differ even for an unchanged tree. The unexecuted helper now sorts Paths before converting to strings, exactly matching the producer. A read-only Python check against the actual retained 7,062-file diagnostic inventory passed; the producer ledger and failed tree were not rewritten. Final source-review conclusion and diagnostic outcomes remained pending.

The reviewer then reported zero remaining applicable findings. The corrected guard matches the producer's ordering across all 7,062 retained files without changing diagnostic records. Both recorder replays must individually exit zero, seal outputs and report no timeout; candidate/history/index identity, cleanup and archive validation are required. The helper freshly selects all eight attachment tuples, then limits/worker/recovery in order, checking exact tuples/counts after successes and stopping on failure. Diagnostics and prerequisite gates remained pending; the unexecuted helper's source review establishes no certification completion.

### Unexecuted full-observer duration probe preparation (2026-10-08)

Standards review found that the draft's preservation checks followed its success assertion. They now run and record original complete inventory/hashes, copied-input bindings, index, staged build and frozen source even after nonzero exit or timeout. Sealed PASS and temporary-storage assertions remain success-only. The reviewer confirmed zero remaining findings in that correction.

Spec review found a timing confounder: the draft's TMPDIR used the mounted workspace instead of the original container /tmp. The new diagnostic Python owner uses the original /tmp backing and private-directory prefix, invokes the unchanged pinned isolated Python observer and removes private storage in finally on normal child exits. Its source hash is retained; host timeout cleanup removes only the unique previously absent named diagnostic container. The measurement includes Python-owner overhead and does not reproduce Java supervision. Both reviewers confirmed zero remaining applicable findings after this correction.

These are read-only preparation reviews. The duration probe was unexecuted and the second original 900-second replay remained pending. The r6 continuation remained blocked by its explicit successful-replay prerequisites. No certification completeness or cause/fix was claimed.

### Completed attachment duration diagnosis and bounded T80 correction (2026-10-08)

The separate `validate-attachments-duration-r1` gate passed. Both read-only reviewers reported zero applicable findings: it preserves the original failed probe, requires its exact extra-assertion traceback, checks actual observer exit zero and sealed 7,060 files / 7,058 manifest entries, binds all 1,797 tool commands and original/copy/index/frozen/staged identities, and validates owner cleanup. The diagnostic measured 905.293 seconds including Python-owner overhead; result sealing occurred at 904.790 seconds. It exports zero certifications and makes no universal performance or JVM supervision claim.

Both reviewers then reported zero applicable findings in the acceptance source correction. Only T80 selects the closed 30-minute coordinator preset; the existing 15-minute default, 5-minute Java individual-tool limit, Python 30-second limits, output limits and private lifecycle remain intact. The focused four-test gate passed without failures, errors or skips. Shipped runtime and POM inputs are unchanged; acceptance code uses Java 8 APIs, so the contract's conditional compatibility-matrix command is not triggered. New source freeze, staging, full verification, explicit four-environment scale and all fourteen fresh obligations are required. R5 successes cannot certify the changed candidate. These source reviews do not establish final evidence completeness.

### Final-r4 delivery-helper preparation review (2026-10-08)

Standards review found an audit-fidelity gap: the new duration block trusted prior tool-limit summary fields and silently collapsed duplicate manifest entries. The current audit rejects duplicate entries, binds the exact previously validated sealed manifest/result hashes and rechecks all 1,797 command limits, absence of limit events and stdout/stderr hashes. The executed historical validator and summary remain unchanged. The reviewer confirmed zero remaining applicable findings.

Spec review found a P2: the fifth-failure timeout stack was incorrectly sought in the parent driver log. It is now checked in the child recorder, bound to the complete original 7,062-file snapshot; the failed parent gate remains unchanged. The reviewer confirmed zero remaining applicable findings. Candidate2 Worker diagnostics, candidate3 T80 diagnostics and current candidate4 remain distinct. The new archive helper also executes the read-only current fifth-failure/duration audit block and binds the current audit SHA.

Both reviewers found zero further applicable issues in the R7 driver and final inventory driver: all fourteen obligations must freshly pass for candidate4 in dependency order, with exact 100/412 identities and preserved five-failure continuity. Full verification, standalone scale, R7 qualification, final inventory/readiness and final completeness review remain pending. These preparation reviews establish no qualification or completion.

### Verification shutdown probe revisions (2026-10-08)

Actual final-r4 full verification errored once in the existing public shaping HARDENED_WORKER method; the original gate/XML/text remain failed and intact. Both reviewers inspected the exact parameterized selection and original-image/classpath/cwd/provider fidelity. Standards found missing loaded-binary inventories and both reviewers found an unhandled diagnostic timeout path; revision1 remained immutable. Its 100 normal passes are diagnostic-only, omit the original empty classpath element and do not bind loaded binary bytes.

The unexecuted revision2 was corrected before execution: complete original classpath including its empty module-cwd element and helper classes are inventoried/hashed before and after each run; unique initially absent owned containers are checked after each run. Timeout rows persist before cleanup, nested cleanup timeout/launch errors are recorded and final ownership/input checks may stop the probe with durable evidence. Both reviewers confirmed zero remaining applicable source findings. Revision2 then executed ten real method/profile replays successfully with unchanged input inventories and no remaining owned container per-run. No timeout, cause, fix, full-suite-context reproduction or rate guarantee is claimed. Original failure and both diagnostics are retained; all diagnostics export zero certifications. Fresh complete verification and final certification/reviews remain pending.

### Guarded full-verification retry delivery preparation (2026-10-08)

Both reviewers reported zero applicable findings in the updated audit/receipt/R7 driver and guarded retry. The exact earlier audit SHA `50bc6c04006a6b4c21b7cb342b6ed677e9e449be79017f89f0a8eec640d096d2` is archived in `audit-final-r7-preverify-source.py`, preserving helper4's successful source binding. The current audit fixes r1/r2 ledger hashes and checks original failed report, source/helper/log references and captured classpath proof. The retry rechecked the actual complete loaded inventory before Maven started. Subsequent report regeneration cannot relabel historical bytes; these diagnostics remain excluded from certification.

The prepared helper5 exercises the updated audit block and binds its current hash; R7 still requires all fourteen fresh qualifications of candidate4. Full verification5 was running at review. Helper5, scale3, R7 certification, final inventory/readiness, final audit/receipt and final completeness review remain pending. No cause, fix or failure-rate guarantee is inferred from isolated diagnostics.

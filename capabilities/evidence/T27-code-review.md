# T27 independent code review

Final source review: Standards has zero unresolved required findings and one
optional duplication judgment; Spec has zero unresolved required findings and
no scope expansion. Both apply to source/test SHA-256
`cce6f26c4e96f386a172bebc83b9ebc0636688c226cabe8d306d2fd4982447c9`.
The final host verification and JDK 8/11/17/21 matrix subsequently passed with
922/0/0/3 each, on the unchanged source/test tree. Their exact results and hashes
are in the [verification receipt](artifacts/T27-verification.txt). Historical
findings and independent correction reports are preserved separately below.

Fixed base: `1d1bf4a92eecf42dc370872fe1bcf5b142e42452`.
The user explicitly required a clean-context Standards / Spec review before
completion. Two separate read-only agents reviewed
`git diff 1d1bf4a92eecf42dc370872fe1bcf5b142e42452` and every path from
`git ls-files --others --exclude-standard`. The committed three-dot diff was
empty at review time and was not used to omit the uncommitted implementation.
No implementation agent substituted for either independent reviewer.

## Initial Standards report

Reviewer: `/root/t27_standards_review` (Ohm).
Reviewed source/test snapshot:
`4d4605e8072b5eac7ad07471c2fdaac4f5744e62e20b4b3050b9a246ab799208`.
Scope: 29 tracked changed files and all 197 untracked delivery files.

Required: **P1, retained graphics bypass the owned-memory budget.** The initial
`rowBytes` calculation charged fixed inline metadata and text bytes but omitted
graphic payloads. Nested images remained reachable after `AppendTableRows`,
while Worker command decoding released its reservation. The reviewer observed
64 independently decoded 262,144-byte images (16 MiB) retained under an 8 MiB
owned-memory limit. This violated ADR-0016/ADR-0018's explicit memory bounds and
the execution contract's admission requirement. The requested correction was
to account for complete graphics in initial sections and appended rows and
release those reservations with the retained semantic state.

Optional judgment: **Possible Duplicated Code.** The margin-to-area conversion
in `PdfBoxLargeTableOperations.begin` repeats the corresponding conversion in
`PdfBoxParagraphOperations.Layout`. A shared internal helper could keep the two
paths consistent. This is a heuristic improvement, not a documented-standard
violation, and was not required for this delivery.

The reviewer independently verified the source/test identity, raw log hashes
and 916/0/0/3 host/four-JDK results, Worker inventory, both PDF structures and
all nineteen profile/raster hashes and changed-pixel counts. No other required
documented-standard violation was established.

Initial Standards: **one required P1; one optional smell**.

## Initial Spec report

Reviewer: `/root/t27_spec_review` (Kepler). Same fixed base, source/test identity
and complete tracked/untracked scope as above.

Required: **P1, retained graphics bypass the large-table memory budget.** The
contract requires “持续追加、未完成跨度、重排和 Worker 传输均不能绕过有限预算”.
The reviewer independently reran the public HARDENED_WORKER reproduction and
observed 16 MiB of retained raw-image bytes under an 8 MiB budget before flush.
It requested complete retained-graphic admission, including initial repeated
sections, and meaningful budget-boundary regressions.

No other required defect or unrequested scope was found. The reviewer verified
source/test identity and original verification-log hashes, all nineteen
profiles and 114 PNG hashes/dimensions/pixel counts, and 300 additional public,
reopened span/width/minimum-height configurations preserving every scalar.

Initial Spec: **one required P1**.

## Correction and follow-up

The [public large-table suite](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/LargeTableWorkflowTest.java)
now includes a reproduced append failure in both profiles and an initial-section
failure in-process (Worker already rejected that oversized message during
transport). The [development receipt](artifacts/T27-development.txt) distinguishes
actual behavioral Reds from a zero-case selector and a test compilation error.
Both graphics methods subsequently passed in both profiles.

The correction scans nested programs and every variable graphic payload through
noncopying length accessors. It charges initial repeated sections in declaration
memory and body graphics in the existing row reservations, bounds recursion and
arithmetic before admission, and keeps cumulative declaration counters separate
from releasable body memory. The shared declaration scan also protects buffered
table flows. Existing reservations still release as rows flush or complete.

Follow-up found that declaration admission alone did not resolve the whole P1.
The Session-wide Canvas group/image/mask/color caches still strongly referenced
painted Composition declarations after row reservations were released. An
independent public probe with 128 fresh 256 KiB images, retained-row bound 3,
8 MiB owned-memory policy and 32 MiB Worker heap terminated under heap pressure
while reporting only one retained row. It returned `WORKER_TERMINATED`, target
`NOT_ATTEMPTED`, and preserved the sentinel. The earlier 64-row/128-MiB-heap
probe was too small to establish declaration release; the preliminary Spec
resolution was withdrawn. A new public bounded-heap regression was then developed before the cache-lifetime
correction. The remaining P1 was subsequently resolved, independently re-reviewed
and verified as recorded in the final follow-ups below.
Independent PDF standards/conformance and Foundation compatibility remain
separate open gates regardless of this code review.


## Final Spec follow-up

On source/test snapshot
`cce6f26c4e96f386a172bebc83b9ebc0636688c226cabe8d306d2fd4982447c9`,
Kepler independently confirmed that the P1 is resolved and found no remaining
required Spec defect or unrequested scope across 30 tracked changes and 198
untracked files. The original unflushed admission reproduction still rejects
with `MEMORY_LIMIT_EXCEEDED`. The previously failing stream now completes 128
fresh 256 KiB graphics with a 32 MiB Worker heap, an 8 MiB owned-memory bound,
retained-row bound three, 63 flushes, zero retained rows at completion and 64
reopened pages. The reviewer verified source/test identity and cache Red/Green
log hashes and traced the call-local cache's lifetime: only backend resources
and operator data escape through the static Plan. Recursive sharing within a
drawing and direct Canvas behavior remain intact. English/Chinese and inventory
statements correctly describe that scope. Full verification and the JDK matrix
were still pending when this source review concluded.

Final Spec: **zero unresolved required findings; no scope expansion**.


## Final Standards follow-up

On the same final snapshot `cce6f26c…2447c9`, Ohm independently confirmed that
both complete-graph admission and declaration-cache lifetime are corrected.
It found no remaining required Standards finding across 30 tracked changed
files and every one of the 198 untracked files. The original image-admission
probe rejects with `MEMORY_LIMIT_EXCEEDED`; all thirteen oversized variable-
field probes reject before publication. A separately authored 128-row stream
passed in both profiles with the 32 MiB Worker heap, 8 MiB owned-memory bound,
retained bound three, zero final retained rows and 64 reopened pages. Recorded
modeled peaks were 2,194,400 bytes in-process and 4,804,278 bytes in Worker.
These are ledger measurements, not RSS. The reviewer checked cache Red/Green
log hashes and the unchanged 667-entry Worker inventory.

The source trace confirms that each Composition drawing's temporary cache
owner becomes unreachable after its static Plan has yielded backend resources
and operator bytes. Initial repeated sections retain their reservation until
completion and body reservations track retained rows. No surviving table or
Session cache reference holds released Composition declarations. Direct Canvas
behavior and sharing within one recursive drawing remain intact.

The optional margin-to-area conversion duplication remains a judgment call;
it is deferred because changing that separate shape is unnecessary to fix the
resource-lifetime contract. Final host/four-JDK results were still pending at
this source-review conclusion.

Final Standards: **zero unresolved required findings; one optional smell**.

The two axes remain separate. Independent PDF standards/conformance,
compatible-status dependency gates and Foundation font/platform certification
are not established by either code-review result.


## Final receipt and scope verification

After the complete host/four-JDK run and final delivery audit, both existing
independent reviewers checked the final records again. Each confirmed unchanged
source/test identity and fixed HEAD; 30 tracked changes plus all 198 untracked
files; focused totals 256/0/0/0; host and every JDK total 922/0/0/3; successful
terminal results and matching raw-log hashes; all three T27 acceptance chains
passing; byte-identical 19 visual records and 95 PNGs; and all 34 criteria in
the original order with resolving evidence links. Neither found a misleading
completion statement or new required issue. The final Standards conclusion
remains zero unresolved required findings and one optional smell. The final
Spec conclusion remains zero unresolved required findings and no scope
expansion. Uncommitted delivery, experimental status and the separate open
conformance/compatibility/certification gates are accurately retained.

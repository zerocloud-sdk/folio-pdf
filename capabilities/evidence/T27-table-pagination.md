# T27 table pagination delivery record

Status: `experimental`
Capability: `composition.layout.tables`
Acceptance Profile: `T27-table-pagination`
Release train: `0.1.0-SNAPSHOT`

Issue: [#28](https://github.com/zerocloud-sdk/folio-pdf/issues/28).
Parent specification: [#1](https://github.com/zerocloud-sdk/folio-pdf/issues/1).
Fixed implementation/review base (also HEAD at pre-publication verification):
`1d1bf4a92eecf42dc370872fe1bcf5b142e42452` on `main`.

T27 was initially delivered as a verified, independently reviewed **uncommitted
diff**. On 2026-09-06, the user subsequently authorized committing the reviewed
changes, pushing them to GitHub and closing issue #28. The verification and
review records below describe the pre-publication working tree.
The implementation covers finite FIXED/AUTO pagination, row/rowspan/colspan
continuations, repeated/omitted headers and footers, hard keeps, explicit
overflow, atomic buffered relayout and real incremental large-table release.
The version-3/Table.version1 behavior remains covered. Native Commands and
Queries work through both IN_PROCESS and HARDENED_WORKER. No dependency was
added or upgraded, no module or backend SPI was introduced, and no Facade stub
or unrelated issue implementation was added. No commit, push or issue write
was performed or authorized at the initial delivery stage.

## Verification

All final implementation/testing results refer to source/test tree SHA-256
`cce6f26c4e96f386a172bebc83b9ebc0636688c226cabe8d306d2fd4982447c9`.
The [verification receipt](artifacts/T27-verification.txt) retains its exact
hash recipe, invocation arguments, log hashes, actual terminal results,
per-JDK versions and image identities. Earlier results are explicitly labeled
with their earlier source snapshots and do not substitute for the final gates.

| Command or check | Final result |
| --- | --- |
| Initial T24/T25/T26 focused baseline | 174 tests; zero failures/errors/skips. |
| Final five Composition suites, both Canvas suites and T26/T27 evidence tests | 256 tests; zero failures/errors/skips, including all 56 T27 public cases. |
| `./mvnw -B -ntp verify` | 922 tests; zero failures/errors; three existing optional scale-profile skips. |
| `./scripts/verify-jdk-matrix.sh` | JDK 8, 11, 17 and 21 each pass with 922/0/0/3. |
| `./scripts/inventory validate`, `generate`, `check` | 20 capabilities, 12 facade surfaces, 19 exclusions; generated documentation current. |
| `git diff --check` and delivery-path audit | Pass; 30 tracked changed files plus all 198 untracked delivery files, 228 total. |

The full focused command appears in the verification receipt. It names
`LargeTableWorkflowTest`, `TablePaginationWorkflowTest`,
`TableCompositionWorkflowTest`, `ParagraphPaginationWorkflowTest`,
`ParagraphCompositionWorkflowTest`, both Canvas contract suites and T26/T27
acceptance tests. Tests observe only `DocumentWorkflow.execute`, public
Commands/Queries, published/reopened output, resource observations, stable
failures and Publication Receipts. The [development receipt](artifacts/T27-development.txt)
retains actual Red/Green failures, including initial missing seams, breakpoint
and keep-attribution failures, retained-graphics admission and cache lifetime.
Zero-case selectors, fixture setup failures and already-supported supplemental
regressions are distinguished from behavioral Reds.

## Independent acceptance

The required full recorder ran through
`./scripts/acceptance /tmp/folio-t27-acceptance-GFPwo5tw` in a fresh temporary
directory. Final cache-corrected T27 products were revalidated in a second fresh
directory through the same `T27TableEvidenceCommand`; its exact arguments and
raw log hash are retained. The [syntax](T27-table-pagination-syntax.md),
[semantic](T27-table-pagination-semantic.md) and
[visual](T27-table-pagination-visual.md) chains all pass. All 19 final visual
records and 95 output PNGs are byte-identical to the retained records/artifacts.

The same 19-page PDF has ID-neutral SHA-256
`95e2fe473a70bc8d4204e30647ec32cbeccc9d9c49a939ee5d9a4a4d9dcfab70`.
Its reference uses only independent positioned T19 text and Canvas rectangles,
never the table/paragraph paginator. The 19 expected rasters and numeric
examples preceded producer comparison. The [reference receipt](artifacts/T27-reference-raster-receipt.json)
records their provenance and hashes. Font hashes remain the original
project-authored FolioPrimary/FolioFallback hashes. Every page meets the
unchanged 0.0001-point semantic tolerance and 144-DPI opaque-white sRGB visual
profile: primary AE 0, zero changed RGB pixels; secondary disagreement 61–1384
pixels, below the fixed 2500 bound. Missing content, wrong header repetition
and a one-point shift all fail the independent negative controls. Tests of
missing tools remain indeterminate; they are not counted as actual-tool PASS.
Pinned qpdf 12.4.0, PDFium CLI v0.11.2 / chromium-7881 and ImageMagick 7.1.2-30
were present and executed for the three passing chains.

## Independent code review

Separate clean-context Standards and Spec agents reviewed the fixed-base
working-tree diff and every untracked file. Their [separate reports](T27-code-review.md)
retain the initial P1, the incomplete admission-only correction, the remaining
cache-lifetime finding, and independently confirmed final resolution.

Standards: zero unresolved required findings; one optional possible duplication
in margin-to-area conversion is deferred. Spec: zero unresolved required
findings and no scope expansion. The shared retained-graphics scanner now
charges every variable payload before admission, and Composition caches live
only during one drawing. Independent constrained-heap probes confirm that
flushed original declaration graphs are released, instead of merely reducing
reported counters.

## Remaining capability limits

Large tables require FIXED layout and a retained non-AUTO table width, with
finite pages and explicit retained-row/declaration/work/resource budgets.
Incomplete spans and undecided final fragments remain bounded semantic state;
only complete releasable groups are flushed. Earlier flushed footers cannot be
retracted, so an unnecessary nonfinal flush can use more pages. Large tables
must complete before Workflow success and do not support relayout; buffered
version-4 composition supports bounded atomic relayout until sealing.

The generated text stream releases rows before all 125 rows arrive, with
retained bound three and 32 output pages. The separate graphic stream passes
128 fresh 256 KiB images through a 32 MiB Worker heap and 8 MiB modeled-memory
policy with retained bound three and 64 output pages. Neither modeled resource
observations nor those restricted executions claim JVM-wide or RSS accounting.
Separate graphic placements may materialize separate PDF resources under the
cumulative object/storage policy; recursive reuse remains within each drawing.

Syntax success and code-review Standards success do not establish PDF standards
conformance. Independent conformance evidence, compatible-status Dependency
Gates and Foundation font/platform certification remain open. The capability
stays **experimental**; Unicode/shaping, Tagged PDF, XFA, barcodes, HTML and
Foundation release work remain outside this delivery.


## Completion evidence by original criterion

All 34 scoped criteria below are PASS, in the original execution-contract order. Independent clean-context review preceded these completion results.

| Criterion | Status | Evidence |
| --- | --- | --- |
| 1. FIXED/AUTO area and page counts | PASS | [Pagination contract suite](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/TablePaginationWorkflowTest.java); independent acceptance pages 1–4. |
| 2. Table/row fragment breakpoints | PASS | Pagination suite; independent pages 5–6 and 11–12. |
| 3. Repeated header positions/counts | PASS | Pagination suite; independent pages 7–10 and first-header omission on page 19. |
| 4. Repeated footer and final handling | PASS | Pagination and large-table suites; independent pages 7–10 and final-footer omission on page 19. |
| 5. Complete split-row and rowspan/colspan content | PASS | Independent pages 5–6 and 11–12; public span and open-span continuation cases. |
| 6. Published reading/extraction order | PASS | [Reopened semantic observations](T27-table-pagination-semantic.md) compare every scalar on all 19 pages. |
| 7. Cell/fragment/border geometry | PASS | Hand-declared coordinates and 0.0001-point tolerance; semantic observer checks all inside black border rectangles. |
| 8. Overflow or stable failure without clipping | PASS | Public WRAP/REJECT/VISIBLE and impossible unsplittable-row cases; page 16 preserves all four overlong glyphs. |
| 9. Successful and impossible keeps | PASS | Public keep backtracking/whole-table movement, impossible kept pair and subsequent ordinary exhaustion; pages 13–14. |
| 10. Successful relayout | PASS | Public buffered three-row replacement and independent page 15. |
| 11. Failed relayout preserves prior success | PASS | Public reopened BBB output and unchanged positions after failure. |
| 12. Flushed/sealed/published unsafe relayout | PASS | Public FlushParagraphs, later AddBlankPage, expired Session and open/flushed large-table cases. |
| 13. Real bounded incremental release | PASS | [Large-table suite](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/LargeTableWorkflowTest.java): 3/1/2 accepted/retained/released rows before remaining append; generated 125 rows, retained bound 3, 32 pages under a 32-page policy. A second generated case streams 128 fresh 256 KiB graphics through a 32 MiB Worker heap and 8 MiB owned-memory policy with retained bound 3, producing 64 pages. Modeled memory is not RSS. |
| 14. Large FIXED and retained-width restrictions | PASS | Public positive and negative BeginLargeTable cases; [English contract](../../docs/table-pagination.md) and public 7.2.6 Table API provenance. |
| 15. Exact and first-excess finite budgets | PASS | Cumulative declarations, open-span bound, fallback 12/11, streaming work 53/52, attempts 3/2, generated content/font bytes, relayout exhaustion, 2048-byte Worker message transport, nested retained-graphic admission for Begin/Append in both profiles and deterministic IN_PROCESS public-memory peak/peak-minus-one-byte bounds. Worker aggregate peaks include timing-dependent transport overlap; no byte-exact RSS claim is made. |
| 16. No premature flush publication; correct failure receipts | PASS | Sentinel destinations remain unchanged at intermediate flush and after propagated failures; NOT_ATTEMPTED receipts and committed final products. |
| 17. Both Workflow execution profiles | PASS | All 56 T27 public cases are parameterized over IN_PROCESS and HARDENED_WORKER; full verification receipt. |
| 18. Public test seam | PASS | DocumentWorkflow.execute, public Commands/Queries, detached observations, reopened outputs and checked failures; independent Standards review. |
| 19. qpdf syntax chain | PASS | [Pinned qpdf 12.4.0 record](T27-table-pagination-syntax.md). |
| 20. Independent semantic chain | PASS | [19-page semantic record](T27-table-pagination-semantic.md), fixed font hashes and declaration hash. |
| 21. Independent pinned visual chain | PASS | [19-page PDFium/ImageMagick record](T27-table-pagination-visual.md): AE 0 and zero changed RGB pixels on every page; secondary 61–1384 <= 2500; independently positioned reference. |
| 22. Effective negative controls | PASS | [Acceptance tests](../../pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T27TableEvidenceCommandTest.java): missing scalar, duplicate header and one-point shift all fail; unavailable tools remain indeterminate. |
| 23. Truthful Capability Matrix | PASS | [Behavioral authority](../capability-matrix.yaml) retains experimental status and open standards/dependency/font/platform gates. |
| 24. Explicit Facade exclusion, no stable stub | PASS | [Facade Surface Manifest](../facade-surface.yaml) names T27 and the absence of an evidenced Table/Cell mapping. |
| 25. English contract and Chinese usage/migration guide | PASS | [English](../../docs/table-pagination.md), [Chinese](../../docs/zh-CN/getting-started.md), retained T26 version-3 contract. |
| 26. Clean-room code/fixture/resource provenance | PASS | [T27 provenance](../../PROVENANCE.md); original project fonts and independent reference/raster receipt, no dependency changes. |
| 27. Focused regressions and preserved T24/T25/T26 | PASS | [Verification receipt](artifacts/T27-verification.txt): 256 tests, zero failures/errors/skips, including direct Canvas regression. |
| 28. Full Maven verify | PASS | Verification receipt: ./mvnw -B -ntp verify; 922 tests, zero failures/errors, three existing optional scale skips. |
| 29. JDK 8/11/17/21 matrix | PASS | Verification receipt: ./scripts/verify-jdk-matrix.sh and separate terminal results for every JDK. |
| 30. Inventory validate/generate/check; no drift | PASS | Verification receipt; both generated Markdown authorities remain current. |
| 31. Independent two-axis review of all working-tree changes | PASS | [Independent review](T27-code-review.md); fixed base plus every untracked delivery file, separate Standards/Spec conclusions and no unresolved required finding. |
| 32. Clean diff and ticket-only workspace | PASS | Final git diff --check and delivery path audit recorded with the fixed base; no investigation files in the repository. |
| 33. Verified/reviewed uncommitted delivery | PASS | At initial delivery, HEAD remained 1d1bf4a92eecf42dc370872fe1bcf5b142e42452; no commit, push or issue write had occurred. The user subsequently authorized publication. |
| 34. Per-criterion evidence, actual commands/results and limits | PASS | This table, the verification/review receipts and the final report; no missing-tool or indeterminate chain is counted as PASS. |

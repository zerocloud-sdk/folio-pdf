# T09 development observations

This journal records development of #71 against baseline
`543c582cb41104f7da43b9d801c629894dcec34a`. It is not Acceptance Evidence or a
completion declaration. The implementation and certification scope remains in
[the execution contract](t09-implementation-plan.md). No commit is authorized.

## Public test boundary

`PdfValueWorkflowTest` uses the public `DocumentWorkflow` callback, queries,
Publication Receipts, Source bytes and reopened outputs. Its existing 21 tests
were retained and parameterized for both `IN_PROCESS` and `HARDENED_WORKER`.
The focused command is:

```sh
./mvnw -B -ntp -pl pdf-document -am -Dtest=PdfValueWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Method selections use `-Dtest=PdfValueWorkflowTest#methodName*`; JUnit's
parameterized suffix requires the trailing wildcard. A successful Maven exit
with zero selected tests is not evidence.

Development logs currently reside in ignored `.build-cache/t71/development/`.
Relevant observations, in dependency order:

| Behavior | RED observation | GREEN observation |
| --- | --- | --- |
| Dictionary removal | `folio-t71-remove-red.txt`: missing public operation | `folio-t71-remove-green.txt`: 22 tests; `worker-green.txt`: 44 tests after codec and safe-diagnostic parity fixes |
| Nested dictionary paths | `path-red.txt`: missing path type | `path-green-final.txt`: 46 tests after updating the Worker class inventory and its pinned digest |
| Invalidated path atomicity | `path-atomic-red-selected.txt`: Worker terminated instead of carrying the stable rejection | `path-atomic-green.txt`: 2 tests after adding the controlled diagnostic |
| Final-graph cycle validation | `nested-remove-cycle-red.txt`: an intermediate cycle removed later in the same Patch was rejected | `nested-remove-cycle-green.txt`: 6 tests, including genuine cycle rejection |
| Array replacement, insertion and removal | `array-set-red.txt`, `array-insert-red.txt`, `array-remove-red.txt`: missing public operations | `containers-green.txt`: 56 tests |
| Core structure rollback | `core-atomic-red.txt`: removing `/Pages` returned `SOURCE_READ_FAILED` after mutation | `core-atomic-green.txt`: 2 tests after moving structural validation and audit inside rollback |
| Protected metadata descendants and aliases | `protected-containers-red.txt`: stream encoding and version arrays were writable | `protected-containers-green-mapping.txt`: 6 tests after graph protection and Worker capability mapping fixes |
| Container implementation refactor | Existing behavior already green; no new public validation seam | `container-refactor-green.txt`: 62 tests after removing dead reference tracking, using explicit operation identifiers and separate undo records, and removing quadratic array membership scans |
| Stream revalidation at the Patch boundary | `stream-preflight-red.txt`: both profiles returned from the Patch before renewed preflight | `stream-preflight-green.txt`: 6 affected tests after invalidation moved before audit and was repeated after rollback |
| Indirect value replacement and ordered nested locations | `value-replacement-red-api.txt`: missing `replaceValue` | `value-replacement-green.txt`: 2 parameterized tests, each exercising REWRITE and INCREMENTAL, stable aliases and Source preservation |
| Existing page-tree links during dictionary replacement | `replacement-existing-links-red.txt`: legitimate Parent/Kids relationships were rejected as new cycles | `replacement-existing-links-green.txt`: 4 tests after preserving equal entries and the existing dictionary identity |
| Later replacement overriding an earlier nested edit | `replacement-order-atomic-red.txt`: both profiles retained 9 instead of restoring 1; the separate atomicity regression already passed | `replacement-order-atomic-green.txt`: 8 tests after equality comparison used pending container state |
| Replacement refactor and complete regression set | Existing replacement behavior already green | `replacement-refactor-green.txt`: 72 tests, zero failures/errors/skips after consolidating dictionary undo preparation |
| Indirect object body grammar | `bare-reference-red.txt`: self-reference and reference-only indirect bodies were accepted | `bare-reference-green.txt`: 4 tests after preserving self-cycle rejection and rejecting reference-only bodies; reference-valued container entries still succeed |
| Existing page-tree links during indirect array replacement | `indirect-array-links-red.txt`: equivalent Kids arrays rejected existing links as new cycles | `indirect-array-links-green.txt`: 6 tests after preserving the array identity |
| Hidden encoding metadata and indirect aliases | `hidden-alias-red.txt`: xref-only stream metadata was writable and a hidden alias reopened with the old value; `hidden-alias-identity-red.txt` also demonstrated split alias identity | `hidden-alias-explicit-green.txt`: 8 tests, including hidden dictionary and array aliases, after explicit changed-container tracking and additional Source updates in incremental saving |
| Incremental save lifecycle and complete regression set | Existing hidden-alias behavior already green | `hidden-alias-refactor-green.txt`: 80 tests, zero failures/errors/skips after scoping the temporary trailer to the save and restoring it on close |
| Discarded equal replacement allocations | `replacement-memory-red-corrected.txt`: 24 replacements carrying the same 1 MiB string exhausted a 16 MiB limit in both profiles | `replacement-memory-green.txt`: 6 affected tests after scoping each materialized string/real and releasing candidates absent from the final document graph |
| Effective dictionary state refactor and complete regression set | Existing allocation behavior already green | `replacement-memory-refactor-green.txt`: 82 tests, zero failures/errors/skips after centralizing pending dictionary names, lookup and mutation on PreparedPatch |
| Intermediate replacement ownership | `intermediate-replacement-memory-red.txt`: the Worker could not complete 96 repeated 1 MiB intermediate replacements within its 64 MiB heap | `intermediate-replacement-memory-green.txt`: 8 affected tests after registering only final replacement wrappers and excluding abandoned temporary containers from persistent update tracking |
| Prepared-state lifetime and complete regression set | Existing intermediate replacement behavior already green | `intermediate-replacement-refactor-green.txt`: 84 tests, zero failures/errors/skips after clearing undo and preparation collections at the operation boundary |
| Explicit stream encoding/data operation | `stream-data-red-api-corrected.txt`: missing `PdfStreamEncoding` API | `stream-data-green.txt`: 2 tests, each covering both save modes and both encodings, aliases, custom attributes and copied caller bytes |
| Discarded generated streams | `stream-rollback-red.txt`: repeated rejected generation exhausted temporary storage | `stream-rollback-green.txt`: 6 affected tests after closing and clearing abandoned streams; `stream-ownership-refactor-green.txt`: 88 tests after cleanup refactoring |
| Existing views after a terminal limit | `terminal-value-views-red.txt`: the IN_PROCESS dictionary view remained readable after a terminal decompression failure | `terminal-value-views-green.txt`: 2 tests after the existing view entry point gained the same resource checkpoint as queries |
| Stream identity and existing Owner links | `stream-owner-links-red.txt`: both profiles rejected an existing Owner cycle during data replacement | `stream-owner-links-green.txt`: 8 affected tests after raw-data undo and in-place stream writing; `stream-identity-refactor-green.txt`: 90 tests after consolidating engine-owned stream names |
| Unchanged dictionary back-reference | `existing-owner-noop-red.txt`: writing the same Owner reference rejected the existing Source cycle | `existing-owner-noop-green.txt`: 8 tests after distinguishing unchanged dictionary edges from new edges, including genuine introduced cycles |
| Unknown encoded stream preservation and decode failure parity | `unknown-preservation-baseline.txt`: IN_PROCESS passed but Worker returned a Patch rejection for a query decode failure | `unknown-preservation-green.txt`: 2 tests covering both save modes after restoring query semantics at the Worker inspection boundary; raw encoded content, attributes, unknown resource aliases and page content survive unrelated changes |
| Whole stream replacement with existing Owner links | `whole-stream-owner-red.txt`: both profiles rejected the existing cycle | `whole-stream-owner-green.txt`: 6 tests after whole stream replacement reused in-place data/metadata undo and compared pending custom attributes |
| Indirect Catalog Version protection | `indirect-version-alias-red.txt`: both profiles accepted replacement through an indirect Version alias | `indirect-version-alias-green.txt`: 6 tests after adding the Version value graph to the existing output-policy guard |
| Final Native refactoring | Existing Native behaviors already green | `native-values-refactor-green.txt`: 98 tests, zero failures/errors/skips after sharing dictionary replacement preparation and extracting the Worker inspection byte writer |

`path-atomic-red.txt` selected zero tests and is excluded. The initial
`value-replacement-red.txt` failed because the test used a nonexistent string
factory; it is excluded in favor of the corrected API RED log. Intermediate
Worker inventory failures and the duplicate-descriptor attempt did not establish
GREEN; only the explicitly named successful runs above do so.
The initial `replacement-memory-red.txt` also used an incorrect test factory and
is excluded; its corrected run demonstrated the actual owned-memory failure.
The initial stream API and unknown-preservation fixture runs also contained
incorrect test factories and are excluded. Missing-fixture observations do not
establish the subsequent behavioral defect. `native-values-refactor-compile.txt`
records a missing IOException declaration while extracting the Worker inspection
helper; it is a refactoring compilation failure, not a completed validation run.

## Independent interim review

Clean-context Standards and Spec reviewers examined the fixed-baseline worktree
diff and untracked files. Their first findings exposed nested metadata bypasses
and structural validation outside rollback. Follow-up review found stale stream
preflight state during the new audit. Each defect received a public regression
before its correction. Both reviewers confirmed all these findings resolved
after the stream-preflight fix. Standards also confirmed the three requested
refactors resolved. These were interim reviews: subsequent replacement work and
the final delivery still require independent review.

Replacement review then identified bare-reference object bodies, equivalent
indirect Kids arrays, hidden aliases and discarded equal allocations. The next
Spec follow-up found no further replacement issue at the 82-test point.
Standards found that intermediate wrappers and abandoned temporary containers
were still held after their allocation scopes were released. The public Worker
heap regression above demonstrated that actual retention, before the correction;
its passing result proves repeated replacement and publication within the same
declared heap, without observing private JVM or backend state. Final replacement
follow-up and the later stream work are separate review obligations.
The replacement follow-up found no remaining material issue at the 84-test
point. The subsequent Standards stream review confirmed ownership, current-item
undo, original Length metadata restoration and the terminal view fence at the
90-test point. It statically reviewed actual scratch I/O recovery failure; no
private failure-injection seam was introduced. Spec then found whole-stream
replacement's existing-cycle rejection and the indirect Version alias bypass;
both received the public RED/GREEN regressions recorded above.
Both independent reviewers confirmed those findings resolved at the 98-test
point and found no new material Native issue. Their closure does not cover the
later Facade implementation or independent certification.

## Facade development

The first Facade tracer follows a number from Catalog mutation through publish
and both Facade and Native reopen. The existing five blank-document tests remain
in the focused selection:

```sh
./mvnw -B -ntp -pl pdf-migration-itext7 -am -Dtest=PdfValuesFacadeTest,BlankDocumentFacadeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

| Behavior | RED observation | GREEN observation |
| --- | --- | --- |
| Live number mutation and publication | `facade-number-red.txt`: missing mapped value/Catalog APIs | `facade-number-green.txt` and `facade-number-refactor-green.txt`: 6 tests after one owned Native callback and synchronous calls on its confined thread |
| Reader snapshot lifetime | `facade-reader-snapshot-red.txt`: deleting the original Source made value inspection fail | `facade-reader-snapshot-green.txt` and `facade-reader-snapshot-refactor-green.txt`: 7 tests after retaining a bounded original-byte snapshot, transferring ownership once to the Document, and deleting it after Native completion |
| Lifecycle review findings | `facade-lifecycle-red.txt`: an illegal-thread close poisoned the owner's document; cancelled initialization later overwrote the prior target | `facade-lifecycle-green.txt`: 9 tests after checking ownership before state changes, recording Session initialization before waiting, and using an independent idempotent stop signal with the actual Native outcome |
| Stop ordering and private snapshot ownership refactor | Existing lifecycle and snapshot behavior already green; independent Standards review found missing private-directory ownership | `facade-stop-order-green.txt` and `facade-private-snapshot-refactor-green.txt`: 9 tests; cancellation is set before the volatile stop signal, and Reader/Document owns and removes both the private directory and Source copy |
| Scalar kind, text and byte-string roundtrip | `facade-scalars-red.txt`: missing Null/Boolean/String APIs and predicates | `facade-scalars-green.txt` and `facade-scalars-refactor-green.txt`: 10 tests; inspected immutable Native strings are reused without an extra retained copy |
| PDFDocEncoding inspection | `facade-pdfdocencoding-red.txt`: legal special characters were decoded as Latin-1 | `facade-pdfdocencoding-green.txt`: 11 tests after using ISO 32000-1 Annex D.3 Table D.2 |
| PDF 2.0 UTF-8 and invalid Java text | `facade-unicode-boundaries-red.txt`: UTF-8 BOM was misread, and an unpaired surrogate silently changed text | `facade-unicode-boundaries-green.txt`: 13 tests; arbitrary malformed byte strings remain publishable and byte-exact, with explicit replacement-character text decoding |
| Published string kind constant | `facade-string-kind-red.txt`: getType returned 9 instead of the documented 10 | `facade-string-kind-green.txt`: 13 tests; all nine mapped numeric constants were checked against the official 7.2.6 constant-values API page |
| Text conversion refactor | Existing text behavior already green | `facade-text-refactor-green.txt`: 13 tests after separating text encoding and PDFDocEncoding decoding |
| Nested dictionary operations | `facade-dictionaries-red.txt`: missing detached constructor, collection and removal mappings | `facade-dictionaries-green.txt` and `facade-dictionaries-refactor-green.txt`: 14 tests; all live mutations use Native Patches and removed scalar return values are detached; a shared wrapper conversion replaced dictionary-local conversion |
| Array operations | `facade-arrays-red.txt`: missing PdfArray | `facade-arrays-green.txt`: 15 tests covering detached and live insertion, replacement, removal, number mutation and both-interface reopen |
| Existing direct views during structural changes | `facade-value-locations-red.txt`: a shifted nested dictionary targeted a number, and another numeric view remained stale | `facade-value-locations-green.txt`: 17 tests after sharing attachment state across views, rebasing array descendants, sharing current number values and detaching replaced entries |
| Attachment-state refactor | Existing direct-view behavior already green | `facade-value-locations-refactor-green.txt`: 17 tests after allocating only the observed child maps and avoiding empty-array rebasing |
| Existing Source editing and copying | `facade-reader-writer-red.txt`: missing reader/writer constructor | `facade-reader-writer-green.txt`: 18 tests after using the actual Source for both value edits and close-only copying, through one shared Session initialization path |
| Indirect alias and reference replacement | `facade-references-red.txt`: missing PdfIndirectReference and identity mapping | `facade-references-green.txt`: 19 tests; default and explicit dereference, root identity, stable scalar aliases, reference-valued arrays, reference replacement and cross-Session inequality survive publish/reopen |
| Reference conversion refactor | Existing reference behavior already green | `facade-references-refactor-green.txt`: 19 tests after sharing inspection defaults and releasing the Session's reference lookup at completion |
| Stream data, metadata and aliases | `facade-streams-red.txt`: missing PdfStream; first `facade-streams-green.txt` attempt still returned a stream instead of its requested raw reference | `facade-stream-aliases-green.txt`: 20 tests after returning stream references for non-direct access and preserving body inspection, decoded data, copied caller bytes and Native Flate metadata |
| Pages queued before opening a reader/writer Session | `facade-queued-pages-red.txt`: a requested second page was missing after close | `facade-queued-pages-green.txt`: 21 tests after passing the actual queued page delta to the Native callback |
| Detached containment cycles and deep conversion | `facade-containment-red.txt`: both tests ended in StackOverflowError before Native validation | `facade-containment-green.txt`: 23 tests after iterative ancestor-checked conversion; three Java container cycle forms reject before mutation, shared children publish, and 12,000 nested arrays reach the actual Native nesting failure with no publication |
| Container conversion and location refactor | Existing container behavior already green | `facade-containers-refactor-green.txt`: 23 tests after iterative path resolution, omitting missing-key locations and releasing completed call closures before the next blocking wait |
| Detached-container dereference | `facade-detached-dereference-red.txt`: default get returned the reference itself | `facade-detached-dereference-green.txt`: 24 tests after a shared conversion honors the flag and dereferences through the reference's own Session |
| Native view expiry through the Facade | `facade-view-expiry-red.txt`: a closed dictionary produced only the bridge's IllegalStateException | `facade-view-expiry-green.txt`: 25 tests; the same actual public Native view operations produce PDF_VALUE_VIEW_EXPIRED after successful completion, while detached scalars remain readable |
| Existing Native inspection bounds | `facade-existing-view-bounds.txt`: 27 GREEN tests observed the existing traversal and cumulative 64 MiB stream limits; no new production validation was introduced | The same tests prove valid mutation and publication after caught inspection-limit failure |
| Caller input/output constructors | `facade-caller-streams-red.txt`: constructors absent | `facade-caller-streams-green.txt`: 28 tests; borrowed caller streams stay open through create, edit, publish and both-interface reopen |
| Failure and preservation parity | Existing behavior observed without production changes | `facade-failure-parity-observation.txt`: 35 tests; actual input and write/flush failures, receipts, foreign/cyclic references, invalid indices, core state, protected aliases, unknown resources and unchanged painting |
| Closed Session retention refactor | Independent Standards review found the expiry bridge retained a Native Session after completion | `facade-view-retention-refactor-green.txt`: 35 tests after removing that field and separating actual Native reads from active-only mapping; the reviewer closed the finding |
| Stream metadata attachment | `facade-stream-metadata-location-red.txt`: a held old Length changed from 3 to 31 after inspecting the replacement | `facade-artifacts-green.txt`: each Facade has 36 passing behavior tests; successful setData invalidates only replaced metadata locations, and the independent Spec reviewer closed the finding |
| Actual jar surface | `facade-artifacts-red.txt`: undeclared PdfObject.ARRAY field | `facade-artifacts-green.txt`: 17 public classes and 89 exact mapped members in both Java 8 jars, including constant values, generics and checked exceptions; every mapped type rejects both mixed classpath orders |
| Portable authored fixtures | `facade-fixture-packaging-red.txt`: three public workflows could not find their fixture in the test artifact | `facade-fixture-packaging-green.txt`: both Facades' 36 behavior tests and the artifact/exclusivity checks pass with test-only resources copied from the shared authored corpus |

The snapshot is captured while Native reads and validates a bounded stream.
Its bytes are reserved separately from Native temporary storage: initialization
reserves the maximum possible snapshot, and the held Document reserves its
actual size. The facade copy does not read, decode or serialize backend objects.
Independent Standards follow-up closed the lifecycle and private snapshot
findings. Independent Spec review accepted the explicit project text-conversion
contract and required the PDFDocEncoding/PDF 2.0 and malformed-text boundaries
covered above. Neither review is a final complete Facade review.
Later Standards review found recursive detached conversion, ignored detached
dereference flags, queued-page loss and unnecessary missing-key locations. It
confirmed all four resolved at the 24-test point. Spec separately accepted the
safe local cycle exception with no invented Native cause, requiring immediate
rejection, continued use, and successful shared acyclic children. Later
Standards/Spec reviews closed the expiry retention and old stream Length
attachment findings. They found no remaining bounded Facade source issue.
The complete independent certification and final whole-diff review remain
required; these development checks do not certify the candidate.

## Project-owned fixture

`pdf-document/src/test/resources/net/zerocloud/pdf/consumer/t09/protected-containers.pdf`
is an original ASCII PDF with a blank page, a three-byte ASCIIHex stream, shared
encoding/parameter containers, version-extension aliases, and a scalar with two
catalog aliases. Its object bodies were authored for these public regressions;
byte offsets and the xref table were calculated from those bodies. The tests
first failed for the missing fixture, then demonstrated the actual metadata
bypass with the fixture present. The scalar entries were added for the
replacement test after its API RED. No reference-library code, resource or
fixture was used. Integration used the already-approved PDFBox 3.0.8 dependency;
its local public dependency sources were consulted for COSObject/COSDocument
identity and serialization behavior without copying implementation code.

`indirect-and-hidden-values.pdf` extends those authored bodies with an indirect
Kids array, an xref-only alias dictionary and alias array, and a hidden stream
whose encoding array has a Catalog alias. Pinned qpdf 12.4.0 checked its grammar
successfully. A separately authored incremental revision reveals a hidden
object while preserving the current plain Catalog, so public reopen tests can
observe both the value and its shared Object Reference without backend access.

The incremental adapter uses PDFBox's public `COSDictionary.toIncrement()`,
`COSIncrement.getObjects()` and `COSDocument.setTrailer()` APIs. It adds changed
Source indirect roots, including roots that contain changed direct containers,
without adding PDF keys or promoting direct children to indirect objects. It
does not rely on update flags for orphan objects, which have no source document
state attached in the backend. The original trailer is restored after saving;
ordinary `PDDocument.saveIncremental` still performs serialization.

`stream-owner-link.pdf` adds an existing Owner back-reference to the authored
ASCIIHex stream. `indirect-version-alias.pdf` uses an indirect Catalog Version
and a second alias of the same object. `unknown-encoded-resource.pdf` contains
27 authored opaque bytes, an unknown filter declaration and private parameters,
an unknown page-resource alias, and a separate simple painted page-content
stream. All three pass pinned qpdf 12.4.0 syntax checking. qpdf's public raw
stream output also independently matches the 27 original bytes exactly.

Stream editing uses the approved dependency's public `COSStream` raw input and
output APIs. New data is encoded into a bounded scratch stream before mutation;
the original encoded bytes are backed up once per target per Patch. Raw writes
preserve the original stream identity. Ordinary rejection restores bytes and
the original metadata, including indirect Length identity. The existing policy
terminates resource-exhausted transactions; a failed I/O restore also terminates
the Session, with no access through old views and no publication. No backend
backing-store reflection, custom stream serializer or policy bypass is used.

## Acceptance development checkpoint — 2026-09-09

Native behavior has 49 public cases in each execution profile. A command-line
selection tracer initially still ran all 98 cases when IN_PROCESS was selected;
after adding `folio.t09.executionProfile`, the same command ran exactly 49 passing
cases. The default suite retains both modes. The Facade has 31 value and five
blank/lifecycle cases in each edition; actual Stable/Preview jars pass their
17-type/89-member and both-order coexistence checks. Earlier bounded Native and
Facade review findings were resolved; this does not replace final whole-diff
review.

The T09 public recorder progressed from a missing semantic command to nine-kind
mutation/reopen evidence, then from missing independent chains to three real
products with syntax, standards, semantic and visual results and controls. The
Source has no ID and incremental output has multiple revisions, so all T09
metadata now records exact SHA-256 without ID normalization or input rewriting.
Legacy chains retain their existing hash policy. A public report-link check
exposed both the wrong `artifacts/` prefix and an uncreated raw-findings filename;
the final four-chain refactor run passed three tests with all links resolved.

The independent Spec review found that any `DocumentFailure` had been counted
as a successfully detected semantic negative. The public semantic command first
reported FAIL for `missing.pdf`; it now records INDETERMINATE and a safe reason
for missing or malformed input. Only a successful reopen with observed value
differences can qualify the negative. The focused RED, GREEN and Refactor runs
are retained in `.build-cache/t71/development/t09-semantic-execution-*.txt`, and
the independent reviewer closed the finding.

The standards qualification retains 62 actual invocations, including the known
misses of both checkers. The frozen union contains 51 rules. The source and
legal DecodeParms `[null]` control pass both tools. The runner's preservation
command additionally checks final effective raw stream bytes and attributes;
the incremental re-encoding control retains the original prefix and decoded
bytes but fails the raw check. The independent Spec reviewer confirmed this
property using real pinned qpdf output.

Runner collection, current-identity preservation, execution planning and atomic
index publication each had failing tracers before implementation. A bounded
Standards review required stronger public CLI tests and an index update that
could not truncate the authority. The new tests now read actual command plans
and temporary indices through the runner CLI, including changed prior authority
and missing new evidence. The read-only inventory process test demonstrated
that a separate incomplete provisional index is assessed without changing the
valid authority or either identity. Seventeen Python tests and the focused
inventory process test pass; the reviewer closed both findings. Planning uses
the same generator as certification but is explicitly unverified. Raw-stream
checks run inside each declared container.

At this checkpoint, eight final T09 certifications, refreshed current-candidate
transactions, final full verify/default JDK matrix, inventory checks and final
whole-diff independent review were still pending. Development observations do
not mark completion criteria or promote global Foundation readiness. The final
delivery receipt belongs in the candidate-specific evidence directory, keeping
this development history separate from the frozen candidate inputs.

## Whole-diff review correction — 2026-09-09

The independent Spec reviewer reproduced a missing core-structure guard on the
first frozen candidate: scalar Page Contents, a Contents array containing a
number, and removal of the last effective Resources could publish. The partial
T09 run was stopped before index publication; its actual original records and
candidate identity remain in the historical interrupted attempt.

Public Native regressions then failed four assertions, covering Contents and
effective Resources in both execution profiles. The Facade regression separately
failed because no Native rejection was returned. The correction validates these
Page entries inside the existing Patch rollback boundary. After minimal GREEN,
the Page checks were extracted into a named helper; Native 100, Stable Facade 36
and Preview Facade 36 public tests passed with zero failures/errors/skips. Tests
retain successful stream arrays and Resources inheritance, Source bytes, valid
later changes and publication after caught failures in both Native save modes.

Native now has 50 cases per profile. The public plan count test first failed
83 versus 82, then all 17 runner tests passed with 50 Native, 31 Facade and two
actual-jar cases required per certification tuple. The
[correction record](../capabilities/evidence/T71-review-correction/README.md)
retains the actual RED/GREEN/Refactor transcripts and identifies the two earlier
test-assumption attempts that are excluded from RED evidence. Final identity
freezing, certification and independent signoff still follow this checkpoint.

## Raw environment observation correction — 2026-09-09

The second candidate completed all eight T09 observations with 83 tests and all
four chains per scope, but index publication rejected its raw environment JSON:
the generic recursive verifier interpreted a recorded `/workspace` observer
path as a host repository reference. The authority remained unchanged. The
public `merge-index` command reproduced the failure on both the complete record
graph and a minimal temporary index before the correction.

A first correction passed the raw-payload and changed-byte regressions, but
independent Standards review found its field-name exception too broad. Three
public CLI assertions then failed for same-named fields in a configuration,
certification record and nested report finding. The corrected traversal grants
the raw role only to a certification report's root observation list; exact file
hashes and repository containment remain mandatory. Both minimal and refactor
runs passed all 19 runner tests. Full historical graph replay passed with the
original eight scopes and identities, without changing the real authority.

The independent reviewer closed the finding. The
[index correction record](../capabilities/evidence/T71-index-correction/README.md)
retains the actual failures, regression results and replay receipt. The second
attempt remains historical; new source bytes require a new staged candidate and
fresh values and transactions certification before final signoff.

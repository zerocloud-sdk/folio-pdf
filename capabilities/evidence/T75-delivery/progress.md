# T75 implementation record — work in progress

This record describes implementation work against
`5b1603c435f11c40368f75b7b9a2777c9c5e9761`. It is not Foundation certification
and does not mark any completion criterion satisfied. Changes remain
uncommitted. The capability and both limitation blockers remain experimental
and unresolved until the complete contract and certification gates pass.

## Baseline and inventory correction

The local baseline focused inventory test passed because ignored build/tool
inputs existed. The same baseline in a clean Git archive failed the known
generated-readiness check. Both observations are retained in
`baseline-local-inventory.txt` and `baseline-clean-inventory.txt`; the baseline
full build is not described as green. The 58 original Native tests passed.

The command-boundary portability regression failed before the correction
(`step1-red.txt`) and passed afterward (`step1-green.txt`). Generated recorded
evidence reports now tolerate absent ambient build/tool inputs, while live
readiness still verifies every required artifact, tool, input and source hash.
Retained evidence files remain byte-verified. All 14 inventory tests and the
original clean-archive inventory check passed. Independent clean-context
Standards and Spec reviewers reported no Step 1 findings.

## Contract and Native structure increment

`docs/t13-certification.md` freezes the six-member matching Facade, all fifteen
finite defaults, required successful cases and budget accounting. The Spec
review identified and resolved the need for an explicit ADR transition,
precise defaults and shared-node accounting. A subsequent standards check
corrected the proposed repeated internal Form MCR behavior: ISO 32000-1
§14.7.4.2 permits repeated structural use through enclosing MCIDs, while an
internally structure-linked Form cannot be invoked repeatedly. The initial
repeated internal MCR observation is retained as superseded evidence, not as a
conforming acceptance case. Ordinary repeated Form marked content and repeated
whole-object OBJRs remain separate supported cases.

Public DocumentWorkflow red/green observations now cover Form marked content,
independent page/Form MCID scopes, duplicate MCID rejection, structural Form
reuse rejection, annotation and whole-Form OBJRs, annotation-owned appearance
MCRs, and ParentTree backlink consistency. The new identities use the existing
Session ObjectReference registry and closed Worker codec. OBJR, MCR and owner
successes run in both actual Native execution profiles and retain Source bytes.
The Worker class inventory remains pinned and includes the new detached type.

Logs use one `*-red.txt`/`*-green.txt` pair per increment. The boundary fixture
was corrected to include its required ParentTree; structure items now charge
root K, element K, the number-tree node, its value and the MCID slot (five).
The focused suite then passed 77 tests: 52 public extraction tests, nine CMap
preflight tests, five font-metric tests and eleven Worker codec tests, recorded
in `structure-regression-green.txt`. This count is an intermediate observation;
later increments must retain their own runs. Structure implementation review
and additional relationship controls are in progress.

## Outstanding execution work

Remaining work includes complete structure graph controls and PDF 2.0
namespaces, the required Type 3 and Encoding/ToUnicode CMap extensions, all six
Facade members, comprehensive cross-profile/parity observers, independent
tool/corpus qualification, the text recorder, final authority/provenance
synchronization, final candidate staging, all eight actual Native environment
tuples, refreshed evidence for the five previously completed obligations,
full Maven/JDK matrix validation and final independent review. No completed
obligation's historical hashes may be reused as current candidate evidence.

The installed HarfBuzz 10.2.0 helper was observed successfully through
`scripts/t29-native-observation.py`; later full checks must explicitly select
that installation. Cached images and tools alone are not certification.

At this initial stage no commit, push, PR, tracker mutation or publication was
authorized or performed. The later delivery instruction authorizes a DCO commit,
GitHub push and closure of #75 after all required work and gates pass.

## Structure review closure and namespace increment

The 84-test structure suite passed in both actual Native profiles. Its initial
full Worker run exhausted three ten-second deadlines on grouped fixture methods;
only those group deadlines were increased, retaining every input, assertion and
Query/Workflow limit. The rerun passed. These host-JDK observations are not the
required immutable-image certification.

Independent public probes then found two leaf violations: MCR content invoking
an OBJR-linked Form, and nested MCRs in an unexecuted appearance. Public RED/GREEN
regressions now reject both orders of each overlap and preserve valid adjacent
content. Related whole-object nesting and whole-object/internal-MCR controls
also fail safely. Independent reviewers confirmed closure of the original two
findings and of the cache ownership concern: unexecuted definitions retain only
MCIDs, unresolved property names and ancestry, with context-dependent lookup.

Review also corrected two assumptions in the prospective contract. ISO 32000-1
§14.7.3 allows circular RoleMap associations and, since PDF 1.5, remaps even
standard-looking unqualified names. New public RED/GREEN cases prove those
outcomes; the prior unused RoleMap cycle was moved out of the malformed-graph
fixture group, while content/structure cycle failures remain unchanged. PDF 2.0
undefined-namespace mapping follows the full chain before default-namespace
resolution, as the approved errata requires.

Namespace observations now expose declared URI, opaque dictionary identity and
resolved standard namespace independently. Public cases cover PDF 1.7/PDF 2.0
vocabularies, RoleMapNS cross-namespace chains, explicit default-name targets,
namespace non-inheritance, language inheritance, root membership, malformed
field/target kinds, direct same-standard-namespace mapping rejection, and bounded
unresolved cross-namespace cycles. URI and dictionary identity remain distinct;
shared namespace/map objects charge once, while distinct equal-name dictionaries
retain distinct identities and charges. Unknown cycle observations do not claim
PDF conformance or import WTPDF/PDF-UA requirements. The full final ISO 32000-2
wording for every transitive namespace return was not available in the consulted
primary material, and this limit is recorded in the contract and provenance.

The 92-test intermediate suite passed after maintaining the private codec's
handcrafted nesting payload for the added fields. The expanded suite passed
99 focused tests in both actual Native modes. The subsequent namespace-admission
and unexecuted-Do fixes passed 102 focused tests IN_PROCESS; their Worker-focused
regressions also passed. Independent Standards review closed the admission,
discarded-resolution-pass and compact definition ownership findings.

Further independent public probes found duplicate MCIDs accepted in unexecuted
whole-object Forms and repeated internally linked child invocations accepted in
referenced appearances. Public RED/GREEN regressions now validate all visited
definitions and count paths through ordinary Forms. Additional entry-point
regressions combine independent appearances and page execution, preserve a
child's independent appearance owner, and reverse structure order. Worker and
independent rechecks of this increment are in progress. Fonts, Facade, independent
certification and the final full-build gates remain outstanding as above.

## Definition owner closure and Type 3 increment

Independent probes found that stream/page deduplication merged distinct
appearance owners and omitted an independently owned ordinary intermediate Form.
The owner-identity RED/GREEN fix retains each owner separately and schedules
normal appearances of Forms already reached by definition or page traversal.
Both independent Spec and public Worker probes pass in both structure orders;
unrelated appearance programs are not decoded. The earlier duplicate-MCID and
path-multiplicity findings are also closed.

The Type 3 declared-metric increment has retained RED/GREEN pairs for admission
and exact byte/entry bounds, horizontal displacement and zero out-of-range
widths, and d0/d1 width agreement. The original blanket-rejection fixture now
proves decoded-byte exhaustion. Additional observations cover two distinct
selected fonts, shared CharProcs aliases, repeated font selection, nested glyph
text suppression and malformed required data. The 111-test focused IN_PROCESS
suite passes, and the full Worker suite also passes all 111 tests. Independent
Standards review found no implementation issue; Spec review independently
confirmed FontMatrix, rotated text, gs, spacing/scaling, signed/zero widths,
hidden glyph text, width disagreement and literal byte/entry boundaries in both
modes. The ADR, public contract, Capability Matrix and limitation identity now
record Type 3 support. Inventory authority validation passes. These host-JDK
checks are implementation evidence, not immutable-image certification.
The remaining Encoding-CMap and inherited-ToUnicode behavior, Facade and all
final certification work remain outstanding. No completion criterion is checked.

## CMap construction accounting increment

The public Query now charges each selected font's ToUnicode node and each
codespace-range declaration before construction. The new exact-boundary test
failed before implementation and passed after it. Two prior CID metric tests
were corrected to include the additional node and codespace costs; their old
boundary failures are retained. All 112 focused tests then passed in both
actual Native modes on the host JDK 17.0.9. These are implementation
observations, not final environment certification.

Independent pre-implementation dependency review established that this extractor
can remove ToUnicode from detached backend font construction while retaining
its own explicit mapping observations. It also identified FontBox 3.0.8's
shared integer-key map for three- and four-byte Unicode source codes; the
inheritance implementation must retain the source length as part of identity.
The remaining CMap behavior, Facade, qualification and certification work is
not complete. No completion criterion has been marked satisfied.

## ToUnicode inheritance and ownership increment

Embedded stream, textual and the four fixed bundled Adobe UCS2 parent CMaps
now use iterative bounded inheritance. Exact byte-and-length keys and local
overrides preserve mapping evidence independently of backend geometry. The
backend construction copy omits ToUnicode and original font identity continues
to control encoding inference and preflight. Program boundaries, mapping counts,
CID-operator misuse and parent-name inconsistencies fail safely. The named
Japan1 fixture independently counts the existing pinned resource at 284124 bytes,
23058 source mappings and one codespace; local overrides still charge.

Independent Spec review found inherited codespace redeclarations and undefined
local range destinations recovering overridden ancestor values. Independent
Standards review confirmed that returned mapping strings outlived their memory
reservations. All three issues were reproduced with failing public tests and
fixed. Original probes passed after the fixes in both modes; a separate
retained-text probe now accounts for exactly 63 additional 512-byte mappings
plus 63 source-code bytes. Reused and unobserved mapping controls also passed.

Full focused validation reached 114 tests in Worker before the review fixes,
then 13 targeted tests in both modes after those fixes. Textual and predefined
parent slices have retained Red/Green logs. The latest full IN_PROCESS run
passed 119 tests on host JDK 17.0.9; the matching full Worker run is pending
at the time of this entry. These remain implementation observations. Required
Encoding CMaps, the Facade, independent qualification and final certification
are still outstanding; no completion criterion is checked.

## Predefined Unicode carry and PDF 2.0 headers

The matching 119-test full Worker run passed. Independent original Adobe
cid2code data then exposed the predefined UCS2 resources' carrying range
semantics at a final-byte FF boundary. The public regression failed, and the
fixed parser now distinguishes predefined full-carry ranges from PDF inline
final-byte ranges. Selected IN_PROCESS and Worker checks passed; independent
probes confirmed all four admitted resources and their literal byte/mapping
limits.

Independent Spec review also identified PDF 2.0 stream CMapName contradictions.
The public Type/CMapName regression failed before the per-node check, then
12 selected tests passed in each mode. Independent name, Type, root and
inheritance probes closed that finding. The additional PDF 2.0 WMode and
CIDSystemInfo regressions both failed before implementation and passed in a
14-test selected IN_PROCESS run. A missing-import compile attempt is retained;
the compiled fix compares raw bytes without additional String construction.
The latest full Worker run is pending at this entry. These remain host-JDK
implementation observations, not final certification. Encoding, the Facade,
qualification and final certification are still outstanding.

## Encoding construction and declared metrics, first increment

The metadata increment's full Worker run passed 123 tests. A subsequent XUID
array regression reproduced an unrelated-metadata mapping-budget charge;
skipping unretained metadata arrays fixed it. The complete IN_PROCESS run
passed 124 tests and the selected Worker run passed 15. Independent Standards
review closed the metadata ownership and per-node validation concerns.

The first Encoding regressions then failed at the public Workflow seam:
Identity resources were not charged and embedded Encoding was rejected. The
new bounded parser and detached Type0 declared-metric adapter pass both
fixtures. Identity-H consumes 7889 decoded resource bytes and 65538 font-data
entries; Identity-V adds 2688 bytes and one node. The embedded fixture covers
one-, two- and three-byte codes, an explicit CID-0 mapping, per-CID widths,
raw-code word spacing, repeated font selection and first-excess limits.

Four older observers failed because their budgets omitted Identity costs; the
old failures are retained. With the explicit additional costs, all 126 focused
IN_PROCESS tests pass. Full Worker validation and independent implementation
review are in progress. Non-Identity predefined Encoding, additional inheritance
and notdef controls, Facade work, qualification and certification remain
outstanding. No completion criterion is checked.

## Complete Encoding resource and inheritance observations

The first Encoding full Worker run passed all 126 focused tests. The next
predefined and unsigned-source fixtures failed before implementation and then
passed. Literal resource controls independently account for all 61 standard
Encoding names, including inherited nodes and source cardinalities; selected
83pv-RKSJ and 90ms-RKSJ horizontal/vertical cases check actual CID geometry.
The selected Worker run passed 14 tests, including all 61 resources.

Independent Spec probes found that mappings outside the adopted codespace,
late codespace declarations and overlapping ToUnicode codespaces could pass.
Public regressions reproduced those failures. Both CMap families now validate
the complete adopted domain and declaration order before construction. Two
older controls required correction: an admitted predefined resource now hits
its declared bound, and a one-byte codespace fixture must use one-byte source
endpoints. Their failed run is retained. Independent probes closed all three
findings in both Native modes without changing the literal geometry outcomes.

Independent Standards review found that distinct Type0 fonts sharing a CID
descendant did not each charge its materialized width table. The public test
failed before the fix. Shared and separate descendants now both require 8204
entries; repeated selection of one font requires 4102. Original independent
probes verified exact success and first-excess failure. The complete IN_PROCESS
suite then passed 131 tests. A further positive observer covers both descendant
kinds and writing modes with inherited CID/notdef overrides and declared W/W2
geometry; its selected 13-test run passes. The matching full Worker suite
passes all 132 tests with zero failures and zero skips (2026-09-12 14:29 CST).

These are host-JDK implementation observations, with Red/Green logs and
independent review outputs retained here. The Facade implementation,
independent tool qualification and final candidate certification remain
outstanding. No completion criterion is checked.

## Six-member Facade implementation

Each of the six frozen members has a retained compilation Red before its
minimal delegation implementation and a passing focused Green. The complete
six-member slice passes in both Stable and Preview. Additional public controls
cover all-or-nothing malformed later pages, all four mapping-confidence states
and diagnostics, caller-owned streams, staged publication and expired handles.
All nine extraction Facade tests pass in each edition; the complete Facade
consumer regression passes all 73 tests per edition with no skips.

Independent Standards review found no hard violation and accepted the two
identical declarative default profiles under the frozen surface constraint.
Independent public Spec probes passed in both editions for all six members,
whole-document limits and malformed content, page identity through insert/copy/
remove, queued pages, detachment, uncertainty, structure, ownership and receipts.
Those probe sources and reports are retained under facade-independent-review.

The capability remains experimental. Stable Surface Manifest registration and
Foundation member authorities must be synchronized when independent tool
qualification supports promotion; the current uncommitted implementation does
not by itself establish that gate. Independent corpus/tool qualification,
recorder work, final candidate certification and complete review remain open.
No completion criterion is checked.

## Updated delivery authorization

The user subsequently authorized committing and pushing to GitHub, followed
by closing #75, only after the entire task is complete. This supersedes the
original handoff's no-commit/no-push/no-tracker-mutation constraint for those
final delivery actions. All implementation, qualification, final-candidate
certification, validation and independent review gates still apply first.
The commit must retain the repository's real-name DCO requirement. No commit,
push or issue closure has been performed at the time of this entry.

## Original corpus and independent observations in development

The first two original Sources are `nested-split-type3` and `marked-structure`.
They have deterministic PDF bytes, literal detached-value expectations and
original RGB rectangle rasters. The authoring CLI passed its failing-then-passing
tests and reproduces the checked-in data. No renderer or Folio output supplied
expected glyph geometry, Unicode confidence, replacements or relationships.

The products CLI records actual Native IN_PROCESS/HARDENED_WORKER and actual
Facade IN_PROCESS extraction, explicit PDF 2.0 rewrite, COMMITTED receipts,
unchanged Sources and reopened complete values. Its first public test failed
before the command existed. The structure observer separately failed before
the new corpus and nonempty values were supported. Public rendering mode was
added after an independent reviewer caught the omission; its public CLI test
failed on the missing field and then passed. Empty versus absent optional
structure values and shared opaque namespace identities remain distinguishable.

Incremental Standards and Spec reviewers found and closed an expectation-file
reread after identity validation, the omitted rendering mode, a forbidden PDF2
Document/Span parent relationship, and an empty K array in a standard-positive
fixture. The corrected fixture has Document/P/Span nesting and omits the leaf K.
It shares the PDF2 namespace between P and Span. The expectations are parsed
from the exact byte snapshot whose digest was checked, with a closing identity
check. Actual Arlington probes also exposed a required Tagged Type3 descriptor;
the corrected original has the descriptor and no unqualified optional FontName
predicate. The latest direct Arlington probe has no Error or Warning. These
probes are not a substitute for the still-pending qualified standards rule union.

The actual visual CLI and controls passed with the pinned PDFium, PDFBox and
ImageMagick engines. Both original cases pass exact pixels. The original width
and placement defects are detected; the raw first-round visual controls remain
under `visual-controls-r1`. The complete five-test public recorder slice passed
with `-Pindependent-certification`, zero skips, after the corpus review fixes.

The independent qpdf semantic CLI compares the complete reachable decoded
object graph while retaining aliases, child/content order and decoded stream
bytes. It normalizes only serialization and the explicit rewrite version.
Both originals and their independently reserialized/compressed forms pass;
ActualText and namespace-link changes fail. A substituted wrapper which silently
read the original instead of the changed PDF initially produced a false PASS;
the public regression now rejects its identity before execution. Both actual
binary and wrapper identities are checked. The Java semantic CLI pins that
qualified script and combines the graph result with all detached public values
against the independently authored expectations. Eight observations across
the two cases, Native modes and actual Facade mode pass. Twelve retained actual
PDF semantic controls detect content order, Form position, font width,
replacement, Alt, language, MCR, ParentTree, OBJR, role/namespace links and empty
versus absent replacement. The raw reports are under `semantic-controls-r1`.

All of these observations use the development host JDK, not the final staged
environment matrix. The corpus still needs additional font/CMap/geometry cases;
qualified standards, the complete T03 text recorder, authority promotion,
final-candidate certification, full validation and final independent review
remain outstanding. No completion criterion is checked.

## Original fonts and complete extraction observations

The corpus now contains five original cases: the earlier nested/marked Type3
cases, direct embedded Type1/MMType1/TrueType/CID0/CID2 H/V fonts, embedded
Encoding/ToUnicode inheritance, and uncertainty with rotation/UserUnit/spacing.
Three original fixed-pitch rectangle fonts were generated reproducibly with the
already licensed fontTools 4.59.2 acceptance tool. No runtime dependency changed.

The direct font case uses an independently authored raw PDF reference and fixed
PDFium smoothing; its primary comparison remains exact. Secondary differences
must stay inside the original one-pixel edges, with total limit 1120. Four actual
PDF visual controls and three secondary-only damaged-raster controls are
effective. Earlier inherited-CMap rendering failures remain INDETERMINATE raw
history; its required standards/semantic cases remain in the corpus.

All mapping Optional presence and complete diagnostic fields are now observed.
The single source-space case caught an acceptance Properties escaping defect,
which was corrected in T13 authoring only. Native/Facade publication, detached
values and reopen agree with every literal case. The full T13 acceptance class
ran 11 tests with actual tools, zero failures/errors/skips. Incremental Standards
and Spec reviews ended clear after fixed-pitch metadata and edge-guard findings
were resolved. Logs, raw reports and review closure are under `font-corpus-r1`.

Six first original font standards controls were generated under strict RGR.
Current tool probes demonstrate unqualified font-name equality and W2 coverage:
pdfcpu accepts the illegal controls and the old Arlington rejects legal W2.
This is an actionable checker qualification gap, not a passing standards chain.
The full rule union, T03 text recorder, authority promotion, final staged
certification, full verification and final clean-context reviews remain open.
No commit, push or tracker update has occurred; the user's delivery authorization
applies after all remaining requirements pass.

## T13 standards qualification in progress

The T13 Arlington build is separate from every historical tool pin. The initial
font controls exposed the incorrect wildcard W2 array context and missing
owning-font predicates. The shared Type1/MMType1 descriptor control then exposed
the missing FontMultipleMaster dispatch. Both findings were corrected and
independently closed. A malformed cumulative patch was also detected by replay;
GNU diff output now replays from the exact archive and every changed/new input
is compared byte for byte with the actual build source/model.

The main marked corpus now supplies the optional Type3 Name and FontName `/F1`.
The unnamed original remains a legal PDF 2.0 positive; pdfcpu's rejection of
that legal omission is retained. All four combinations of optional name presence
are now observed through the actual Arlington recorder. Independent Spec probes
also checked the two asymmetric cases and wrong-type/mismatched-name controls.
The source changed to `8ab0ade118339b4d9efe0939be481dd6453482edf0751961d745f9b6c52bcac6`;
the corpus and independent observer literal pins were refreshed together.

The full T13 acceptance class plus the first standards qualification ran 13
tests with zero failures/errors/skips. All detached public outcomes, Source
preservation, rewrite/reopen, semantic and visual controls remain passing on the
development host. Core qualification reuses 60 pdfcpu and 24 Arlington rules
over the five original cases (with a separately authored PDF 2.0 nested positive).
Eight owning-font/W2 rules pass with direct, inherited and all optional Type3
name positives. Subsequent UserUnit, Type3 dictionary and d0/d1 header/declared
width rules bring the currently qualified union to 117. The actual current
standards suite passes both tests; every configured negative is run against
each corresponding positive, with no tool skips.

Logs and raw reports through the initial core/font qualification are retained
under `standards-r1`; later increments are being retained as they complete.
The current glyph parser increment remains under independent review. The
complete font-program/CMap/content/structure/namespace rule union, full text
recorder, authority promotion, final staged eight-environment certification,
refresh of prior obligations, full verification and fresh final review are
still pending. No completion criterion is checked and no commit/push/issue
closure has occurred.


## Current declaration and CMap program qualification

The current Arlington/pdfcpu declaration union is 334 rules: 60 pdfcpu core,
24 Arlington core, eight owning-font/W2, 81 text/simple-font, 50 CIDFont,
94 descriptor and 17 CMap dictionary rules. All three actual qualification tests
pass with zero skips in `t75-cmap-declarations-checker-r1.log`. Type3 glyph,
simple-font null/Widths and descriptor/CMap declaration review findings have
been independently closed. Earlier paragraphs describe their historical stages.
The publication qualification uses PDF 2.0; legacy required-field gaps remain
explicitly unqualified.

The new independent qpdf CMap program CLI has 12 passing public tests after
strict Red/Green repair of nested direct Font coverage, expansion budgets,
indirect Subtype, PostScript names and FF comments, optional declaration types,
ToUnicode font-zero selection and empty-block ordering. Original review probes
are retained under the standards profile. The two independent reviewers are
rechecking this increment; it is not the final Standards/Spec gate.

`standards-r4` retains the latest descriptor/CMap declaration raw reports,
program Red/Green logs and original independent review probes. Raw report
archives are byte-verified against every original member; `archive-identities.json`
binds archive and member SHA-256 identities. Full binary font, owning-font CMap,
content and structure qualification, combined text recorder, authority promotion,
final eight-environment certification and refresh of the five prior obligations,
full Maven/JDK validation and fresh final review remain outstanding.
No completion criterion is checked; no commit, push or issue closure has occurred.


## CMap owner and resource review closure

The CMap program catalog now has 33 isolated negative controls with specific
source clauses. Owner checks cover Registry/Ordering (not equal Supplement),
simple-font byte length, Type0 mixed-prefix agreement and local/inherited
ToUnicode source membership in the owning Encoding. The first source-domain
fixture selected the wrong font relation; its failed attempt and corrected
Red/Green are explicitly retained in `standards-r5/README.md`.

Independent review also closed late codespace, inherited-source allocation and
mixed-prefix findings. Effective sources are bounded at 4,096 before copying or
insertion; an override preserves the count and the first new excess is
INDETERMINATE. The final review-stage prefix helper extraction keeps all 35
public CLI/authoring tests green. Sixty-one actual program observations, raw
reports, exact checker snapshot and review archives are retained in
`standards-r5`. Both axes are clear for this increment only. Binary-font
qualification has begun with original corrupt-header controls; it has no
passing program observation yet. All final recorder/certification/delivery gates
remain pending, and no completion criterion is checked.

## Initial font-program review closure

The font scope now reads the SHA-pinned fontTools 4.59.2 wheel independently of
Folio and the authoring command. Three malformed headers and two declared CFF
kind substitutions have isolated original controls. Seven independent review
findings were fixed through actual public CLI Red/Green: Python optimization,
missing CharStrings, raw glyph count, compatible minor versions, unsupported
charset exceptions, duplicate TrueType tags and reserved DICT operators.
The charset must also account for every indexed glyph. Both review axes closed
their original findings; a separately authored one-byte INDEX confirms 229/230
charset count outcomes. All 48 program/authoring tests passed without skips.

`standards-r6` retains the exact observer snapshot, Red/Green logs and four
byte-verified original/closure archives. Earlier font paragraphs describe the
historical first stage. Complete font table/outline/metric/owner qualification,
content and structure qualification, text recorder, authority promotion, final
eight-environment certification and prior-obligation refresh, full validation
and fresh final review remain pending. No completion criterion is checked and
no commit, push or issue closure has occurred.

## CID TrueType source split and continuing font qualification

Font binary qualification now also checks sfnt directory fields, byte ranges,
alignment/padding and the whole-font checksum; CFF INDEX locations; a bounded
hmoveto/hlineto/endchar outline grammar with actual fontTools drawing; simple
TrueType glyph coordinate bounds; and embedded FontName/Length1 agreement.
These additional rules are under incremental independent review. Full font
metadata/metrics and all content/structure qualification remain pending.

The original CIDFontType2 Source now embeds a distinct 1,144-byte TrueType
program without `cmap`, preserving original glyph IDs, outlines and metrics.
This conservative change follows directly read ISO 32000-1 §9.9; the final
2020 wording remains unobserved. The original three font files and rendered
reference PNG are byte-identical. The preceding 369 corpus/control/reference
files are archived, with every byte verified, under `font-corpus-r2`.
New direct/inherited Sources, corpus manifests, 334-rule control identities
and semantic pins have been synchronized. All 69 Python tests pass without
skips. Actual Maven acceptance/tool qualification subsequently passed 14 tests
with zero failures, errors or skips in 2:08; its log is retained alongside the
corpus evidence. This includes both public extraction products and all 334
declaration rules on the revised Sources.
Final recorder, promotion, certification, full validation, final review and
authorized commit/push/closure remain pending. No completion criterion is checked.

## Font layout, outline and name review closure

The expanded font catalog has 36 isolated controls. Both incremental review
axes are closed after actual RED/GREEN repairs for non-Type-2 CharStrings and
conflicting TrueType platform PostScript names. All 59 public program/authoring
tests pass without skips. `standards-r7` retains the exact observer, 26
incremental logs, the full-suite log and four byte-verified original/closure
archives. Font metadata/metrics and owner relationships remain the next
qualification scope. Final recorder, certification and delivery gates remain
pending; no completion checkbox, commit, push or issue closure has occurred.

## Font bounds review closure and initial text recorder plan

The font catalog now has 45 controls. FontBBox checks measure actual outlines,
accept either diagonal ordering and indirect numeric coordinates, and check
TrueType units before scaling. CID CFF requires both FDArray and FDSelect.
Independent review exposed incorrect general CFF matrix interpretation and
floating-point false failures. Explicit CFF matrices now remain INDETERMINATE;
all required original corpus fonts use the qualified implicit matrices.
Both review axes closed their probes. `standards-r8` retains exact source,
20 incremental logs and four byte-verified original/closure archives.

The public Foundation CLI now generates an unverified text plan containing
all eight actual Native combinations, 118 consumer/artifact checks per run and
the real IN_PROCESS Facade mode. `recorder-r1` retains the plan RED/GREEN.
The combined program/authoring/text-plan/T12 suites pass 68 tests with no skips.
Width/owner, content/structure qualification, complete recording/collection,
final certification and prior-obligation refresh, full verification, final
review and authorized delivery remain pending. No completion checkbox is marked.

## Exact font metrics and syntax recording

Nine isolated simple/CID width controls now supplement the 45 font-program
controls. Both incremental review axes closed fractional TrueType width and
bounds findings after exact integer-ratio fixes. CFF real DICT widths and
curved TrueType bounds remain explicitly unqualified; all required original
Sources meet the qualified profile. All 75 public Python tests pass without
skips. Eight byte-verified archives, exact observer snapshots and actual
RED/GREEN logs are retained in standards-r9.

The public T13 syntax command now passes all original Sources, detects a
truncated PDF and refuses a substitute tool with the right version label.
Two actual Maven/tool tests pass with no skips; recorder-r2 retains RED/GREEN
and the pre-review Java snapshot. Independent review of this increment is in
progress. Complete content/structure qualification, combined recording and
collection, authority promotion, final certification and previous-obligation
refresh, full validation, final review and authorized delivery remain pending.
No completion checkbox, commit, push or issue closure has occurred.

## Content program review and qualification

All 55 original content/Type3 negative controls fail their intended predicates,
and all five required Sources pass. The complete public Python program,
authoring, qpdf-identity, semantic and Foundation suites pass 91 tests without
skips. Standards independently closed its name-byte and duplication findings
through 28 actual CLI probes. Spec closed all four findings through 55 actual
CLI observations, with the PDF 2.0 nesting and qpdf brace boundaries explicit.
Exact pre-review and
current observers, original and closure archives, every actual RED/GREEN and
honest failed fixture attempts are retained in standards-r10.

Structure qualification, complete recording/collection, authority promotion,
final candidate/environment certification and previous-obligation refresh,
full verification, clean final reviews and authorized delivery remain pending.
No completion checkbox, commit, push or issue closure has occurred.

## qpdf runtime identity review closure

The syntax recorder and both Python observers now bind the unchanged qpdf
distribution, actual loaded pin and all eleven runtime library paths. Empty
and relative cache selection and wrapper links follow actual process semantics.
Final syntax reports retain input/tool identity failures separately from raw
qpdf observations. Both review axes independently closed all related findings.
Nine byte-verified review archives and exact external-binary reconstruction
recipes are retained in recorder-r2, together with the official ZIP receipt.
The complete Maven acceptance/qualification suite passes 20 tests without skips.

Content operand, PDF lexical and per-context text/graphics/marked balancing
increments have public RED/GREEN evidence. Resource/structure qualification,
combined recorder and collection, authority promotion, final certifications
and prior-obligation refresh, full gates and final clean reviews remain pending.
No completion checkbox, commit, push or issue closure has occurred.

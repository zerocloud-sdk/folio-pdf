# T12 Foundation annotation certification contract

This contract is frozen before collecting T74 product observations. It applies
to `document.annotations-actions.manage`, issue #74, under ADR-0034 and
ADR-0040. The implementation baseline is
`db88f8c21dcd2e8f33613838b71cd42216254527`. This document defines expectations;
it does not establish compatibility or passing evidence.

## Public behavior and migration surface

The approved test boundaries are `DocumentWorkflow.execute`, the public
Migration Facade, the repository acceptance commands, and the Foundation
runner CLI. Observations use detached Queries, reopened published files,
Publication Receipts, stable safe failures, and independent tools. They never
assert backend identity or private calls. Each new generation or validation
boundary requires a recorded failing test before implementation.

The six families are Text, Stamp, Highlight, FileAttachment, standalone Widget,
and Link. Every family must be created, published, reopened, changed, published
again and reopened, preserving its identifier, containing page, rectangle,
contents, flags, normal appearance and subtype-specific values. Highlight
quads and color, Text icon/open state, Stamp name, attachment metadata and bytes,
and direct versus Action Link activation are independently observable.

The matching Stable and inherited Preview surface follows the existing T73
adaptation: use the immutable Native Annotation, AnnotationAppearance,
GoToAction, NavigationTarget and PageActions values. Do not duplicate their
representation or introduce mutable backend handles. The complete selected
member set is:

- `PdfDocument.getAnnotations(int,long,long)` and
  `updateAnnotations(List<Annotation>,List<String>)`: bounded document-order
  reads and one atomic replacement/removal Command, including moves by identity.
- `PdfPage.getAnnotations(int,long,long)`, `addAnnotation(Annotation)` and
  `removeAnnotation(String)`: page-array order, creation/replacement on the
  handle's current page, and removal by document-wide identifier on that page.
  Page handles follow page identity through reordering. Read bounds apply to
  the complete document inspection before selecting that page.
- `PdfPage.setNormalAppearance(String,AnnotationAppearance)`: replace only a
  selected annotation's normal appearance while retaining all other properties.
  This explicitly adapts the reference annotation setter to a page-owned edit.
- `PdfCatalog.setOpenAction(GoToAction)` and `getOpenAction(int)`, plus
  `PdfPage.setAdditionalAction(PdfName,GoToAction)` and
  `getAdditionalActions(int)`: catalog open and page O/C bindings; a null Action
  removes the selected binding. Reads use a document-wide binding bound.
- `PdfDocument.getActions(int)` and `flattenAnnotations(String...)`: detached
  complete Action inspection and one atomic non-form flattening selection.
  Flattening is an explicit Folio extension and never maps AcroForm flattening.

Replacement ordering follows the Native builder: the last supplied value for
an identifier wins at that identifier's first declaration position. An update
retains unselected annotations in order, then appends replacements in declaration
order on each page. Page appearance replacement follows the same ordering.
Page removal and appearance editing inspect at most 100,000 annotations and
8 MiB each of decoded appearance and attachment bytes across the document.
An identifier belonging to another page, or to no page, is a programming error
for these page-scoped selections. Document-wide removal and flattening retain
the Native `ANNOTATION_NOT_FOUND` operational failure.

Factories for the six families, appearance, explicit/named Navigation Targets,
and Link activation remain the existing public Native values, as do metadata
and destination values in T73. The manifest must label adapted signatures and
extensions accurately. Every selected member must have observable behavior;
no unsupported Stable stub is permitted. Facade execution remains IN_PROCESS
and its records must state that mode even in a Native Worker certification.

## Original corpus and independent expectations

Sources use PDF 2.0 for associated-file relationships, contain no fonts, and
carry explicit 120 by 100 point page boxes with zero rotation. Primary pages
A/B/C and appendix D have an opaque rectangle at `[10 20 40 30]`, colored
red/green/blue/black respectively. These page identities and geometry reuse
the qualified T10/T11 original corpus conventions. Page A also retains a
non-identity graphics state after its existing paint, exercising flattening's
required isolation. Its original content program must remain intact.

Normal appearances use an identity Matrix, empty Resources and an explicitly
authored nonzero-origin BBox. Opaque axis-aligned geometry permits exact raster
expectations at 144 DPI (240 by 200 pixels). Expected pixels are authored from
the fixed coordinates and colors, before any product rendering; PDFium output
must never generate expected images or thresholds. Each family's appearance
must be observed independently, including Widget annotation appearance without
creating an AcroForm field. Independent structure verifies the BBox, Matrix,
Resources, bytes and the correct placement transform after flattening.

Products include the original six-family round trip, a changed round trip,
non-Widget flattening, page copy, merge and split. Expected annotation order
is page order followed by each page's annotation array. Copy suffixes colliding
identifiers by the first available `-N`, redirects selected-range targets to
the copied pages, and retains external targets. Merge appends D, renames a
colliding named target `shared` to `shared-1`, and rewrites its Link and Action
references together. Split independently retains only bindings whose owner
and target both survive, with exact product page numbers. Explicit destination
operands and named-target resolution are checked separately. The primary
catalog Action wins merge; an absent primary adopts the first appendix Action.

Every selected non-Widget appearance is incorporated without changing retained
paint, and its Annotation disappears. Widget flattening, missing appearance,
invalid programs, malformed/dangerous graphs and orphaning destination changes
must fail before mutation. Exact values and page selections belong to the
checked-in corpus and may not be inferred from observed products.

## Failure and resource controls

Tests cover annotation count, appearance and attachment read bounds, 1 MiB
per-appearance input and 8 MiB document-wide decoded appearance/attachment
passes, and transaction resource limits. Rejections preserve the preceding
Session state and prevent unintended publication. Caller streams remain open;
receipts follow Target declaration order and retain Native failure semantics.

Unknown/chained Actions remain inert and structurally preserved when an
annotation-only change does not interpret them. Action Queries reject them;
page rewrites requiring target semantics reject before mutation. Script,
process, file and network sentinels must be capable of detecting execution.
Worker runs must use the selected execution profile for the actual operation,
including safety cases. Explicitly replacing a binding is allowed only when
the remaining graph is safe. AcroForm fields, form Actions and form flattening
remain outside the approved subset; unsafe existing form relationships reject.

## Independent chains and environment identity

Every exact product requires separate syntax (pinned qpdf), standards
(independently qualified strict/offline pdfcpu and Arlington predicates),
semantic (project-owned graph assertions), and visual (independent PDFium)
records. Required standards rules cover common annotation dictionaries, every
supported subtype, appearance Form structure, local destinations and Action
bindings, and retained page/embedded-file structure. Freeze rule assignments
before collecting products. Each required rule needs an original illegal
control and a matching checker diagnostic; missing predicates require a new
qualified checker revision. A producer label cannot establish rule coverage.

Semantic controls must detect wrong order, identity, target, subtype property,
payload, appearance and flattening removal. Visual controls must detect a
one-pixel change and incorrect placement or lost retained paint. ImageMagick
AE uses zero fuzz and a zero threshold against the independently authored
opaque sRGB raster. Renderer disagreement, missing tools or rules, unexpected
output and any identity mismatch are unavailable evidence, never PASS.

Required environments are the eight actually executed Ubuntu 24.04/Linux
x86-64 JDK 8/11/17/21 by Native IN_PROCESS/HARDENED_WORKER combinations.
Records bind actual products, candidate inputs/artifacts, contract, immutable
OS image, observed JDK vendor/build, execution configuration and tool identities.
Windows and macOS remain explicitly uncertified. Refresh transactions, values,
pages and metadata evidence for the same final candidate. Unrelated Foundation
obligations may keep aggregate readiness NOT READY.

The three annotation release blockers are resolved only by the complete six-
family round trips, complete supported navigation/event cases, and successful
appearance/retained-paint/flattening cases above with their independent chains.
Their restrictions then remain explicit contract boundaries, not substitutes
for successful behavior. Full verification, the four-JDK matrix, inventory
validation/checking and independent Standards/Spec review cover the complete
uncommitted change since the fixed baseline. No checklist item is complete
before that independent review.

## Provenance

All new fixtures, expectations and implementation are original project work
under Apache-2.0. Public ISO 32000 requirements establish correctness. The
public iText Core 7.2.6 API documentation for PdfPage, PdfCatalog and
PdfAnnotation supplies mapping names only. No iText implementation, resource,
fixture or binary-derived implementation detail is used. Acceptance tools and
fixtures remain outside product runtime.

## Appearance observation qualification

The fixed PDFium page-rendering command omits standalone Widgets. T12 therefore
uses its independent `flatten` command to project annotation appearances into
an acceptance-only PDF, then renders that projection. Each page record binds
the original exact hash, the projection exact hash, both invocations and the
pinned PDFium identity. PDFBox supplies secondary disagreement evidence by
rendering the original PDF directly. This projection neither changes the
candidate product nor establishes Native flattening success: removal, retained
page programs and flattened Form placement are checked on the original product.

The original pixel grids include every standalone Widget and retain zero AE
and zero renderer-disagreement thresholds. Original reference PDFs qualify the
projection before Folio products are observed; changed appearance colors and
positions must fail against those same expectations. The explicit identity
Matrix in the original appendix revealed a Native read gap: omission and an
explicit numeric identity are now equivalent, while other or malformed matrices
remain rejected before a page operation can mutate the document.

The reference PDFs also contain optional embedded-file parameters and Unicode
file aliases for independent standards qualification. These are reference
serialization choices, not additional Native FileAttachment operations. Product
semantics compare the required payload, filename, description, MIME type and
relationship independently of those optional serialized entries.

Page-program observations permit only additional trailing PDF whitespace after
the original complete program. An original positive control qualifies this
semantically inert case; changed painting operators or operands still fail.
Expected pixels, their positions and comparison thresholds remain unchanged.
The independent object-graph check also exposed double escaping of annotation
attachment MIME names. The writer now supplies the decoded name to the PDF name
serializer, and the reader retains the already decoded name, including literal
hash sequences; Native public value inspection and qpdf check this independently.

## Tool qualification and reproduction

The 174-rule union is frozen in
`capabilities/profiles/T12-standards/required-rules.txt`: pdfcpu checks 69 rules,
Arlington core checks 68, and Arlington annotation checks 37. Each rule has an
original illegal PDF with its exact expected diagnostic. The T12 Arlington
revision adds only page-local identifier uniqueness, optional P-owner identity
and Link Dest/A exclusivity to the cumulative T10/T11 checker. See the
[acceptance tool build record](../build-tools/acceptance/arlington/README.md).
The three legal-boundary PDFs and original six-family PDF must pass. Missing
predicates, changed profile hashes or unavailable tools never qualify a rule.

`scripts/t12-semantics.py` reads pinned qpdf JSON without importing Folio, the
product generator or its helpers. It verifies exact page programs, all Annotation
properties, AP resources/programs/BBox/Matrix, file metadata and payload hashes,
Action kinds, targets/operands, names, order and flattening placement/removal.
Original positives and defects qualify it separately; 18 mutations of actual
published products then demonstrate detection through the evidence CLI.

Actual visual controls alter a standalone Widget's color and rectangle and a
flattened product's retained page paint. An additional original one-pixel raster
control independently qualifies the fixed ImageMagick comparison: identical
inputs require AE=0, the one-pixel copy requires AE=1, with fuzz and threshold
both zero. The original expected PNG remains unchanged. This comparator control
supplements the actual PDF defects; it cannot replace their successful detection.

The Foundation recorder uses the runner-mounted `/usr/bin/python3.12` explicitly;
the immutable JDK images do not provide a generic Python alias. The public Action
probe runs under the independent Linux observer in
`scripts/t12-safety-observer.py`. Each invocation first qualifies real reads,
writes, executable-file access, script-file effects and loopback connections.
The probe then preserves and rejects unknown/chained graphs and explicitly
replaces a binding through Native and Facade APIs. Declared canary paths and
the endpoint are observed across descendants; the record states this scope
without claiming an interpreter trace or observing unrelated process activity.
A separate original synthetic signed Source verifies protected rewrite rejection
and unchanged source/target bytes. Normal public tests retain resource-policy,
malformed graph, unsupported Widget and incremental signature boundaries.

Provision the existing fixed tools and HarfBuzz helper as described in the
[README](../README.md), and build the pinned T12 Arlington supplement before
running the independent suite. Ordinary `verify` excludes the external-tool
category; run it explicitly:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_t12_*.py'
./mvnw -B -ntp -pl pdf-acceptance,pdf-migration-itext7-preview -am \
  -Pindependent-certification \
  -Dtest=AnnotationWorkflowTest,AnnotationFacadeTest,T12EvidenceCommandTest,T12StandardsQualificationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -ntp verify
./scripts/verify-jdk-matrix.sh
./scripts/inventory generate
./scripts/inventory validate
./scripts/inventory check
```

The evidence CLI offers `products`, `observe`, `semantic`, `visual`, `controls`,
`pixel` and `safety` phases for diagnosis. Its complete form is
`T12EvidenceCommand <repository> <fresh-output> <execution-profile> <release>`.
The complete recorder creates all 16 products, observes every chain, requires
effective controls and safety, and refuses a non-passing aggregate. It preserves
individual receipts, qpdf graphs, AP projection PDFs, original/actual/difference
rasters, tool diagnostics, observer qualification and all actual control bytes.

Freeze source, contracts and generated documents before staging. Readiness
Markdown uses repository-relative findings so host and container checks consume
the same files while retaining every stale-evidence diagnosis. From the
repository root, with `FOLIO_HARFBUZZ_HELPER` set to the qualified executable:

```sh
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T74-r1-annotations --obligation annotations
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T74-r1-transactions --obligation transactions
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T74-r1-values --obligation values
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T74-r1-pages --obligation pages
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T74-r1-metadata --obligation metadata
./scripts/inventory generate
./scripts/inventory readiness
```

Each certification uses the four immutable Ubuntu 24.04 images declared in
`foundation-environments.yaml` and actually executes both Native profiles. Each
tuple runs 36 Annotation Native tests, nine Stable Facade tests and two artifact
contracts (47 total). The existing four-JDK build separately verifies Preview's
inherited source/tests and actual jar surface. The runner rejects a missing or
changed candidate, contract, selected mode, receipt, rule assignment, semantic
object graph, page projection, detected control or qualified safety observation.
A later source or contract edit requires another stage and refresh of all five
obligations; a static evidence index alone never certifies changed bytes.

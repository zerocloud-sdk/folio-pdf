# T13 text and logical-structure extraction evidence

Status: `compatible`

Capability: `document.text-structure.extract`

Acceptance Profile: `T13-text-logical-structure`

Release train: `0.1.0-SNAPSHOT`

This profile covers the bounded version-1 `ExtractTextAndStructure` Document
Query and its six matching Stable and Preview Migration Facade members. The
complete result is immutable, detached from its Document Session and free of
backend types. The [public guide](../../docs/text-logical-structure.md) defines
ordering, geometry, mapping uncertainty, relationships and all fifteen limits.
The [T13 certification contract](../../docs/t13-certification.md) fixes the
successful cases, source authorities, corpus and independent qualification.

The compatible candidate declaration is supported by the four qualified chains
below. Platform declarations are frozen contract inputs, not environment
observations. A candidate is accepted only after its actual eight Native
combinations, refreshed prior obligations and final validation/review gates
pass. The [Foundation evidence authority](../foundation-evidence.yaml) binds
those observations to the candidate, compiled artifacts, immutable images,
observed JDK builds, execution settings and tools. Changing those identities
invalidates certification. No global Foundation readiness follows from this
profile; unrelated unfinished obligations remain mandatory.

## Successful public behavior

`TextStructureExtractionWorkflowTest` exercises 115 cases through
`DocumentWorkflow.execute`. Each certified Native tuple must run those cases,
nine Stable `TextStructureFacadeTest` cases and two compiled-artifact contracts,
for 126 mandatory cases. Preview inherits the same implementation and tests;
its compiled surface is checked separately. Public observations cover:

- Deterministic page and content-stream execution order, nested and repeated
  Forms, tokens split across Contents members, and independently specified
  matrix, advance, rotation, spacing and coordinate expectations.
- Defensive exact source bytes and explicit, inferred, contradictory or missing
  Unicode evidence; no fabricated mapping certainty or reading-order text.
- Ordered marked-content occurrences, outer ActualText replacement precedence,
  independent Alt, inherited language, MCIDs, ordered structure children,
  annotation-owned appearance MCRs, whole annotation/Form OBJRs, ParentTree
  backlinks and detached Session object identity.
  Omitted/null optional StmOwn retains no fabricated owner identity. Normal
  appearance roots participate in bounded invocation checks even without their
  own structure item; direct and transitive repeated descendants fail safely.
  Whole Form OBJRs accept direct appearances and actual nested appearance calls;
  same-page repetitions remain valid for whole Forms without internal structure
  claims. Resource declarations without the needed call do not establish an
  appearance descendant's page association.
- PDF 2.0 namespace identities, distinct standard vocabularies, transitive
  RoleMapNS associations, root RoleMap separation, bounded unresolved roles
  and direct root namespace declarations with indirect element/target identity.
  Invoked and referenced Forms accept absent or null optional Type entries.
- Type3 declared geometry and bounded glyph programs; embedded font kinds;
  all 61 standard predefined Encodings; bounded embedded CMap inheritance;
  four predefined UCS2 parents; exact code identities, local overrides,
  codespaces, CID metrics and safe rejection of contradictory declarations.
- Exact-boundary success and first-excess failure for all fifteen limits,
  iterative graph traversal, bounded decompression/font construction,
  all-or-nothing failures, Source preservation, explicit ownership,
  preceding Session Commands and values usable after Session close.

The six Facade members are `PdfTextExtractor.getTextFromPage(PdfPage)` and its
`ExtractionLimits` overload, `PdfPage.getPageText(ExtractionLimits)`,
`PdfDocument.getTextAndStructure(ExtractionLimits)`, and both
`PdfDocument.getStructTreeRoot` overloads. The last pair returns immutable
`List<LogicalStructureElement>` values. The two convenience overloads use the
fixed fifteen-bound profile in the certification contract. Every overload
queries the whole document before selecting its result. Public parity checks
include reopened products, committed Publication Receipts, detached values and
stable safe failures. The Facade's actual execution mode is `IN_PROCESS`.

The exact six mappings appear in the [Facade Surface Manifest](../facade-surface.yaml)
and the Foundation text obligation. Compiled Stable and Preview surfaces,
Java 8 class versions, backend isolation and classpath exclusivity are checked
through their public artifact contracts.

## Independent acceptance evidence

The frozen original corpus has five products, each observed through Native and
Facade publication. All ten products require syntax, standards and semantic
chains; nested/split content, marked structure and direct embedded fonts also
require visual evidence, producing six visual observations. Inherited-font
and uncertain-geometry cases retain their explicitly different chain scope.

- [Syntax](T75-text-syntax.md): pinned qpdf 12.4.0, original input/output,
  exact process receipts and a truncated-document negative control.
- [Standards](T75-text-standards.md): strict offline pdfcpu and the qualified
  Arlington T13 supplement cover 334 declaration rules in seven profiles.
  The independent Python/fontTools program observer adds 42 predicates across
  eight scopes with 165 original illegal controls. All 376 required rules,
  legal boundaries and exact negative diagnostics must be present.
- [Semantic](T75-text-semantic.md): an independent qpdf graph observer compares
  unchanged original Sources with each product; separately authored complete
  public-value expectations and twelve source-derived defects cover the
  extraction contract without using the Folio backend as a correctness oracle.
- [Visual](T75-text-visual.md): pinned PDFium and ImageMagick compare original
  rectangular-glyph expectations at 144 DPI. Primary comparison is exact;
  the independent secondary font-edge allowance keeps decimal AE separate
  from exact changed pixels. Four PDF defects and three exact raster defects
  must fail, while the edge-only positive must pass.

Producer receipts retain the original bytes and bind complete file sets.
The Foundation collector checks authority identities, actual tool processes,
qualified coverage, all negative batches and original public outcomes before
and after collection. Raster controls require the specified changes to the
frozen opaque RGB8 original. Missing tools/rules, changed bytes or failed
controls cannot be repaired by changing report labels. Protocol fixtures test
collector rejection and do not replace independent PDF/tool qualification.

Original qualification, actual development observations, RED/GREEN logs and
independent review closures are retained in [T75 delivery evidence](T75-delivery/README.md).
Final certification requires Ubuntu 24.04/Linux x86-64 JDK 8/11/17/21 with both
`IN_PROCESS` and `HARDENED_WORKER` Native execution. Windows x86-64 and macOS
x86-64/arm64 remain uncertified under ADR-0040.

## Retained boundaries and history

The contract preserves execution order without inventing whitespace, layout,
bidi order or OCR. Unsupported font/relationship shapes remain explicit in
the public guide and independently qualified profile; safe rejection alone
is not evidence for a required successful case. The successful structure and
font/CMap cases above resolve the two former limitation blockers. Query bounds
compose with the shared Workflow Resource Policy and opt-in Worker isolation.
Image/resource extraction, encryption and later capability slices are outside
this profile.

The complete original 2026-09-02 implementation/syntax-only record is preserved
byte-for-byte as [historical T13 evidence](T75-delivery/historical-T13-text-logical-structure.md),
with its original identity in the adjacent JSON record. Its experimental state,
old unsupported boundaries and historical validation counts describe that
prior implementation. The original qpdf syntax record remains intact. Clean-room
inputs and acceptance-only tools/resources are recorded in [PROVENANCE.md](../../PROVENANCE.md).

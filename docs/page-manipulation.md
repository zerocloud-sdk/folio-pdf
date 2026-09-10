# Page manipulation, merge and split

This is the Foundation page mapping contract delivered by T72 for
`document.page.manipulate-merge-split`. The capability is `compatible` for the
declared Ubuntu 24.04/Linux x86-64 JDK profiles. Candidate and environment claims
come only from the current Foundation Evidence inventory; the contract and local
development tests cannot certify a changed candidate.

The Native Interface retains its six version-1 Commands: `InsertBlankPage`,
`RemovePages`, `MovePages`, `CopyPages`, `MergeDocuments`, and `SplitDocument`.
Ranges are one-based and inclusive. Move positions refer to the sequence after
removing the selection; copy positions refer to the original sequence. Existing
preserve-or-reject rules, collision handling and failure codes remain in force.

## Frozen Migration Facade subset

The existing `PdfDocument` constructors remain available. An additional
constructor accepts `Map<String, PdfReader> sources`, `String primarySource`,
and `Map<String, PdfWriter> targets`. It copies both maps in iteration order.
Nonempty Sources require an explicitly selected primary Source; empty Sources
use a null primary name. Every Source and Target is declared before the Native
Workflow starts. Use an insertion-ordered map when declaration order matters.

The page members are `addNewPage()`, `addNewPage(int)`, `getPage(int)`,
`removePage(int)`, `removePages(int, int)`, `movePage(int, int)`,
`movePages(int, int, int)`, and `copyPages(int, int, int)`. Copy returns the
new page handles in their insertion order. `PdfPage.getPdfObject()` exposes the
existing validated Values view. A page handle retains the identity of its page
through insert, move and copy; it never silently follows a changed page number.
Existing boxes, rotation, resources, content and safely preservable attached
data are handled by the Native Commands. The inserted blank page uses the
Native library-default geometry.

`PdfDocument.getMerger()` returns a `kernel.utils.PdfMerger` abstract view.
`merge(String... sourceNames)` selects complete, declared non-primary Sources
in call argument order and returns the merger. Repeated calls may select a
previously used Source. `close()` closes the owning document. Its protected
owner constructor exists only as the subclass hook used by document-owned views.

`PdfDocument.getSplitter()` returns a `kernel.utils.PdfSplitter` abstract view.
`extractPageRanges(String[] targetNames, PageRange[] ranges)` accepts parallel
arrays using the existing Native `PageRange`. All declared Targets must appear
exactly once. The operation creates the complete product group in one Native
Workflow; document close publishes that group. Subsequent Document Commands
retain `COMMAND_REJECTED`; queries remain available until close. Callers reopen
the published products independently. Its protected owner constructor likewise
supports only a document-bound subclass.

`PdfDocument.getPublicationReceipts()` returns the actual immutable Native
receipt list after close, including on failed publication. It is unavailable
before close. Receipts follow Target declaration order, even when split ranges
were selected in another order. Publication failures retain the original
`DocumentFailure` as the `PdfException` cause, including partial-stream output
diagnostics and `COMMITTED`, `FAILED`, and `NOT_ATTEMPTED` receipts.

These are explicit source adaptations of the Reference Suite page, merger and
splitter APIs. Cross-document live-page copying, late Source registration and
individually closeable split-document results are outside this declared subset.
The public views expose operations, not Native Session callbacks or backend
objects. All operations run through the public Native Workflow in `IN_PROCESS`.

## Ownership and lifetime

Construction validates all declarations and reader ownership before claiming
any reader. Each reader transfers its validated private snapshot once; duplicate
reader ownership is rejected. Closing a claimed reader does not release the
document-owned snapshot. The document accounts for the aggregate retained
snapshot storage and releases every snapshot on close. Caller input and output
streams remain caller-owned. Writer construction and document operations do not
truncate Path Targets before publication.

The document and its utility views are confined to the creating thread. One
Native Session owns all active page and Values views until document close.
Opening that Session materializes any queued blank pages and their handles
before page mutation; page operations never replace the Session. Removed-page
handles retain Native detached-object semantics. Publication happens once;
repeating close does not retry a failed publication.

Unchecked Java argument/state errors remain distinct from Native failures.
Ranges, positions, preservation, split selection and terminal-command failures
retain Native codes. The Facade does not add rollback promises beyond the
Native Commands, including merge's documented possible mutation on a metadata
input/output failure.

## Public API references

Only public API documentation is used for the source adaptations:

- [PdfDocument](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfDocument.html)
- [PdfPage](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfPage.html)
- [PdfMerger](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/utils/PdfMerger.html)
- [PdfSplitter](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/utils/PdfSplitter.html)

## Certification boundary

The frozen T10 profile produces edited, merged, left-split, and right-split PDFs
through both the Native Interface and this Facade. Separate qpdf syntax,
qualified pdfcpu/Arlington standards, public Native reopen semantic, and
PDFium/ImageMagick visual chains bind every exact product. Native contracts run
in IN_PROCESS and HARDENED_WORKER for JDK 8, 11, 17, and 21; Facade behavior is
always recorded as IN_PROCESS. Missing tools or rules, changed profile hashes,
wrong product order, and a one-pixel raster change cannot produce PASS. See the
[T10 certification contract](t10-certification.md) and current
[Foundation Evidence inventory](../capabilities/foundation-evidence.yaml).

This scope does not certify Windows or macOS. Foundation-wide readiness remains
NOT READY while unrelated obligations are incomplete.

# Metadata, navigation and attachments

This is the T73 Foundation mapping contract for
`document.metadata.outlines-destinations-attachments`. Compatibility requires
the independent [T11 certification contract](t11-certification.md) and current
candidate-bound Foundation evidence. The Native implementation and local tests
alone do not certify a candidate.

## Frozen Migration Facade mappings

The four-argument `PdfDocument(Map<String, PdfReader>, String,
Map<String, PdfWriter>, net.zerocloud.pdf.PdfVersion)` constructor explicitly
selects the output version for every declared Target. Attachment certification
uses PDF 2.0 for the standard `AFRelationship` entry. The existing constructors
retain the Native PDF 1.7 default; no implicit version upgrade occurs. This
constructor exposes only the existing version choice, not security policy.

`PdfDocument.getDocumentInfo()` returns a document-owned `PdfDocumentInfo` view.
The view supplies paired getters and fluent setters for Title, Author, Subject,
Keywords, Creator and Producer, plus `getTrapped()` / `setTrapped(PdfName)`.
`getMoreInfo(String)` reads a text entry; `setMoreInfo(String, String)` and
`setMoreInfo(Map<String, String>)` update text entries, with null values selecting
removal. `addCreationDate()` and `addModDate()` write the current date and time
with the system offset in ISO 32000 PDF date syntax and return the same view.
Callers can still pass an explicit PDF date string to `setMoreInfo`. Text is
written as ASCII or BOM-prefixed UTF-16BE and read using the existing
project-owned PDF text-string interpretation.

`PdfDocumentInfo.getEntries()` returns the immutable, detached Native
`net.zerocloud.pdf.PdfDictionary`. `updateEntries(Map<String, ? extends PdfValue>,
List<String>)` applies replacements and removals in one validated Native
Command. It supports the full Native detached-value contract, including
unknown arrays and dictionaries; streams and indirect references are rejected.
The maps and removal lists are copied before submission. Unnamed entries remain
unchanged. Non-text values are available through `getEntries()`; the text
getter returns null for an absent or non-string entry.

The remaining mappings on `PdfDocument` use detached, project-owned values:

| Member | Contract |
| --- | --- |
| `getXmpMetadata()` | Reads at most 64 MiB; returns null if absent. |
| `getXmpMetadata(long)` | Reads a packet under an explicit decoded-byte bound. |
| `setXmpMetadata(byte[])` | Copies and validates the complete packet before mutation. |
| `getNamedDestinations(int)` | Immutable `Map<String, PageDestination>` under an entry bound. |
| `addNamedDestination(String, PageDestination)` | Creates or replaces one named target. |
| `setNamedDestinations(Map<String, PageDestination>, List<String>)` | Applies one atomic replacement/removal Command. |
| `getOutlines(int)` | Immutable ordered `List<OutlineItem>` under a total-item bound. |
| `setOutlines(List<OutlineItem>)` | Replaces the complete tree; an empty list removes it. |
| `addFileAttachment(EmbeddedFile)` | Creates or replaces an embedded file by its declared name. |
| `getFileAttachments(int)` | Immutable ordered `List<EmbeddedFileSummary>` under an entry bound. |
| `getFileAttachment(String, long)` | `Optional<EmbeddedFileData>` with bounded decoded content and checksums. |

The explicit adaptations replace live reference outline and file-specification
graphs with `OutlineItem`, `PageDestination` and `EmbeddedFile`. All eight
Native destination styles and their exact nullable operands are available.
Information values, packet bytes, navigation trees and attachment observations
are detached and remain usable after document close. The document and Info
view are confined to their creating thread and expire at close.

## Preservation, errors and ownership

All mappings use the same document-owned Native Workflow as page and PDF Value
operations, with actual execution profile `IN_PROCESS`. Queries observe earlier
Commands and queued pages. Read-only mutations and closed/wrong-thread view use
throw `IllegalStateException`; invalid Java arguments remain unchecked. Native
operational failures retain their `DocumentFailure` cause, stable code,
capability and safe diagnostic in `PdfException`.

Info updates preserve unnamed supported entries. XMP preserves the exact
approved packet and safe unknown stream entries; it does not synchronize Info
automatically. XML parsing disables DOCTYPE, external entities and XInclude.
Inert unknown XML remains packet data. The packet command retains its 64 MiB
bound. Per-query metadata bounds compose with the Workflow Resource Policy.

Outlines retain the Native all-open write contract. Named destinations sort by
unsigned encoded key bytes. Reorder and copy follow the original pages; merge
renames destination and attachment collisions with the deterministic `-N`
suffix and rewrites named outline references. Split filters destinations and
outline branches by surviving pages and copies attachment data into each
product. Orphaning removals fail before mutation with `DESTINATION_CONFLICT`.
Page preflight failures retain the page capability attribution. Unknown content
is preserved when safe or rejected; Existing Signatures authorize no metadata
Command. The existing publication, terminal split, and page-operation failure
contracts remain applicable.

Readers retain their existing private Source snapshots, and caller streams
remain caller-owned. Publication occurs at close and exposes the actual Native
Publication Receipts in Target declaration order. Rejected Commands make no
partial metadata change; publication has the existing per-Target guarantees.

## Public API references

The source adaptations use the public 7.2.6 API documentation:
[PdfDocument](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfDocument.html),
[PdfDocumentInfo](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfDocumentInfo.html),
[PdfOutline](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfOutline.html),
and [PdfFileSpec](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/filespec/PdfFileSpec.html).
During review remediation, public iText 7.2.5 `PdfDocumentInfo` source was
briefly inspected only to confirm that the two public date convenience methods
use the current time. No implementation code was copied; Folio's formatting was
authored independently from ISO 32000 PDF date syntax and the public API
contract.
The Facade Surface Manifest records the exact adapted signatures and generic
and exception contracts. These mappings do not introduce a backend or external
XML dependency into the public interface.

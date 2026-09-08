# PDF Values and Document Patches

`document.value.inspect-patch` inspects and changes the nine PDF Value kinds:
null, boolean, number, byte string, name, array, dictionary, stream and indirect
reference. The Native Interface owns these values and their validation. The
Migration Facade maps the frozen kernel subset listed in the
[Facade Surface](generated/facade-surface.md); both interfaces use the same
Document Workflow engine.

The #71 implementation and artifact checks are development observations.
Current certification state is recorded in the [T09 profile](../capabilities/evidence/T09-document-value-inspection-patch.md)
and [Foundation readiness](generated/foundation-readiness.md). A successful
unit test or jar surface check does not certify an environment.

## Native inspection and changes

Inspect a Session's Catalog through `DocumentRootReference` and `InspectObject`.
`PdfInspectionLimits` bounds the number of values traversed and cumulative
stream bytes decoded by that inspection. A dictionary lookup returns an
indirect reference without implicitly following it; inspect that reference
explicitly when its body is needed.

```java
import java.nio.file.Paths;
import net.zerocloud.pdf.*;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;

WorkflowRequest request = WorkflowRequest.builder()
        .source("input", DocumentSource.path(Paths.get("input.pdf")))
        .primarySource("input")
        .target("output", PublicationTarget.path(Paths.get("output.pdf")))
        .saveMode(SaveMode.REWRITE)
        .build();

WorkflowOutcome<Void> outcome = new DocumentWorkflow().execute(request, session -> {
    ObjectReference root = session.query(DocumentRootReference.INSTANCE);
    PdfDictionary catalog = (PdfDictionary) session.query(InspectObject.version1(
            root, PdfInspectionLimits.of(1000, 1024 * 1024)));
    PdfValue previous = catalog.get(PdfName.of("Counter"));
    PdfValuePath values = PdfValuePath.root(root).dictionaryEntry(PdfName.of("Values"));
    session.execute(DocumentPatch.builder()
            .setDictionaryEntry(root, PdfName.of("Counter"), PdfNumber.of(7))
            .setDictionaryEntry(root, PdfName.of("Values"),
                    PdfArray.of(PdfNull.INSTANCE, PdfBoolean.of(false)))
            .insertArrayElement(values, 1, PdfString.of(new byte[] {0, 40, 92}))
            .setArrayElement(values, 2, PdfBoolean.of(true))
            .removeArrayElement(values, 0)
            .build());
    return null;
});
```

The complete operation set is:

| Operation | Contract |
| --- | --- |
| `setDictionaryEntry` | Inserts or replaces one dictionary entry. |
| `removeDictionaryEntry` | Removes an entry; absence is a no-op. |
| `setArrayElement` | Replaces an element at `0..size-1`. |
| `insertArrayElement` | Inserts at `0..size`, including append. |
| `removeArrayElement` | Removes an element at `0..size-1`. |
| `replaceValue` | Replaces a dictionary entry, array element or the direct body of an indirect object. |
| `replaceStreamData` | Replaces decoded data with explicit `UNFILTERED` or `FLATE` encoding. |

A `PdfValuePath` starts with a Session Object Reference and appends dictionary
names or zero-based array positions. Operations resolve paths in declaration
order, so later operations can address values inserted earlier in the same
Patch. Invalid targets, indices or introduced cycles reject the complete Patch.
Dictionary-only version-1 requests retain their original representation; the
expanded operations use version 2 in both Native execution profiles.

An Object Reference remains stable within its Session, including when its
value changes. Its equality has no meaning as a cross-document object number.
Foreign references fail with `OBJECT_REFERENCE_OWNERSHIP_INVALID`. References
may be values in dictionaries and arrays. A bare indirect reference cannot be
the entire body of another indirect object; that invalid placement fails with
`PATCH_VALUE_REJECTED`. Existing valid graph links and cycles are preserved;
new cycles fail with `PATCH_CYCLE_REJECTED`.

## Streams and preservation

`PdfStream.readBytes()` returns decoded data under the inspection budget.
`replaceStreamData(path, bytes, encoding)` copies the supplied bytes and changes
the encoded content and engine-owned metadata atomically. It preserves the
stream's indirect identity, aliases and unrelated attributes. Length, Filter,
DecodeParms, F, FFilter, FDecodeParms and DL cannot be edited directly, including
through their nested values or aliases. External-file streams are never resolved.

Untouched encoded streams keep their encoded bytes and attributes, including
unknown filter names and private resources. Asking to decode an unknown encoding
fails with `QUERY_FAILED`; that does not prevent a later safe unrelated Patch.
Source bytes are unchanged. In unsigned INCREMENTAL mode, the Source remains the
prefix of the published revision, and changed objects and existing aliases are
updated without losing unrelated indirect objects. REWRITE preserves unrelated
content and resource declarations without promising byte-identical serialization.

Low-level changes must leave the Catalog and page tree valid. Page Contents
must be absent, a stream or an array of streams. Each Page must retain a valid
Resources dictionary, directly or through page-tree inheritance. Malformed
Contents or removal of the last effective Resources rejects the whole Patch
with `COMMAND_REJECTED`, preserving earlier state for continued publication.
Version,
Extensions and password-security state remain owned by their explicit output
policies, including when reached through aliases. Existing Signatures grant no
Document Patch authority. These invariants do not replace successful ordinary
value, container or stream changes, and do not add downstream Forms, Trust or
page-manipulation semantics.

## Failure, lifetime and ownership

An ordinary rejected Patch restores the earlier state before control returns.
A caller can catch the failure, inspect the earlier values, apply a valid Patch,
and publish. The `DocumentFailure` carries a stable code, capability and safe
diagnostic, without backend exceptions. Inspection-limit failures are also
recoverable; a new explicit inspection has a new budget.

Workflow Resource Policy limits apply in addition to inspection limits. Resource
exhaustion is terminal even if caught, as documented in the
[hostile-input policy](hostile-input-policy.md). Recursive backend materialization
has a finite 256-level bound, checked before conversion; exceeding it reports
`NESTING_LIMIT_EXCEEDED`. Failed restoration caused by actual stream I/O also
terminates the Session. A terminal Session does not publish its working state.

Scalar values are detached from their backing document. Container and stream
views are confined to the Native callback and expire after its Session closes;
access produces `PDF_VALUE_VIEW_EXPIRED`. Module-opened resources close at the
end of their owner. Caller input/output streams and channels remain open.
Successful output publication flushes a caller output. Failed output can be
partial; consult the actual `PublicationReceipt`, including its status and
`isPartialOutputPossible()`. Native Path publication retains its transactional
replacement contract.

## Migration Facade

The mapped `PdfDocument` owns a single Native IN_PROCESS Session from the first
value operation until `close()`. It is confined to its creating thread. It uses
REWRITE publication. Native execution profiles, resource-policy configuration,
explicit save modes and Publication Receipt APIs remain Native controls.
The Facade does not claim HARDENED_WORKER execution.

```java
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;

try (PdfReader reader = new PdfReader("input.pdf");
        PdfDocument document = new PdfDocument(reader, new PdfWriter("output.pdf"))) {
    PdfDictionary catalog = document.getCatalog().getPdfObject();
    catalog.put(new PdfName("Counter"), new PdfNumber(2));
    ((PdfNumber) catalog.get(new PdfName("Counter"))).setValue(7);
    catalog.put(new PdfName("PrivateData"), new PdfStream(new byte[] {1, 2, 3}));
    ((PdfStream) catalog.get(new PdfName("PrivateData"))).setData(new byte[] {4, 5});
}
```

The complete subset includes the nine `PdfObject` kind constants and predicates;
`PdfNull`, `PdfBoolean`, `PdfNumber`, `PdfString`, `PdfName`, `PdfArray`,
`PdfDictionary`, `PdfStream`, `PdfIndirectReference`, `PdfCatalog`; and the mapped
Reader, Writer and Document lifecycle. Constants follow the documented 7.2.6
values, including `STREAM = 9` and `STRING = 10`. `PdfStream` inherits the mapped
dictionary members. Exact constructors, return types, generics and exceptions
are enumerated in the Facade Surface, including override declarations.

- `get` dereferences by default; `get(index/name, false)` retains the stored
  reference. `getIndirectReference()` exposes an inspected body's opaque identity.
- Detached containers copy their graph into Native values when inserted. Later
  detached changes do not change a previously inserted copy. Shared acyclic
  children are accepted. Java containment cycles fail immediately with a safe
  `PdfException` beginning `PATCH_CYCLE_REJECTED`; this local failure has no
  fabricated Native cause.
- Attached mutations validate immediately through Native Patches. Held direct
  numeric views follow successful changes at their location. Array insertion
  and removal rebase existing element locations. A replaced direct number,
  including old stream Length after `setData`, becomes detached. Removed
  containers can be read during their Session but can no longer mutate a
  replacement at their old location. `keySet()` is an immutable snapshot.
- Each reference inspection uses 100,000 traversed values and 64 MiB cumulative
  decoded stream bytes. Closed container/stream reads preserve the real Native
  `PDF_VALUE_VIEW_EXPIRED` cause. Closed dereferencing and live mutation reject
  with `IllegalStateException`; scalar reads and opaque reference equality remain
  available. A terminal Native failure takes precedence over ordinary expiry.
- `PdfReader` accepts a filename or caller input stream. Construction validates
  and retains a bounded snapshot in a private temporary directory; the original
  Path is closed before return. One Document takes ownership of that snapshot.
  An unclaimed Reader releases it on close; a claimed Reader leaves it to its
  Document. Closing a Reader never closes caller input.
- `PdfWriter` accepts a filename or caller output stream. Construction does not
  truncate a Path or write caller output. Document close waits for the actual
  Native publication result and releases the Source snapshot. A mapped Native
  failure retains its actual `DocumentFailure` cause and receipts. Repeating
  close does not retry a failed publication.

`PdfNumber(int/double)` and its setters accept finite values; non-finite doubles
reject with `IllegalArgumentException`. `intValue()` uses Java double-to-int
conversion. Native exact decimals remain available through the Native Interface.
`PdfString(byte[])` and `getValueBytes()` copy exact bytes. `PdfString(String)`
encodes printable ASCII plus TAB/LF/CR directly and other well-formed text as
BOM-prefixed UTF-16BE. `getValue()` decodes PDFDocEncoding (ISO 32000-1 Annex D.3),
UTF-16BE with its BOM, or PDF 2.0 UTF-8 with its BOM. Undefined codes and malformed
Unicode bytes become U+FFFD in text only. Unpaired Java surrogates reject before
mutation. These explicit project rules do not claim undocumented reference-library
default encoding or map the separate `toUnicodeString()` API.

Use exactly one Migration Facade artifact. Preview contains the Stable mappings
plus any declared Preview additions. The actual jar contract checks every public
mapped type, constructor, method and constant, and rejects mixing the two
artifacts in either classpath order. No unsupported Stable placeholder member is
included.

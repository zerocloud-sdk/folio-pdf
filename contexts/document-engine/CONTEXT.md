# Document Engine

This context owns the logical PDF document, its pages and resources, and the lifecycle by which a document is read, changed, and published.

## Language

**Document Workflow**:
The reusable Native Interface entry point that executes one isolated document transaction and owns its resource, error, and publication lifecycle.
_Avoid_: document service, PDFBox wrapper

**Document Session**:
The thread-confined interaction scope supplied inside a Document Workflow. Queries observe earlier changes in the same session, and the session is invalid after the workflow returns.
_Avoid_: PDF document object, backend document

**PDF Value**:
A backend-neutral representation of a low-level PDF null, boolean, number, string, name, array, dictionary, stream, or indirect reference.
_Avoid_: COS object, PdfObject, backend value

**Object Reference**:
A stable, backend-neutral identity for one indirect PDF object inside a Document Session. It is not a live mutable handle and cannot be dereferenced after its Session ends.
_Avoid_: object pointer, COS reference

**Document Patch**:
An ordered request to inspect or change low-level PDF Values through validation owned by the Document Engine.
_Avoid_: direct object mutation, COS edit

**Document Command**:
A versioned, project-defined request that changes the current Document Session. Commands are ordered and batchable and never contain caller code or backend objects.
_Avoid_: callback, remote method, custom command class

**Document Query**:
A versioned, project-defined request for information from the current Document Session. A query is an ordering barrier that observes every earlier Document Command.
_Avoid_: live view, direct backend query

**Save Mode**:
The explicit publication strategy for a changed document: `REWRITE` serializes a replacement document, while `INCREMENTAL` appends a new revision under stricter preservation rules.
_Avoid_: auto-save, smart save

**Publication Receipt**:
The result of publishing one workflow's outputs, recording whether each destination was committed, failed, or not attempted. It does not imply a transaction spanning multiple destinations.
_Avoid_: transaction result, global commit

**Hardened Worker Profile**:
The process-isolated execution profile required for hostile multi-tenant input, with enforced memory, CPU, time, temporary-storage, and network limits.
_Avoid_: safe mode, background thread

**Document Failure**:
A checked operational failure with a stable code, capability identifier, and safe diagnostics. It never exposes a backend or worker implementation exception as its public contract.
_Avoid_: PDFBox exception, generic runtime exception

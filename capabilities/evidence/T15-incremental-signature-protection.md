# T15 incremental publication and Existing Signature protection evidence

Status: `experimental`

Capability: `document.incremental-signature.protect`

Acceptance Profile: `T15-incremental-signature-protection`

Release train: `0.1.0-SNAPSHOT`

T15 enables explicit incremental publication through the public Document
Workflow and applies a conservative version-1 Existing Signature and DocMDP
policy. It recognizes and protects signature structures but does not create or
cryptographically validate signatures.

## Implementation evidence

- `IncrementalSignatureWorkflowTest` drives every behavior through
  `DocumentWorkflow.execute`. It proves unsigned append publication from Path,
  stream, channel, and bounded-byte Sources; unchanged complete Source prefixes;
  non-empty appended revisions; public reopen; ordered Path/stream receipts;
  caller resource ownership; the missing-Source and closed-command failures;
  and unchanged Targets on every pre-publication refusal.
- Project-owned deterministic structural fixtures prove target-free signed
  queries, signed-REWRITE refusal, ordinary-signature default denial, DocMDP
  P=1/P=2 denial, the sole coherent P=3 non-Widget annotation allowance,
  Widget and other-command rejection, multiple-signature intersection, and
  safe handling of invalid ranges, cyclic fields, contradictory permission
  references, indirect critical DocMDP values, unsupported transforms, and
  bounded policy traversal exhaustion. The fixtures contain no third-party
  signed bytes and make no cryptographic-validity claim.
- `PdfBoxIncrementalCommandPolicy` explicitly classifies every current command
  family. `SplitDocument` and caller-defined or future unclassified commands
  are rejected before mutation; signed P=3 admits only the footprint-checked
  `UpdateAnnotations` command.
- Staging computes a Source SHA-256 and length, requires the complete staged
  prefix to match and the appended suffix to be non-empty, reopens the staged
  product, and then reuses T03's ordered publication and receipt machinery.
- `WorkflowTransactionContractTest` retains cancellation, deadline, staging,
  validation, publication, and safe diagnostic contracts and proves that an
  incremental create request fails before caller work.
- The authoritative policy guide, ADR-0037, domain glossary, research note,
  public Javadoc, and provenance record document the command matrix, permission
  proof, stable failures, parsed-graph boundary, and downstream scope.
- The repository acceptance command creates an original and appended product
  through the public workflow, verifies the exact prefix, non-empty suffix,
  committed receipt and public reopen, and submits both unmodified products to
  pinned qpdf 12.4.0. Two fresh command runs reproduce their evidence records
  and revision-ID-neutral hashes.

## Evidence and status boundary

The separate T15 qpdf record supplies one passing independent syntax chain.
It establishes parseability only, not standards conformance, semantic parity,
visual parity, signature authenticity, certificate trust, or permission-digest
validity. Mandatory standards, semantic, and visual Acceptance Evidence remain
absent. The T09 Dependency Gate is open because that prerequisite remains
`experimental`, and T06 remains a promotion gate. T15 therefore remains
`experimental`, with no compatible or certified-platform claim.

T15's parsed COS inspection cannot recover raw lexical distinctions normalized
by PDFBox, including duplicate dictionary keys and hexadecimal-versus-literal
string delimiters. Comprehensive raw hostile-input enforcement remains T20;
Forms/signature creation remain T34+; cryptographic signature and trust
validation remain T38+.

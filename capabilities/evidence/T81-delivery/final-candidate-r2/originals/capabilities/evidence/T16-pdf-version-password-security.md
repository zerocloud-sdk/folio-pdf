# T16 PDF version and password security evidence

Status: `experimental`

Capability: `document.version-password-security`

Acceptance Profile: `T16-pdf-version-password-security`

Release train: `0.1.0-SNAPSHOT`

T16 makes PDF version and Standard-handler password security explicit at the
public Document Workflow seam. It adds strict version declarations, secure
AES-256 publication defaults, destroyable credentials, detached authority and
permission observations, legacy input support, request-scoped obsolete output,
and pre-operation authorization.

## Implementation evidence

- `PdfVersionPasswordSecurityWorkflowTest` drives the contract through
  `DocumentWorkflow.execute`. Project-authored fixtures cover exact headers
  from PDF 1.0 through 1.7 and 2.0, catalog precedence, malformed and
  unsupported declarations, default and explicit output markers, and public
  reopen without backend types.
- Secure products are serialized as Standard-handler V=5, R=6, AESV3 with
  256-bit keys. Tests inspect the detached serialized dictionary and reopen
  through user and owner credentials. PDF 1.7 adds the qualified ADBE Extension
  Level 8 declaration; PDF 2.0 uses its normative R6 profile and requires
  permission bit 10.
- Deterministic clean-room fixtures implement the public Standard algorithms
  for V=1/R=2 and R=3 RC4-40, V=2/R=3 RC4-128, and V=4/R=4 RC4-128 and AES-128.
  Unknown profiles and crypt filters, malformed entries, inconsistent R6
  `Perms`, missing/wrong/destroyed credentials, and unsafe output policies fail
  before caller work and publication with stable T16 failures.
- Every standard permission bit round-trips. The closed authorization map
  checks assembly, extraction, general modification, annotation modification,
  and their conservative intersections before the corresponding operation.
  Owner authority is unrestricted; user authority receives only the declared
  permission word.
- `PasswordCredential` defensively copies mutable input, owns an idempotently
  destroyable `char[]`, supplies execution-local copies, and is never closed by
  the workflow. Consumer tests cover caller mutation, all Source kinds,
  resource ownership, destruction, safe rendering, and cleanup-sensitive
  success and failure paths.
- Protected REWRITE requires separately proven owner authority and an explicit
  output security policy, so protection is never silently removed or weakened.
  Protected INCREMENTAL publication preserves the existing encryption and
  Source prefix; the complete T15 Existing Signature and DocMDP regression
  suite remains the independent signature-policy guard.
- Public API and jar integration tests retain project/JDK-only signatures, the
  Java 8 class-file target, module name, notices, and an unbundled PDFBox 3.0.8
  runtime dependency.
- The repository acceptance command creates one PDF 1.7 Extension Level 8 and
  one PDF 2.0 AES-256 product through public workflows, verifies user and owner
  reopen, and sends both unchanged encrypted products to pinned qpdf 12.4.0.
  The temporary password-file path and every password value are redacted and
  the temporary file is deleted. Repeat runs reproduce a non-secret security
  observation hash because secure encryption entries, identifiers, and
  ciphertext are intentionally randomized.

## Evidence and status boundary

The separate T16 qpdf record supplies one passing independent syntax chain.
It observes PDF versions, R=6, the permission word, AESv3 stream/string/file
methods, and parseability, but it is not standards, semantic, visual, security,
or interoperability proof. The observation hash intentionally omits
credential-derived entries, file identifiers, and ciphertext; the generated
encrypted artifacts remain the inspected inputs and are expected to differ
byte-for-byte between runs.

Mandatory standards, semantic, and visual Acceptance Evidence remain absent.
The `document.value.inspect-patch` Dependency Gate is open because that
prerequisite remains `experimental`, and T06 remains a promotion gate. T16
therefore remains `experimental`, with no compatible or certified-platform
claim.

The PDF 1.7 AES-256 path is a qualified industry convention using ADBE
Extension Level 8; no public normative Adobe supplement establishing that
profile was found, so no PDF 1.7 standards-conformance claim is made. PDF 2.0
R6 is the fully normative secure-output path. Public-key encryption, FIPS,
signature creation/trust, broad hostile-input enforcement, worker isolation,
metadata-clear output, and attachment-only encryption remain outside T16.

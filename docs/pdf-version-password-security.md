# PDF version and password security

This guide is the authoritative English contract for T16 version 1. The single
capability identity is `document.version-password-security`, and every behavior
is reached through `DocumentWorkflow.execute`. Public values are project-owned
and detached from PDFBox.

## Native Interface

`DocumentVersion.INSTANCE` returns `PdfVersionInfo`: the exact header version,
the optional catalog `/Version`, and the effective version. `DocumentSecurity.INSTANCE`
returns `PasswordSecurityInfo`: whether the Source is password protected, its
algorithm and Standard security-handler revision, encryption scope, declared
and effective permissions, and the authority established by the supplied
credential.

Every Path, bounded stream, bounded channel, or bounded byte `DocumentSource`
may be copied with `withCredential(PasswordCredential)`. The credential belongs
to the caller; the workflow neither exposes nor closes it.

Every declared non-primary Source is read, authenticated, strictly classified,
and copied into a workflow-owned temporary snapshot before caller work starts.
This makes one-shot streams and channels safe to use later as merge donors while
preserving caller ownership. Snapshot files and their execution-local credential
copies are removed on every exit.

Published-product choices live in an immutable `PdfOutputPolicy`. For example:

```java
char[] ownerCharacters = obtainOwnerPassword();
char[] userCharacters = obtainUserPassword();
try (PasswordCredential owner = PasswordCredential.of(ownerCharacters);
        PasswordCredential user = PasswordCredential.of(userCharacters)) {
    Arrays.fill(ownerCharacters, '\0');
    Arrays.fill(userCharacters, '\0');

    PasswordSecurityPolicy security = PasswordSecurityPolicy.builder(owner, user)
            .permissions(DocumentPermissions.builder()
                    .allowPrinting(true)
                    .allowContentExtraction(true)
                    .build())
            .build();

    WorkflowRequest request = WorkflowRequest.builder()
            .target("product", PublicationTarget.path(target))
            .saveMode(SaveMode.REWRITE)
            .outputPolicy(PdfOutputPolicy.version(PdfVersion.PDF_1_7)
                    .withPasswordSecurity(security))
            .build();
    new DocumentWorkflow().execute(request, session -> {
        session.execute(AddBlankPage.INSTANCE);
        return null;
    });
}
```

The example zeroes the caller array after construction and closes each
credential only after the workflow finishes. Application code should use the
same lifetime pattern.

## Version policy

Input inspection searches the first 1,024 bytes for an exact `%PDF-M.m` header.
A PDF 1.x marker must begin at byte zero; PDF 2.0 may follow a bounded preamble,
with file offsets interpreted from the marker. The tuple must be one of PDF 1.0
through 1.7 or PDF 2.0. A catalog `/Version`,
when present, must be a direct or single-resolved PDF name containing one of the
same exact tuples. The effective version is the later of the header and catalog
declarations. A lower catalog declaration does not downgrade the header.

A Source with no PDF marker retains the general `SOURCE_READ_FAILED` contract.
Malformed marker syntax, wrong catalog types, and unsupported tuples never
inherit PDFBox's repair or floating-point fallback; they fail before caller
work. This policy is intentionally stricter than a lenient reader.

Every new or rewritten product defaults to an exact PDF 1.7 header when no
output policy is supplied. `PdfOutputPolicy.version` supports explicit PDF 1.7
and PDF 2.0 only; the writer removes the redundant catalog version so header
and effective version agree. An incremental product preserves its Source
version. An explicit incremental version is accepted only when it equals the
Source effective version and never rewrites the header.

A PDF 2.0 Source cannot be safely relabelled as PDF 1.7 without proving every
retained feature. T16 performs no such downgrade proof, so PDF 2.0 REWRITE
requires an explicit PDF 2.0 output policy and otherwise fails before work. The
same rule is applied to every declared named Source so a later merge cannot
silently downgrade a donor.

## Password-security profiles

The Standard password-security handler is the only handler in T16. Secure
output defaults to AES-256 whenever a `PasswordSecurityPolicy` is present and
no algorithm is selected.

| Direction | Supported exact profiles |
| --- | --- |
| Secure output | V=5, R=6, 256-bit AESV3, Standard `StdCF`, all strings and streams encrypted, metadata encrypted. PDF 1.7 or PDF 2.0. |
| Legacy output | V=2/R=3 RC4-128 or V=4/R=4 AESV2-128, PDF 1.7 only, and only with request-scoped Legacy Security Mode. |
| Legacy input | V=1/R=2 or R=3 RC4-40; V=2/R=3 RC4-128; whole-document V=4/R=4 `StdCF` using V2-128 or AESV2-128, with metadata encrypted or the fixture-proven metadata exception. |
| Secure input | Whole-document V=5/R=6 AESV3-256 with metadata encrypted. PDF 2.0 also requires permission bit 10; PDF 1.7 requires a supported ADBE Extension Level 8 declaration. |

RC4-40 output is always rejected because PDFBox 3.0.8 cannot reliably select
its required R2/R3 revision. V=4 RC4 output and R=5 output are also excluded.
R=5 input is not silently treated as R=6. Public-key handlers, unknown
SubFilters, unknown crypt filters, non-`DocOpen` authorization events,
noncanonical authentication entries, contradictory RC4-40 revisions, malformed
permission words, and inconsistent R6 `Perms` values fail closed.

The minimum effective versions are PDF 1.1 for V=1/R=2, PDF 1.4 for R=3,
PDF 1.5 for V=4 crypt filters, PDF 1.6 for AESV2, and PDF 1.7 for AESV3.

When protected PDF 1.7 input carrying the exact project-owned ADBE Extension
Level 8 signal is explicitly rewritten as PDF 2.0 with AES-256, the obsolete
PDF 1.7 signal is removed. Unknown or extended ADBE state is not silently
deleted and instead fails the transition before caller work.

PDF 1.7 did not normatively define R=6. Folio PDF writes the established ADBE
Extension Level 8 declaration and labels this as a qualified industry
convention, not an ISO 32000-1 conformance claim. PDF 2.0 R=6 is the fully
normative secure-output choice.

## Credentials and authority

`PasswordCredential.of(char[])` immediately makes a defensive copy. Mutating
the caller array later cannot change it. `close()` is idempotent, overwrites the
owned array, and makes every later execution fail with `CREDENTIAL_DESTROYED`.
Each workflow makes and clears its own execution-local character copies; the
same live caller credential can be used by sequential requests.

Output owner and user credentials must both be non-empty and distinct. To avoid
silent encoding, SASLprep, and truncation equivalence in the backend, T16 output
accepts printable ASCII only, up to 127 characters for AES-256 and 32 for the
legacy profiles. Input retains the backend's profile-specific decoding so
supported existing documents remain readable. A protected legacy document
whose user password is empty still requires an explicitly supplied empty
credential; absence is never treated as authentication.

PDFBox's public loader and protection policy require immutable Java `String`
passwords. Folio PDF minimizes those conversions, drops the containing
documents, and clears every array it owns, but Java cannot guarantee erasure of
backend or JVM string copies. The contract is defensive ownership and bounded
lifetime, not physical secure erasure.

Successful authentication reports one of four authorities. Folio does not use
`AccessPermission.isOwnerPermission()` as proof because an unrestricted user
also satisfies it; when that ambiguity exists, Folio separately evaluates the
Standard-handler owner predicate against the execution-local credential:

- `NONE` for an unprotected Source;
- `USER` when the declared permission word restricts the supplied credential;
- `OWNER` when owner authentication is separately provable; or
- `UNRESTRICTED` when the permission word itself grants everything but separate
  owner proof was not established.

The last state is intentionally not promoted to `OWNER`; security-sensitive
owner-only publication therefore fails closed. Printable-ASCII owner
credentials produced by T16 are independently proven even when `/P` is
unrestricted; an unrestricted user remains `UNRESTRICTED`.

## Permissions

`DocumentPermissions` preserves the signed 32-bit Standard-handler `/P` word
and exposes all eight selectable bits. Its builder denies every optional user
permission by default; `unrestricted()` grants all eight. The flags describe
processor cooperation after decryption, not cryptographic DRM.

| Bit | Native Interface meaning |
| --- | --- |
| 3 | printing |
| 4 | general modification |
| 5 | copying or content extraction |
| 6 | annotation modification |
| 9 | filling existing forms |
| 10 | accessibility extraction |
| 11 | document assembly |
| 12 | faithful/high-quality printing |

PDF 2.0 deprecates restriction through bit 10, so a PDF 2.0 writer policy must
set it. Printing and form filling have no current Document Command; their bits
round-trip but T16 makes no execution claim for those operations.

Authentication and authorization are separate. Owner authority receives
unrestricted effective permissions. User authority is checked immediately
before every current operation according to this closed map:

| Operation | Required user permission |
| --- | --- |
| add, insert, remove, move, or copy pages | assembly |
| merge | assembly on the primary Source and extraction on every donor Source |
| split | extraction on the primary Source |
| replace outline tree | assembly |
| document information, XMP, named destinations, embedded files, and Actions mutation | general modification |
| update annotations | annotation modification |
| flatten annotations | general and annotation modification |
| `DocumentPatch` | general, annotation, and assembly permissions |
| text, structure, image, resource, object, metadata, attachment, annotation, Action, outline, or destination content queries | extraction |
| page count, document version/security, and reference-only root/page queries | successful authentication only |

The accessibility bit alone does not authorize a generic extraction query.
Unknown commands and queries already fail through the workflow's closed public
operation set rather than inheriting a permission accidentally.
`DocumentPatch` also rejects catalog `/Version` or `/Extensions` changes and any
reachable encryption-dictionary or trailer `/Encrypt` change under the T16
identity, regardless of owner authority.

## Encryption scope

`PasswordEncryptionScope` distinguishes all-content encryption, all content
except document-level metadata, and embedded-files-only encryption. The current
writer supports only `ALL_CONTENT`; either other output choice fails before
work or publication. Project-authored V=4/R=4 fixtures prove
`ALL_EXCEPT_METADATA` input after validating the global crypt filters and the
metadata-exception file-key derivation. Metadata-clear AES-256 input is not in
the version-1 allowlist because its R6 `Perms` agreement has not been
fixture-proven. Attachment-only input is likewise unclaimed and fails closed
because PDFBox 3.0.8 has no proven `EFF`-specific path.

## Protected publication and signatures

A protected Source with a Target cannot use `REWRITE` unless the opening
credential has proven `OWNER` authority and the request supplies a complete
explicit password-security output policy. This prevents implicit decryption,
plaintext publication, or accidental weakening. User and `UNRESTRICTED`
authority cannot rekey a Source.

An encrypted `INCREMENTAL` request may omit an output policy; the existing
encryption dictionary and credential remain in force and staged validation
reopens the appended revision with the Source credential. An explicit security
change is rejected in incremental mode. T15 still requires an unchanged Source
prefix, a non-empty appended revision, a supported command, and the intersection
of every Existing Signature permission. Password permissions never override
Signature Permission, and Signature Permission never overrides password
permissions.

A protected named donor may contribute to a published product only when the
effective output remains protected with an algorithm and scope at least as
strong as that donor. Plaintext output, a weaker algorithm, or narrower
encryption coverage fails before caller work. Donor extraction permission is
still checked at the merge command boundary.

## Stable failures

All entries use capability identity `document.version-password-security` and
safe fixed diagnostics. A pre-publication failure reports every declared
Target as `NOT_ATTEMPTED` and leaves existing Target bytes unchanged.

| Code | Meaning |
| --- | --- |
| `PDF_VERSION_INVALID` | a present header or catalog declaration is malformed or wrongly typed |
| `PDF_VERSION_UNSUPPORTED` | a valid version tuple or output transition is outside the supported set |
| `CREDENTIAL_REQUIRED` | a protected Source has no explicit credential |
| `CREDENTIAL_REJECTED` | the opening credential did not authenticate |
| `CREDENTIAL_DESTROYED` | a required caller credential was already closed |
| `PASSWORD_SECURITY_UNSUPPORTED` | an input profile, dictionary, scope, credential form, or output combination is unsupported |
| `PASSWORD_SECURITY_POLICY_REQUIRED` | protected rewrite omitted explicit replacement protection |
| `LEGACY_SECURITY_MODE_REQUIRED` | obsolete output was selected without the request-scoped opt-in |
| `DOCUMENT_PERMISSION_DENIED` | established authority does not permit the requested operation |

Failures never identify which credential would succeed and never contain
passwords, secret-derived values, document data, Source paths, backend
exceptions, or private security state.

## Scope boundary

T16 does not implement public-key encryption, FIPS validation, signature
creation or cryptographic trust, arbitrary encrypted-stream combinations,
comprehensive hostile-input enforcement, or Hardened Worker isolation. Those
remain T37, T38+, T20, and T21 work. The exact public standards and PDFBox
sources, unresolved PDF 1.7 Extension Level 8 provenance, and clean-room
fixture boundary are recorded in the T16 research note and `PROVENANCE.md`.

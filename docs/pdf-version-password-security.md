# PDF version and password security

This guide is the authoritative English contract for baseline password security
under #78. `document.version-password-security.baseline` is the certifiable
member of the aggregate `document.version-password-security`. Runtime failures
retain the aggregate identity. Every Native behavior is reached through
`DocumentWorkflow.execute`; public values are project-owned and backend-neutral.
The aggregate remains experimental until #79 (metadata-clear) and #80
(embedded-files-only) are separately complete. The [profile audit](research/T78-baseline-profile-audit.md)
and [certification contract](t78-certification.md) freeze the successful cases,
source-grounded exclusions, migration members and independent evidence.

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

The Standard password-security handler is the only baseline handler. Secure
output defaults to AES-256 whenever a `PasswordSecurityPolicy` is present and
no algorithm is selected.

| Direction | Supported exact profiles |
| --- | --- |
| Secure output | V=5, R=6, 256-bit AESV3, Standard `StdCF`, all strings and streams encrypted, metadata encrypted. PDF 1.7 or PDF 2.0. |
| Legacy output | V=1/R=2 or R=3 RC4-40, V=2/R=3 RC4-128 and V=4/R=4 AESV2-128; PDF 1.7 and request-scoped Legacy Security Mode only. R2 requires extended permission bits 9–12 set; otherwise R3 is selected. |
| Legacy input | V=1/R=2 or R=3 RC4-40; V=2/R=3 fixed 40/128 bits (omitted Length defaults to 40); all-content V=4/R=4 `StdCF` V2-128 or AESV2-128; AESV3 V=5/R=5 with a valid PDF 1.7 ADBE Level 3 declaration. Fixture-proven metadata-clear R4 input is preserved. |
| Secure input | All-content V=5/R=6 AESV3-256. PDF 1.7 requires ADBE Level 8. PDF 2.0 reader permission bit 10 is ignored for accessibility restrictions while the exact declared word is retained. |

The admitted crypt-filter arrangement uses `StdCF` for strings and streams,
optional EFF=`StdCF`, and absent/default or explicit `AuthEvent=DocOpen`.
Scalar `/Crypt` and first array `/Crypt` selectors with `Name=StdCF` are
admitted. AESV2 filter Length may be omitted; a present value is 16 bytes.
AESV3 filter Length is 32 bytes and is required for PDF 2.0. Global encryption
Length remains a bit count. Wrong types, scope selectors, duplicates, encrypted
`Perms` contradictions and malformed authentication entries fail closed.

New R3/40 output follows ISO 32000-1 Algorithm 3: hash the full MD5 digest for
50 rounds, then truncate the owner key. Inputs also admit the established
first-n-byte round convention. Independent owner checks distinguish these
constructions; qpdf's convention does not redefine normative output.

R5 and separate V4 RC4 output selectors are not required by the frozen API:
AES-256 output uses R6 and RC4-128 output uses V2/R3. Arbitrary intermediate
48–120-bit RC4 keys, public-key handlers, SubFilter, and custom/unknown
crypt-filter arrangements are outside this fixed baseline. These are explicit
profile boundaries grounded in the audit, not backend-error waivers. PDF 2.0
reader acceptance preserves deprecated legacy input representations; new
PDF 2.0 output remains R6 only.

The minimum effective versions are PDF 1.1 for V=1/R=2, PDF 1.4 for R=3,
PDF 1.5 for V=4 crypt filters, PDF 1.6 for AESV2, and PDF 1.7 for AESV3.

When protected PDF 1.7 input carrying the exact ADBE Extension Level 3 (R5)
or Level 8 (R6) signal is explicitly rewritten as PDF 2.0 with AES-256, the obsolete
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

Empty, equal and non-ASCII credentials are supported. Legacy input/output
maps each Java character U+0000–U+00FF to its exact byte and uses the first
32 bytes; unmappable characters are rejected without replacement aliases.
This byte bridge does not claim PDFDocEncoding. AES-256 R5/R6 input and R6
output use RFC 4013 SASLprep with Unicode 3.2 tables and the first 127 UTF-8
bytes, including a boundary that cuts a multibyte sequence. Stored output
strings reject prohibited/unassigned characters; input queries admit the
RFC-defined unassigned query case. Equivalent prepared/truncated credentials
have the equivalence required by their algorithm.

An empty prepared owner generates independent random owner authority for each
request. An empty user is a valid explicit opening credential. An absent
credential never authenticates a protected document. Equal owner/user values
report OWNER only because the owner predicate actually succeeds. Legacy and
R5/R6 credential handling runs before content decryption and authorization.

Backend protection policies require temporary immutable Java `String` values.
Folio minimizes their lifetime, charges preparation and retained key memory,
and clears arrays it owns on success or failure, including parser and output
handler keys. Java cannot guarantee erasure of backend/provider/JVM copies;
there is no physical secure-erasure claim.

Successful authentication reports one of four authorities. Folio does not use
`AccessPermission.isOwnerPermission()` as proof because an unrestricted user
also satisfies it. Folio separately evaluates the
Standard-handler owner predicate against the execution-local credential:

- `NONE` for an unprotected Source;
- `USER` when the declared permission word restricts the supplied credential;
- `OWNER` when owner authentication is separately provable; or
- `UNRESTRICTED` when the permission word itself grants everything but separate
  owner proof was not established.

The last state is intentionally not promoted to `OWNER`; security-sensitive
owner-only publication therefore fails closed. Owner credentials are proven
independently even when `/P` is unrestricted; an unrestricted user remains
`UNRESTRICTED`. Preparation cannot turn a noncanonical R5 owner spelling into
owner authority when the prepared bytes authenticate only the user.

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
set it; readers retain the declared mask but grant its effective accessibility permission. Printing and form filling have no current Document Command; their bits
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
this baseline. Metadata-clear AES-256 and embedded-files-only input/output
remain required separate #79 and #80 obligations. Their selectors are not added
to the Stable baseline surface or counted as baseline completion.

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

## Migration Facade and 0.x migration notes

Stable and inherited Preview expose 61 mappings linked to the baseline:
Reader/Writer and Document construction/lifecycle; `ReaderProperties` and
`WriterProperties`; the four `EncryptionConstants` algorithms and eight flags;
`PdfVersion` values/conversions/comparison; `PdfDocument.getPdfVersion()`;
and Reader encryption, authority, permission and algorithm observations.
The exact signatures are authoritative in `capabilities/facade-surface.yaml`.
All retain the `kernel.pdf` package suffix under `net.zerocloud.pdf.itext7`.

`ReaderProperties.setPassword(byte[])` copies exact legacy bytes or strict
UTF-8 AES-256 bytes. Null removes the opening credential; an empty array
supplies an empty credential. `setCredential(PasswordCredential)` is a Folio
extension and borrows the caller's Native credential. Closing properties never
destroys that borrowed credential. Writer properties copy user then owner
bytes, the permission mask and algorithm. Reader/Writer capture their own
snapshot; closing or modifying the caller's properties cannot change it.
Caller streams remain open. Close properties/Writers to clear retained arrays.

`setLegacySecurityMode` is an explicit Folio extension required for obsolete
output. `isOpenedWithFullPermission()` means proven owner or plaintext, not
an unrestricted user. `getPermissions()` preserves the unsigned declared
32-bit word, and `getCryptoMode()` returns -1 for plaintext. No permissive
reader bypass or public plaintext-password String API is introduced. The
Facade executes IN_PROCESS; only Native workflows select HARDENED_WORKER.

The former T16 ASCII/nonempty/distinct/length rejection policy is replaced by
standard preparation and truncation. Applications comparing original password
spellings must account for algorithmic equivalence. RC4-40 output and R5 input
are now successful supported cases; secure defaults and explicit protection
requirements remain unchanged. Runtime failure capability IDs stay stable.

## Scope boundary

Public-key encryption, FIPS validation, signature creation/trust and downstream
Forms, Conformance, Sanitization or Office work are outside this ticket.
Password permissions continue to intersect with Existing Signature restrictions.
The baseline's compatible claim requires all four chains on Ubuntu 24.04 Linux
x86-64 × JDK 8/11/17/21 × both Native modes, plus the actual IN_PROCESS Facade
on every JDK. Windows/macOS remain uncertified and are not F0.1.0 blockers.
Historical T16 evidence retains its original syntax-only meaning. See the
T78 audit and `PROVENANCE.md` for public sources and original fixture/tool identities.

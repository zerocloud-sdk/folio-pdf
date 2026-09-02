# T16 PDF versions and password security: primary-source research

Status: research input, not an authoritative Folio PDF policy, ADR, Capability
Matrix entry, or Acceptance Evidence record.

Researched: 2026-09-02 against repository fixed point
`0d74da80c9607d3fbe50cd12fbd13810d69bfc8c`.

## Scope and source discipline

This note answers the implementation questions raised by T16:

1. Which input and output PDF version declarations are in scope, and how is
   the effective version determined?
2. Which standard password-security revisions, algorithms, credentials,
   permission flags, and encryption scopes can be promised?
3. Which obsolete output choices require request-scoped Legacy Security Mode?
4. What does Apache PDFBox 3.0.8 actually parse, write, preserve, or leave to
   its caller?
5. How must those findings intersect with the existing transactional workflow
   and T15 Existing Signature policy?

The existing convention places non-authoritative primary-source notes under
`docs/research/`; Acceptance Evidence belongs under `capabilities/evidence/`.
Only inputs permitted by [CONTRIBUTING.md](../../CONTRIBUTING.md) were used:
project-owned specifications, publicly available PDF standards material, and
official Apache PDFBox documentation and source. No iText source, resource,
fixture, binary, output, decompiled detail, proprietary implementation detail,
or unapproved black-box evidence was consulted. No third-party PDF was adopted
as a project fixture and no source text was copied into implementation.

## Source inventory and license facts

| Source | Exact material consulted | Provenance, license, and boundary |
| --- | --- | --- |
| Folio PDF parent specification | [GitHub issue #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1), including its body, labels, state, and comments, read with `gh issue view 1 --comments` | Project-owned specification by `mabaiqiu`; open and labelled `ready-for-agent` when read. Its one comment concerns product naming and supplies no T16 behavior. |
| T16 ticket | [GitHub issue #17](https://github.com/zerocloud-sdk/folio-pdf/issues/17), including its body, labels, state, and comments, read with `gh issue view 17 --comments` | Project-owned ticket by `mabaiqiu`; open, labelled `ready-for-agent`, and without comments when read. No tracker state was changed. |
| Repository authorities | [CONTEXT.md](../../CONTEXT.md), [SECURITY.md](../../SECURITY.md), [CONTRIBUTING.md](../../CONTRIBUTING.md), ADRs 0002, 0005, 0006, 0010–0013, 0016–0017, 0019–0021, 0023, 0025, 0028–0029, and 0037, plus the T15 policy and research note | Project-owned Apache-2.0 material. These define clean-room, backend-neutral, transactional, failure, evidence, secure-default, resource-ownership, and signature-preservation constraints. |
| PDF 1.7 standard | Adobe-hosted authorized copy of [ISO 32000-1:2008, *Document management — Portable document format — Part 1: PDF 1.7*](https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf), SHA-256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff` | The copy says its technical material and clause/page numbering are identical to ISO 32000-1. The PDF is copyright Adobe/ISO, all rights reserved, even though its containing Adobe documentation repository is MIT-licensed. It was read temporarily and is not redistributed. This note paraphrases it and names clauses/tables. |
| Current PDF 2.0 status | PDF Association's [ISO 32000-2 resource](https://pdfa.org/resource/iso-32000-2/) and ISO's [ISO 32000-2:2020 catalogue entry](https://www.iso.org/standard/75839.html) | First-party standards metadata. The 2020 edition replaces the 2017 edition; the PDF Association's no-cost copy includes Errata Collection 3 as of June 2026. ISO retains copyright. The current standard PDF itself was not downloaded or redistributed. |
| Public PDF 2.0 clause material | The 2017-01-09 ISO/FDIS 32000-2 draft formerly published by Adobe at `developer.adobe.com/.../PDF_ISO_32000-2.pdf`, SHA-256 `605796798c2bf73f55a1a63e3cb8d9a45001864417050b6f36b3b5735d6bd63d`, together with the PDF Association's current [ISO 32000-2:2020 EC3 resolutions for clause 7](https://pdf-issues.pdfa.org/32000-2-2020/clause07.html) | The draft is copyright ISO 2017, all rights reserved, and explicitly warns that it is not an International Standard and may change. It is used only as provisional clause-shaped material, not misrepresented as the current normative edition. The public `pdf-issues` resolutions are maintained by the PDF Association; their repository is licensed CC-BY-4.0. Current resolutions, especially issue 399 on version upgrades and the security-algorithm corrections, were checked before drawing conclusions. |
| Adobe PDF 1.7 AES-256 extension | Adobe, *Adobe Supplement to the ISO 32000, BaseVersion: 1.7, ExtensionLevel: 3* (June 2008), obtained through the PDF Association's [PDF Specification Archive](https://pdfa.org/resource/pdf-specification-archive/) and its [Adobe archival link](https://web.archive.org/web/20220306152229/https://www.adobe.com/content/dam/acom/en/devnet/pdf/adobe_supplement_iso32000.pdf), SHA-256 `638f531b57ceb50b4f0b86a6740a57438ccecb0e434e32f0209d9c8200ecc44b` | Official Adobe public specification, copyright Adobe, all rights reserved. It defines `V=5`, `R=5`, `AESV3`, and the associated password and `Perms` algorithms for Adobe Extension Level 3. Read temporarily and not redistributed. |
| PDF 1.7 Extension Level 8 signal | Roman Toda, [*Encryption with PDF 2.0*](https://pdfa.org/wp-content/uploads/2018/05/1415_Toda.pdf), PDF Association presentation dated 2017-05-15, especially slide 8 | Public PDF Association presentation, copyright PDF Association. It identifies `/ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >>` as the PDF 1.7 signal for the corrected AES-256 revision later standardized in PDF 2.0. It is useful public interoperability guidance, **not** a normative specification for Extension Level 8. |
| PDFBox documentation | Apache PDFBox [3.0 migration guide](https://pdfbox.apache.org/3.0/migration.html#use-loader-to-get-a-pdf-document) | Official first-party documentation. |
| PDFBox 3.0.8 source | Apache tag `3.0.8`, peeled commit [`9286e47d89d6877005c9d2d0f2fd38793a62519a`](https://github.com/apache/pdfbox/tree/9286e47d89d6877005c9d2d0f2fd38793a62519a); locally resolved source JAR SHA-256 `eaed642d27599c78229857e4ab571805979f828f5ec8c695e3135ca933766132` | Official first-party source under [Apache License 2.0](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/LICENSE.txt). It was inspected to establish backend behavior. No PDFBox source was copied or adapted into Folio PDF. |

The current ISO 32000-2:2020 text remains the normative PDF 2.0 authority.
Where only the public 2017 draft, current EC3 resolution, Adobe extension, or
PDF Association presentation was available, this note marks the resulting
claim and uncertainty instead of silently promoting secondary guidance to a
standard.

## Requirements established by issues #1 and #17

The parent specification's user stories 22–24 require:

- PDF 1.7 with AES-256 as the secure output default when password protection
  is requested;
- reading supported legacy password encryption without enabling legacy
  output;
- an explicit Legacy Security Mode for obsolete output; and
- explicit PDF 2.0 output plus PDF 1.0 through 2.0 input handling.

Its implementation and testing decisions add the following direct
requirements:

- `WorkflowRequest` carries credentials and all policy is explicit and
  request-local;
- the public model contains only project-owned/JDK types and PDFBox remains a
  private dependency;
- checked failures have stable codes, one capability identity, and diagnostics
  that omit passwords, filenames, document data, metadata, backend exceptions,
  and private security state;
- all products are staged and reopened before publication;
- tests use `DocumentWorkflow.execute`, observable serialized/reopened
  behavior, and permission decisions rather than PDFBox object identity; and
- qpdf is syntax evidence only, never a standards or semantic oracle.

[Issue #17](https://github.com/zerocloud-sdk/folio-pdf/issues/17) narrows this
to supported version markers, explicit PDF 1.7/PDF 2.0 output, AES-256 for new
protected documents, owner/user/wrong/missing credential behavior, permission
masks, supported legacy protected input, and Legacy Security Mode around
obsolete RC4 output. It does not add public-key encryption, FIPS, Trust,
signing, or comprehensive hostile-input processing.

The phrase “AES-256 default” applies when password protection is selected. It
does not require silently encrypting an otherwise unprotected new document,
nor does it authorize silently replacing an existing Source's security
policy.

## Version declarations

### Standards-defined header and catalog behavior

ISO 32000-1 clause 7.5.2 says the first line is `%PDF-1.N`, where `N` is 0
through 7, and requires readers to accept 1.0 through 1.7. The public PDF 2.0
clause material adds `%PDF-2.0` and explains that offsets are measured from the
percent sign, allowing arbitrary prefix bytes. A bounded prefix search is
therefore a Folio resource policy, not a limit supplied by the standard.

ISO 32000-1 clause 7.7.2, Table 28, types catalog `/Version` as a **name**, not
a number. It affects the document only when it denotes a later version than
the header; otherwise the header governs. The PDF 2.0 table preserves that
rule, and the current EC3 resolution for issue 399 clarifies that an
incremental catalog update may upgrade but shall not reduce the current
version. Version values are `major.minor` integer pairs, not floating-point
numbers.

For T16 terminology:

- **header version** is the exact supported marker found in the file header;
- **catalog version** is the optional exact supported name in the resolved
  catalog `/Version` entry; and
- **effective version** is the later of those two values.

The catalog mechanism can legitimately upgrade a header older than 1.4 after
an incremental update; an implementation must inspect it even when the header
itself is 1.0–1.3. A lower but otherwise valid catalog value has no downgrade
effect. Keeping it observable in detached version information is useful, but
the effective value remains the header value.

### Deterministic T16 input classification

The following is the narrow, testable product contract supported by the issue;
it is stricter than ISO's forward-compatibility advice:

| Input state | T16 result |
| --- | --- |
| Exact `%PDF-1.0` through `%PDF-1.7` at byte zero on the first line | Accept the header declaration, subject to ordinary parse/security validation. ISO 32000-1 does not authorize a leading preamble. |
| Exact `%PDF-2.0` selected after zero or more prefix bytes | Accept within Folio's documented finite search bound and calculate file offsets from the marker's percent sign. The bound is a product limitation; the public PDF 2.0 text places no 1,024-byte or other finite limit on the prefix. |
| Optional catalog name `/1.0` through `/1.7` or `/2.0` | Expose it and use the later tuple as effective version. |
| Catalog version equal to or lower than the valid header | Preserve it as an observed declaration; it does not lower the effective version. |
| Header or catalog such as `1.8`, `1.9`, `2.1`, or `3.0` with otherwise valid `M.m` syntax | Fail as **unsupported version** before caller work. This is Folio's bounded T16 support set; ISO Annex I instead tells processors to attempt newer versions when possible. |
| Missing marker, a nonzero-offset PDF 1.x marker, truncated marker, wrong separator, extra digit, non-digit, catalog number/string/dictionary, indirect cycle, or name not exactly `M.m` | Fail as **invalid version declaration** before caller work. |
| Multiple marker-looking byte sequences | The first header marker selected by the documented bounded preflight is authoritative; later comments/content are not alternate headers. A malformed first selected marker is not skipped in search of a friendlier one. |
| Parser-repaired or parser-defaulted version | Never expose the repair/default as a standards-defined declaration; raw preflight failure wins. |

The prefix byte limit and exact permitted bytes before/after the marker must be
documented in the authoritative policy. The public PDF 2.0 material permits an
arbitrary prefix, whereas ISO 32000-1 called the marker the first line. T16 can
use a finite hostile-input bound, but must record that as a product limitation
rather than claim the standard imposes (for example) a 1,024-byte limit.

### Output marker rules

For a new or rewritten product whose effective version is PDF 1.7:

- write the exact header `%PDF-1.7`;
- omit catalog `/Version` or write `/Version /1.7`; and
- never leave a contradictory or higher catalog core-version marker.

For PDF 2.0 output:

- write the exact header `%PDF-2.0`;
- omit catalog `/Version` or write `/Version /2.0`; and
- remove a stale Adobe extension signal that was present solely to declare a
  feature now defined by PDF 2.0, when it is safe and owned to do so.

Canonical rewrite output should prefer a truthful header and no redundant
catalog override. Tests must inspect the serialized header and raw catalog
value, then reopen and query the detached effective version. `PDDocument`'s
float value alone is not evidence.

An incremental product retains the original byte prefix and therefore cannot
replace its header. The standard permits a later catalog `/Version` to upgrade
the effective version, never to downgrade it. The smallest T16/T15 contract is
more conservative: incremental publication preserves the Source's effective
version and rejects a request to change output version. A future capability
may deliberately add and evidence incremental version upgrade.

ISO Annex I says a writer should never change a newer input to an older
version and that merging/inserting content should retain the newest applicable
version. Consequently a rewrite default of PDF 1.7 is safe for an input up to
1.7, but a PDF 2.0 Source or merged Source cannot simply be relabelled 1.7. It
must either publish as 2.0 or prove every retained feature is valid in 1.7;
where Folio cannot prove that preservation, ADR-0017 requires rejection.

### AES-256 and the PDF 1.7 extension marker

Base ISO 32000-1 does not define AES-256. Adobe's public Extension Level 3
supplement added `V=5`, `R=5`, and `AESV3` to PDF 1.7. Current PDF 2.0 says
revision 5 shall not be used and standardizes the corrected AES-256 profile as
`V=5`, `R=6`, `AESV3`.

PDFBox 3.0.8 writes R6, not R5. The PDF Association presentation identifies
this PDF 1.7 declaration for the corrected algorithm:

```text
/Extensions <<
  /ADBE << /BaseVersion /1.7 /ExtensionLevel 8 >>
>>
```

No public, detailed Adobe Extension Level 8 specification was located in the
approved sources. Therefore:

- PDF 2.0 plus R6/AESV3 is the fully standards-backed combination;
- PDF 1.7 plus `ADBE` Extension Level 8 and R6/AESV3 is a documented public
  interoperability convention required to reconcile ADR-0021 with PDFBox;
- it must not be described as base ISO 32000-1 conformance or as proven by a
  normative public EL8 text; and
- maintainers should explicitly accept and document that convention before
  making it the PDF 1.7 secure default. The alternative is to make a protected
  R6 product PDF 2.0, which conflicts with ADR-0021's stated default and thus
  also needs an explicit project decision.

## Password-security profiles

### Standards structure and exact revisions

ISO 32000-1 clause 7.6 places an encryption dictionary at trailer `/Encrypt`.
Its absence means unencrypted. The standard password handler uses
`/Filter /Standard`; owner and user authentication are distinct. Strings and
streams are encrypted except for the listed structural/signature exceptions,
and embedded file streams normally follow stream encryption. Crypt filters
permit different stream/string policies and, since PDF 1.6, `EFF` selects the
default for embedded-file streams.

The standards and official PDFBox source establish this interoperable matrix:

| Profile | Dictionary/algorithm | Earliest declaration | T16 input | T16 output |
| --- | --- | --- | --- | --- |
| Legacy RC4-40 | `V=1`, 40-bit RC4. ISO 32000-1 Table 21 requires `R=2` when all revision-3-only permission bits (9, 10, 11, and 12) are 1, and `R=3` when any of them is 0. | PDF 1.1 for the algorithm; the revision-3 permission semantics are PDF 1.4-era behavior | Support the exact standards-defined V1/R2 and V1/R3 forms, with their matching permission rules. | A possible future profile only with Legacy Security Mode. The recommended baseline below leaves it unsupported because PDFBox chooses the wrong revision for two permission-mask extremes; any expansion must normalize or reject those cases and inspect serialized `V/R/P`. |
| Legacy RC4 variable length | `V=2`, canonical `R=3`, RC4; `Length` is a multiple of 8 from 40 through 128 bits | PDF 1.4 | PDFBox has the algorithm path. T16 version 1 should contract and fixture the 128-bit profile; other lengths are unsupported unless each is deliberately tested and documented. | RC4-128 only with Legacy Security Mode; do not expose arbitrary lengths accidentally. |
| Legacy crypt-filter security | `V=4`, `R=4`; `StdCF` with `CFM /V2` (RC4) or `CFM /AESV2` (AES-128) and absent/default or explicit `AuthEvent /DocOpen`, with `StmF`/`StrF` and optional `EFF` | PDF 1.5; `AESV2` and `EFF` are PDF 1.6 features | Whole-document `V2`/`AESV2` profiles are backend-readable. General `EFF`/attachment-only behavior is not established by PDFBox and is excluded below. | AES-128 whole-document output may be offered only with Legacy Security Mode. A distinct V4/RC4 output adds no required secure behavior and should remain unsupported unless a migration need and evidence justify it. |
| Adobe AES-256 revision 5 | `V=5`, `R=5`, `StdCF`, `CFM /AESV3`, absent/default or explicit `AuthEvent /DocOpen`, 256-bit key, `OE`, `UE`, `Perms` | PDF 1.7 Adobe Extension Level 3 | Evidence-gated candidate only. The recommended baseline rejects it unless a project-authored fixture proves the exact dictionary and authentication behavior. | Never write. PDF 2.0 explicitly says R5 shall not be used, and PDFBox coerces V5 output to R6. Legacy Security Mode does not override “shall not be used.” |
| Current AES-256 revision 6 | `V=5`, `R=6`, `StdCF`, `CFM /AESV3`, absent/default or explicit `AuthEvent /DocOpen`, 256-bit key, `OE`, `UE`, `Perms` | PDF 2.0; PDF 1.7 uses the EL8 convention described above | Supported | Secure password-output default; Legacy Security Mode is irrelevant. |

`V=0` is undocumented and shall not be used. `V=3` is an unpublished
algorithm and shall not appear in a conforming PDF. Unknown security handlers,
custom crypt filters, public-key handlers, R1, unknown revisions, invalid
lengths, or noncanonical `V/R/CFM` combinations are unsupported, even if
PDFBox happens to return a document. Public-key encryption remains T37.

Strict inspection also checks `/Filter /Standard`, rejects an unhandled
`SubFilter`, resolves every `StmF`, `StrF`, and `EFF` name to `Identity` or the
admitted `StdCF`, and rejects unknown `CFM` or non-`DocOpen` Standard-handler
events. R2–R4 require 32-byte `O` and `U` strings. R5/R6 require 48-byte `O`
and `U`, 32-byte `OE` and `UE`, and a 16-byte `Perms`. For AESV2/AESV3 the
algorithm fixes the key at 128/256 bits; a Standard-handler crypt-filter
`Length`, if retained, is expressed in bytes (16/32), while the top-level
encryption `Length` is expressed in bits. Omitting the redundant crypt-filter
`Length` avoids an ambiguity in PDFBox described below.

### Recommended Folio T16 version-1 allowlist

This is the narrow derived product contract that the approved sources and
PDFBox 3.0.8 can support without turning incidental leniency into a promise:

- Unprotected input admits exact effective versions 1.0 through 1.7 and 2.0.
  PDF 1.0 input cannot carry a Standard encryption profile.
- Protected input admits V1/R2 and the standards-required V1/R3 form at 40
  bits; V2/R3 at exactly 128 bits; V4/R4 whole-document `StdCF /V2` at 128
  bits; V4/R4 whole-document `StdCF /AESV2`; and V5/R6 whole-document
  `StdCF /AESV3`. `EncryptMetadata=false` may be admitted for those V4/V5
  inputs only after the metadata exception and, for R6, `Perms` agreement are
  fixture-proven. Attachment-only/EFF input remains unsupported.
  T16 version 1 supplies that proof for V4/R4 only; metadata-clear V5/R6 input
  remains outside the contracted allowlist.
- Each security profile requires at least the PDF version in the matrix.
  V1/R3 and V2/R3 require effective PDF 1.4, V4/RC4 requires 1.5,
  V4/AESV2 requires 1.6, V5/R6 requires PDF 2.0 or PDF 1.7 with the qualified
  EL8 signal, and an effective PDF 1.0 file must be unencrypted.
- V5/R5 is a defensible **candidate** legacy input because Adobe's approved
  public Extension Level 3 specifies it and PDFBox has a read path. It should
  not be in the shipped T16 contract unless a project-authored EL3 fixture and
  dictionary/authentication checks land in the same slice. Without that
  evidence, reject it as unsupported; never write it. Non-ASCII R5 passwords
  remain an additional backend uncertainty.
- Rewrite output is canonical PDF 1.7 by default or explicit PDF 2.0. Password
  protection means V5/R6/AESV3, all-content encryption, and the secure
  owner/user policy below. PDF 1.7 output carries the qualified ADBE EL8
  declaration; PDF 2.0 needs no Adobe extension marker.
- The smallest reliable legacy-output set is V2/R3 RC4-128 and V4/R4
  AESV2-128, each only on an explicit PDF 1.7 output policy and only with
  request-scoped Legacy Security Mode. V1 RC4-40 output should remain
  unsupported until Folio corrects or safely constrains PDFBox's R2/R3
  predicate and fixtures every permitted mask. V4/R4 RC4 adds no required
  behavior and remains unsupported. This bounded policy rejects legacy
  algorithms combined with an explicit PDF 2.0 output marker rather than
  publishing a deliberately deprecated PDF 2.0 product.
- Incremental output preserves the Source's effective version and complete
  encryption state. It never adds, removes, upgrades, downgrades, or rotates
  security. Full rewrite never removes protection implicitly.

If maintainers deliberately expose a broader profile, that expansion needs an
authoritative-policy change plus project-owned serialized and reopen evidence;
changing an enum alone does not expand this allowlist.

PDF 2.0 deprecates `V<5` and security-handler revisions 1–5, but readers still
need backward compatibility. Deprecation supports T16's split: read the exact
legacy profiles without Legacy Security Mode; require that mode for every
obsolete output; never turn it on implicitly.

### Password rules and authority

For R2–R4, password algorithms operate on a maximum of 32 bytes. PDFBox maps
its Java `String` through ISO-8859-1. A Folio legacy-output policy must reject
characters that cannot be represented losslessly by its documented encoding;
silent replacement would create a password other than the caller supplied.
It should also reject output passwords longer than 32 encoded bytes rather
than silently create an equivalent truncated credential.

For R6, PDF 2.0 applies SASLprep, encodes UTF-8, and truncates to 127 bytes.
PDFBox applies its `SaslPrep` implementation for R6 and truncates the UTF-8
bytes. PDFBox's R5 path uses UTF-8 but does not apply its R6 SASLprep call, so
non-ASCII R5 input interoperability is not proven and should be stated as a
legacy-input limitation.

Output validation must compare the **effective** credentials after the exact
revision's normalization and length rule. Merely comparing caller `char[]`
values is insufficient: distinct inputs can normalize or truncate to the same
password and then authenticate through the owner-first branch. The secure
product policy should reject over-limit R6 output rather than silently
truncate, and reject empty or equal effective owner/user credentials.

The owner password grants unrestricted authority, including changing
passwords and access permissions. The user password grants only the `P` flags.
The standard notes that after decryption nothing inherent in PDF encryption
enforces those permissions; the processor must do so.

Standards algorithms permit an absent owner password to fall back to the user
password and permit an empty default user password. Those are unsafe defaults
for an API promising enforced owner/user separation:

- secure output should require non-empty owner and user credentials;
- owner and user credentials should be distinct, because PDFBox tests owner
  authentication first and equal credentials can collapse user access into
  owner access;
- missing credential material and an explicitly supplied empty legacy input
  credential must be distinguishable; and
- Folio should require an explicit empty credential to try a protected legacy
  file whose user password is empty rather than silently treating “missing” as
  an authorization value.

Missing and incorrect credentials fail before `DocumentWork` is invoked and
before staging or publication. Neither failure says which password would have
worked. A successful open records detached authority (`OWNER` or `USER`) and
permissions, never password material.

### Permission bits

`P` is an unsigned 32-bit flag word stored as a PDF integer; a set bit grants
the operation. Readers ignore positions other than 3, 4, 5, 6, 9, 10, 11,
and 12. Canonical writers keep bits 1–2 clear and reserved high bits set as
required by the applicable revision, which normally makes `P` negative.

| Bit | Meaning |
| --- | --- |
| 3 | Print. For R3+, quality also depends on bit 12. |
| 4 | Modify content except categories controlled by bits 6, 9, and 11. |
| 5 | Copy or otherwise extract text and graphics. In R2 it also covers accessibility extraction. |
| 6 | Add/modify annotations and fill form fields; creating/modifying form fields additionally needs bit 4. |
| 9 | R3+: fill existing form/signature fields even if bit 6 is clear. |
| 10 | R3–R5 / PDF 1.x: accessibility extraction. PDF 2.0 deprecates this restriction; readers ignore it and writers shall set it to 1. |
| 11 | R3+: assemble: insert, rotate, delete pages and create outline items or thumbnails, even if bit 4 is clear. |
| 12 | R3+: faithful/high-quality print. With bit 3 set and bit 12 clear, only degraded print is allowed. |

Input validation must check that `P` is an integer, normalize it according to
the declared revision, and reject contradictory/noncanonical security
structures rather than trusting PDFBox booleans alone. R6 `Perms` contains an
encrypted copy of `P`, the `EncryptMetadata` value, and the `adb` marker; all
three must agree. PDFBox merely logs disagreement, so Folio must fail closed.
For PDF 2.0 output, bit 10 is not a selectable restriction: Folio must force it
to 1 or reject a policy that requests otherwise, and must verify the staged
`P` and `Perms` values. PDFBox passes the caller's permission word through and
does not perform this version-sensitive normalization.

### Mapping current workflow operations

Permission enforcement happens after successful authentication and before
each query or mutation. Owner authority bypasses `P` but never bypasses T15,
version, preservation, or transaction rules. User authority uses the following
conservative mapping at the fixed point:

| Current operation | Required user permission |
| --- | --- |
| `AddBlankPage`, `InsertBlankPage`, `RemovePages`, `MovePages`, and in-document `CopyPages` | Bit 11, assemble. |
| `MergeDocuments` | Bit 11 on the primary/destination Source; bit 5 on every donor Source opened with user authority. Each named Source is authenticated and authorized independently. |
| `SplitDocument` | Bit 5 on the primary Source because it exports copied page content. Every split product also follows the protected-rewrite rules below. |
| `ReplaceOutlineTree` | Bit 11, because outline creation is explicitly an assembly operation. |
| `UpdateDocumentInfo`, `SetXmpMetadata`, `SetNamedDestinations`, `EmbedFile`, and `UpdateActions` | Bit 4, general modification. |
| `UpdateAnnotations` | Bit 6. Widget/form behavior remains out of scope. |
| `FlattenAnnotations` | Bits 4 **and** 6, because it changes page content and removes annotations. |
| `DocumentPatch` | At least bits 4, 6, and 11 unless a complete mutation-footprint classifier proves a narrower category. Patches may never change engine-owned header/catalog version state, `/Encrypt`, encryption dictionaries, credentials, permissions, or security extension markers. Reject an unclassifiable user-authority patch. |
| `ExtractTextAndStructure`, `ExtractImagesAndResources`, `InspectObject`, `ReadEmbeddedFile`, and other content-bearing metadata/annotation/action/outline/destination queries | Bit 5. The current generic extraction APIs do not attest an accessibility-only purpose, so bit 10 alone does not authorize them. |
| `PageCount`, `DocumentVersion`, and reference-only root/page queries | No `P` bit beyond successful authentication; they expose structural identity rather than decrypted content. |

Printing and form filling have no present Document Workflow command. Their
bits must round-trip accurately but cannot be claimed as behavior until the
corresponding capability exists. In PDF 2.0 accessibility content cannot be
restricted through bit 10, but that does not turn a generic copying API into
an accessibility-only API.

The exact list should live in one closed project-owned authorization function
so a newly added command/query cannot accidentally inherit permission. An
unknown operation fails closed. Security permission and T15 Signature
Permission are independent restrictions; an operation proceeds only if both
permit it.

### Metadata and attachment encryption scopes

The public policy needs to distinguish at least:

- whole-document encryption with document-level XMP metadata encrypted
  (`EncryptMetadata=true`, the secure default);
- whole-document encryption with only document-level XMP metadata left
  plaintext (`EncryptMetadata=false`); and
- attachment-only encryption using `StmF`/`StrF /Identity` and an embedded-file
  crypt filter selected through `EFF` or an explicit stream `Crypt` filter.

`EncryptMetadata=false` does not make every metadata-like value plaintext; it
applies to the document-level metadata stream defined by the standard.
Attachment-only encryption uses the same owner/user passwords and requires
authorization when embedded content is accessed.

PDFBox 3.0.8's standard high-level writer cannot implement the latter two
policies faithfully. T16 may model them for an honest backend-neutral surface,
but must reject them as unsupported before publication unless Folio supplies
and tests a project-owned implementation. It must not silently substitute
whole-document, metadata-encrypted output. General attachment-only input is
also not a T16 claim because PDFBox has no `EFF`-specific path; an explicit
per-stream crypt filter may work, but does not prove the default `EFF` case.

## Credential storage and cleanup contract

Passwords must not be public Java `String` values. The narrow observable
contract should be:

1. Accept mutable characters and copy them immediately; later caller mutation
   cannot change the declared credential.
2. Never expose the internal array. Any internal handoff receives a fresh
   request-scoped copy.
3. Make destruction explicit and idempotent. Closing/destroying the public
   credential zeroes its owned characters and makes future use fail without
   revealing prior content.
4. At `execute`, copy only the credentials needed by that request, then zero
   all project-owned temporary character/byte arrays on every success and
   failure path after the last load/save use.
5. Do not include secret-derived values in equality, hash, `toString`, logs,
   progress, exceptions, evidence, filenames, or receipts.
6. Keep each Source's opening credential separate from output owner/user
   credentials. A merge cannot reuse the primary credential implicitly for a
   named donor.

PDFBox's public loader and `StandardProtectionPolicy` require immutable Java
`String` passwords; the policy stores those Strings. Java provides no reliable
way to erase them. Folio can minimize conversion lifetime, drop all references,
close `PDDocument`, and wipe every array it owns, but must explicitly disclaim
physical or JVM-wide secure-erasure guarantees. A defensive-copy/zeroization
test proves Folio's ownership behavior, not erasure of backend or JVM copies.

## Apache PDFBox 3.0.8 findings

### Version parsing is lenient and lossy

`COSParser.parseHeader` searches later lines for a marker, trims leading
garbage, tests a regex whose dot is a wildcard, substitutes 1.4 when a version
is missing, and in lenient mode can substitute 1.7 after parse failure
([source lines 1616–1696](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/COSParser.java#L1616-L1696)).
Successful load is therefore not strict marker evidence.

`PDDocument.getVersion()` converts both declarations to `float`, consults the
catalog only when the header float is already at least 1.4, logs malformed
catalog text, and returns `Math.max`
([lines 1398–1428](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L1398-L1428)).
It therefore cannot preserve exact syntax, distinguish unsupported tuples, or
handle a catalog upgrade over a 1.0–1.3 header correctly. Folio needs a bounded
raw header preflight plus exact COS type/value inspection.

A new `PDDocument` installs catalog `/Version /1.4`
([lines 172–195](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L172-L195)).
`setVersion(float)` refuses downgrade only by logging and normally writes a
catalog value instead of changing a header
([lines 1437–1460](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L1437-L1460)).
`COSWriter` writes the raw COS float as the header and can bump compressed
output to at least 1.6
([lines 650–674](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/COSWriter.java#L650-L674)).
Folio must set the header/catalog directly and validate the staged bytes; it
cannot inherit PDFBox's defaults.

PDFBox does not add the ADBE Extension Level 8 dictionary when writing
R6/AESV3. Folio must add and validate that signal for the accepted PDF 1.7
convention or write PDF 2.0.

### Security defaults are insecure for T16

PDFBox `ProtectionPolicy` defaults to a 40-bit key and `preferAES=false`
([lines 33–86](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/ProtectionPolicy.java#L33-L86)).
Folio must always set its selected profile; it must never use the backend
default.

PDFBox maps 40 bits to V1, AES-128 to V4, 256 bits to V5, and ordinary
128-bit output to V2
([`SecurityHandler` lines 850–875](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/SecurityHandler.java#L850-L875)).
`StandardSecurityHandler` maps V5 to R6 and chooses R2/R3/R4 from version and
permission state
([lines 104–135](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L104-L135)).
Every staged encryption dictionary must be independently inspected for the
requested exact profile.

The 40-bit branch has a specific conformance trap. ISO 32000-1 Table 21 selects
R2 only when **none** of bits 9, 10, 11, and 12 is 0, and otherwise selects R3.
PDFBox's `hasAnyRevision3PermissionSet()` instead tests whether any of those
bits is 1. Consequently its stock writer chooses the wrong revision when all
four are 1 and when all four are 0; mixed masks happen to select R3 under both
rules. RC4-40 output therefore requires project-owned normalization or must
reject the affected masks, followed by raw staged validation. Backend output
alone is not standards evidence.

The high-level writer converts null passwords to empty, substitutes the user
password when the owner password is empty, and derives `P` from PDFBox's
`AccessPermission`
([lines 365–423](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L365-L423)).
T16 request validation must reject unsafe ambiguity before this fallback.

For R6 it writes `AESV3`, `StmF/StrF /StdCF`, hard-codes the `Perms` metadata
byte to `T`, and provides no `EFF` selection
([lines 425–506](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L425-L506),
[lines 565–574](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L565-L574)).
On input it records only global stream and string filters; no official source
path references `EFF`
([lines 149–220](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L149-L220)).
That establishes the metadata/attachment limitations above.

PDFBox's generated Standard crypt-filter dictionary writes its `Length` from
the policy's bit count (128 or 256), although ISO expresses this particular
entry in bytes (16 or 32) and ignores it for AESV2/AESV3. A canonical Folio
writer should remove this optional redundant entry or normalize it before
publication, then inspect the staged dictionary. PDFBox also does not reject
every unknown or mismatched `CFM` combination and can fall through to an RC4
path; parser success is not an input allowlist.

### Authentication succeeds, permission enforcement does not

The official `Loader` overloads take a `String`; passwordless overloads pass
the empty string and throw `InvalidPasswordException` only when that value
does not authenticate
([lines 157–242](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/Loader.java#L157-L242)).
Folio must preserve missing-versus-explicit-empty intent outside Loader.

`StandardSecurityHandler` tries owner first, grants owner permissions on
success, otherwise tries user and constructs read-only `AccessPermission(P)`,
then rejects a wrong password
([lines 240–295](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L240-L295)).
It contains authentication paths for R2, R3, R4, R5, and R6
([lines 592–608](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L592-L608)).
Folio must detach the actual branch that authenticated. PDFBox's
`AccessPermission.isOwnerPermission()` merely tests whether all eight public
permission bits are allowed; it also returns true for an unrestricted user
credential and is therefore not proof of owner authority
([lines 128–165](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/AccessPermission.java#L128-L165)).
PDFBox also exposes the Standard handler's owner-password predicate. T16 uses
that predicate with its execution-local credential to detach `OWNER` when the
permission word itself is unrestricted; a failed or unsupported independent
proof remains the fail-closed `UNRESTRICTED` authority.

R5/R6 `Perms` validation only logs a bad marker, copied `P`, or metadata byte
and continues
([lines 315–355](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java#L315-L355)).
That is not fail-closed input validation.

`PDDocument.getCurrentAccessPermission()` explicitly tells content-access
methods to rely on the returned object
([lines 1335–1349](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/PDDocument.java#L1335-L1349)).
No PDFBox 3.0.8 document/query/mutation implementation calls those `can*`
methods; they are a model for the caller. Folio must enforce its closed
operation mapping before every query/command.

`StandardProtectionPolicy` stores owner and user passwords as Java Strings
([lines 36–112](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardProtectionPolicy.java#L36-L112)).
This is the backend erasure limitation described above.

### Rewrite and incremental security behavior

During a full write, PDFBox removes trailer `/Encrypt` when
`setAllSecurityToBeRemoved(true)` is selected. Otherwise a loaded encrypted
document without a new protection policy causes `IllegalStateException`;
incremental output skips protection-policy preparation and uses the existing
encryption state
([`COSWriter` lines 1518–1547](https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/COSWriter.java#L1518-L1547)).

Consequences:

- never call security removal as an implicit recovery path;
- protected rewrite cannot “preserve” unknown plaintext passwords—especially
  R5/R6, where owner authentication cannot recover the user password;
- protected rewrite requires owner authority plus explicit output credentials
  and policy, or it fails before mutation/publication;
- user authority cannot change passwords, permissions, algorithm, encryption
  scope, or remove protection;
- an explicit weaker algorithm additionally requires Legacy Security Mode;
  absent policy never means plaintext or weaker output; and
- encrypted incremental output preserves the existing encryption dictionary,
  key, algorithm, permissions, and Source prefix. Any output-security change
  is rejected. T15's signed-document rules remain an additional gate.

## Derived T16 decision order and stable distinctions

The sources support this implementation order. It is a design derivation,
not yet the authoritative policy:

1. Validate the request's version/security combination, required output
   credentials, encryption scope, and same-request Legacy Security Mode before
   opening targets or invoking caller work.
2. Read each Source under its byte bound. Strictly classify the raw header,
   catalog version, and encryption dictionary before accepting parser repairs.
3. For every protected Source, require explicitly supplied opening credential
   material, authenticate, validate the exact `V/R/Length/CF/CFM/P/Perms`
   profile, and detach owner/user authority. Wrong or missing fails before the
   callback.
4. Inspect Existing Signatures and build the T15 policy. Invalid security or
   signature structures fail closed; neither repair widens the other policy.
5. Invoke `DocumentWork`. Before each operation, intersect Source-specific
   user permissions, the closed T16 operation map, T15 Signature Permission,
   save-mode rules, and preservation policy. Apply nothing until all gates for
   that operation pass.
6. Before a rewrite, require a truthful version and, for a protected Source,
   owner authority plus an explicit output protection policy. Protection
   removal is outside T16. Before an incremental save, require exact security
   preservation and no requested version/security change.
7. Stage each product. Inspect raw header/catalog/extensions and the encryption
   dictionary, reopen with owner and user credentials, assert authority and
   permission behavior, and only then enter the existing ordered publication
   transaction.
8. Wipe all Folio-owned temporary credential arrays and close backend/source
   resources on every path. Emit only fixed safe diagnostics and
   `NOT_ATTEMPTED` receipts for pre-publication failure.

Stable failures need to distinguish at least:

- invalid version syntax versus a well-formed unsupported version;
- invalid/unsupported security structure or version/profile combination;
- missing credential versus rejected credential;
- permission denial;
- legacy output requested without Legacy Security Mode;
- unsupported metadata/attachment scope;
- protected rewrite without owner authority and explicit protection;
- incremental version/security change; and
- ordinary write, validation, and publication failures.

The recommended single capability identity is
`document.version-password-security`. Diagnostics should identify only the
stable category and capability, not the Source name, password role attempted,
algorithm internals, permission word, backend exception, or document content.

## Request-scoped Legacy Security Mode

Legacy Security Mode is an immutable value on one `WorkflowRequest`:

- absent/false by default;
- consulted only when that request explicitly selects an obsolete output
  profile;
- never a static, system-property, environment, thread-local, workflow-global,
  or remembered setting;
- never required to read an admitted R2–R5 legacy Source;
- unable to select an algorithm by itself or replace the R6 default; and
- unable to authorize forbidden V3, R5 output, public-key encryption, an
  invalid profile, permission bypass, signature bypass, or plaintext output.

RC4-40, RC4-128, AES-128, and any other admitted V1–V4 output all require the
mode because PDF 2.0 deprecates them and ADR-0021 calls them obsolete. An
explicit PDF 2.0 marker does not make a legacy algorithm secure; if such a
combination is admitted, it still requires the mode and must be documented as
deprecated. It is also reasonable for the authoritative version-1 policy to
reject legacy encryption with explicit PDF 2.0 as an unsupported product
combination, even though deprecation is not the same as syntactic prohibition.

Concurrency tests should run one legacy-enabled and one default request
through the same reusable workflow and prove the setting cannot cross the
request boundary.

## Acceptance and provenance implications

Project-owned fixtures/products should cover:

- exact first-line headers 1.0–1.7, PDF 2.0 with zero and bounded nonzero
  preambles, catalog upgrades/lower values, wrong catalog types, malformed
  markers, and well-formed unsupported tuples;
- canonical PDF 1.7 and PDF 2.0 rewrite markers;
- V1/R2 and V1/R3 RC4-40 permission cases, V2/R3 RC4-128, V4/R4 RC4 and
  AES-128, and V5/R6 AES-256 input using public algorithms only; add V5/R5
  only if it is contracted, otherwise test its stable unsupported rejection;
- R6 default output and every actually exposed legacy-output profile;
- missing, wrong, explicit-empty legacy, effective-password normalization and
  collision, user, and owner authentication;
- each permission bit and every mapped current operation, including donor
  Source permissions, `DocumentPatch`, T15 intersection, and unknown-operation
  fail-closed behavior;
- protected rewrite, incremental preservation, signed encrypted input,
  metadata-scope rejection, attachment-only rejection, and cross-request
  Legacy Security Mode isolation; and
- defensive copy, explicit destroy, finally-path cleanup, safe diagnostics,
  target receipts, and unchanged targets.

Fixture provenance must say that bytes were authored by the project from the
cited public algorithms. No downloaded standard example or third-party
protected document should become a fixture. Fixture passwords may exist only
as controlled test inputs; acceptance records and generated tool output must
not print them.

Serialized project-owned inspection should verify header/catalog/extensions,
`Filter`, `V`, `R`, `Length`, `CFM`, `StmF`, `StrF`, `EFF`,
`EncryptMetadata`, `P`, and required entry lengths. Reopen behavior independently
proves credential and permission decisions at the public seam. qpdf 12.4.0 may
record parse/syntax findings but cannot be labelled standards, semantic,
cryptographic, or permission-enforcement proof.

T16 must remain `experimental` while T09 is experimental or any mandatory
Acceptance Evidence chain is incomplete. Passing consumer tests and qpdf do
not establish compatibility. The Capability Matrix, Facade Surface exclusion
or evidence-backed preview mapping, authoritative policy, provenance record,
and generated inventories must use the same capability identity and state.

## Uncertainties and explicit non-claims

- The current ISO 32000-2:2020 EC3 PDF was not ingested. The public 2017 FDIS
  is a draft and cannot substitute for it. Contract facts above were limited
  to clauses corroborated by current public EC3 resolutions, ISO/PDF
  Association status material, the Adobe extension, and official PDFBox
  source. Any discrepancy with the current normative copy wins over this note.
- No detailed public Adobe Extension Level 8 specification was found. The EL8
  marker for R6 in PDF 1.7 is public interoperability guidance, not a
  normative clean-room algorithm source. This is the most important residual
  standards uncertainty for ADR-0021.
- ISO Annex I directs processors to attempt future PDF versions. Rejecting a
  well-formed version later than 2.0 is a deliberate T16 scope/security policy,
  not standards-mandated behavior.
- ISO 32000-1 calls the header the first line; the public PDF 2.0 material
  permits arbitrary prefix bytes. A finite search window is a documented Folio
  limitation and hostile-input control, not a standards limit.
- PDFBox has algorithm paths for more variable RC4 lengths and crypt-filter
  combinations than the recommended contract. Incidental backend readability
  is not Folio support. Each admitted combination needs a project-owned fixture
  and exact serialized validation.
- PDFBox's 40-bit R2/R3 predicate and its owner-looking permission convenience
  method are not authority. Exact raw `V/R/P` validation and detached
  authentication provenance are required at the project seam.
- PDFBox exposes no complete `EFF` default path for standard password
  security. Explicit per-stream `Crypt` behavior does not prove general
  attachment-only support.
- Permission flags express creator intent, not cryptographic DRM. A caller
  with decrypted bytes or owner authority can access content. T16 claims that
  Folio's own workflow enforces the flags, not that the PDF format prevents all
  circumvention.
- Wiping Folio-owned arrays cannot erase immutable Strings, parser buffers,
  heap copies, swap, backups, or physical storage. No secure-erasure or FIPS
  claim is made.
- No fixture, implementation, authoritative policy, or Acceptance Evidence was
  produced by this research note.

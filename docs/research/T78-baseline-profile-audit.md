# T78 baseline password-security and migration-surface audit

Researched: 2026-09-29. Comparison baseline:
`861c4ba81c7aecf9fba15052859f8cc80fd0259d`.

This is research input to the execution contract
`/workspace/contracts/issue-78-contract.md`, not a certification result. The
contract and the project-owned [Foundation requirements](../../capabilities/foundation-requirements.yaml)
remain authoritative. In particular, `spec-us-22`, `spec-us-23`, `spec-us-24`,
`spec-id-40`, `spec-id-41`, and `slice-78-1` through `slice-78-5` require an
audited profile and corresponding successful behavior. The old T16 allowlist
and backend restrictions are implementation history, not approved waivers.

The tables below distinguish required success, justified profile exclusions,
and evidence still needed. An existing test named here is a public verification
seam, not proof that a new candidate or all eight Native environment/profile
combinations passed. Final evidence must bind these rows to actual retained
observations and candidate identities.

## Source boundary

Only project documentation, public standards, public Reference Suite API
documentation, official ICU API documentation, Apache PDFBox preparation/owner
algorithm behavior, and the permissively licensed independent Arlington model,
qpdf, pdfcpu, pypdf and pyHanko checker sources
were consulted. No
iText implementation, source, resources, fixtures,
binaries, decompilation, or black-box output was consulted or adopted. API
signatures and numeric constants below are interface facts; implementation and
fixtures must remain independently authored.

| ID | Primary source and use |
| --- | --- |
| F | [Issue #1](https://github.com/zerocloud-sdk/folio-pdf/issues/1), [#33](https://github.com/zerocloud-sdk/folio-pdf/issues/33), [#78](https://github.com/zerocloud-sdk/folio-pdf/issues/78), as transcribed into the supplied execution contract and Foundation requirements; [ADR-0021](../adr/0021-use-secure-pdf-and-encryption-defaults.md), [ADR-0029](../adr/0029-separate-behavioral-and-facade-inventories.md), [ADR-0040](../adr/0040-certify-only-observed-foundation-environments.md). These establish project requirements, including qualified PDF 1.7 R6 output and the limited certification matrix. |
| ISO1 | Adobe's authorized [ISO 32000-1:2008 equivalent](https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf), especially §§7.5.2, 7.6, 7.7.2, Tables 20–26/28 and Annex I. Downloaded temporarily; SHA-256 `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`. Copyright Adobe/ISO; not redistributed. |
| EL3 | Adobe, [Supplement to ISO 32000, BaseVersion 1.7, ExtensionLevel 3, June 2008](https://web.archive.org/web/20220306152229if_/https://www.adobe.com/content/dam/acom/en/devnet/pdf/adobe_supplement_iso32000.pdf), §3.5, pp. 13–22, reached through the [PDF Association specification archive](https://pdfa.org/resource/pdf-specification-archive/). This defines the older AES-256/R5 extension and credential processing. Copyright Adobe; not redistributed. |
| ISO2 | [ISO 32000-2:2020](https://www.iso.org/standard/75839.html) is the normative PDF 2.0 authority. The [PDF Association edition](https://pdfa.org/resource/iso-32000-2/) includes Errata Collection 3; its [public clause-7 corrections](https://pdf-issues.pdfa.org/32000-2-2020/clause07.html) were read. The complete licensed standard was not obtained in this audit; see the access limitation below. |
| EL8 | Roman Toda, PDF Association, [Encryption with PDF 2.0](https://pdfa.org/wp-content/uploads/2018/05/1415_Toda.pdf), 2017-05-15, especially slide 8. This supplies the public PDF 1.7 `/ADBE` Extension Level 8 interoperability signal; it is not a normative EL8 specification. |
| PREP | IETF [RFC 4013](https://www.rfc-editor.org/rfc/rfc4013), especially §2, and [RFC 3454](https://www.rfc-editor.org/rfc/rfc3454), including Unicode 3.2 tables. These supply the versioned SASLprep mapping, normalization, prohibited-character and bidirectional rules. A newer Unicode runtime is not automatically an equivalent oracle. |
| API | The exact [Reference Suite 7.2.6 public Java API](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/package-summary.html), with the class-specific URLs and fetched HTML hashes recorded below. Current [first-party encryption guidance](https://kb.itextpdf.com/itext/aes-gcm-encryption-support) was used only as supporting documentation, not as a substitute for the frozen 7.2.6 signatures. |
| Q | qpdf's [encryption documentation](https://qpdf.readthedocs.io/en/12.4/encryption.html) and [CLI documentation](https://qpdf.readthedocs.io/en/12.4/cli.html), especially Unicode Passwords and `--suppress-password-recovery`. These establish qpdf behavior and limitations, not ISO compliance. The live `/12.4/` documentation identifies itself as 12.4.2; executable evidence must still use and identify the repository-pinned tool. |
| A | PDF Association [Arlington model at `fe4a1a8897ec07f674c73160c35d748b29052f8f`](https://github.com/pdf-association/arlington-pdf-model/tree/fe4a1a8897ec07f674c73160c35d748b29052f8f), notably `tsv/1.7` and `tsv/2.0` encryption and crypt-filter models. This is an independent machine-readable model, not the normative PDF standard. |
| CPU | Official [pdfcpu validation policy](https://pdfcpu.io/core/validate/) and Apache-2.0 [v0.15.0 `crypto.go`](https://github.com/pdfcpu/pdfcpu/blob/v0.15.0/pkg/pdfcpu/crypto.go)/[`read.go`](https://github.com/pdfcpu/pdfcpu/blob/v0.15.0/pkg/pdfcpu/read.go). Strict validation covers implemented rules; PDF 2.0 coverage is expressly incomplete and parser recovery can precede validation. Actual pinned-tool qualification remains required. |
| ICU | Official [`StringPrep` API](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/StringPrep.html), especially `RFC4013_SASLPREP`, `DEFAULT`, `ALLOW_UNASSIGNED`, and `prepare`. The project already pins ICU4J 77.1; see its [dependency identity and license](../third-party/icu4j-77.1.md). The API explains the profile interface; PREP remains the preparation authority. |
| PP | BSD-3-Clause [pypdf 6.1.1 encryption source](https://github.com/py-pdf/pypdf/blob/6.1.1/pypdf/_encryption.py) and its [official published module documentation](https://pypdf.readthedocs.io/en/6.1.1/_modules/pypdf/_encryption.html). Used only to identify an independent permission-block check; it is not a general conformance validator. |

## Version profile

All rows derive their required Folio behavior from F; ISO1/ISO2 supply the
format rules. Exact inspection must remain independent of a backend floating
point version or parser repair.

| Required case | Frozen behavior | Public/evidence seam |
| --- | --- | --- |
| Input `%PDF-1.0`, `1.1`, `1.2`, `1.3`, `1.4`, `1.5`, `1.6`, `1.7`, `2.0` | Accept each otherwise valid Source and expose its exact declaration. Encryption minimum versions still apply. | `supportedHeaderVersionsAreReportedThroughTheWorkflow`; nine independent declaration fixtures. |
| Header versus catalog `/Version` | Expose both; later supported declaration wins. A lower catalog declaration cannot downgrade the header. | `catalogVersionUsesTheLaterSupportedDeclaration`; equal/lower/higher catalog controls. |
| Malformed or unsupported declarations | Distinguish malformed types/syntax from unsupported tuples; fail before work, with unchanged destinations and ordered receipts. | `invalidVersionDeclarationsFailBeforeWorkOrPublication`; independently serialized malformed header/name/number/indirect-cycle cases. |
| New/rewrite default; explicit output | Default exact PDF 1.7; explicit PDF 1.7 and PDF 2.0. Reject requests for other output versions. Remove redundant/stale owned version state. | `rewritesDefaultToPdf17AndExplicit17Or20MarkersReopen`, `unsupportedOutputVersionFailsBeforeWorkOrPublication`; raw header/catalog and reopened observations. |
| PDF 2.0 primary or named Source | Require explicit PDF 2.0 for rewrite; no implicit donor downgrade. | `namedSourcesCannotDowngradeVersionOrPasswordProtection`; public Facade equivalent. |
| Incremental version | Preserve header bytes and Source effective version; reject a requested change under the existing conservative Native incremental contract. | `protectedRewriteRequiresExplicitProtectionAndIncrementalPreservesIt`; unchanged-prefix and reopened checks. |
| PDF 1.7 EL8 to PDF 2.0 R6 | Remove only the exact owned EL8 signal. Reject unknown extension state when safe preservation cannot be established. | `pdf20RewriteRemovesTheOwnedPdf17AdobeSecurityExtension`; positive conversion and unknown-extension control. |

The 1,024-byte preflight search bound and the Native requirement for explicit
credential presence are project resource/authorization policy. They are not
limitations asserted by ISO. The version constants in the Facade represent
input declarations; their existence does not expand the output set below 1.7.

## Algorithm, revision, key and scope profile

The input/output distinction is material. An algorithm can be required for
legacy reading without becoming a selectable new output revision. The four
Reference output algorithm selectors establish RC4-40, RC4-128, AES-128 and
AES-256 migration coverage; they do not offer an arbitrary key-length or
security-handler-revision selector.

| Case | Input disposition | Output disposition | Source and required evidence |
| --- | --- | --- | --- |
| Unprotected PDF | Required, subject to version checks. | Default when no protection was requested; never the implicit result of protected rewrite. | F; version and protected-publication tests. |
| RC4-40: `V=1`, `R=2`, 40 bits | Required from PDF 1.1. | Required, PDF 1.7, explicit Legacy Security Mode. | ISO1 Table 21; API `STANDARD_ENCRYPTION_40`. Test both restrictive and unrestricted permissions, independent `/V /R /P` and decryption observations. |
| RC4-40 with revision-3 permission restrictions: `V=1`, `R=3`, 40 bits | Required from PDF 1.4, including both owner-hash constructions distinguished below. | Required when those restrictions select R3. New output uses the normative full-digest owner construction; accepting the established truncated-round input convention is separate. | ISO1 Table 21. All of bits 9–12 set selects R2; any clear selects R3. Backend revision selection is not authority. |
| RC4-40 represented by `V=2`, `R=3`, `Length=40` (including omitted/default length) | Required alternate representation of the same fixed 40-bit input family. | No separate output selector is required. | ISO1 Table 20 explicitly permits/defaults 40 bits for V2; Table 21 selects R3. The output constant does not narrow the reader's representations. |
| RC4-128: `V=2`, `R=3`, 128 bits | Required from PDF 1.4. | Required, PDF 1.7, Legacy Security Mode. | ISO1; API `STANDARD_ENCRYPTION_128`; fixture/reopen and request-local opt-in controls. |
| Crypt-filter RC4-128: `V=4`, `R=4`, `/CFM /V2`, 128 bits | Required all-content legacy input from PDF 1.5. | A separate V4 output selector is not required: the advertised all-content RC4-128 behavior is supplied by canonical V2/R3 output. | ISO1; no separate API selector. Incremental preservation of admitted V4 input remains required; metadata-clear output remains #79. |
| AES-128: `V=4`, `R=4`, `/CFM /AESV2`, 128 bits | Required from PDF 1.6. | Required, PDF 1.7, Legacy Security Mode. | ISO1; API `ENCRYPTION_AES_128`; independently authored ciphertext fixture and dictionary observations. |
| AES-256 legacy: `V=5`, `R=5`, `/CFM /AESV3`, 256 bits | Required PDF 1.7 legacy input with an appropriate ADBE Extension Level 3 declaration; cannot remain excluded merely for lack of a fixture. PDF 2.0 deprecation is not itself an input prohibition; see the input/output distinction below. | Do not add R5 output. AES-256 output is the required secure R6 profile, and the API has no R5 selector. R5 output is excluded by that project policy, not a newly inferred reader prohibition. | EL3 §§3.5.1–3.5.4; F; API AES-256 selector. Add owner/user/empty/Unicode/length and `Perms`-tamper fixtures. |
| AES-256 current: `V=5`, `R=6`, `/CFM /AESV3`, 256 bits | Required PDF 1.7 EL8-qualified and PDF 2.0 input. | Required secure default in both supported output versions. Legacy opt-in must not change it. | F, ISO2 and EL8, with distinct normative/qualified claim labels. Independently observe real dictionary fields and artifact hashes. |
| V2/V4 RC4 intermediate key lengths (48–120 bits) | These are valid format possibilities outside the selected fixed 40/128-bit profile. This is a bounded project profile exclusion, not a standards-invalid input. It must be explicit in the final profile; absence of an output selector alone does not establish a reader limitation. | No arbitrary-length selector is required. | ISO1 Table 20 permits 40–128 bits in multiples of 8. F says supported legacy profiles rather than every possible legacy parameter, while the migration output family names fixed 40/128 bits. No backend limitation is the reason for this boundary. |
| `V=0`, `V=3`, unknown revisions/algorithms, inconsistent lengths or entries | Reject unsupported/invalid structures. | Never emitted. | ISO1 Table 20 and the declared Standard-handler profile; single-defect controls. |
| Public-key/custom handler, FIPS, new AES-GCM/MAC extensions | Outside #78. | Outside #78. | F non-goals and frozen Reference version; no downstream expansion. |

For all-content crypt-filter profiles, streams and strings select `StdCF`;
an omitted `EFF` inherits the stream choice, and an explicit matching `EFF`
must be equivalent. `AuthEvent` omitted or `DocOpen` is admitted. The
Standard-handler limit to `StdCF`/`Identity` and `DocOpen` is source-based
(ISO1 §7.6.3.1 and ISO2 §7.6.4.1 corrections), not a backend allowlist.

| Crypt-filter arrangement | Audit result and evidence requirement |
| --- | --- |
| Default global `StdCF`; absent/default `AuthEvent`; explicit matching `EFF` | Required successful all-content variants. A redundant declaration is not an unsupported algorithm. |
| Per-stream `/Crypt` naming the same `StdCF` | Required all-content representation. ISO1 §7.4.10/Table 14 and §7.6.5 explicitly permit the stream override. `Crypt` must be the first stream filter; its decode parameters select `StdCF`. Decrypt exactly once with the selected key before later decoding filters. Do not apply the global `StmF` a second time or classify this as attachment-only. |
| Absent `StmF` or `StrF` | The format default is `Identity`; it is not silently equivalent to all-content encryption. Classify actual scope before admitting it. |
| `Identity` selectively exposing content, unequal stream/string protection, custom filter names or algorithms | Not the all-content baseline. Standard custom names also violate the closed handler filter convention. Do not silently misreport as `ALL_CONTENT`. |
| Metadata-clear V4/R4 fixtures already admitted | Preserve their fixture-proven input behavior as a regression obligation. Do not certify complete #79 behavior. |
| AES-256 metadata-clear or embedded-files-only expansion, `EFOpen` authorization | Separate #79/#80 obligations. Do not implement or mark complete through #78. |
| Length units | Top-level encryption `Length` is bits; Standard crypt-filter length is bytes (AESV2 16, AESV3 32). ISO1 Table 25 permits omission, whereas the current ISO2 Table 25 correction marks the entry required. Preserve valid omitted AESV2 Length in input and output; a present value must be 16 bytes. Require the explicit 32-byte AESV3 value in certified new output. The input model distinguishes the older extension form from the current PDF2 rule. Test missing, wrong type and contradictory values against the applicable edition. |

The `Crypt` override defaults to `Identity` when its decode-parameter `Name`
is absent. That changes actual scope and must not be silently interpreted as
`StdCF`. Negative controls should cover absent/wrong/unknown names, `Crypt`
after another filter, incorrect decode-parameter shape, and unencrypted bytes
incorrectly labelled `StdCF`. Cross-reference streams cannot carry `Crypt`
(ISO1 §7.5.8.2). A global `StdCF` plus explicit stream `StdCF` positive fixture
is distinct from those malformed or selectively clear cases.

PDF 2.0 deprecates older handlers; this does not establish a reader rejection
requirement. The pinned Arlington
[`fn:Deprecated` definition](https://github.com/pdf-association/arlington-pdf-model/blob/fe4a1a8897ec07f674c73160c35d748b29052f8f/INTERNAL_GRAMMAR.md)
distinguishes permitted-but-discouraged deprecation from prohibited
obsolescence. Its exact PDF 2.0 `EncryptionStandard.tsv` still lists R2/R3/R4
and explicitly R5 as deprecated values. EL8 slide 7 also recommends retaining
old-handler reader compatibility. Thus the source-supported input disposition
is to admit an otherwise consistent supported legacy tuple independently of
a PDF 2.0 declaration; retain the project R6-only PDF 2.0 output policy.
Neither the T16 `revision == 6` restriction nor historical draft R5 wording
is a current normative input waiver. This is an inference from the current
primary corrections and independent model; the complete licensed current
Table 21 was not read.

Likewise, PDF 2.0 writers set permission bit 10, while readers ignore it as a
restriction. Keep the actual declared bit observable, grant accessibility
under the reader rule, and do not call the input malformed solely because
the bit is clear. A writer-oriented Arlington bit predicate is not itself a
reader rejection requirement.

### RC4-40/R3 owner-key construction correction

ISO1 §7.6.3.4 Algorithm 3(c) hashes the complete preceding 16-byte MD5 result
in each of the 50 owner-key rounds, then step (d) truncates to the key length.
By contrast, Algorithm 2(h), for the file encryption key, explicitly hashes
only the first `n` bytes in each round. For 128-bit keys those constructions
coincide; for R3/40 (`n=5`) they differ.

The pinned [qpdf 12.4.0 source](https://github.com/qpdf/qpdf/blob/v12.4.0/libqpdf/QPDF_encryption.cc),
`iterate_md5_digest` and `compute_O_rc4_key`, uses the truncated `n`-byte
round input for both purposes. The current upstream file fetched on the audit
date was byte-identical. This confirms the established qpdf compatibility
choice independently of the product backend; it does not amend ISO1.
pdfcpu 0.15.0's `key` function instead hashes all 16 owner-digest bytes before
truncation and therefore supplies a distinct external literal-ISO owner
authentication path to qualify.

Retain two independently authored R3/40 fixtures, one per construction.
Native owner authentication must support both bounded predicates, granting
owner authority only after the recovered candidate passes the user predicate.
Do not rewrite the literal-ISO fixture into the interoperability construction
or describe qpdf's owner rejection as proof it is malformed. User
authentication can succeed with either construction because Algorithm 2 uses
the stored `O` bytes; it does not recompute them from the owner password.
Record owner and user observations separately. New R3/40 output must use
Algorithm 3's full-digest owner construction. The contract preserves a
qualified PDF1.7 R6/EL8 convention; it does not authorize a new nonnormative
R3/40 output waiver. Merely labeling truncated-round output interoperable
would leave the standards obligation unmet. The earlier research suggestion
to retain that output convention is superseded by this correction.

The pinned [pypdf owner-key implementation](https://github.com/py-pdf/pypdf/blob/6.1.1/pypdf/_encryption.py)
(`AlgV4.compute_O_value_key`) and pdfcpu `key` independently implement the
full-digest owner rounds. Executed pypdf probes authenticate the original
literal-ISO fixture as owner and user, but authenticate the first-n fixture
only as user; the same supplied owner credential returns no authority for
the latter. This is a discriminating owner-construction control, unlike
identical field sizes. Retain successful external owner authentication and
independent exact O calculation for each new normative R3/40 output. Use
qpdf's successful user authentication for its syntax/decryption checks; that
does not waive the separate owner proof. The checked-in authoring/fixture
identities for these temporary observations are captured in
`/tmp/t78-audit-external/final/r3-owner-construction-observations.json`.

O contributes to Algorithm 2's file key. A writer correction must compute
normative O before deriving the user entry/file key and encrypting content,
or re-encrypt all affected content consistently. Replacing only the
serialized O field after encryption produces an inconsistent file.

The qpdf file SHA-256 is
`8b4a371e58a346d3526bf172da59bf26787552cb898673587d19d4ae0f4b58c7`.

## Credential interpretation and authority

The exact 7.2.6 `byte[]` setter documentation does **not** prescribe a charset
or normalization algorithm. The byte-array call shape therefore cannot be
used as evidence that Latin-1, UTF-8 or charset guessing is mandated by the
Reference API. EL3 pp. 18–20 explicitly describes PDFDocEncoding for legacy
passwords and SASLprep followed by UTF-8 and 127-byte truncation for R5.
R6 uses the PDF 2.0 algorithms with the same versioned preparation family.
The current iText guidance's “Latin-1” shorthand does not turn
PDFDocEncoding-specific characters into Latin-1 code points.

| Credential case | Required result or justified failure | Positive/negative evidence |
| --- | --- | --- |
| Missing, incorrect, destroyed | Preserve distinct safe failures before work. Missing is not an implicit empty credential. | Existing `missingIncorrectAndDestroyedCredentialsFailBeforeWorkOrPublish`; Facade/Worker equivalents and unchanged targets. |
| Nonempty distinct owner/user | Both open; owner proof gives unrestricted authority; user remains subject to `/P`. | Existing defaults/authority tests plus independent authentication. |
| Empty user | Required supported value. Explicit empty input authenticates an empty user password; protected output remains encrypted. | New output/reopen checks for every admitted output algorithm; missing credential still fails. |
| Facade null/empty owner | Reference setter defines generated random owner credentials. Preserve that behavior without returning or logging generated material. | Verify empty user/user credential never gains owner authority because owner generation was skipped or replaced with user bytes. |
| Native explicit empty owner | The selected Native adaptation treats an empty output owner credential as a request for a fresh ephemeral random owner credential, matching the Facade request semantics. It does not write a known literal-empty owner credential or expose the generated value. Empty input credentials still authenticate literal-empty credentials in an existing Source. | User/empty-input reopen, independent owner predicate, lifetime cleanup, and proof that user bytes were not substituted for generated owner bytes. |
| Equal nonempty or effectively equal credentials | No reviewed source forbids equality. Support it; report `OWNER` only if the independent owner predicate succeeds. Do not promise user/owner separation for equivalent credentials. | Literal equality, normalization equality and truncation equality; unrestricted-user negative control. |
| Legacy encoding | Preserve Facade byte-array credentials exactly. The selected Native adaptation treats each `char[]` code unit in `U+0000`–`U+00FF` as one already-encoded credential octet; callers encode PDFDocEncoding before using this bridge. This is a byte-preserving adapter, not a promise of Latin-1 text equivalence to PDFDocEncoding. Reject code units outside that range without replacement for legacy handlers. | Non-ASCII Latin letters plus a PDFDocEncoding-specific encoded octet; raw-byte values that happen to be valid UTF-8; unmappable Unicode negative. |
| Legacy length | Standard derivation uses the first 32 bytes with defined padding. Do not retain an arbitrary 32-character rejection as if it were the algorithm. | 31/32/33 encoded bytes, empty input and distinct suffixes sharing the first 32 bytes. |
| R5/R6 Unicode and length | Apply the revision's preparation and first-127-byte rule. Printable ASCII is not the required repertoire. R5 must not inherit a backend omission of SASLprep. | 126/127/128 UTF-8 bytes; truncation splitting a multibyte sequence; normalization/space/mapped-away and valid bidirectional cases; same effective credential. |
| Invalid Unicode or prohibited preparation result | Safe rejection is justified by the character/preparation contract, without substitute characters. | Unpaired surrogates, malformed UTF-8 byte input, prohibited code points and invalid bidirectional sequences. |
| Defensive ownership | Copy mutable caller data; clear owned copies on close, replacement and failure; borrowed Native credentials retain caller ownership. No password `String` public contract. | Caller mutation, reuse, close-before-work, failure cleanup, Worker transport and resource-ownership tests. |

A Latin-1 conversion can be a **lossless internal bridge for already encoded
bytes**; it is not a Unicode-to-PDFDocEncoding implementation. For a Facade
reader that only has bytes before opening, the best design is to classify the
handler and choose the corresponding interpretation. If complete bounded
Native authentication attempts are used instead, accept a successful result
only when its observed revision agrees with the interpretation attempted.
Otherwise UTF-8-looking legacy bytes can authenticate a different password.
Retry only credential rejection, against the same immutable snapshot; never
retry malformed-dictionary, permission, resource or publication failures.
This is a Native adaptation, not behavior inferred from undocumented iText
implementation details.

SASLprep requires more than Java `Normalizer.normalize` alone. RFC 4013 §2
specifies mappings, Unicode 3.2 NFKC, prohibited output and bidirectional
checks. Stored-string and query treatment of unassigned code points must
follow the selected PDF algorithm and versioned RFC profile. An independent
observer must not import the product's preparation helper as its oracle.

The implementation handoff selects the already-pinned ICU4J 77.1
`RFC4013_SASLPREP` profile, with stored-string `DEFAULT` and query
`ALLOW_UNASSIGNED`, behind a private adapter with safe error mapping. The
implementation agent also consulted Apache PDFBox 3.0.8 preparation behavior;
its final implementation does not copy or adapt that source. Record that
consultation and the ICU reuse in product provenance. Independent credential
observations must use separate RFC-derived vectors and calculations.

## Permissions and protected workflows

Permission facts are taken from ISO1 Table 22; current-operation authorization
and publication protection are project requirements F. The API numeric values
come from the [7.2.6 constant-value table](https://api.itextpdf.com/iText/java/7.2.6/constant-values.html#com.itextpdf.kernel.pdf.EncryptionConstants.ALLOW_PRINTING).

| Native permission | PDF bit | Facade constant and value |
| --- | --- | --- |
| Printing | 3 | `ALLOW_DEGRADED_PRINTING = 4` |
| General modification | 4 | `ALLOW_MODIFY_CONTENTS = 8` |
| Content extraction | 5 | `ALLOW_COPY = 16` |
| Annotation modification | 6 | `ALLOW_MODIFY_ANNOTATIONS = 32` |
| Filling existing forms | 9 | `ALLOW_FILL_IN = 256` |
| Accessibility extraction | 10 | `ALLOW_SCREENREADERS = 512` |
| Assembly | 11 | `ALLOW_ASSEMBLY = 1024` |
| Faithful printing, together with printing | 12 and 3 | `ALLOW_PRINTING = 2052` |

`ALLOW_PRINTING` is a combined mask, not the bare high-quality bit. Reader
`getPermissions()` reports declared permissions as an unsigned 32-bit `long`;
it must not substitute the owner's effective unrestricted permissions. Proven
owner authority, a user with unrestricted permission bits, and an unprotected
Source remain distinct. `isOpenedWithFullPermission()` is true for a proven
owner or an unprotected Source, false for a user even when `/P` is
unrestricted. All eight bits need independent set/clear observations, with
the version-sensitive accessibility rule separately checked.

| Workflow requirement from F | Required observations |
| --- | --- |
| Authorize current operations | Assembly for page edits/outlines; extraction for content queries/split and merge donors; modification for metadata/destinations/attachments/Actions; annotation permission for annotation edits; intersection for composite operations and `DocumentPatch`. Authentication-only introspection remains available. |
| Protected Source preflight | Authenticate every primary/named Source before caller work, in declaration order, preserving one-shot ownership and donor permission checks. |
| Rewrite | Explicit replacement protection plus proven owner authority. Missing policy, user authority or unrestricted-user authority cannot result in plaintext output. |
| Incremental | Preserve encryption and Source bytes; reopen appended result with the Source credential; reject attempted rekeying. |
| Existing Signature | Intersect password permissions with every applicable Existing Signature restriction; neither source of authority overrides the other. |
| Failure/publication | Ordered `NOT_ATTEMPTED` receipts and unchanged destinations before publication; preserve separately specified partial-publication behavior. |
| Tampering | Wrong `/O /U /OE /UE /Perms` shape, invalid `/P`, inconsistent encrypted permissions/metadata/marker, filter changes and wrong versions must be detected independently. |

Existing public seams are
[`PdfVersionPasswordSecurityWorkflowTest`](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfVersionPasswordSecurityWorkflowTest.java),
[`HardenedWorkerWorkflowTest`](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/HardenedWorkerWorkflowTest.java),
and [`IncrementalSignatureWorkflowTest`](../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/IncrementalSignatureWorkflowTest.java).
They require new coverage where the profile above expands T16. Permission
flags concern Folio's operation authorization; they are not cryptographic DRM.

## Required Reference Suite 7.2.6 member inventory

All reference types below are under `com.itextpdf.kernel.pdf`; mapped types
preserve `kernel.pdf` beneath `net.zerocloud.pdf.itext7`. `String` parameters
below are filenames/version text, never passwords. Each selected mapping must
name `document.version-password-security.baseline` and `password-baseline` in
the separate inventories. The class-specific API links are the signature
authority.

| Required member | Native behavior and contract adaptation |
| --- | --- |
| [`ReaderProperties()` and `ReaderProperties setPassword(byte[])`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/ReaderProperties.html#setPassword(byte%5B%5D)) | Declare a defensively owned clearable credential. A Native `PasswordCredential` overload is a documented supplemental adaptation, with explicit borrowed/owned lifetime. |
| [`PdfReader(String, ReaderProperties) throws IOException`; `PdfReader(InputStream, ReaderProperties) throws IOException`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html) | Authenticate and capture a bounded Source before exposing work. Preserve caller-stream ownership and safe checked construction failures. |
| [`boolean PdfReader.isEncrypted()`; `boolean PdfReader.isOpenedWithFullPermission()`; `long PdfReader.getPermissions()`; `int PdfReader.getCryptoMode()`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html#isOpenedWithFullPermission()) | Detached security observations, exact declared mask and proven authority. State the unencrypted crypto-mode sentinel explicitly; the method's brief JavaDoc does not specify it. |
| [`WriterProperties()`; `WriterProperties setPdfVersion(PdfVersion)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/WriterProperties.html#setPdfVersion(com.itextpdf.kernel.pdf.PdfVersion)) | Default PDF 1.7 and explicit PDF 1.7/2.0; validate before publication. |
| [`WriterProperties setStandardEncryption(byte[] userPassword, byte[] ownerPassword, int permissions, int encryptionAlgorithm)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/WriterProperties.html#setStandardEncryption(byte%5B%5D,byte%5B%5D,int,int)) | Map all four admitted output algorithms and all eight permissions. Empty user and generated owner semantics above apply. Add an explicit request-local Native Legacy Security Mode adapter; the algorithm constant itself is not sufficient opt-in. |
| [`PdfWriter(String, WriterProperties) throws FileNotFoundException`; `PdfWriter(OutputStream, WriterProperties)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfWriter.html) | Bind a snapshot of complete output policy to the target. Construction does not truncate; caller streams stay caller-owned. |
| [`PdfVersion PdfDocument.getPdfVersion()`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfDocument.html#getPdfVersion()) | Return the effective observed/output version through the existing public workflow seam. |
| [`PdfVersion` static final constants `PDF_1_0`, `PDF_1_1`, `PDF_1_2`, `PDF_1_3`, `PDF_1_4`, `PDF_1_5`, `PDF_1_6`, `PDF_1_7`, `PDF_2_0`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfVersion.html) | Represent all nine declarations with backend-neutral values. No public constructor is documented. |
| [`static PdfVersion fromString(String)`; `static PdfVersion fromPdfName(PdfName)`; `PdfName toPdfName()`; `String toString()`; `int compareTo(PdfVersion)`; `boolean equals(Object)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfVersion.html) | Preserve mapped value/conversion/comparison shape. `PdfVersion implements Comparable<PdfVersion>`; inventory any compiler bridge and project-added `hashCode()` consistently. |
| [`EncryptionConstants` public static final ints `STANDARD_ENCRYPTION_40=0`, `STANDARD_ENCRYPTION_128=1`, `ENCRYPTION_AES_128=2`, `ENCRYPTION_AES_256=3`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/EncryptionConstants.html) | Fixed algorithm selectors, with R6 chosen for AES-256 output by F. |
| [`EncryptionConstants` eight permission constants](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/EncryptionConstants.html) | Exact values from the preceding table; combined masks preserve each bit. |
| Existing [`StampingProperties()`; `StampingProperties(StampingProperties)`; `StampingProperties useAppendMode()`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/StampingProperties.html) and mapped `PdfDocument` constructors | Extend evidence to encrypted incremental behavior; do not relabel current IN_PROCESS Facade calls as Worker execution. |

This is the required reader/writer configuration/inspection family selected
by #78, not a claim to every public method in those classes. Existing ordinary
constructors and document lifecycle members remain covered by their existing
obligations. Additions such as `AutoCloseable` properties, a Native credential
overload or Legacy Security Mode setter must be recorded as supplemental
Native adaptations with exact compiled signatures and lifetime semantics.

The following adjacent members were explicitly audited rather than silently
lost from a blanket security-family exclusion:

| Adjacent API | Disposition and source-grounded reason |
| --- | --- |
| [`PdfReader setUnethicalReading(boolean)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html#setUnethicalReading(boolean)) | Exclude. Permission bypass conflicts with the execution contract; neither a permissive implementation nor a throwing Stable stub is acceptable. |
| [`byte[] PdfReader.computeUserPassword()`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html#computeUserPassword()) | Credential recovery/export is not one of the required Foundation reader/writer outcomes. It is distinct from authenticating a caller credential and reporting authority. Record an explicit unmapped boundary; do not expose backend secret recovery incidentally. |
| [`StampingProperties preserveEncryption()`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/StampingProperties.html#preserveEncryption()) | The reference allows a preservation request, while its default rewrite may remove encryption. Folio's protected rewrite requires complete explicit replacement policy and owner authority; encrypted append is already represented by `useAppendMode`. Do not map a silently decrypting default or claim unrestricted preserve-on-rewrite parity. |
| `DO_NOT_ENCRYPT_METADATA=8`, `EMBEDDED_FILES_ONLY=24` and the respective setter combinations | Reserve for #79/#80; no unsupported Stable stubs or premature aggregate compatibility. Existing admitted V4 metadata-clear input still needs regression evidence. |
| [`EncryptionProperties()` and `EncryptionProperties setStandardEncryption(byte[],byte[],int,int)`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/EncryptionProperties.html), and [`PdfEncryptor`](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfEncryptor.html) convenience entry points | These duplicate separately packaged re-encryption/permission helpers; they are not necessary to supply the selected reader/writer family. Do not assert full `PdfEncryptor` parity. If selected for this ticket, include the exact additional inventory below and public behavior evidence instead of silently omitting pieces. |
| Public-key setters, external decryption providers, `PdfEncryptor.getContent(...)`, conformance/logging helpers | Outside baseline password security and/or expose another library's types; no backend or Reference implementation types may enter the Facade. |

The audited `PdfEncryptor` helper signatures are `static boolean`
`isPrintingAllowed(int)`, `isModifyContentsAllowed(int)`, `isCopyAllowed(int)`,
`isModifyAnnotationsAllowed(int)`, `isFillInAllowed(int)`,
`isScreenReadersAllowed(int)`, `isAssemblyAllowed(int)`,
`isDegradedPrintingAllowed(int)`, and `static String getPermissionsVerbose(int)`.
Its standard-encryption entry points are `PdfEncryptor()`,
`PdfEncryptor setEncryptionProperties(EncryptionProperties)`,
`void encrypt(PdfReader, OutputStream)`,
`void encrypt(PdfReader, OutputStream, Map<String,String>)`, and the two
`static void encrypt` overloads taking `(PdfReader, OutputStream,
EncryptionProperties)` with an optional final `Map<String,String>`.
These signatures come from the exact 7.2.6 public page, not an earlier or
current-major API.

## Independent standards and credential coverage gaps

The repository's [T03 standards profile](../../capabilities/profiles/T03-standards/README.md)
provides the right pattern: explicit required rules, checker assignments,
original positive/negative fixtures, reliable findings, and pinned identities.
T15 adds separately recomputed predicates over raw observations. Neither
pattern authorizes a generic tool-success claim for encrypted products.

| Required coverage | Observed limitation; implication |
| --- | --- |
| Exact syntax/ciphertext opening | qpdf syntax is useful, but never standards certification. Do not retain `--show-encryption-key` or plaintext-password-bearing output. Sanitize retained observations without losing verification facts. |
| Exact credential bytes | qpdf CLI normally tries alternate encodings. Disable recovery for exact-credential controls. Its Unicode documentation says it does not implement normalization/bidirectional preparation; independent SASLprep checks are needed. |
| Complete security standard rules | pdfcpu strict covers only implemented rules and has incomplete PDF 2.0 coverage. Qualify each selected encryption rule with a detected original defect, using actual pinned binaries. |
| PDF 1.7 EL8/R6 | Pinned Arlington `tsv/1.7/EncryptionStandard.tsv` has no R6/EL8 admitted value. An EL8 compatibility supplement must be explicit and must retain the qualified claim. |
| V1/R3 with revision-3 permissions | The same Arlington model's `R` expression selects R2 for `V<2`; it does not encode the ISO1 permission-dependent V1/R3 alternative. This is a model-coverage gap, not grounds for rejecting required RC4-40 output. |
| `OE`, `UE`, `Perms` size/integrity | Arlington checks `OE` size, but `UE`/`Perms` have no size predicate at the pinned revision and no decrypted `/P`/metadata/marker integrity proof. The temporary overlay and independent pypdf probes below demonstrate separate ways to fill these gaps; final retained qualification is still required. |
| Crypt-filter declarations | Arlington `CryptFilter.tsv` marks `Length` required even in its PDF 1.7 model, whereas ISO1 Table 25 makes it optional. The final checker policy must distinguish edition-specific defaults and diagnostics. |
| Handler-specific filters | Arlington `CryptFilterMap.tsv` has a wildcard dictionary link; generic grammar acceptance does not prove Standard-handler filter-name restrictions or complete all-content scope. |
| Authority versus permission flags | A tool that opens ciphertext or reports all permission bits cannot prove owner authentication. Independently evaluate the owner predicate; a deliberately unrestricted-user fixture must remain non-owner. |
| Visual preservation | Render successfully authenticated original bytes with pinned PDFium and compare to expectations fixed independently before the candidate exists. A password supplied to a renderer is not authorization evidence. |

### Concrete R6 external-checker qualification

The following assignments define qualification work, not candidate certification.
pdfcpu v0.15.0's public source establishes plausible checks to exercise;
`read.go` calls permission-block validation after either owner or user
authentication. Run the actual pinned strict/offline command against the
unchanged encrypted product and an original positive fixture. For each
assigned rule, keep a single-defect negative fixture and its exact detected
finding. Use a known authenticating credential for structural controls and account
for the checker's required authority, so a permission denial cannot masquerade
as detection of the intended standards defect.

| Explicit rule | Candidate independent check and detected control |
| --- | --- |
| R6 `OE`/`UE` are 32 bytes; `Perms` is 16 bytes | pdfcpu `validateAES256Parameters`: independently shorten/extend each entry, preserving a parseable PDF. Record three separate rule results and detection identities. |
| R6 password validation and key unwrapping | pdfcpu owner/user R6 paths independently open original fixtures and products; wrong credential and damaged validation-salt/hash controls fail. This supplies cryptographic interoperability evidence, not a claim that every preparation edge case is covered. |
| `Perms` contains the expected marker, metadata flag and declared `/P` | pdfcpu `validatePermissions`: decrypts AES-ECB and compares those fields. Construct separate encrypted single-field defects using an original fixture's known key, not arbitrary corruption that only proves an earlier failure. |
| Exact 48-byte `O`/`U` entries | pdfcpu checks only a minimum, so oversized-entry controls need the independently qualified Arlington rule or another identified external checker. The product's own rejection does not fill external standards coverage. |
| Required reserved permission bits; `Perms` bytes 4–7 | pdfcpu does not independently enforce every such bit. Assign dictionary bits to qualified Arlington predicates. pypdf's external permission-block check detects individually modified bytes 4, 5, 6 and 7 in the temporary probes below; a wrapper must fail on its warning even though decryption reports success. |
| R6 `V=5`, `R=6`, 256-bit global key; Standard AESV3 filter length 32 | Use qualified Arlington predicates plus explicit version/profile rules. pdfcpu's AESV3 filter-length path supplies no length predicate; missing/wrong/contradictory entry controls must prove the assigned alternate check. |
| Global `StdCF`, `DocOpen`, matching stream/string/embedded-file choices | Qualify the named structural predicates independently. A generic model wildcard or a tool's successful decryption is not sufficient coverage. |
| PDF 1.7 EL8 compatibility | An explicit qualified supplemental model must admit the project's selected extension and rule tuple while detecting missing/wrong extension and incompatible algorithm controls. Retain its interoperability label. |
| Underlying ordinary PDF structure | Reuse only applicable already-qualified T03/T15 rules, with encrypted-input/parser handling observed. Decrypting a projection can test the projection's structure but cannot certify the original encryption dictionary. |

The pinned pdfcpu source also exposes a specific legacy-checker disagreement:
for PDF 1.x strict mode its V2/AESV2 crypt-filter length checks expect bits,
whereas the admitted Standard-handler profile uses bytes. Relaxed mode can
digest that difference, but changing to relaxed mode cannot silently become
strict standards evidence. Assign the affected rule to an independently
qualified checker and retain the disagreement. Generic rejection by a checker
with that mismatch is not proof that the original product is invalid.

The source files were read temporarily, not copied into the repository:
`crypto.go` SHA-256
`52b78c08718d90bf8ca46f319e0a544f5c11d28e6af54209f6b25728b64c35a0`;
`read.go` SHA-256
`a8a8b641896c1c4e78c56bec8e26c9bdd18ccb1960c3e06333cbfbad5c225702`.
These source observations do not replace executable qualification. If any
required rule lacks a correctly detected control and a successful original
positive fixture, the standards chain remains incomplete. A project semantic
observer must not simply be renamed as an external standards checker.

Pinned Arlington files read in this audit:

| Model file | SHA-256 |
| --- | --- |
| `tsv/1.7/EncryptionStandard.tsv` | `9e09eb5725260c177bc99b143b6b4bc0ab7e59cf4121aa5d8bf6339aa03e8d91` |
| `tsv/2.0/EncryptionStandard.tsv` | `5598036bcc8b30aac2705df484ac5cf0fad14dc7aa41b50dc500c0c5a67d16ab` |
| `tsv/1.7/CryptFilter.tsv` | `8d3c56f1021e88e05acc741d267dbcb4d10c3c0db6f6280f72114abd7600dc06` |
| `tsv/2.0/CryptFilter.tsv` | `b95fe5a947195967faad8015d3f0e786b98f85a17f71d94e1e92d91439d5bc9e` |
| `tsv/2.0/CryptFilterMap.tsv` | `79837d316c1d969a6c5584b856cadaa18da6c96bfac2868c87d51ce19705bb1b` |

### Executed external-tool feasibility probes

These probes used original T78 corpus bytes and independently serialized
single-field controls under `/tmp/t78-audit-external/`. They establish tool
behavior and viable checker assignments. They are not the final retained
qualification, product observations, or eight-environment certification.
No product implementation or acceptance-tooling files were changed by this
research. Only this note is a repository deliverable from these probes.

| Executed component | Identity and observed result |
| --- | --- |
| qpdf | Repository-pinned 12.4.0 executable, SHA-256 `9ac787a28597e8428289a12ba3fedafd74bdfb4b4da1be814722faf76f14f21b`. Exact-byte decryption succeeded for prepared R6 Unicode, R6 truncation splitting the last UTF-8 character, and 33-octet legacy credentials. |
| pdfcpu | Repository-pinned 0.15.0 executable, SHA-256 `5d1a9ff691ae1d720ba822fdc93d48ffc306aadd9497a858bf270b780e7d7c7d`. Strict validation accepted original R2, literal-ISO R3/40, RC4-128, R5, R6/EL8 and R6/PDF2 positives. It rejected the valid V4 RC4/AESV2 positives because of its byte-versus-bit filter-length disagreement. |
| Arlington | Pinned `fe4a1a8` TestGrammar executable, SHA-256 `45de36669a3aad3087346335acdfbe716a27beb27f3a39202d3f8f4997ba7458`. The unchanged engine accepted the temporary model overlay under `--validate`; original positives for all eight algorithm/version tuples plus omitted AuthEvent/matching EFF, scalar per-stream StdCF, and PDF2 input bit10-clear passed the overlaid rules. |
| pypdf | Acceptance-only 6.1.1, with PyCryptodome 3.23.0, installed under `/tmp/t78-audit-python`. `python3 -S` and an explicit import root selected provider `("pycryptodome", "3.23.0")`; without isolation, an already installed `cryptography` package can take precedence. No third-party code entered the product. |

Command shapes actually exercised were:

```text
pdfcpu validate --mode strict --offline --conf disable --opw <credential> <original.pdf>
TestGrammar --tsvdir <overlaid-model> --force exact --brief --no-color --password <credential> --pdf <original.pdf>
TestGrammar --tsvdir <overlaid-model> --validate --no-color
python3 -S <isolated-pypdf-probe.py> <original.pdf>
qpdf --password-file=<ephemeral-file> --password-mode=hex-bytes --suppress-password-recovery --decrypt --deterministic-id <original.pdf> <ephemeral-derivative.pdf>
```

The pypdf probe receives exact credential bytes through standard input. The
qpdf file contains only the hex encoding of those bytes, has mode `0600`, and
is deleted with the decrypted derivative in a `finally` block. The probe
retains original and derivative hashes, never the plaintext derivative or
password. A decrypted derivative is useful for PDFium rendering and ordinary
structure checks; it cannot replace standards checks on the original
encryption dictionary. The correct pdfcpu CLI uses double-hyphen options;
initial single-hyphen invocations were usage failures and were not counted.

The external pypdf rule is
[`AlgV5.verify_perms`](https://github.com/py-pdf/pypdf/blob/6.1.1/pypdf/_encryption.py),
which compares the first twelve decrypted permission bytes against the
declared mask, four reserved `FF` bytes, metadata flag and marker. Its public
`PdfReader.decrypt` path logs `ignore '/Perms' verify failed` while still
returning successful owner authentication. Thus a qualified wrapper must
require successful authentication **and** reject that exact diagnostic. It
must also reject unexpected diagnostics, exceptions, missing tools or
provider/version mismatches. Absence of the warning after failed
authentication is not a pass.

| Original R6 control | Actual isolated pypdf observation |
| --- | --- |
| Unmodified original | Owner authentication succeeds; no permission warning. |
| Wrong credential | Authentication fails; no permission warning. This is a distinct failure, not proof that `Perms` was checked. |
| Change decrypted byte 0, then re-encrypt with the original key | Owner authentication still succeeds; exact permission warning detected. |
| Change each decrypted reserved byte 4, 5, 6 or 7 separately | Four separate controls retain owner authentication; each produces the exact permission warning. |
| Change decrypted metadata byte 8 or marker byte 9 separately | Both controls retain owner authentication; each produces the exact permission warning. |

The original bytes were regenerated and required to match the checked-in
fixture before mutation. Only the encrypted permission field changed in
these controls; this avoids mistaking an earlier password/hash failure for
permission-block validation. The consulted pypdf encryption source SHA-256 is
`f8bc0510a7e12f7075d1a7e4728e9f5f1f84aad8e85dfebba405201f65417c3c`;
the [BSD-3-Clause license](https://github.com/py-pdf/pypdf/blob/6.1.1/LICENSE)
file SHA-256 is
`a97ac230e5f33ef10a5367a850eb01f91f1a0b064e34742c7794d2294557f524`.
This is an external cryptographic rule check, not a claim that pypdf is a
complete PDF standards validator. pdfcpu and the consulted pyHanko 0.27.0
permission check omit reserved bytes 4–7 and cannot fill that rule merely by
successfully opening the same file.

The Arlington experiments follow the upstream
[private-overlay guidance](https://github.com/pdf-association/arlington-pdf-model/blob/fe4a1a8897ec07f674c73160c35d748b29052f8f/MODEL_NOTES.md).
Rules are project-selected public-standard expressions evaluated by the
unchanged external engine. They are distinct from a project semantic
observer. The overlay must have its own retained identity, rule provenance,
positive fixtures and detected controls. Findings must be parsed; TestGrammar
returns exit zero for many invalid PDFs, and `END` alone only shows processing
completed.

| Structural rule group | Detected temporary control and qualification caveat |
| --- | --- |
| Handler, algorithm/revision/key tuples and types | Unknown Standard handler, inconsistent V/R, wrong global key length and non-integer V/R/Length emitted the expected named findings. |
| Exact O/U/OE/UE/Perms lengths | Appending one byte to each original field emitted its exact string-length finding; shorter OE/UE/Perms controls also failed. Upper bounds cannot be inferred from pdfcpu's minimum O/U checks. |
| Standard crypt-filter method/byte length/event | Wrong CFM, wrong present CF Length, EFOpen, missing CFM and missing StdCF emitted their corresponding findings. Missing AESV3 Length failed a CFM presence guard. Missing AESV2 Length passed, preserving the valid ISO1 default. A conditional Required predicate alone missed absent AESV3 Length in this engine. |
| All-content selectors | Identity StmF/StrF/EFF and missing StmF/StrF/CF were detected. A custom extra CF key produced an external `Info: unknown key ... for CryptFilterMap` finding; the qualified closed-profile policy must reject that finding despite its informational severity. |
| Metadata scope | `fn:Eval(@EncryptMetadata==true)` accepts true and rejects false. Literal `PossibleValues=[true]` incorrectly rejects both values in this engine and is not a usable predicate. This all-content output rule does not erase the separately retained metadata-clear legacy input regression. |
| Reserved P bits | Separate controls for bits 1, 2, 7, 8, 13 and 32 and R2 bit 9 produced the expected P special-case finding. PDF2 writer bit10 has a separately qualified output predicate; the reader overlay accepts a valid encrypted bit10-clear original. |
| Version/extension consistency | AESV2 below PDF1.6 and EL8 missing, level 7 or wrong BaseVersion controls failed. Missing extension paths need explicit `fn:IsPresent` guards; a comparison through an absent path can otherwise be indeterminate. |
| Per-stream Crypt order | Separate `ArrayOfFilterNames` entries `0` and `1*` admit Crypt only at index zero. First-Crypt passes; second-Crypt and duplicate-Crypt controls fail. A preliminary wildcard predicate only checking the first value missed repeated Crypt and was discarded. |
| Per-stream Crypt scope | `Stream/Filter` predicates detect Identity or missing DecodeParms Name for both scalar and array Crypt forms. Ordinary scalar and array Flate DecodeParms still pass. The array predicate uses `(Filter::@0!=Crypt) || fn:IsPresent(DecodeParms::0::@Name==StdCF)`, combined with matching array lengths. Wrapping the comparison makes an absent Name false. Relying only on `FilterCrypt.tsv` fails because the engine may select another generic DecodeParms model. Identity/default Name changes all-content scope, but is not universally malformed PDF. |

Required values with a model default can evade a simple Required predicate
in the pinned engine. The tested output overlay removes such defaults or
uses explicit presence guards. Do not transplant this writer restriction to
otherwise valid reader defaults. The engine also normalizes some large PDF
integers through its backend; a successfully parsed bitmask alone did not
prove a signed 32-bit serialized range. Every claim beyond the qualified
rules above remains separate work.

The final array solution does not need a closed Crypt-only DecodeParms model
or an engine patch. The grammar accepts an intermediate numeric index in a
value expression such as `DecodeParms::0::@Name`; the corresponding bare key
path `DecodeParms::0::Name` is not accepted and caused a parser crash. The
engine's `fn:Contains(@Filter,Crypt)` also failed to supply the required array
guard in the probe. Use the positively and negatively qualified first-element
comparison rather than assuming that similarly named predicates work.

Arlington's CLI converts the password through UTF-8 and wide strings. Actual
probes with prepared Unicode, a split UTF-8 boundary and legacy raw octets
produced `Info: Unsupported encryption` for the textual owner credential,
while a separate ordinary user credential succeeded. The PDFium shim
deliberately continues after password/handler failures and can inspect
unencrypted encryption-dictionary fields. That limited traversal does not
resolve Catalog/Extensions inside encrypted object streams, authenticate the
supplied credential, or prove decoded content. Full structural qualification
of actual products therefore uses a known authenticating credential. Bind separate exact-byte qpdf/pypdf authentication observations and
qualified visual derivatives instead of treating Arlington's exit code as
authorization evidence.

External diagnostics can print binary O/U/OE/UE/Perms values when a field
fails. Safe retention must redact the entire rendered string payload and
continuations, credential observations and source paths while preserving the
rule name, expected condition, tool exit status and completion marker.
`--brief`, without `--debug`, reduces unrelated content; it is not sufficient
redaction by itself. The private temporary probe scripts and reports are
implementation handoff material, not distributable evidence; the production
collector must repeat these checks with its own pinned identities and safe
report policy.

## Final checker-model handoff and rule traceability

The final research overlays are `input-model` and `output-model` beneath
`/tmp/t78-audit-external/`. Their portable six-file replacements, per-file
base/overlay SHA-256 manifests and unified patches are under
`/tmp/t78-audit-external/final/`. Earlier exploratory `model` directories are
superseded. The repository's provisioning and evidence paths must retain
their own corresponding identities; a temporary experiment is not candidate
certification.

The unchanged external executable is Arlington TestGrammar v0.81, SHA-256
`45de36669a3aad3087346335acdfbe716a27beb27f3a39202d3f8f4997ba7458`.
The base source commit is
`fe4a1a8897ec07f674c73160c35d748b29052f8f`, source archive SHA-256
`587265b2af48561079147da04868ed5cd7f9753d1fa339c0ffb8cbf12cc6abc4`,
and the repository-pinned `tsv/latest` model identity is
`334aa8d6ccd88c96cf01c463971f3079206a47f5cf2101bf6d8cf81f374d5408`.
The final input patch SHA-256 is
`a28b745acf41b92899644b0e0def2b19cd7d8d42a39679f385d9c0bc693da184`;
the output patch SHA-256 is
`2d3ce25560eaea4fed1a5079ea2aa357a11030a41ca04043277830567d146ae4`.
Both models passed the external engine's `--validate` grammar check.

| TSV file | Base SHA-256 | Input overlay SHA-256 | Output overlay SHA-256 |
| --- | --- | --- | --- |
| `ArrayOfFilterNames.tsv` | `b990d15b77e3cdac322c2828446cc175631bbba89fdd4f7322774026f01bc8d4` | `76e56125006dd075408cad7b8715153900d6c8a36aaf2c6d171612573532de3c` | `76e56125006dd075408cad7b8715153900d6c8a36aaf2c6d171612573532de3c` |
| `CryptFilter.tsv` | `dc6f9e1b672cc71cd48eeca3b5364fadc433c716a198b8ebf71a898dd1a11878` | `b879dd6036014157c2d9ba877f28d5232154e8d5f32518db44fc34a80fbad079` | `c3fc65ed13b9a4bb89199067647502fa43c2fd0b412a643b8648aec967e31260` |
| `CryptFilterMap.tsv` | `79837d316c1d969a6c5584b856cadaa18da6c96bfac2868c87d51ce19705bb1b` | `03ac25695980e9c83860761d5c7e69852d3021727a18e43c900c2cb9b15797ff` | `03ac25695980e9c83860761d5c7e69852d3021727a18e43c900c2cb9b15797ff` |
| `EncryptionStandard.tsv` | `2567481c9006fdd3fa75ac91b33dbf0dbc5fa3c2d614d1581ea862de2da3b3e3` | `4c38a619a21054a8b93d08004453ebe68f968a5ab6e4e1eb12aa7a12e320317a` | `cc1125dffd3a96c18ab22dba7983954089955810d630e510dd129d277f3ef58d` |
| `FilterCrypt.tsv` | `f76eab8744b107a8cd7c136a7b13d133ab4d09af77eaf18d3957cf0765c2f3a8` | `c52172e70d010f9b9cc3d888711b530b35601d24c9ae41b787e3bff184b49296` | `c52172e70d010f9b9cc3d888711b530b35601d24c9ae41b787e3bff184b49296` |
| `Stream.tsv` | `d2897c7a08cd47a97352f3e389041cd3f73e42bef1b9c18fc9a86bac349397fa` | `bcfa0087a93a5b8a41adb95f0a8c8cfb98b386d04bafa31feea8315c9d92a3a7` | `bcfa0087a93a5b8a41adb95f0a8c8cfb98b386d04bafa31feea8315c9d92a3a7` |

Only `EncryptionStandard.tsv` and `CryptFilter.tsv` differ between the two
overlays. The input model preserves optional global Length, including the
40-bit default where applicable, deprecated PDF2 legacy inputs, input bit10
clear, and the separately retained V4 metadata-clear regression. The output
model requires the selected writer representation's global Length, true
EncryptMetadata and PDF2 accessibility bit10. AESV2 CF Length is optional in
both; a present value must be 16 bytes. Output AESV3 CF Length is explicitly
32. The input AESV3 presence rule distinguishes the older extension form
from the current PDF2 table. An omitted AuthEvent defaults to DocOpen, and
omitted EFF inherits StmF. These are deliberate input/output distinctions,
not reports that every writer-policy failure is universally invalid PDF.

The closed CF map admits StdCF and rejects an unexpected active custom
filter. An unused custom entry is an excluded closed-profile structure, not
proof of failed ciphertext. In particular ISO1 Table 20 says an Identity
entry in CF is ignored; do not turn this project restriction into a claim
that every redundant Identity declaration violates PDF syntax.

The following table records actual findings from the unchanged external
engine against independently authored controls. `R6` means the original
`aes-256-pdf20` fixture; `E` abbreviates `EncryptionStandard`. A named finding
means that model/key and finding class, not a substring somewhere inside
an unrelated predicate. Most invalid PDFs still exit zero in TestGrammar.

| Required rule / producer | Positive original | Single-defect control IDs or reproducible mutation | Observed finding |
| --- | --- | --- | --- |
| Standard handler and Filter type / Arlington | R6 | `filter-absent`; `filter-type` in the initial generator substitutes name Unknown | Required `Filter (E)`; possible-value `Filter (E)`. The initial `filter-type` label does not prove a wrong PDF object type: add a numeric Filter control or name the original unknown-handler case accurately. |
| No SubFilter in this Standard profile / Arlington | R6 | `subfilter-present` adds public-key SubFilter | Special-case `Filter (E)` explicit absence guard. |
| V/R presence and type / Arlington | All seven algorithm/revision families | `v-absent`, `v-type`, `r-absent`, `r-type` | Corresponding required-key or wrong-type `V (E)` / `R (E)`. |
| V/R relation / Arlington | V1/R2, V1/R3, V2/R3, V4/R4, V5/R5 and V5/R6 originals | `tuple-v2-r6`, `tuple-v5-r4`, `tuple-v9` | Respectively `R (E)` with additional Length/CFM findings, `R (E)`, and `V (E)` value/version findings. V2/R6 is not a V-value failure. |
| Global key Length type/value; writer presence / Arlington | Explicit 40/128/256-bit originals; input-only V1 and V2 omitted/default40 positives | `global-length-bits`, `length-type`, `length-absent` | `Length (E)` value, type and writer required-key findings. An input default is not an output failure waiver. |
| P presence/type and reserved bits / Arlington | R6, unrestricted and restricted originals | `p-absent`, `p-type`; `p-reserved-1`, `-2`, `-7`, `-8`, `-13`, `-32` | Required/type/special-case `P (E)`. Each bit has its own control. |
| R2 reserved permission bits / Arlington | RC4-40/R2 | `r2-reserved9` clears bit9 | Special-case `P (E)`. Bits9–12 must be set for R2. |
| PDF2 writer accessibility rule / Arlington | R6 PDF2 bit10 set; separate input bit10-clear positive | `p-bit10-output` | Output special-case `P (E)`; the input model accepts the independently encrypted bit10-clear positive. |
| Serialized signed32 P / pdfcpu | R5 and R6 originals; derivative qualification extends to every admitted family | `p-range` changes only P from −4 to 4294967292 | Exit1 plus exact `out of signed 32-bit range` diagnostic. Arlington/qpdf wrapping does not cover this rule. See the derivative qualification below. |
| O/U presence and exact revision-specific lengths / Arlington | Legacy 32-byte O/U and R5/R6 48-byte O/U originals | `{o,u}-{absent,short,long,type}`; remove entry, remove/append one octet, or replace with integer | Required/type or exact-string-length `O (E)` / `U (E)`. |
| OE/UE/Perms presence / Arlington | R5/R6 originals | `{oe,ue,perms}-absent` | Special-case `Filter (E)` explicit presence guard. The engine missed these absent keys when only conditional Required was used. |
| OE/UE/Perms exact type/length / Arlington | R5/R6 originals: 32/32/16 bytes | `{oe,ue,perms}-{short,long,type}` | Exact-string-length or wrong-type finding on `OE (E)`, `UE (E)`, or `Perms (E)`. |
| CF dictionary presence/type and required StdCF / Arlington | R6; V4 RC4 and AES128 | `cf-absent`, `cf-type`, `cf-stdcf-absent` | `CF (E)` required/type findings, or required `StdCF (CryptFilterMap)`; missing CF also triggers the Filter guard. |
| Closed CF map / Arlington | StdCF-only original | `cf-extra` adds unused Custom | Exact informational `unknown key 'Custom' ... CryptFilterMap`. The qualified profile rejects this finding although upstream severity is Info. |
| CFM presence and method/global relation / Arlington | V4/V2, V4/AESV2, V5/AESV3 originals | `cf-method`, `cf-method-absent`; research-only `cf-method-v4-consistent` also changes the local byte length to16 | `CFM (CryptFilter)` required/value findings; the consistent local AESV2 control proves the global relation is actually evaluated. |
| CF Length type, units and current AESV3 presence / Arlington | Canonical16/32-byte originals; omitted AESV2 positive | `cf-length-bits`, `cf-length-type`, `cf-length-absent` | Value/type `Length (CryptFilter)`; absence is detected by `CFM (CryptFilter)` special-case guard. Conditional Required alone did not detect it. |
| DocOpen AuthEvent / Arlington | Explicit and omitted DocOpen originals | `cf-event`, `cf-event-type` | Value/type `AuthEvent (CryptFilter)`, not CF. |
| All-content stream/string selectors / Arlington | Explicit matching StdCF originals | `{stmf,strf}-{absent,identity,type}` | Missing selectors: `Filter (E)` presence guard; present invalid selectors: value/type `StmF (E)` / `StrF (E)`. |
| EFF inheritance or matching StdCF / Arlington | Original absent EFF and explicit matching EFF | `eff-identity`, `eff-type` | Value/type `EFF (E)`. |
| Metadata type and selected output scope / Arlington | True output and retained V4 false-input originals | `encryptmetadata-type`, `metadata-clear-output` | Type/special-case `EncryptMetadata (E)`; input V4 false passes, output false fails. |
| AESV2 minimum version / Arlington | AES128 PDF1.6 | `aes128-version15` | Special-case `CFM (CryptFilter)`. |
| R3 minimum version / Arlington | RC4-40/R3 PDF1.4 | Research-only `rc4-r3-version13` | Special-case `V (E)`. |
| PDF1.7 R6 extension declaration / Arlington | Original EL8 catalog | `extension-missing`, `extension-level`, `extension-base` | Special-case `V (E)`; wrong BaseVersion also has a `DevExtensions` finding. Explicit path-presence guards are necessary. |
| Per-stream Crypt must be first and unique / Arlington | Scalar StdCF and first-array StdCF originals | `crypt-array-second`, `crypt-array-duplicate` | Value finding on `1* (ArrayOfFilterNames)`. |
| Per-stream Crypt scope and decode parameters / Arlington | Scalar and array StdCF originals; ordinary scalar/array Flate parameters | `crypt-name-identity`, `crypt-name-missing`, `crypt-array-identity`, `crypt-array-name-missing`; research-only missing/null array parameters | Special-case `Filter (Stream)`; ordinary Flate positives remain accepted. The missing/null cases cannot be substituted with a project observer. |
| Decrypted Perms mask / pypdf | R6 authentication success | `perms-plaintext-0`: xor decrypted byte0 by1, then AES-ECB re-encrypt with the original key | Authentication still succeeds; exact permission warning is detected and converted to failure. |
| Four reserved decrypted Perms octets / pypdf | Same R6 positive | `perms-plaintext-4`, `-5`, `-6`, `-7`: mutate each byte separately | Four independent successful-authentication cases each produce the same exact permission warning. |
| Decrypted Perms metadata and marker / pypdf | Same R6 positive | `perms-plaintext-8`, `-9` | Successful authentication plus exact permission warning. The last four random bytes are deliberately unconstrained. |

The research control generator is
`/tmp/t78-audit-external/final/reproduce-controls.py`; it creates 87
positive/control PDFs and a `control-recipes.json` containing the parent
fixture, mutation, artifact hash and authoring-source hash. It requires the
regenerated R6 positive to equal the checked-in original before mutating it.
The mutations operate on the original independently authored dictionary or
catalog/stream declaration and regenerate object offsets. The Perms controls
decrypt/re-encrypt only that original field with the original authoring key;
O/U/OE/UE and the other encrypted content remain unchanged. Original binary
PDFs include the ISO1 §7.5.2 binary marker comment.

Reproduction entrypoints, using only temporary output directories:

```sh
python3 /tmp/t78-audit-external/final/build-overlays.py "$PINNED_TSV_LATEST" "$EMPTY_MODEL_DIRECTORY"
python3 /tmp/t78-audit-external/final/reproduce-controls.py
python3 /tmp/t78-audit-external/final/run-probes.py
python3 /tmp/t78-audit-external/final/check-repository-controls.py
```

`build-overlays.py` checks each base/replacement file hash before copying.
The probe scripts record tool status, completion marker, named findings and
source/report hashes. `observations.json` holds 200 research observations:
both models over 87 controls, eight further retained-input positives per
model, both grammar checks, and eight pypdf permission observations. The
repository-control probe separately checks the implementation task's
`T78-controls/rules.json`; changes to that corpus require regenerated
qualification evidence. Reports containing raw credential values or rendered
binary encryption fields must not be retained as public evidence.

For actual writer outputs, Catalog/Extensions may be in encrypted object
streams. Arlington must receive a known authenticating ASCII user or owner
credential for traversal; a failed textual password followed by a trailer
inspection does not prove the encrypted extension declaration. Keep the
separate exact-byte user/owner observations for unusual password encodings.
For visual checking, qpdf can take a transient hex password file using
`--password-mode=hex-bytes --suppress-password-recovery --decrypt
--deterministic-id`, after which the independently rendered temporary
plaintext derivative is removed. Bind both the original encrypted artifact
hash and derivative hash to the visual observation; do not retain the
plaintext derivative or claim that it replaces validation of the original.

### Signed permission range and the separately identified pdfcpu derivative

Arlington's PDFium shim obtains integer values through `CPDF_Number::GetInteger`.
Actual probes used `[fn:Eval((@P<0) && (@P>=-2147483648))]` with both `bitmask`
and `number` model types. Each rejected a P of 1 but accepted both −4 and the
out-of-range literal 4294967292. Thus the predicate executes after the
original magnitude has been lost. Neither changing the model type nor adding
another bit predicate closes this rule. The public source is
[`ArlingtonPDFShimPDFium.cpp`](https://github.com/pdf-association/arlington-pdf-model/blob/fe4a1a8897ec07f674c73160c35d748b29052f8f/TestGrammar/src/ArlingtonPDFShimPDFium.cpp);
the isolated observations are `final/p-range-engine-observations.json`.

Unmodified pdfcpu v0.15.0 does retain and reject that signed32 violation, but
its strict crypt-filter validation incorrectly blocks valid pre-PDF2
Standard-handler byte-valued or omitted CF Length before the permission
check is reached. The final `pdfcpu-t78-r1` security patch makes only these
CF corrections: preserve valid optional absence, reject a present
non-integer Length, and interpret Standard-handler Length as bytes for every
PDF version. The public-key path and original `validatePermission` signed32
predicate remain unchanged. The source justification is ISO1 Table 25 and the
current ISO2 Table 25 corrections, with the different AESV3 presence rule
still supplied by Arlington.

| Derivative build input | Exact identity |
| --- | --- |
| Upstream release / commit | pdfcpu v0.15.0 / `f2686555086a2e76dc19f602ea1897f6e3baae4d` |
| [Source archive](https://codeload.github.com/pdfcpu/pdfcpu/tar.gz/f2686555086a2e76dc19f602ea1897f6e3baae4d) | SHA-256 `3f460172e40cf5870f3f826d7816e94dc6b63d116626919f6dd1f51d3e15a6bd`, 279035988 bytes |
| Original `pkg/pdfcpu/crypto.go` | SHA-256 `52b78c08718d90bf8ca46f319e0a544f5c11d28e6af54209f6b25728b64c35a0` |
| Final patch | SHA-256 `47bb198f3241339b9122befaff19bae1becdcc931fb89da9e29e16f1f137098d` |
| Patched `crypto.go` | SHA-256 `8abd773d7f9d848186972ea3aebeba034bcccd9dba8b3af9b852b7a1f92cb7d4` |
| Upstream `go.mod` | SHA-256 `37265c62ca3a192cb2f92317514891e0abbbaf919278393987d5752df2d2caf8`; Go 1.25.0 language requirement |

Temporary patch, complete patched source, source identity, build recipe and
qualification command are under
`/tmp/t78-audit-external/pdfcpu-t78-r1/`. Its `qualify.py` authors eleven
positive representations, eleven corresponding out-of-range P controls,
and eight malformed V4 CF controls. Each range control must fail with the
actual signed32 marker; an unrelated validation failure is not a pass.
The malformed CF controls separately cover present integer Length in wrong
units, a name-valued Length, incompatible CFM and EFOpen for both V4 RC4 and
AES128. Both R3/40 owner constructions use user authentication for this
qualification, because authenticating as an owner is a distinct obligation.

The implementation task built the derivative with Go 1.25.0 and
`CGO_ENABLED=0`; all thirty qualification probes passed in the retained
temporary result. The final executable receives a distinct
`v0.15.0-folio-t78-r1` version identity. The release evidence must bind the
final versioned binary, compiler/module graph, security patch and per-product
observations; the earlier development binary hash is deliberately not frozen
as the final candidate identity here. This is still separate from a passing
candidate environment matrix. Do not rename the upstream checker identity or describe this
project-patched derivative as unchanged upstream. Existing Arlington
malformed-CF findings remain mandatory; this focused derivative is not a
replacement for the other dictionary/Perms rules or a project-authored
semantic observer relabeled as an external validator.

### Optional V2 key length and the separately identified qpdf derivative

ISO1 Table 20 makes global Length optional for V2 and defaults it to 40 bits.
The original V2/R3 40-bit fixture with that entry omitted succeeds through
Native, pypdf and the qualified pdfcpu derivative, but unmodified qpdf 12.4.0
fails ordinary credential authentication. Its
[`QPDF_encryption.cc`](https://github.com/qpdf/qpdf/blob/babad179ce5db9a21635c8d1ac17baa59637eada/libqpdf/QPDF_encryption.cc#L847)
leaves a guessed 128-bit key length when the V2 entry is absent. Separately,
[`QPDFWriter.cc`](https://github.com/qpdf/qpdf/blob/babad179ce5db9a21635c8d1ac17baa59637eada/libqpdf/QPDFWriter.cc#L1099)
reads that missing entry as an integer while preparing preserved encryption
for `--check`, producing a null-object warning. Neither rejection nor warning
is a reason to reject the valid profile representation.

The final `qpdf-t78-r1` patch changes only two conditions: choose 40 bits in
authentication for V2 absent/null Length, and preserve the existing five-byte
writer default for that same case. A null dictionary value is equivalent to
an absent entry under ISO1 §7.3.9. Present values retain their prior paths.
There is no input mutation, warning suppression, file-key option, changed
owner algorithm, or skipped credential authentication in this correction.

| qpdf derivative input | Exact identity |
| --- | --- |
| Upstream release / commit | v12.4.0 / `babad179ce5db9a21635c8d1ac17baa59637eada` |
| [Source archive](https://codeload.github.com/qpdf/qpdf/tar.gz/babad179ce5db9a21635c8d1ac17baa59637eada) | SHA-256 `f7ef5f6781bba5f91b1b2921b0484d882ab48e19c28d2f6bc03daf6ca3dafd65`, 19366330 bytes |
| Original / patched `QPDF_encryption.cc` | `8b4a371e58a346d3526bf172da59bf26787552cb898673587d19d4ae0f4b58c7` / `f8e97c6a18bc716431d84cce5fd1a0bd23f882724a421ea89100d55a2c1797f8` |
| Original / patched `QPDFWriter.cc` | `d941f1f89447e815df7e224de4e3b1ab351c53a1573d45b16be12b7e1a99ccbe` / `49eb8d713ced98ffca390931afa8cc781255e5b69585a73060604c76819e788b` |
| Final two-condition security patch | SHA-256 `c0933d4983f2e186f68efb5ae36c99bfa215091aefb7afe9387c62287dc8f9c5` |

Patch, upstream/patched files, source/license identities, build recipe and
`qualify.py` are under `/tmp/t78-audit-external/qpdf-t78-r1/`. The independent
qualification creates sixteen positives and four negatives. Positives cover
V1/V2 explicit/omitted Length with both user and owner credentials, V2 null
default, the literal-ISO owner construction authenticated as user, and the
unaffected 128/256-bit profiles. Each requires both warning-free `--check`
and ordinary credential-based `--decrypt`. Wrong credentials, a name-valued
Length, explicit 128 on 40-bit ciphertext and explicit 0 remain failures.
Temporary hex-password files and decrypted derivatives are deleted; safe
records contain categorical results and original/derivative/diagnostic
hashes. The implementation task must retain the final versioned derivative's
build/runtime identities and executed qualification. This focused correction
does not make qpdf a standards validator or change its R3/40 owner convention.

## PDF 2.0 normative access and claim limits

The primary normative R6 authority is ISO 32000-2:2020 §7.6.4, especially
Algorithms 2.A/2.B and 8–13, together with §7.6.6. The [official no-cost
distribution](https://pdfa.org/sponsored-standards/) routes to the PDF
Association store, whose [cart notice](https://www.pdfa-inc.org/cart/) requires
named licensing/registration. No account, checkout, identity submission or
order was performed during this read-only audit. A previously licensed copy
can be used locally without redistributing it.

Freely accessible primary materials include the
[current clause-7 errata](https://pdf-issues.pdfa.org/32000-2-2020/clause07.html),
which correct the R6 hash construction and AES-ECB permission-block operations;
the [normative-reference directory](https://pdfa.org/iso-32000-normative-references/);
[RFC 4013](https://www.rfc-editor.org/rfc/rfc4013)/[RFC 3454](https://www.rfc-editor.org/rfc/rfc3454);
and NIST [AES](https://csrc.nist.gov/pubs/fips/197/final) and
[Secure Hash Standard](https://csrc.nist.gov/pubs/fips/180-4/upd1/final)
specifications. These do not replace missing clauses of the complete PDF
standard. The 2017 FDIS from the historical T16 note is a draft; its original
Adobe URL returned 404 during this audit. Neither that draft, the EL8
presentation, the API, nor a validator implementation can be relabeled as a
current normative PDF 2.0 source.

## API-document identities and remaining handoff

The following exact 7.2.6 pages were fetched from
`https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/CLASS.html`.
Their original HTML was read temporarily outside the repository; it is not
redistributed. Hashes identify the documents consulted, including their site
wrapper, not a stable upstream content-addressed publication.

| Class | Fetched HTML SHA-256 |
| --- | --- |
| `PdfReader` | `eb44addd930b306081401eacb58a5b58cae4a0f8e1606f258c61527ed5944c95` |
| `PdfWriter` | `e2bc9eb63a82f069f54346b3f16c4a8be492a3a4a7b4ffad4dd369799f70210d` |
| `PdfDocument` | `cf9168cc576ddfa2d8a8d19742e88b7dc139c155e2d0327dd128a35bf862e30e` |
| `ReaderProperties` | `44719046945511db417ef8d7e5862e81940f737cd1c3aad5e1cad2c6234c5e3e` |
| `WriterProperties` | `30480ff9cf56502176542908efc34de7da57ad0f7dc382603604802af9eb7f7b` |
| `EncryptionConstants` | `9c285d59967ab828d5df27c3bfb7ae2435896720aa69b6d9a0ba9270f5df455d` |
| `EncryptionProperties` | `e07830e9b1cbd4d8286af58922d62375b54359991fc54840b2b8231a916a3241` |
| `PdfVersion` | `6a4f106dab7f5ed34df1fe14b2e83e423bb6d4dd677a9ed2cb21f109b7089d45` |
| `PdfEncryptor` | `cd9635d334dcd32599e6591683e6d06f66d6be9b22bf9a44835ec52de6ce4ae5` |
| `StampingProperties` | `915fc3d02f897fe134ee1800b9831cc010dc70a7cf516f86394bdc633bf44b4d` |

The exact [7.2.6 constant-value page](https://api.itextpdf.com/iText/java/7.2.6/constant-values.html)
was also fetched and its fourteen `EncryptionConstants` numeric values were
checked directly. Its HTML SHA-256 is
`d8269cb916d7b3bd3fc49c1365f9dd1953242cda70728679938598a7dba06af8`.

Final certification must connect the input/output distinctions, both owner
constructions, successful cases and qualified standards controls above to
retained public Native/Facade observations. The research handoff does not
replace those candidate-specific records.
Only final passing syntax, standards, semantic and visual evidence on Ubuntu
24.04 Linux x86-64 × JDK 8/11/17/21 × both Native execution profiles can certify
the baseline. Matching Facade evidence records its actual IN_PROCESS execution.
#79/#80 and the aggregate remain separate, incomplete obligations.

# T80 embedded-files-only password-security profile audit

Researched: 2026-10-02. Comparison baseline:
`08928c8d08de677a99f092c4ebd6bbdaa64ef6ba`.

This is the source and coverage profile for the sole execution contract
`/workspace/contracts/issue-80-contract.md`, SHA-256
`2d57acbb1f84f7d83d2dfa2d6512f84e15dcc82151d456d9db06fdd273c2d871`.
It does not certify a candidate. The contract owns the required behavior:
`document.version-password-security.attachments`, Foundation member
`password-attachments`, Acceptance Profile `T32-password-embedded-files-only`.
The [T78 audit](T78-baseline-profile-audit.md) and
[T79 audit](T79-clear-metadata-profile-audit.md) continue to define their separate
profiles and credential rules. A backend rejection cannot waive a required
success in this profile.

## Sources, identities and clean-room boundary

| ID | Primary material consulted and claim boundary |
| --- | --- |
| F | The supplied contract, the project-owned [Foundation requirements](../../capabilities/foundation-requirements.yaml), [password-security contract](../pdf-version-password-security.md), [attachment contract](../metadata-navigation.md), and [ADR-0021](../adr/0021-use-secure-pdf-and-encryption-defaults.md). These own Folio behavior; they are not universal PDF rules. |
| ISO1 | Adobe-authored *PDF 32000-1:2008*, §§7.4.10, 7.6, 7.11.3–7.11.4, Tables 14/20–26/44–46. The [Adobe origin][ISO1] and the link in the [PDF Association archive][ARCHIVE] returned 404 during T80. The document was obtained from the [public mirror][ISO1_MIRROR] and reread locally: SHA-256 `2dc784890e65da6c324d46ece182fb4705b751e49587b1bbe9c3225c25fed4d3`. This is the identity actually read, not T78/T79's different downloaded-file identity. Copyright Adobe; temporary copy, not redistributed. |
| EL3 | Adobe's June 2008 [BaseVersion 1.7 / ExtensionLevel 3 supplement][EL3], §3.5, downloaded and reread. SHA-256 `638f531b57ceb50b4f0b86a6740a57438ccecb0e434e32f0209d9c8200ecc44b`, matching T78. This extends crypt-filter entries to V5 and supplies R5/AESV3 algorithms. Copyright Adobe; not redistributed. |
| ISO2 | [ISO 32000-2:2020][ISO2] and the public [clause 7 corrections][EC7]. The complete licensed current standard was not obtained; the official [no-cost distribution][ISO2_ACCESS] still routes through registration/licensing. No account, checkout or identity submission was performed. Corrections establish their displayed amended provisions, not unseen clauses. |
| EL8 | PDF Association, Roman Toda, [*Encryption with PDF 2.0*][EL8], 2017-05-15, slide 8 and crypt-filter discussion. This supplies the public PDF 1.7 ADBE EL8 interoperability convention, not a normative Adobe EL8 supplement. |
| API | Fixed Reference Suite **7.2.6 public Java API documentation**: [WriterProperties][WRITER], [EncryptionConstants][CONSTANTS], [constant values][VALUES], [PdfReader][READER]. Only public interface facts and documented selector semantics were used. |
| PREP | IETF [RFC 4013][SASL] and [RFC 3454][STRINGPREP], incorporated by the password-security profile; Unicode 3.2 SASLprep remains the versioned AES-256 preparation rule. |

No iText implementation/source, source-link target, resource, fixture, binary,
decompilation or black-box output was consulted or adopted. No new dependency,
fixture, model or product code is introduced by this research. Its tables are
independently written findings; downloaded standards/API documents remain
temporary reference material. Existing independent tools require fresh
attachment-specific qualification; their previous results are not T80 evidence.

The four downloaded API HTML documents have the same hashes recorded in T78
and T79. These hashes identify the HTML consulted, including its wrapper;
they do not make the upstream website immutable.

| API document | SHA-256 |
| --- | --- |
| `WriterProperties` | `30480ff9cf56502176542908efc34de7da57ad0f7dc382603604802af9eb7f7b` |
| `EncryptionConstants` | `9c285d59967ab828d5df27c3bfb7ae2435896720aa69b6d9a0ba9270f5df455d` |
| `PdfReader` | `eb44addd930b306081401eacb58a5b58cae4a0f8e1606f258c61527ed5944c95` |
| `constant-values.html` | `d8269cb916d7b3bd3fc49c1365f9dd1953242cda70728679938598a7dba06af8` |

## Normative rules and the declared interoperability qualification

Two source qualifications must remain visible. ISO1 §7.6.3.1 requires the same
user and owner password when only attachments are encrypted. It also restricts
the Standard handler's `StdCF` to `DocOpen`. Generic §7.6.5/Table 25 defines
`EFOpen` for attachment access. EL3 §3.5.2 retains the Standard-handler
`DocOpen` wording; the current PDF2 §7.6.4.1 correction does likewise.
[ISO1][ISO1_MIRROR]; [EL3][EL3]; [EC7][EC7].

F nevertheless explicitly requires attachment authentication boundaries,
`EFOpen` consistency, and independently proven user/owner behavior. Therefore
the declared Folio profile **includes and tests** `StdCF/EFOpen` and distinct
user/owner credentials as qualified password-attachment interoperability.
They must not be described as unqualified normative Standard-handler
conformance or excluded to accommodate a backend. Equal-password,
`StdCF/DocOpen` input is the separately identified source-conforming case.
An `EFOpen` filter selected by `StmF` or `StrF` has document-open semantics
under Table 25 and cannot establish the deferred attachment boundary.

PDF 2.0 supplies the normative R6 algorithm/version path. That statement
does not erase the authentication-event qualification above or certify every
clause of ISO2. PDF 1.7/R6 uses the qualified EL8 convention of ADR-0021;
R5 instead uses Adobe's published EL3 extension. Keep these claims separate.
[ADR-0021](../adr/0021-use-secure-pdf-and-encryption-defaults.md); [EL8][EL8].

## Input/output algorithm and version profile

Every attachment-only success must establish clear ordinary strings/streams
and encrypted actual embedded payloads. New output explicitly selects
`StmF /Identity`, `StrF /Identity`, `EFF /StdCF`, `EncryptMetadata false`, and
the qualified `StdCF/EFOpen` boundary. `EFF` is a PDF 1.6 entry; earlier
crypt-filter representations can select each embedded stream explicitly.
Algorithm/version minima and exact encryption-field validation carry forward
from T78. F; ISO1 Table 20; EL3 §3.5.

| Case | Required input | Required new/rewrite output | Claim and Facade treatment |
| --- | --- | --- | --- |
| EF-RC4 | RC4-128, `V4/R4`, `CF/StdCF/CFM /V2`, 128-bit file key. PDF 1.5 explicit stream-`Crypt` representation; PDF 1.6+ may use `EFF`. | Native `RC4_128`, PDF 1.7, explicit request-scoped Legacy Security Mode, attachment scope. Do not emit baseline `V2/R3` while claiming selective coverage. | Legacy qualified scope. The Reference RC4-128 selector suppresses the attachment flag; Folio's safe rejection of the contradictory Facade selector is a declared divergence, not a backend waiver of Native output. |
| EF-AES128 | AES-128, `V4/R4`, `CFM /AESV2`, 128-bit key; PDF 1.6+. | PDF 1.7 with explicit Legacy Security Mode and attachment scope. | Facade AES-128 plus selector 24 gives mode `26`. |
| EF-R5 | AES-256, `V5/R5`, `CFM /AESV3`, 256-bit key, PDF 1.7 with appropriate ADBE EL3 declaration. | No new R5 output: authorized AES-256 rewrite emits R6. | Required legacy reading, including user/owner preparation and permission-block validation. Facade reports the AES-256 attachment scope, without exposing a revision output selector. |
| EF-R6-17 | AES-256, `V5/R6`, `CFM /AESV3`, 256-bit key, PDF 1.7 with the established ADBE BaseVersion 1.7 / ExtensionLevel 8 declaration. | PDF 1.7, explicit attachment scope; no Legacy Security Mode needed. | Qualified EL8 algorithm/version compatibility and qualified `EFOpen` scope. Facade mode `27`. |
| EF-R6-20 | The same R6/AESV3 tuple under PDF 2.0. | Explicit PDF 2.0, R6; output sets accessibility permission bit 10. | Normative PDF2 R6 algorithm path; qualified event/credential distinctions remain explicit. Facade mode `27`. |
| EF-LEGACY-20 | Otherwise consistent supported R4/R5 attachment tuples under PDF 2.0, preserving T78's reader/output distinction. | No new legacy PDF 2.0 output. | Deprecation alone is not the retained profile's reader prohibition; this is the T78 source-grounded interpretation, not a new complete reading of ISO2 Table 21. |
| EF-NONREPRESENTABLE | Preserve baseline fixed RC4-40 `V1/R2`, `V1/R3`, `V2/R3` and RC4-128 `V2/R3` as their actual all-content scopes. They cannot establish selective EFF routing. | Fixed `RC4_40` attachment-only output fails safely. | The fixed migration selector does not require every imaginable V4 RC4 key-length arrangement. RC4 48–120-bit possibilities remain T78's explicit bounded-profile exclusion. |

The table is derived from F, [ISO1 Tables 20/21/25][ISO1_MIRROR],
[EL3 §3.5][EL3], [EC7 §§7.6.4/7.6.6][EC7], and the fixed
[selector documentation][WRITER]. It must bind to successful public behavior,
not merely an enum or dictionary label.

The qualified PDF 1.7 extension declaration resolves ordinary indirect values
but retains PDF object types: Extensions and ADBE are non-stream dictionaries,
BaseVersion is the name `/1.7`, and ExtensionLevel is an integer at least 3 for
R5 or 8 for R6. Strings and real numbers with matching text/numeric values do
not meet those types. Optional Type entries retain their Extensions and
DeveloperExtensions names. ISO1 §7.12, Tables 49/50, and EL3 §3.6.4 ground
these predicates; separately authored type controls
and indirect positive originals qualify the product and independent adapter.

No protection request still means an unprotected new document. A password
protection request retains secure all-content AES-256/R6 defaults. Attachment
scope is explicit, and Legacy Security Mode permits only the requested
obsolete output; it cannot alter the default or another request. F; ADR-0021.

## Routing, dictionary and original-byte evidence

| Rule | Required positive observation | Distinct defect/control |
| --- | --- | --- |
| EF-ROUTES | Resolve effective filters: explicit stream `Crypt` overrides `EFF`; otherwise actual embedded streams use `EFF`; absent `EFF` inherits `StmF`. Omitted ordinary `StmF`/`StrF` mean Identity. Test explicit EFF, explicit stream selection, and absent EFF with every protected attachment explicitly selecting `StdCF`. | Removing EFF without a stream override leaves attachment bytes under Identity; a label cannot turn that into encrypted coverage. |
| EF-CRYPT | Admit `/Crypt` as the first filter, naming known `StdCF`, followed by ordinary decoding filters. Ordinary streams may explicitly select Identity. Avoid double decryption. | Duplicate/late Crypt, wrong parameter type/shape/Type, unknown Name, missing Name (defaults to Identity), and an attachment selecting Identity. |
| EF-IDENTITY | Observe ordinary page content, Info strings, attachment names/descriptions and document XMP without supplying an attachment credential. Inspect original files, allowing only ordinary non-crypt decoding. | Encrypting an ordinary stream/string or observing only a decrypted derivative must fail the declared coverage rule. |
| EF-RELATION | Identify actual embedded payloads through file specification `EF` references, including names-tree and file-attachment references. `EmbeddedFile` stream Type is optional; if present it must be correct. The managed attachment contract retains #73's EF paths; related-file navigation outside those paths is not a new #80 capability. | A suggestive Type on an unrelated stream is insufficient proof. Untyped actual EF streams cannot evade protection; inconsistent EF or an alias through an ordinary-content path cannot bypass authorization. |
| EF-EVENT | Independently record absent/default and explicit `DocOpen` input separately from qualified `EFOpen`. New output's protected filter is used only for embedded payloads. | Unknown/wrong-type event, EFOpen routed through ordinary default selectors, or attempted protected access without authorization. |
| EF-FIELDS | Preserve the Standard-handler field/type/length and V/R/CFM/key agreement checks inherited from T78. Global Length is bits; Standard CF Length is bytes: 16 for the fixed R4 profiles, 32 for AESV3. Preserve valid older omission rules; certified new AESV3 output carries 32. | Wrong units, types, missing required applicable fields, contradictory algorithms/revisions and unknown filters need separate controls. |
| EF-KEY-PERMS | Derive/unlock the actual revision-specific file key independently and compare decrypted payload bytes/hashes. R4 uses its actual metadata flag during derivation; R5/R6 validate the complete meaningful Perms prefix against P and that flag. Output declares false. | Flag-only/key disagreement, P mutation, each reserved byte, metadata marker, `adb` marker and malformed authentication-entry lengths. Re-encrypted Perms controls must retain valid password authentication. |

Routing/event rules come from [ISO1 §§7.4.10/7.6.5, Tables 14/20/25][ISO1_MIRROR];
attachment identity from [§§7.11.3–7.11.4, Tables 44/45][ISO1_MIRROR].
Revision/key/permission checks retain [T78](T78-baseline-profile-audit.md) and
[T79](T79-clear-metadata-profile-audit.md), with [EL3][EL3] and [EC7][EC7]
as the primary extension/R6 authorities. The metadata flag alone never proves
which data is encrypted. Any admitted flag/default variant must retain its
actual key/Perms meaning; it cannot be silently rewritten during authentication.

Standard RC4/AES-CBC password encryption does not provide general authenticated
ciphertext integrity. F's tampering checks concern the independently tested
dictionary, permission-block, routing and credential predicates; exact payload
comparisons detect corpus corruption. Do not invent a universal arbitrary
ciphertext-tamper rejection promise or add the out-of-scope PDF MAC/AES-GCM
extensions. ISO1 general encryption algorithms; F non-goals.

## Authentication, permissions and safe public paths

The required qualified `EFOpen` boundary permits opening and observing ordinary
clear document content without an attachment credential. It does not grant an
owner role, a file key, attachment extraction or protected publication. For
`DocOpen` input, the declared event requires document-open authorization; clear
bytes remain separately observable in original-file evidence. Missing and
incorrect credentials never disclose protected attachment bytes. An explicitly
supplied invalid credential may fail at the existing credential-validation
boundary; an absent credential is not an empty credential. F; ISO1 Table 25.

| Public/evidence boundary | Frozen requirement |
| --- | --- |
| Correct user and owner | Recover exact attachment bytes. Enforce the declared `/P` word through Folio's mapped permissions. Independently prove the owner algorithm; unrestricted user permissions alone do not establish owner authority. |
| Equal credentials | Preserve this source-conforming case; record independently established authority. Do not pretend an equal-password owner proof is a distinct restricted-user authentication. |
| Preparation | Retain T78's exact legacy byte bridge/32-byte truncation and AES-256 Unicode 3.2 SASLprep/127-byte UTF-8 truncation, including a split multibyte boundary. Empty, equal, Unicode, prohibited/preparation failures and destroyed credentials retain their applicable cases. |
| Missing/wrong credential | Clear document observation succeeds where EFOpen declares it. Protected bytes fail before disclosure, whether reached by attachment query, PDF Value, resource, copy or publication. No anonymous owner authority. |
| Permissions | Preserve all eight mapped choices and original signed32 P observation. Folio's attachment extraction authorization is product enforcement; ISO1 Table 22 does not add a separate attachment-specific permission bit. PDF2 retains T78's writer/accessibility reader distinction. |
| Mutation/publication | Keep predecessor owner-authorized protected rewrite, named-Source donor-strength, version, incremental and Existing Signature restrictions. Clear page accessibility does not authorize weakening or accidental attachment copying. Preserve existing Targets and declaration-ordered receipts for pre-publication failure. |
| Ownership/limits | Credentials and streams remain caller-owned. Keys/private intermediate data are execution-local and cleared; outcomes detach. Apply existing bounds to input, decoding, owned memory, temporary storage and elapsed work in both actual Native profiles. |

These are F's public Document Workflow/Facade requirements, carrying forward
[T78](T78-baseline-profile-audit.md) and [T79](T79-clear-metadata-profile-audit.md).
The preparation authority remains [RFC 4013][SASL]/[RFC 3454][STRINGPREP].
Neither successful password authentication nor a plaintext page implies
authorization for another protected path.

## Fixed Facade selector and reader observations

The 7.2.6 API calls the public field `EMBEDDED_FILES_ONLY`, value **24**
(`0x18`), not `ONLY_EMBEDDED_FILES`. It already includes
`DO_NOT_ENCRYPT_METADATA` (**8**). The contract's latter spelling can be a
clearly documented Folio alias; it is not a field observed in the fixed
Reference API. Numeric identity and any alias must agree in the surface
inventory. [EncryptionConstants][CONSTANTS]; [constant values][VALUES].

| Selection/observation | Documented Reference fact or declared Folio result |
| --- | --- |
| AES-128 OR 24 | Successful attachment scope: `2 \| 24 = 26`; ORing 8 again changes nothing. Legacy opt-in remains Folio's policy. |
| AES-256 OR 24 | Successful attachment scope: `3 \| 24 = 27`; explicit R6 secure output. ORing 8 again changes nothing. |
| RC4-40 OR 24 (`24`) | Reference documentation suppresses both exception flags. Folio rejects the contradictory explicit scope safely, preserving the caller's intent. |
| RC4-128 OR 24 (`25`) | Reference documentation suppresses embedded-files-only. Folio rejects that contradictory Facade selection safely; Native RC4-128 V4 attachment output remains separately supported. |
| Algorithm OR only bit 16 | This does not supply the documented field value 24. Do not invent a separately documented Reference flag from one bit; retain malformed-selector rejection. |
| `PdfReader.getCryptoMode()` | Public API returns an encryption-mode integer and references EncryptionConstants; calls before document read fail. Folio round-trip tests must assert actual algorithm plus canonical scope, including mode 25 for admitted RC4-128 attachment input and 26/27 for AES input/output. These expected mappings are Folio's certified contract, not a black-box observation of Reference implementation. |
| `getPermissions`, `isEncrypted`, owner observation | Preserve declared unsigned32 permissions, encrypted-file state even when ordinary data is clear, and independently established owner authority. Anonymous clear access must not become full owner permission. |

Selector suppression is explicitly documented by
[WriterProperties.setStandardEncryption][WRITER]. Reader public method
semantics are from [PdfReader][READER]. Test copied WriterProperties, borrowed
credentials, byte preparation and caller stream ownership through the actual
Facade; its execution remains `IN_PROCESS` on every JDK. No unsupported Stable
stub or fictitious Worker Facade result satisfies the contract.

## Evidence handoff and exclusions

Certification must independently author original input fixtures and single-defect
controls, and examine original emitted products. Each required positive and
actual product receives separate qualified syntax, standards, semantic and
visual observations. Capture ordinary clear-content observations before
supplying an attachment credential. A ciphertext sentinel's absence, syntax
PASS, suggestive Type, producer label or decrypted derivative is insufficient
security evidence. Keep T78/T79 scope predicates intact and separately qualify
the attachment model. F;
[ADR-0023](../adr/0023-require-independent-acceptance-evidence-chains.md).

Bind exact artifacts, candidate/source/contracts/harness/corpus, actual
environment and pinned tools. Retain credential-free categorical findings and
safe hashes; omit credentials, keys, authentication-entry values and their
secret-derived hashes, raw content, private paths and backend exceptions.
Delete private intermediates. Refresh invalidated predecessor evidence in F's
specified dependency order; historical findings retain their identities.

The only certification matrix is actual Ubuntu 24.04 / Linux x86-64 with
JDK 8/11/17/21, Native `IN_PROCESS` and `HARDENED_WORKER`: eight Native tuples,
with actual Facade `IN_PROCESS` observations on each JDK. Windows x86-64 and
macOS x86-64/arm64 remain explicitly uncertified and do not block F0.1.0.
F; [ADR-0040](../adr/0040-certify-only-observed-foundation-environments.md).

Concrete boundaries remain: no new R5 output; no fixed RC4-40 attachment-only
output; no arbitrary key lengths/custom security handlers, public-key/FIPS,
AES-GCM/MAC, unencrypted-wrapper protocol or other downstream security work;
no selectively encrypted ordinary-content or partly clear attachment profile
silently reported as this scope. Source-valid possibilities outside the
bounded profile are not universally invalid PDF. None of these exclusions
permits rejection of a required row above, weakening #78/#79 defaults,
claiming parent #33 release integration, or declaring aggregate completion
without current required-member evidence.

[ISO1]: https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf
[ISO1_MIRROR]: https://www.pdftron.com/downloads/pdfref.pdf
[ARCHIVE]: https://pdfa.org/resource/pdf-specification-archive/
[EL3]: https://web.archive.org/web/20220306152229if_/https://www.adobe.com/content/dam/acom/en/devnet/pdf/adobe_supplement_iso32000.pdf
[ISO2]: https://www.iso.org/standard/75839.html
[ISO2_ACCESS]: https://pdfa.org/resource/iso-32000-2/
[EC7]: https://pdf-issues.pdfa.org/32000-2-2020/clause07.html
[EL8]: https://pdfa.org/wp-content/uploads/2018/05/1415_Toda.pdf
[WRITER]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/WriterProperties.html
[CONSTANTS]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/EncryptionConstants.html
[VALUES]: https://api.itextpdf.com/iText/java/7.2.6/constant-values.html
[READER]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html
[SASL]: https://www.rfc-editor.org/rfc/rfc4013
[STRINGPREP]: https://www.rfc-editor.org/rfc/rfc3454

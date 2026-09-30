# T79 clear-metadata password-security profile audit

Researched and initial profile frozen: 2026-09-30. Comparison baseline:
`7420a656d27d17b82c7632be7c6214f8a657a22f`.

This note supplies the source-traceable profile for the sole execution contract
`/workspace/contracts/issue-79-contract.md`, SHA-256
`cd60f23252a7aa9533ea0e3825816839a664d57929276978c3b4a3dbb3b20461`.
It is research and a coverage specification, not a claim that any candidate has
passed certification. The contract owns product scope. The
[Foundation requirements](../../capabilities/foundation-requirements.yaml)
`slice-79-1` through `slice-79-4` and
[Foundation profile](../../capabilities/profiles/foundation-release.md)
`T32-password-clear-metadata` trace the required behavior to
[issue #79](https://github.com/zerocloud-sdk/folio-pdf/issues/79).

The owned capability is `document.version-password-security.clear-metadata`,
Foundation member `password-clear-metadata`, dependent on `password-baseline`
and inheriting the parent's external `document.value.inspect-patch` gate.
The aggregate security obligation, parent #33, and #80 embedded-files-only
remain incomplete. This audit preserves the corrected
[T78 profile](T78-baseline-profile-audit.md); the older
[T16 note](T16-pdf-version-password-security-primary-sources.md) does not
override T78's later reader, credential, or profile corrections.

## Sources and claim boundaries

| ID | Source consulted and authority |
| --- | --- |
| F | The execution contract above; the project-owned Foundation requirements/profile; [ADR-0021](../adr/0021-use-secure-pdf-and-encryption-defaults.md), [ADR-0023](../adr/0023-require-independent-acceptance-evidence-chains.md), [ADR-0040](../adr/0040-certify-only-observed-foundation-environments.md); and the baseline [password-security contract](../pdf-version-password-security.md). These establish product requirements rather than universal PDF restrictions. |
| ISO1 | Adobe's authorized [ISO 32000-1:2008 equivalent][ISO1], §§7.6.1–7.6.5, Tables 20–26, §7.7.2/Table 28, and §14.3/Tables 315–317. The existing temporary PDF was reread and its SHA-256 rechecked: `9de0ca9e8570d6209e8bd48a355be8eb6ec376acfc3fc3ae97cd8730351417ff`. Copyright Adobe/ISO; not redistributed. |
| EL3 | Adobe's June 2008 [BaseVersion 1.7, ExtensionLevel 3 supplement][EL3], §3.5, as previously read and identified in T16/T78; SHA-256 `638f531b57ceb50b4f0b86a6740a57438ccecb0e434e32f0209d9c8200ecc44b`. The [PDF Association archive][ARCHIVE] still identifies this official supplement. The archive's PDF fetch did not succeed during T79, so its clause findings are inherited from the retained audit rather than claimed as a new download. Copyright Adobe; not redistributed. |
| ISO2 | [ISO 32000-2:2020][ISO2] remains the normative PDF 2.0 authority, with public [clause 7 corrections][EC7] and [clause 14 corrections][EC14] reread for this audit. A complete licensed current standard was not obtained. Public corrections establish the amended text they show, not every omitted provision. The T78 normative-access limitation remains explicit. |
| EL8 | Roman Toda, PDF Association, [Encryption with PDF 2.0][EL8], 2017-05-15, slide 8. Reread to confirm the PDF 1.7 ADBE Extension Level 8 signal. This is public interoperability guidance, not a normative EL8 specification. |
| API | Exact Reference Suite 7.2.6 public API pages: [WriterProperties][WRITER], [EncryptionConstants][CONSTANTS], [constant values][VALUES], and [PdfReader][READER]. Interface signatures, constants and documented selector behavior only. No iText source page was opened; incidental implementation search results were ignored. No iText implementation, resource, fixture, binary, decompilation or black-box output was used or adopted. |
| A | PDF Association [Arlington model at `fe4a1a8897ec07f674c73160c35d748b29052f8f`][ARLINGTON], particularly `tsv/2.0/EncryptionStandard.tsv` and `CryptFilter.tsv`, reread locally. This is an independent model, not the ISO standard or automatic rule coverage. |
| P | [RFC 4013][SASL] and [RFC 3454][STRINGPREP], as bound by T78: Unicode 3.2 SASLprep and algorithm-specific truncation. Scope selection does not introduce a different credential scheme. |
| PP | BSD-3-Clause [pypdf 6.1.1 encryption module][PYPDF], specifically its independent `AlgV5.verify_perms` rule and authentication result handling, reread as a checker reference. It is not a complete standards validator. |
| B | Apache PDFBox 3.0.8 source, peeled commit [`9286e47d89d6877005c9d2d0f2fd38793a62519a`][PDFBOX], Apache-2.0. The three temporary source files identified below were inspected only to locate integration constraints. Backend behavior cannot waive F or establish standards correctness. |

The API HTML already downloaded for T78 was reread and rehashed. Live public
API pages were also checked; hashes identify the retained HTML, including its
site wrapper, rather than claiming an immutable upstream web publication.

| Retained API document | SHA-256 |
| --- | --- |
| `WriterProperties` | `30480ff9cf56502176542908efc34de7da57ad0f7dc382603604802af9eb7f7b` |
| `EncryptionConstants` | `9c285d59967ab828d5df27c3bfb7ae2435896720aa69b6d9a0ba9270f5df455d` |
| `constant-values.html` | `d8269cb916d7b3bd3fc49c1365f9dd1953242cda70728679938598a7dba06af8` |
| `PdfReader` | `eb44addd930b306081401eacb58a5b58cae4a0f8e1606f258c61527ed5944c95` |

## Frozen input/output profile

Every successful row has explicit `ALL_EXCEPT_METADATA`, actual
`EncryptMetadata false`, protected ordinary streams and strings, and the
unchanged Standard-handler authentication and permissions contract. A backend
rejection is an implementation gap for a required success row. Input minimum
versions are format constraints; F restricts new output to PDF 1.7 or 2.0.

| Case ID | Algorithm and dictionary | Required input | Required new/rewrite output | Facade selector and source |
| --- | --- | --- | --- | --- |
| CM-RC4 | RC4-128: `V=4`, `R=4`, `CF/StdCF/CFM /V2`; file key 128 bits | Valid crypt-filter input from PDF 1.5, including the already admitted metadata-clear R4 fixtures. | PDF 1.7, explicit request-scoped Legacy Security Mode. RC4 clear metadata requires V4/R4 even though baseline all-content RC4-128 output uses V2/R3. | `STANDARD_ENCRYPTION_128` OR `DO_NOT_ENCRYPT_METADATA` = `9`. F; ISO1 Tables 20/21/25; API. |
| CM-AES128 | AES-128: `V=4`, `R=4`, `CF/StdCF/CFM /AESV2`; file key 128 bits | Valid input from PDF 1.6. | PDF 1.7, explicit request-scoped Legacy Security Mode. | `ENCRYPTION_AES_128` OR `DO_NOT_ENCRYPT_METADATA` = `10`. F; ISO1 Tables 20/21/25; API. |
| CM-R5 | AES-256 legacy: `V=5`, `R=5`, `CF/StdCF/CFM /AESV3`; file key 256 bits | PDF 1.7 with the appropriate ADBE Extension Level 3 declaration. Required legacy input, including owner/user authentication, preparation boundaries and permission-block checks. | No new R5 output. The AES-256 selector writes the secure R6 profile; neither F nor the API adds a revision selector. | Reader reports AES-256 plus bit 8 (`11`); an authorized rewrite selecting AES-256 emits R6. F; EL3 §3.5; T78. |
| CM-R6-17 | AES-256: `V=5`, `R=6`, `CF/StdCF/CFM /AESV3`; file key 256 bits | PDF 1.7 with the established ADBE BaseVersion 1.7/ExtensionLevel 8 convention. | PDF 1.7; explicit clear-metadata scope, no Legacy Security Mode required. Retain the qualified industry-convention label. | `ENCRYPTION_AES_256` OR `DO_NOT_ENCRYPT_METADATA` = `11`. F; ADR-0021; EL8. |
| CM-R6-20 | Same R6/AESV3 tuple | PDF 2.0. | Explicit PDF 2.0; normative R6 profile; set accessibility permission bit 10. | Selector `11` with explicit PDF 2.0. F; ISO2 §7.6.4 and EC7. |
| CM-LEGACY-20 | Otherwise consistent admitted R4 RC4-128/AES-128 or R5 AES-256 tuple under PDF 2.0 | Preserve T78's reader distinction: deprecation alone does not reject an otherwise admitted legacy tuple. Test input separately from output policy. | No new legacy PDF 2.0 output; retain the R6-only project policy. | T78's source-grounded inference from current EC7 and the pinned Arlington R values, not a newly asserted complete reading of ISO2 Table 21. |
| CM-NONREPRESENTABLE | Baseline fixed RC4-40 forms `V=1/R=2`, `V=1/R=3`, or `V=2/R=3` | Preserve their all-content input. These forms have no effective metadata-exception flag and cannot establish this scope. | No clear-metadata output for `RC4_40`. Do not silently claim clear metadata or upgrade the fixed 40-bit selector into a different profile. | Reference API documents that `STANDARD_ENCRYPTION_40` disables bit 8. Folio's explicit-scope Native request must fail safely; a Facade rejection of contradictory `0` OR `8` must be documented as the safe Folio boundary, not described as Reference behavior. ISO1 Table 21; API; F safe-failure requirement. |

The RC4-40 conclusion is about the fixed Foundation/API profile. It is not a
claim that every conceivable V4 RC4 key-length arrangement is forbidden by
PDF. T78's bounded 40/128-bit profile exclusions remain unchanged. Required
clear-metadata success is RC4-128, AES-128 and AES-256. [ISO1][ISO1]; [API][WRITER].

Absence of a security request still produces an unprotected new document;
when password protection is requested, the default remains all-content
AES-256/R6. Clear metadata always requires an explicit scope/bit. Legacy
Security Mode permits only the selected obsolete output and cannot change
another request or the secure default. F and [ADR-0021](../adr/0021-use-secure-pdf-and-encryption-defaults.md).

## Dictionary, key and permission coverage

| Rule ID | Frozen requirement and positive case | Single-defect controls and source |
| --- | --- | --- |
| CM-FILTERS | Standard handler; streams and strings use `StdCF`; `EFF` absent inherits `StmF`, or explicitly matches it where the input version permits that entry. `AuthEvent` absent defaults to `DocOpen`, or explicitly equals it. | Wrong/missing stream or string selector, mismatched/Identity `EFF`, wrong `CFM`, unknown filter name and `EFOpen` cannot be mistaken for this scope. Preserve valid default/matching variants. ISO1 §§7.6.3.1/7.6.5, Tables 20/25; EC7 §§7.6.4.1/7.6.6; T78. |
| CM-LENGTH | Global key length is in bits; Standard crypt-filter length is in bytes: 16 for RC4-128/AESV2 and 32 for AESV3. Preserve edition-appropriate omitted AESV2 length input/output. New AESV3 output explicitly carries 32. | Wrong units, types, omitted required AESV3 output length and contradictory lengths need distinct controls. Do not apply the current PDF 2.0 required-entry rule indiscriminately to older valid defaults. ISO1 Table 25; EC7 Table 25; T78. |
| CM-R4-KEY | R4's file-key derivation incorporates the metadata-exception suffix of four `FF` octets before finishing the initial MD5 digest. Validate both user and owner routes against independently authored RC4 and AESV2 ciphertext. | Correct dictionary flag with a key derived as though metadata were encrypted; flag-only mutation of an authenticating original. R4 O/U are 32-byte strings. ISO1 Algorithm 2(f), Algorithms 3–7, Table 21. |
| CM-R56-KEY | R5/R6 use a random 256-bit file key authenticated/unwrapped through the revision-specific O/U and OE/UE algorithms. The R4 MD5 suffix is not added to R5/R6 derivation. O/U are 48 bytes; OE/UE 32; Perms 16. | Revision/hash/salt/key-wrapper and exact field length/type controls must not be counted as permission checks merely because opening failed. EL3 §3.5; ISO2 §7.6.4; EC7; T78. |
| CM-PERMS | For R5/R6, independent AES-ECB decryption of `Perms` must agree with `/P` in bytes 0–3 (little endian), reserved `FF` bytes 4–7, metadata `F` at byte 8 and marker `adb` at bytes 9–11. The final four random bytes have no fixed expected value. | Mutate `/P`, each reserved byte, metadata byte, and marker independently, re-encrypting with the original file key so authentication remains valid. External pypdf's `verify_perms` checks this prefix, but its caller can warn and still return successful authentication: the qualified wrapper must reject that warning. EL3; ISO2 Algorithms 10/13; EC7 AES-ECB correction; PP. |
| CM-PWORD | Preserve the exact signed 32-bit `/P` word and all eight Foundation permissions. Independent owner proof remains separate from unrestricted user permissions. PDF 2.0 output sets bit 10; input keeps the declared bit observable while applying the reader rule. | Reserved permission bits/range, unrestricted user misclassified as owner, restricted-user extraction/modification, and conflicting signature authority. ISO1 Table 22; T78; F. |
| CM-CRYPT | Preserve admitted ordinary-stream `Crypt` overrides naming the same `StdCF`; decrypt once before later decoding. A document-metadata stream may explicitly select `Identity`, the source-defined means of leaving that stream clear. | Wrong order, duplicate Crypt, wrong decode-parameter shape, unknown names, or `Identity` on protected ordinary data cannot establish this scope. Distinguish a valid clear metadata override from a selectively clear ordinary stream. ISO1 §7.4.10/Table 14 and §7.6.5; T78. |

## What the metadata exception proves

ISO1 Table 21 identifies the **document-level metadata stream**. Section
14.3.2/Table 315 describes a stream with `Type /Metadata` and `Subtype /XML`;
the document catalog's `Metadata` entry identifies its document-level role.
Section 14.3.3/Table 317 separately describes document information strings.
Therefore observing the flag, a suggestive key name, or a readable title is
not proof that the intended XMP stream has the intended protection. [ISO1][ISO1].

| Object or observation | Required treatment/evidence |
| --- | --- |
| Actual catalog XMP metadata | Original independent fixtures and emitted products contain known XMP bytes. Recover and compare those bytes without supplying or deriving a document credential. Ordinary decoding may be needed for compressed metadata; raw unfiltered XMP makes the byte proof especially direct. |
| Document Info `Title`, `Author`, custom text values | Remain encrypted strings under `StrF`; their semantic role as metadata does not grant a stream exception. Verify exact authenticated recovery and absent unauthorized plaintext recovery. |
| Ordinary content streams, strings and other protected streams | Independently decrypt with valid credentials and compare expected bytes. Missing/wrong credentials fail. Plaintext-sentinel absence alone is inadequate because compression or a different serialization also removes a sentinel. |
| Component metadata or misleading metadata-like values | Do not extend the document-level exemption merely from a name or `Type /Metadata`. Component metadata is a real distinct construct under ISO1 §14.3.2/Table 316; a claim about all Metadata streams needs separate authority and cannot be inferred from backend behavior. The implementation must preserve the narrower declared scope. |
| Malformed catalog `Metadata` | A non-stream, absent/wrong required Type/Subtype, or contradictory crypt-filter declaration cannot justify exposing arbitrary content as clear metadata. Safe rejection is consistent with F's malformed-scope criterion; never silently relabel arbitrary content. |
| Metadata stream dictionary strings | Stream-data exemption does not turn associated arbitrary PDF strings into metadata-stream bytes. Keep ordinary string protection under `StrF`. |

The scope does not add unauthenticated Document Workflow sessions. Native
protected Sources still require an explicit valid Password Credential before
caller work; independent byte access to the XMP is the separate observation
that proves the file's metadata exception. Empty passwords are explicit valid
credentials, not absent credentials. F and T78's protected-Source contract.

## Credentials, lifecycle and public coverage

F requires each successful profile above through public creation, reopening
and owner-authorized protected rewrite. The observations expose algorithm,
revision, scope, permissions and authority, with Native `IN_PROCESS` and
`HARDENED_WORKER` and matching Facade calls. The Facade's actual execution
profile is `IN_PROCESS`; labelling a Facade run Worker is false evidence.

| Coverage group | Preserved/new observations required by F |
| --- | --- |
| Credentials | Legacy exact U+0000–U+00FF byte bridge and first 32 bytes; AES-256 SASLprep/Unicode 3.2 and first 127 UTF-8 bytes, including split multibyte boundary. Empty/equal/non-ASCII, invalid forms, missing/wrong/destroyed credentials, independent owner predicate and unrestricted user distinction remain covered. See T78 and [RFC 4013][SASL]/[RFC 3454][STRINGPREP]. |
| Facade | Bit 8 is public; successful selectors are 9/10/11, with reader round-trip, copied properties, exact legacy bytes/strict AES UTF-8, caller stream ownership, permissions and safe failures. Invalid extra selector bits and #80's selector are not admitted. The numeric constants are interface facts from [7.2.6 constant values][VALUES]. |
| Publication | Explicit owner-authorized rewrite; protected named-Source anti-downgrade; clear scope cannot weaken all-content donors. Incremental output preserves existing dictionary/coverage and cannot implicitly change scope. Signature restrictions continue to intersect with password authority. |
| Failure/ownership | Invalid preflight runs no caller work, leaves existing targets unchanged and emits declaration-ordered `NOT_ATTEMPTED` receipts. Caller credentials/streams/channels stay caller-owned; temporary files/keys/resources are released on success/failure; outcomes detach and diagnostics stay safe. |

## Independent evidence handoff

The retained T78 qualification is a starting point, not #79 certification.
Its all-content output predicate requiring `EncryptMetadata true` must stay
intact. A separately identified clear-metadata model must require `false`,
admit the rows above, and reject a scope-inconsistent original. Both models
need independently detected controls; simply changing the predicate in one
shared baseline model would erase required all-content proof. F; T78.

The four mandatory chains remain distinct: pinned qpdf syntax; qualified
external standards rules on original encrypted files; independent structural,
content and public-behavior observations; pinned PDFium/ImageMagick visual
comparisons. Decrypted derivatives may assist rendering/ordinary structure,
but cannot substitute for checking original encryption dictionaries and
ciphertext. Preserve exact randomized ciphertext hashes, safe raw findings,
controls, rule coverage, tools/runtime hashes and candidate/source/contract
identities, and delete private credential files and plaintext derivatives. F;
[ADR-0023](../adr/0023-require-independent-acceptance-evidence-chains.md).

Certification covers only actual Ubuntu 24.04/Linux x86-64 environments with
JDK 8, 11, 17 and 21: eight Native environment/profile tuples and Facade
`IN_PROCESS` on each JDK. Record immutable image, actual vendor/build, Java
executable, execution/native/tool identities. Windows and macOS remain
uncertified and nonblocking for Foundation 0.1.0. F;
[ADR-0040](../adr/0040-certify-only-observed-foundation-environments.md).

## Backend integration observations, not standards proof

The following Apache-2.0 PDFBox 3.0.8 files were inspected from temporary
copies; no source was copied into this note or adopted as implementation.

| Upstream file at the pinned PDFBox commit | Temporary copy SHA-256 |
| --- | --- |
| [`pdmodel/encryption/StandardSecurityHandler.java`][PDFBOX_STANDARD] | `14cbbd39028179968d9c945a226398fb692039a21f4d79b1b1f605f16cadd37c` |
| [`pdmodel/encryption/SecurityHandler.java`][PDFBOX_SECURITY] | `ab260e09a8fc5e3561ff446b78eb9fbacbbe337eabf092e735a4f32de2d81680` |
| [`pdfwriter/COSWriter.java`][PDFBOX_WRITER] | `82aada39ceda46e79957da9c111b4178e6faa56378e57d7125fbb5f9c4c84a54` |

`SecurityHandler.decryptStream` skips every stream whose Type is Metadata
when metadata decryption is disabled; it does not inspect catalog ownership.
Its ordinary `encryptStream` does not provide the corresponding selective
metadata exemption. `COSWriter.visitFromStream` delegates stream encryption
to the handler. `StandardSecurityHandler` prepares legacy keys as encrypted
metadata and writes `T` into the R6 permission block. These are integration
constraints to resolve, not grounds for unsupported success rows, broadening
the metadata exception, or changing the independent oracle. [B][PDFBOX_SECURITY].

[ISO1]: https://opensource.adobe.com/dc-acrobat-sdk-docs/standards/pdfstandards/pdf/PDF32000_2008.pdf
[ISO2]: https://www.iso.org/standard/75839.html
[EL3]: https://web.archive.org/web/20220306152229/https://www.adobe.com/content/dam/acom/en/devnet/pdf/adobe_supplement_iso32000.pdf
[ARCHIVE]: https://pdfa.org/resource/pdf-specification-archive/
[EC7]: https://pdf-issues.pdfa.org/32000-2-2020/clause07.html
[EC14]: https://pdf-issues.pdfa.org/32000-2-2020/clause14.html
[EL8]: https://pdfa.org/wp-content/uploads/2018/05/1415_Toda.pdf
[WRITER]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/WriterProperties.html
[CONSTANTS]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/EncryptionConstants.html
[VALUES]: https://api.itextpdf.com/iText/java/7.2.6/constant-values.html
[READER]: https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfReader.html
[ARLINGTON]: https://github.com/pdf-association/arlington-pdf-model/tree/fe4a1a8897ec07f674c73160c35d748b29052f8f
[SASL]: https://www.rfc-editor.org/rfc/rfc4013
[STRINGPREP]: https://www.rfc-editor.org/rfc/rfc3454
[PYPDF]: https://pypdf.readthedocs.io/en/6.1.1/_modules/pypdf/_encryption.html
[PDFBOX]: https://github.com/apache/pdfbox/tree/9286e47d89d6877005c9d2d0f2fd38793a62519a
[PDFBOX_STANDARD]: https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/StandardSecurityHandler.java
[PDFBOX_SECURITY]: https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdmodel/encryption/SecurityHandler.java
[PDFBOX_WRITER]: https://github.com/apache/pdfbox/blob/9286e47d89d6877005c9d2d0f2fd38793a62519a/pdfbox/src/main/java/org/apache/pdfbox/pdfwriter/COSWriter.java

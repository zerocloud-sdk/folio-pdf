# T78 Foundation baseline password-security certification

This is Acceptance Profile `T32-password-baseline`, owned by #78 and
`document.version-password-security.baseline`. Its parent remains the
incomplete `document.version-password-security` aggregate. The child inherits
the compatible `document.value.inspect-patch` external gate; the parent is
not a prerequisite. #79 metadata-clear and #80 embedded-files-only remain
mandatory separate obligations and are not completed by this profile.

## Frozen behavior and migration surface

The [source audit](research/T78-baseline-profile-audit.md) is the auditable
version/algorithm/revision/key/filter/credential/permission table. It links
public ISO, Adobe, RFC and Reference Suite 7.2.6 API sources to required
successful behavior, malformed controls and source-grounded exclusions.
The [security policy](pdf-version-password-security.md) is the public contract.
The Facade Surface Manifest freezes 61 related mappings, including 48 added
members. Native extensions are explicitly marked `folio-extension`; Preview
inherits Stable. Compiled signatures, generics, constants, exceptions, Java 8
bytecode and both classpath-conflict orders are artifact contracts.

The Native suite exercises all nine input declarations; header/catalog
precedence, malformed/unsupported versions and protected transitions; every
baseline algorithm; independent owner/user authority; empty/equal/Unicode and
byte boundaries; all eight permissions and current operation authorization;
destroyed/missing/incorrect credentials; named donor preflight and R5 merge;
owner-only protected rewrite; encrypted incremental preservation; ordered
NOT_ATTEMPTED receipts and destination preservation; Existing Signature
intersection. The matching Facade proves public behavior, resource/property
ownership, lifetime, safe exceptions and prevention of implicit plaintext output.

The following bindings resolve the audit's evidence requirements. Source IDs
refer to the audit's primary-source table. Input and product names are exact
keys in the frozen JSON manifests; control names are keys in `rules.json`.
The public suites run in every certified tuple, alongside the four independent
chains. The Facade member families link to the individual 61 manifest entries.

| Required profile dimension and source | Positive evidence | Negative evidence and matching Facade family |
| --- | --- | --- |
| Nine input versions; header/catalog precedence; 1.7 default and 1.7/2.0 output (F, ISO1, ISO2) | `version-*` inputs; `default-version`, `explicit-17`, `explicit-20`; Native header/catalog tests | Native malformed/unsupported declaration and anti-downgrade tests; `PdfVersion`, `PdfDocument.getPdfVersion`, `WriterProperties.setPdfVersion` |
| RC4-40 R2/R3, V1/V2, 40 bits including default Length (ISO1, API) | `rc4-40-*` inputs; `rc4-40`, `rc4-40-r2`, `rewrite-iso40` products | `rc4-r3-version13`, `normative-r3-output-owner` and tuple/length controls; algorithm constants and standard-encryption setter |
| RC4-128 R3 and V4/R4, AES-128 R4 (ISO1, API) | `rc4-128*`, `rc4-cf*`, `aes-128*`; `rc4-128`, `aes-128`, `rewrite-rc4-cf` products | V/R, CFM, bit/byte Length and required-entry controls; request-local legacy opt-in tests and `WriterProperties.setLegacySecurityMode` |
| AES-256 R5 input; R6 input/output, 256 bits (EL3, ISO2, EL8, F) | `aes-256-r5*`, `aes-256-r6*`, `aes-256-pdf20*`; `secure-default`, `secure-20`, `rewrite-r5`, `rewrite-r5-pdf20` | R5/R6 invalid Perms inputs; extension/version/type/length controls; `setStandardEncryption` and all four reader security observations |
| All-content StdCF, default AuthEvent/EFF, scalar/array Crypt (ISO1, ISO2) | `aes-128-default-event-eff`, `aes-128-stream-crypt*`; both `rewrite-crypt-*` products | Identity, mismatched EFF, EFOpen, Crypt order and missing/null parameter controls; reader crypto-mode observation and encrypted rewrite |
| Empty, equal, prepared Unicode, valid bidi, raw legacy bytes, 32/127-byte boundaries (PREP, EL3, ISO1, ISO2, API) | `credential-*` products; R5 empty/equal/split/empty-owner, R6 prepared/bidi and legacy `*-byte-boundary` originals; public 0/31/32/33 and 126/127/128 loops | Missing/incorrect/destroyed, prohibited/unpaired Unicode, noncanonical owner and unmappable legacy alias tests; `ReaderProperties.setPassword`, borrowed `setCredential`, property ownership/close |
| Eight permissions, exact declared P, PDF2 bit10 and independent owner authority (ISO1, ISO2, F, API) | Eight `permission-*`, `unrestricted-user`; `aes-256-pdf20-bit10-clear`; independent user/owner proofs | Reserved/signed-P and decrypted-Perms controls, public operation-denial and unrestricted-user authority tests; eight permission constants, `getPermissions`, `isOpenedWithFullPermission` |
| Protected primary/named Sources, rewrite/incremental, Existing Signature, publication/lifetime (F) | R5 donor merge; protected rewrite; `incremental` and preserved ciphertext prefix; Worker/signature suites | Before-work authentication, donor extraction, anti-downgrade, destination/receipt and signature-intersection tests; Reader/Writer constructors, mapped document lifecycle and append mode |

The 45 successful original inputs and 68 products have separate recorded
observations. The three rejected original inputs and 95 security controls
remain negative evidence. Metadata-clear R4 input is regression coverage;
it does not certify #79. The audit's exclusions remain explicit profile
boundaries and do not acquire a Stable throwing stub.

## Original inputs and products

`capabilities/profiles/T78-password/cases.json` freezes original independently
authored inputs; `products.json` freezes 34 products per API, 68 per Native
tuple. These include plaintext defaults/explicit versions; secure PDF 1.7
and 2.0; RC4-40 R2/R3, RC4-128 and AES-128 obsolete output; each permission
bit, unrestricted user, empty/equal/Unicode/127-byte/split-byte/long/legacy-byte
credentials; R5-to-R6 rewrites including PDF 2.0, V4 RC4/ISO R3/40/Crypt
rewrites; and an encrypted two-page incremental product. Every successful
product is reopened through public APIs; the complete encrypted incremental
Source prefix and actual randomized product SHA-256 remain evidence.

`generate-t78-corpus.py` implements public PDF algorithms solely to author
original synthetic input: independent RC4/MD5, R5 SHA-256, R6 Algorithm 2.B
and Unicode 3.2 preparation. `cryptography` supplies the AES primitive,
checked against an original NIST known-answer vector. No product/backend
parser, renderer or iText material authors expectations. The one-page
72×72pt grid has a blue rectangle at (12,16), 24×20pt. At 144 DPI the literal
144×144 sRGB grid is white except pixels x=24..71, y=72..111. The appended
blank page has its own 1224×1584 grid. No font is required.

## Separate independent chains

| Chain | Actual observation and qualification |
| --- | --- |
| Syntax | Separately pinned qpdf 12.4.0-folio-t78-r1 authenticates through a private hex-password file and checks the original encrypted bytes; code 0, no warnings and the exact success diagnostic are required. The truncated original control must fail. |
| Standards | Unchanged Arlington 0.81 engine with separately frozen input/output models checks the original encrypted dictionary and version predicates. The independent pypdf 6.1.1/PyCryptodome 3.23.0 path proves exact-byte credentials and encrypted Perms. pdfcpu 0.15.0-folio-t78-r1 validates the original encrypted file, including signed 32-bit P; original pdfcpu 0.15.0 strictly validates its private plaintext derivative. |
| Semantic | Independent qpdf graph/content observation compares versions, V/R/Length, crypt filters, scope, declared P, extension, page count, exact decrypted page/title hashes and public reopened observations against literal authoring expectations. It never imports product or authoring code. |
| Visual | Pinned PDFium v0.11.2/chromium-7881 renders each private decrypted product at 144 DPI with annotations. ImageMagick 7.1.2-30 compares it against original sRGB grids: AE=0, fuzz=0, threshold=0. Changed paint fails and an independently changed pixel yields AE=1. |

These are closed-profile predicates, not a general ISO conformance claim.
PDF 1.7 R6/ADBE Level 8 is explicitly qualified interoperability; PDF 2.0
R6 is the normative profile. Public EC3 clauses/model evidence and the limits
of access to the licensed standard are recorded in the source audit. R5 is
checked as R5, not relabeled R6. New R3/40 output uses the ISO full-digest
owner calculation; pypdf and pdfcpu prove its owner independently. A
first-n-round control still authenticates the user but fails that normative
output owner predicate; it remains an admitted interoperability input.

`T78-controls/rules.json` freezes 95 required single-defect controls and exact
producer/model/finding markers. The audit's rule table assigns V/R/version,
global and crypt-filter Length, required entries, entry types and sizes,
reserved permission bits, scope selectors, Crypt order/parameters and ADBE
declarations to Arlington; signed-P magnitude to the pdfcpu supplement; and
decrypted Perms byte agreement/owner construction to pypdf. Nineteen original
T03 core-document rules must also be covered. Every applicable model runs on
each positive input/product; every required control is executed in each run.
No external warning or missing predicate becomes PASS through a producer label.

The two acceptance-only derivatives repair documented tool gaps: qpdf's
omitted V2 Length default, and pdfcpu's Standard-handler byte-valued/optional
crypt-filter Length interpretation. Their source patches, immutable upstream
commits, build identities and runtime hashes remain separate from original
tools. They neither suppress failures nor change product behavior. See
`build-tools/acceptance/{qpdf,pdfcpu}/README.md` and the original upstream
versus derivative qualification findings in the audit.

## Privacy, qualification and collection

Each observer tool process is limited to 30 seconds and 16 MiB of output. The
repository-owned Python coordinator runs the complete frozen suite under a
separate 15-minute/16-MiB bound; the existing Java individual-tool API retains
its five-minute maximum. Boundary tests keep these two budgets distinct.
Cancellation terminates the active Linux tool process group and unwinds private
files; a Linux parent-death signal makes a surviving shell supervisor terminate
the whole group on forced coordinator exit, including AppImage descendants.
The Java caller owns the coordinator's private temporary directory and removes
it after bounded termination, including interruption and output overflow.
Reports retain normalized
commands, statuses, safe raw diagnostics and literal projections. Password
files and plaintext derivatives are private and deleted; paths and credential
arguments are replaced with fixed labels. Authentication entries O/U/OE/UE/
Perms are projected to byte lengths, never values or secret-derived hashes.
Original synthetic ciphertext PDFs retain their actual artifact hashes.
Expected, actual and difference grids are retained as the required visual
artifacts; document text and raw backend exceptions are not log content.

Before/after identities bind all frozen authorities, model/runtime manifests,
tool binaries and the observed Python executable. Missing tools, changed
identities, invalid credentials, uncovered rules, undetected controls, false
modes and FAIL/INDETERMINATE abort recording. The collector checks the retained
manifest and independently replays the frozen external observations against
original ciphertext products. A changed and resealed finding cannot pass
by changing its aggregate label. Collector qualification tests exercise those
rejection paths; synthetic protocol data is never candidate certification.

## Actual candidate and environments

Provision the repository's existing tools and explicit HarfBuzz installation,
then the baseline supplements:

```bash
python3 scripts/provision-t78-security-checkers.py
python3 scripts/provision-t78-arlington-model.py
python3 scripts/provision-t78-pdfcpu.py
python3 scripts/provision-t78-qpdf.py
```

The independent runtime is acceptance-only; no new product dependency is
introduced. Provisioning may access public pinned sources; certification runs
without network. `freeze-t78-inputs.py` is a maintenance step before complete
requalification, never a way to reseal old observations.

With `FOLIO_HARFBUZZ_HELPER` and `FOLIO_FOUNDATION_PYTHON_ROOT` pointing to
the documented complete installations, finish source/contracts/generated
views and full verification, then stage the unsigned local candidate:

```bash
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T78-final/password-baseline --obligation password-baseline
```

Refresh transactions, values, pages, metadata, annotations, text, images and
incremental first, in that order, in fresh sibling directories on this same
candidate. Any changed source/contract invalidates that candidate and requires
refresh again. No historical evidence is relabeled. This staging grants no
release signing, upload, publication or tracker authority.

The four immutable Foundation images observe Ubuntu 24.04 Linux x86-64 and
actual Temurin JDK 8/11/17/21 builds, Java executable hashes, native/tool
identities and execution settings. Each JDK runs Native IN_PROCESS and
HARDENED_WORKER. Each tuple separately records the Facade's actual IN_PROCESS
execution; it is never counted as Worker execution. Windows and macOS remain
explicitly uncertified and are not Foundation 0.1.0 blockers. Global readiness
may remain blocked by #79/#80 and other unfinished obligations, but this
baseline and refreshed prior obligations must have valid same-candidate evidence.

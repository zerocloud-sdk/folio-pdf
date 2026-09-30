# T79 clear document metadata certification

This profile completes only `document.version-password-security.clear-metadata`,
Foundation member `password-clear-metadata`, Acceptance Profile
`T32-password-clear-metadata`. The [source audit](research/T79-clear-metadata-profile-audit.md)
freezes the required inputs, outputs, source clauses and nonrepresentable cases.
`password-baseline` is the prerequisite and `document.value.inspect-patch` is the
inherited external gate. Neither the incomplete parent nor #80 is a prerequisite
for this child; neither is completed by its evidence.

## Public and original-byte coverage

The original corpus is authored by `scripts/generate-t79-corpus.py`, reusing only
the project's independent T78 authoring algorithms. Observers never import the
author. Actual catalog XMP, encrypted Info Title/Keywords, a custom Metadata
stream dictionary string (`FolioProof`), page component metadata, blue page paint
and an embedded file have known bytes. No fonts are used.

Thirty-seven admitted original encrypted inputs cover V4/R4 RC4-128 and AES-128,
AES-256 R5/R6, minimum versions, deprecated PDF 2.0 input, effective crypt-filter
defaults, matching EFF, exact credential preparation/truncation, equal/empty user,
independent owner proof and unrestricted users. Catalog Metadata `Crypt/Identity`
scalar, array and default forms are successful inputs. Wrong flag types, malformed
Metadata roles/filter declarations, metadata-sensitive R4 keys and twelve distinct
AES-256 permission-block defects are Native preflight failures.

Each public API creates 39 products (78 total): PDF 1.7 and PDF 2.0 R6, explicitly
opted-in legacy R4 output, credential boundaries, eight permission flags,
unrestricted-user distinction, R4/R5/R6 owner rewrites, R5-to-PDF-2.0, strengthening
clear metadata to all-content, explicit Identity normalization and protected
incremental updates. Original randomized ciphertext, publication receipts,
reopening observations and unchanged Source hashes/prefixes are retained.

The route runs fourteen Native scope tests, seven Facade scope tests and two
compiled artifact contracts in every tuple. Native selects IN_PROCESS or
HARDENED_WORKER; Facade always records its actual IN_PROCESS mode. Broader baseline,
Worker, signature and lifecycle suites remain required regressions. The original
merge donor omits page-component metadata because page merge independently rejects
that preservation structure; clear scope does not expand page-operation support.

## Four independently observed chains

| Chain | Actual producer and qualification |
| --- | --- |
| Syntax | Pinned qpdf 12.4.0-folio-t78-r1 checks each original encrypted file with an exact credential. Its retained T78 truncated-file control must fail. |
| Standards | Frozen Arlington 0.81 predicates inspect original encryption structures; separate T79 input/output models require clear metadata and the revision/version profile. Scope-local all-content overlays keep T78's security predicates unchanged while admitting the original `FolioProof` extension. Pinned pypdf 6.1.1/PyCryptodome 3.23.0 prove credentials, independent owner authority, metadata-sensitive keys and the complete required Perms prefix; a warning is a failure. pdfcpu 0.15.0-folio-t78-r1 checks signed P range and original encrypted structures, and strict core validation runs on private decrypted derivatives. |
| Semantic | Independent qpdf graph observations compare actual protected content and public observations. A separate pypdf reader, with decryption disabled and before receiving any credential, decodes catalog XMP. A separate authenticated reader verifies protected content, embedded data, Info and stream-dictionary strings, and component metadata. Original unauthorized recovery must differ or be unavailable for each protected value. |
| Visual | Pinned PDFium v0.11.2/chromium-7881 and ImageMagick 7.1.2-30 compare 144-dpi authored page grids, annotations on, AE=0 with zero fuzz. Scope-specific changed paint and retained one-pixel controls must fail. |

The 117 scope-specific controls and 95 retained baseline security controls execute
in every recording and collector replay, together with nineteen core controls and
the retained syntax/semantic/visual controls. Rule coverage is closed: a missing
rule, missing tool, undetected control, wrong credential, altered mode or incomplete
chain cannot record success. `scripts/t79_foundation_reports.py` independently
replays actual predicates; resealing altered findings does not establish truth.

The supplemental original all-content metadata-StdCF fixture is a baseline
rewrite regression, outside the owned clear-metadata input table. Native verifies
its exact XMP and both selected rewrite scopes; both APIs' emitted rewrites pass
all four chains. qpdf, Arlington and pypdf independently accept the original's
syntax, encryption structures and credentials. The retained development probe
also records pdfcpu's erroneous treatment of a sole named Crypt as Identity,
which prevents its direct encrypted-XMP validation of that supplemental original.
No required clear-metadata input or emitted product is exempted from a chain.

Checker limitations are explicit. pypdf's ordinary authenticated stream loader
attempts to decrypt clear XMP; it is not the catalog-XMP oracle. The unauthenticated
reader and qpdf supply that proof. Its later decoder also rejects named Crypt
filters even after object decryption: the adapter only removes a qualified leading
StdCF after authentication, or an explicit Identity, before remaining decoding.
No cryptographic algorithm is supplied by this adapter. Component metadata proof
uses the authenticated pypdf stream bytes; the catalog exception is not generalized
from another tool's treatment of Type Metadata. Scope-specific original controls
qualify each of these observations. Private credential files and decrypted files
are removed on success/failure; retained reports use categorical checks and safe
content hashes, never passwords, file keys or authentication-entry values/hashes.

## Reproduction and identities

Use the complete pinned HarfBuzz installation via `FOLIO_HARFBUZZ_HELPER`, the
Foundation Python 3.12 runtime via `FOLIO_FOUNDATION_PYTHON_ROOT`, and the existing
T78 tool installations. `scripts/provision-t79-arlington-model.py` applies exact
checked-in overlays to the pinned upstream model. `scripts/freeze-t79-inputs.py`
records the original corpus/control/model/observer hashes before qualification.
Changes require requalification; never use freezing to reseal an existing run.

```sh
./mvnw -B -ntp -pl pdf-acceptance -am -Pindependent-certification \
  -Dtest=T78PasswordProductsTest,T79PasswordProductsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
PYTHONPATH=.build-cache/foundation-host-python python3 -B -m unittest discover \
  -s scripts/tests -p 'test_*foundation.py'
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T79-final/password-clear-metadata \
  --obligation password-clear-metadata
```

The independent profile must run in the pinned Ubuntu image with the exact Python
and external-tool identities (the delivery command ledger retains the full mount
and environment recipe). Host Maven alone is not independent certification. The
optional test property `folio.t79.independentOutput` selects a fresh retained run
for live collector qualification; the normal route controls its own output path.

Before the last command, refresh transactions, values, pages, metadata,
annotations, text, images, incremental and password-baseline in that dependency
order on the same staged candidate. Identity-bound source/contract/harness changes
require staging again and fresh affected evidence. The Foundation index, not this
prose, binds the current source, contract, candidate and actual environment.

Only observed Ubuntu 24.04 Linux x86-64 × JDK 8/11/17/21 is certified: eight Native
tuples plus matching Facade IN_PROCESS on each JDK. Actual vendor/build, immutable
image digest, Java executable, native/tool runtime hashes and execution settings
are retained. Windows x86-64 and macOS x86-64/arm64 remain explicitly uncertified
and are not Foundation 0.1.0 blockers. Global readiness may remain NOT READY for
unrelated obligations; stale/missing retained members are not acceptable.

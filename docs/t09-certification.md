# T09 values certification contract

T09 covers `document.value.inspect-patch` and the Foundation `values` obligation.
The [public value contract](document-values.md) defines Native operations and the
frozen equivalent Facade family. The [Foundation Evidence inventory](../capabilities/foundation-evidence.yaml)
is the authority for the current candidate. Implementation tests and checker
qualification alone do not establish compatibility. A candidate, contract,
configuration or evidence identity change invalidates affected certifications.

## Required observations

Each Ubuntu 24.04/Linux x86-64 Temurin JDK 8/11/17/21 image runs the actual staged
Native jars with IN_PROCESS and HARDENED_WORKER. Each of these eight scopes runs
50 Native value contracts selected by `folio.t09.executionProfile`, 31 Stable
Facade value contracts and two actual-jar contracts, with zero skips. The default
Native suite still runs both profiles. Stable and Preview expose the same frozen
17 public types and 89 declared members; actual jars are checked separately and
all public classes reject coexistence in both classpath orders.

The Facade runs IN_PROCESS and publishes with REWRITE. The chosen Native mode is
never attributed to it. Corresponding tests cover all nine value kinds, ordered
mutations, references, byte ownership, bounded views, expiry, callback failures,
foreign/cyclic/invalid requests, ordinary rollback with continued publication,
terminal resource failures, caller-owned streams and actual publication receipts.
Unknown encoded resources and indirect metadata aliases use the project-owned
consumer fixtures retained in the staged test harness.

`T09EvidenceCommand` creates Native REWRITE, Native unsigned INCREMENTAL and
Facade products from the same pinned Source. Its private application dictionary
contains all nine value kinds, shared references and encoded streams. An opaque
blue rectangle remains visibly unchanged. Each product has four separate chains:

- Syntax: pinned qpdf `--check`, with a real non-PDF exit-2 control. All T09
  metadata binds the exact PDF SHA-256, including incremental revisions; no ID
  normalization or PDF rewriting is used to prepare checker inputs.
- Standards: pinned pdfcpu strict/offline and Arlington jointly qualify the
  [51 required rules](../capabilities/profiles/T09-standards/README.md). Each rule
  requires its matching illegal-input diagnostic in the current invocation.
  Arlington is the primary standards producer, with mandatory pdfcpu support.
- Semantic: public Native reopen compares the frozen changed values, exact byte
  strings, array/dictionary edits, indirect aliases, changed and retained streams,
  private entries, page geometry/resources and painting bytes. The unchanged
  Source must successfully reopen and fail the changed-value expectations.
  Execution failures are INDETERMINATE with safe reasons, and cannot qualify a
  negative control. Full public Native/Facade/artifact suite transcripts are bound
  here. A supplemental qpdf JSON v2 observation with `--decode-level=none` compares
  the final effective retained stream and page-content bytes and attributes with
  the Source. A project-authored incremental control keeps the complete old Source
  prefix and identical decoded data but changes the current object's encoding;
  it must fail this preservation check.
- Visual: the project-authored golden defines a white 1224×1584 RGB image with a
  blue rectangle at 144 DPI. PDFium supplies the independent raster; ImageMagick
  uses AE, fuzz 0 and threshold 0. The Source and all three products must pass.
  A separate one-black-pixel golden must fail. PDFBox supplies only secondary
  disagreement evidence, with a zero-pixel agreement ceiling. The canonical
  golden and thresholds are unchanged by observations.

Every chain retains actual PDFs, raw findings, controls and hashes. Missing tools,
rules, models, fixtures, input identities or uncertain observations cannot become
PASS. This does not certify general ISO conformance, every downstream feature,
PDF/A, PDF/UA or arbitrary encodings. The positive stream contract uses decoded
replacement with explicit unfiltered or Flate output and preserves untouched
encoded streams. Engine-owned stream/version/security metadata stays protected.

## Reproduce and inspect

Use the pinned tools and private comparator runtime in
[the installation record](third-party/t03-standards-tools.md). The runner uses
Python 3.12, PyYAML 6.0.1 and Podman. The immutable images, explicit HarfBuzz
installation and all loaded tool hashes are observed before and after each JDK's
two execution scopes. Container inputs are read-only and network access is off.
The mounted Python standard library runs native-installation observation and the
supplemental raw-stream inspection within the same declared environment.
Use a fresh output directory for each certification invocation; retained
observations are never overwritten.

```sh
export FOLIO_HARFBUZZ_HELPER=/absolute/installation/bin/folio-harfbuzz
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py plan .build-cache/t09-plan --obligation values
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T71-values --obligation values
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T71-transactions --obligation transactions
```

`stage` builds the unsigned local 0.1.0 candidate and complete test harness. It
does not sign or publish. `plan` emits the same command generator used by
`certify`, labels its output unverified, and executes no test or checker.
`collect <observations> --obligation values` checks and indexes retained recorder
artifacts; `preservation <observations>` performs the supplemental raw inspection.
These repository-only commands do not certify a candidate by themselves.

The identity probe uses `scripts/inventory readiness <repository-relative-index>`
to read a separate provisional index through the unchanged validation rules.
It never writes the current authority. After all eight scopes pass, the runner
retains the observed index, identities and prior-authority hash. It validates new
records and recursively rechecks retained references, holds a repository lock,
prepares and fsyncs a complete replacement beside the authority, checks that the
old authority is unchanged, then atomically replaces it. `merge-index <completed-run>`
can retry this last local step only against the recorded prior authority. It runs
no certification and grants no new identity to old records.

Only a certification report's top-level `environment-observations` list denotes
raw observer payloads. Each referenced file must remain inside the repository
and match its exact hash; paths embedded in its bytes describe the observed
container or tool installation and are not repository evidence references.
Configuration, certification records, reports, products and findings retain
recursive verification, including any same-named field outside that list.

Refreshing transactions preserves still-valid values records byte-for-byte.
Stale candidate, contract, environment or nested report references are excluded
from the current index while their historical files remain unchanged. No old
PASS is relabeled. Global Foundation readiness remains NOT READY while other
required obligations are unfinished; Windows and macOS remain uncertified and
are not required Foundation 0.1.0 environments.

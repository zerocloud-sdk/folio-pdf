# T03 transaction certification

T70 certifies `document.blank.create-publish-reopen` and the `transactions`
Foundation obligation. The Stable Facade exposes the existing 12 lifecycle
entries. This does not certify another capability or the whole Foundation Release.
The [Foundation Evidence inventory](../capabilities/foundation-evidence.yaml)
is the authority for the current candidate and eight environment/execution tuples.
A changed source, declaration, candidate artifact, profile or runtime configuration
invalidates the affected certification; rebuilding and rerunning is required.

## What is observed

Each pinned Ubuntu 24.04/Linux x86-64 Temurin JDK 8/11/17/21 image runs the same
staged product jars in IN_PROCESS and HARDENED_WORKER. The transaction suite covers
named Path/stream/channel/bounded-byte Sources, ordered Path/stream Targets,
COMMITTED/FAILED/NOT_ATTEMPTED receipts, command/query ordering, unchanged callback
runtime exceptions, inactive Sessions, caller ownership, module resource closure,
cancellation and deterministic deadlines. A nested checked callback failure is
reported against the outer transaction's targets in both execution profiles.
Path descriptors must be released after the Workflow; the contract does not require
a descriptor to stay open while the callback is running.

The Stable public consumer covers creation, publication, reopen, page count,
Native safe failures, Path release and close ownership. The Facade has no execution
profile parameter among its 12 mappings and keeps its IN_PROCESS behavior.
The selected Native execution profile is never attributed to a Facade operation.
The Facade Surface lists disjoint Stable and Preview-addition tiers. Actual jar
reflection checks Stable against the Stable tier and Preview against their union.
All six public classes must reject coexistence in both jar orders.

For each Native and Stable one-page output, four independent records retain exact
PDF SHA-256, raw findings and controls:

- Syntax: pinned qpdf `--check`; a non-PDF control must fail with exit 2.
- Standards: pdfcpu 0.15.0 strict/offline plus Arlington TestGrammar 0.81 with the
  pinned model and PDF 1.7 rules. The 22-rule union covers the decoded minimal
  Catalog/page-tree/Page requirements of ISO 32000-1 7.7.2–7.7.3. Each claimed rule
  has a hash-pinned, independently authored negative PDF with a required diagnostic.
  Arlington is the primary standards producer in the Foundation record; pdfcpu is
  a separately identified supporting producer, required by the report.
- Semantic: public Native reopen and a closed minimal blank structure (one Page,
  empty Resources, no Contents or additional Catalog/page-tree/Page keys, and
  MediaBox `[0 0 612 792]`). A known two-page PDF must fail the one-page assertion.
  The public consumer, lifecycle and actual-jar test transcript is retained here.
- Visual: the pinned PDFium renderer and ImageMagick raster comparison retain the
  original 144 DPI, 1224×1584 opaque sRGB expected image, AE 0 and zero fuzz.
  A separate one-black-pixel expected-image control must fail. It never replaces
  or modifies the canonical expected image. PDFBox rendering remains secondary
  disagreement evidence, not the independent visual authority.

Physical serialization is covered by the syntax chain. The standards profile does
not assert general ISO conformance of arbitrary feature dictionaries, PDF/A or
PDF/UA. Additional structure cannot silently pass the minimal semantic scope.
Other content/behavior profiles remain owned by their separate Foundation slices.
Windows and macOS remain explicitly uncertified and are not required for 0.1.0.

## Reproduce

Use the repository's pinned qpdf, PDFium, ImageMagick and explicit HarfBuzz
installations. Provision the two unbundled standards checkers as described in
[the tool qualification and build record](third-party/t03-standards-tools.md).
Their executable/model hashes must match `scripts/{pdfcpu,arlington}-pin.properties`.
No checker downloads content while recording. Each standards process has a 10 s,
1 MiB combined-output limit; the general acceptance subprocess defaults are 30 s
and 4 MiB, with hard caps of 300 s/16 MiB. Missing, mismatched, uncertain or uncovered
observations cannot produce PASS. An invalid standards result is FAIL.

The host runner uses Python 3.12 and PyYAML 6.0.1, Podman and the immutable images
in `foundation-environments.yaml`. It mounts the host's Python executable,
standard library and Expat read-only solely to run the existing live native-engine
observer inside the unchanged pinned image. Observer identities and raw loaded
file mappings are retained; this startup observation does not certify shaping.

```sh
export FOLIO_HARFBUZZ_HELPER=/absolute/installation/bin/folio-harfbuzz
# First provision the pinned private comparator runtime described below.
python3 scripts/t03-foundation.py stage
mkdir -p capabilities/evidence/foundation
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T70-new-run
```

`stage` clean-builds version 0.1.0 locally, attaches sources/Javadocs, and stages the
complete declared artifact set under `target/foundation-0.1.0`. Its candidate zip
is unsigned. It does not run signing, deployment, publication or a full Release
Rehearsal, and grants no release-control PASS. Acceptance dependencies and test
harnesses are staged separately from product artifacts. The build receipt binds
the source and contract inputs captured before compilation to the resulting
artifacts and harness. Changes during the build or before certification require
a new build. The runtime classpath uses only declared products and the recorded
harness; leftover jars cannot join it.

`certify` requires a fresh output directory and the unchanged build receipt, and
runs with `--network=none` and read-only repository inputs. The initial candidate
identity is obtained from the existing inventory checker using a provisional
inventory that is restored immediately, including on failure. The current
Foundation inventory is replaced only after all eight observations pass;
raw command arguments, JVM options, policies, locale/timezone and configuration
input hashes are retained separately for every tuple.

The minimal JDK images do not provide all shared libraries needed by the existing
ImageMagick AppImage. T03 uses five pinned Ubuntu 24.04 libraries in its private
acceptance cache; provision them using [the comparator runtime instructions](third-party/t03-standards-tools.md#private-comparator-runtime).
Only the ImageMagick wrapper adds this directory to its child process library
path. Its hashes are part of the staged acceptance harness and each execution
configuration, and are checked again after each tuple. They do not enter the
Java classpath, product artifacts, or the explicit Folio HarfBuzz installation.

The repository's full Maven verify, JDK matrix, inventory validate/check/readiness
and fixed-baseline independent review remain separate required delivery gates.
Global readiness remains NOT READY while other obligations are unfinished.

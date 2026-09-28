# T14 image and Resource Inventory certification

This is the bounded Foundation `images` contract for issue #76 and
`document.images-resources.extract`. The [Native contract](image-resource-extraction.md)
remains authoritative for the query. The [Foundation evidence authority](../capabilities/foundation-evidence.yaml)
binds certification to a specific source tree, staged artifacts, tool identities,
environment and execution configuration. A compatible mapping or this document
alone does not certify a changed candidate.

Required observations are Ubuntu 24.04/Linux x86-64 with the four immutable
Temurin JDK 8/11/17/21 images in `foundation-environments.yaml`, each with Native
`IN_PROCESS` and `HARDENED_WORKER`. The Migration Facade actually executes in
`IN_PROCESS` in every tuple. Windows x86-64 and macOS x86-64/arm64 remain
explicitly uncertified and are not Foundation 0.1.0 blockers.

## Public mappings and ownership

Stable and Preview expose the same three members, enumerated in the Facade
Surface and Foundation mapping family:

| Member | Detached result |
| --- | --- |
| `PdfDocument.getImagesAndResources(limits, byteAccess)` | Complete `DocumentResourceInventory` |
| `PdfPage.getResources()` | `List<DocumentResource>` for the current page, with no image bytes selected |
| `PdfPage.getResources(limits, byteAccess)` | The same page selection under explicit document-wide bounds and selection |

`getResources()` adapts the reference return type; it is not a mutable
`PdfResources` wrapper. The two parameterized members are Folio extensions.
The convenience limits are 10,000 pages, 100,000 page-tree nodes, 1,000,000
traversed resource values, depth 32, zero decoded pixels, 64 MiB decompressed
metadata and zero returned image bytes. Metadata ICC decoding still consumes
its decompression budget with `ImageByteAccess.NONE`.

Page handles follow identity through preceding page Commands. Selection occurs
after a complete successful document query and preserves inventory order.
Selected records keep all declarations and complete Page Usage, including
other pages. A malformed or over-budget other page prevents a partial result.
Null inputs and closed/expired handles reject; query failures retain the
Native `DocumentFailure` code and safe diagnostic through `PdfException`.
Returned values and defensive bytes survive closure; caller streams retain
their ownership. An extraction query does not declare a publication target.
An explicitly writable document retains its ordinary close/publication contract.

Resource Inventory means declaration reachability, including inherited
Resources and nested Forms. It does not claim that content paints a resource,
extract inline images, produce a page raster or parse font glyph programs.
Independent visual checks below establish preservation of authored page paint
and valid encoded assets; they do not redefine Page Usage.

## Original fixtures and successful extraction

`scripts/generate-t14-corpus.py` authors PDF bytes and literal expectations
without importing Folio, a PDF parser or a renderer. Its frozen JSON and
Properties catalogs must match their pinned hashes. Pixel expectations come
from authored rectangle coordinates and literal samples at 144 DPI.
`scripts/generate-t14-codecs.py` independently authors the encoded assets;
`scripts/generate-t14-standards.py` creates the malformed controls.

| Product | Required successful observations |
| --- | --- |
| `inventory` | Two pages with inherited Resources; an unpainted second page; repeated indirect aliases; separate direct ExtGState declarations; nested Forms; deterministic category/name/depth-first order; complete declaration paths and Page Usage; RGB samples; explicit image, subsidiary soft and color-key masks; TrueType, declared subset and Type3 font metadata |
| `filters` | Exact encoded and decoded bytes for unfiltered, ASCIIHex, ASCII85, RunLength, Flate, ASCII85→Flate, TIFF predictor 2 and PNG predictor 15; declared filter order and effective parameters |
| `formats` | Valid CCITT Group 4, JPEG/DCT, JPX/JPEG2000, JBIG2/MMR and LZW images; successful encoded extraction with independent original SHA-256; nonempty page paint independently rendered from every real payload |
| `colors` | DeviceRGB/CMYK, CalGray, CalRGB, Lab, literal Indexed palette and valid ICCBased metadata; original ICC profile identity, length and hash; unpainted declarations remain inventoried; ProcSet preserves declared order and duplicate direct names |
| `classifications` | Classified malformed Pattern color; unsupported ICC, Indexed stream and Separation metadata; external data never resolved; Crypt and nonconforming image color alias metadata retained under their documented availability behavior |

Each product is extracted and explicitly rewritten through both public APIs,
then reopened through the public Native query. Complete observations, source
preservation, exact output hashes and one committed Publication Receipt are
retained. The comparison includes defensive bytes read after session closure,
font identity/embedding/subset metadata, mask relationships, selection and
availability, and every declaration. First-occurrence ordinals compare the public indirect
identity partition with the independent graph partition, detecting accidental
identity reuse without comparing session-specific token values. Source graphs are compared independently
with output graphs, normalizing serialized object numbers and lengths while
retaining direct shapes, indirect sharing and original encoded stream hashes.

The format limitation is resolved by these successful encoded extractions and
real independently rendered products, together with all five supported decoded
paths and both predictors. Version 1 continues to report decoded
`UNSUPPORTED_FILTER` for LZW, DCT, JPX, CCITT, JBIG2 and Crypt. This is an
explicit bounded extraction contract; it does not promise normalization of all
image codecs or rendered page images. Unsupported labels alone supply no
successful format evidence. External-file streams remain `EXTERNAL_STREAM`
without a filesystem or network read.

An Image XObject `/ColorSpace /Alias` referring to a page ColorSpace resource
is nonconforming outside a content stream under ISO 32000-1 §8.6.3. Existing
Native metadata resolution is preserved for such input. It belongs in the
classification product and a standards negative control, not a standards
positive. `ColorStatus.SUPPORTED` describes metadata interpretation and is not
a whole-file PDF conformance assertion. The classification product requires
syntax and semantic evidence only; all four other products require every chain.

## Independent chains and qualification

All tools are pinned, run offline in the observed container and checked before
and after observation. `scripts/t14-evidence-pin.properties` binds the corpus,
controls, observer, recorder, wrappers, binaries and runtime manifests.
The Java producer and Python collector pin that authority itself. The recorder
also requires the exact Python 3.12 executable used by T13.

| Chain | Independent observation | Required negative controls |
| --- | --- | --- |
| Syntax | qpdf 12.4.0 `--check` on every actual published PDF | Truncated PDF must produce the specific trailer failure |
| Standards | Offline strict pdfcpu 0.15.0 plus `t14-observer.py` predicates over qpdf raw/decoded JSON | 46 original core Catalog/page/Form/resource controls plus 56 original image/font/resource controls |
| Semantic | Independent reconstruction from pinned qpdf graphs, complete public observations and authored literal expectations | Six individual order/usage/hash/identity/mask/font faults and one actual changed-sample PDF |
| Visual | PDFium v0.11.2/chromium-7881 at 144 DPI versus literal authored PNG grids, using ImageMagick 7.1.2-30 AE with fuzz 0 and threshold 0 | Complemented sample rectangle with AE 800 and one white pixel changed to black with AE 1 |

The image standards profile has 28 required rule groups. Positive products
must exercise their complete union and every group must have a detected
original malformed control. The groups cover image type/dimensions/bits/color,
calibration, ICC and Indexed data, filters/parameters/predictors/sample sizes,
codec declarations, mask relationships/Matte/JPX state, fonts, Forms and resource
maps. This is a closed ordinary-PDF declaration profile, not global ISO/PDF-A
conformance or validation of arbitrary compressed/font programs. Valid codec
programs are separately exercised through their known payload hashes and
independent PDFium paint. The query itself does not parse font programs.

The pinned ImageMagick AE measures channel-error magnitude, not a general
integer changed-pixel count. Its exit status may be zero for a small nonzero
error. The recorder applies the exact zero threshold to the numeric metric;
the two controls deliberately change all three channels by their full range.

Each subprocess retains its exact argument vector, bounded stdout/stderr,
exit status, timeout and hashes. qpdf JSON, reconstructed inventory, canonical
graphs, actual rasters and differences remain in the receipt. A complete file
manifest binds every retained artifact. The collector rechecks raw processes,
predicates, expected values, modes, controls and before/after identities instead
of accepting aggregate pass labels. Missing tools or rules, undetected controls,
timeouts, changed inputs, wrong mode claims or modified receipts reject.
Ordinary producer and collector protocol tests are not independent certification.

## Bounds and failure evidence

Each environment/profile tuple runs all 28 Native tests, eight Facade tests
and the two public artifact contracts: 38 tests in total. Native tests actually
select the tuple's execution profile, including complete Worker transport of
detached values. The existing bounded Native extractor is reused.

Exact-boundary and first-excess tests cover all seven mandatory limits: pages,
page-tree nodes, traversed resource values, resource depth, decoded pixels,
every decompression stage and aggregate returned encoded/decoded bytes. Separate
cases cover nested Form depth, repeated Type3 declarations, predictor stages,
overflow before materialization, and exact 3,024-byte ICC metadata decoding
with zero selected image bytes. Cycles, conflicting reuse, malformed supported
filters/masks/fonts/graphs and invalid target-bound queries exercise safe
all-or-nothing failure and no Source mutation. An explicit failed Native target
remains not attempted. Metadata classifications remain distinct from failures.

Facade tests cover all three mappings, whole-document failure before page
selection, command/query ordering, page identity, Source/stream ownership,
detached bytes, publication/reopening and safe diagnostics. No private backend
identity or call is used as an oracle; there is no applicable Provider seam.

## Reproduction and environment preparation

Provision the existing pinned tools with the repository scripts documented in
[acceptance tooling](../build-tools/acceptance/arlington/README.md) and the prior
[T13 certification contract](t13-certification.md). Select the complete explicit
HarfBuzz installation with `FOLIO_HARFBUZZ_HELPER`. Do not install fonts or tools
into product artifacts. The host runner requires Python 3.11+ and PyYAML.

On a host whose Python differs from the frozen observer, obtain these three
official Ubuntu Noble amd64 packages at version `3.12.3-1ubuntu0.15`:
`python3.12-minimal`, `libpython3.12-minimal`, `libpython3.12-stdlib`.
Their immutable location is
`https://snapshot.ubuntu.com/ubuntu/20260710T000000Z/pool/main/p/python3.12/`.
Use Ubuntu's `libexpat.so.1` with SHA-256
`c42ff317838b4b4639e2ea801905f0317177c6df7e31b2f0d0240e3c3ac0cfde`
(the Noble 20260908 snapshot). `scripts/provision-foundation-python.py` verifies
all package and library hashes before unpacking and publishes a fresh explicit
runtime. It does not modify the host or the immutable JDK images.

```sh
python3 scripts/provision-foundation-python.py /path/to/debs /path/to/libexpat.so.1 .build-cache/foundation-python
export FOLIO_FOUNDATION_PYTHON_ROOT="$PWD/.build-cache/foundation-python"
python3 scripts/generate-t14-codecs.py .build-cache/t14-codecs-reproduced
python3 scripts/generate-t14-corpus.py .build-cache/t14-corpus-reproduced
python3 scripts/generate-t14-standards.py .build-cache/t14-controls-reproduced
python3 -m unittest scripts.tests.test_t03_foundation scripts.tests.test_t10_foundation scripts.tests.test_t14_foundation
./scripts/inventory generate
./scripts/inventory check
./mvnw -B -ntp verify
./scripts/verify-jdk-matrix.sh
python3 scripts/t03-foundation.py stage
python3 scripts/t03-foundation.py certify capabilities/evidence/foundation/T76-images --obligation images
```

The explicit observer executable, standard library and expat are mounted read
only at fixed Ubuntu paths with bytecode writing disabled and networking off.
The complete selected runtime joins the staged harness receipt. Changing the
selection, adding a module or modifying a runtime file requires restaging.

Certification freezes source/contract/staged-artifact identities before any
tuple runs. Refresh transactions, values, pages, metadata, annotations and text
for the same final candidate with the corresponding `--obligation` commands.
Retain fresh directories and merge their indexes through the Foundation runner;
historical receipts are not edited. Regenerate/read the readiness report and
require zero `images` blockers. Unrelated unfinished obligations may keep global
Foundation readiness nonzero. Staging creates an unsigned local candidate only;
publication, pushing and issue closure belong to the orchestrator.

Normative inputs: [ISO 32000-1](https://opensource.adobe.com/dc-acrobat-sdk-docs/pdfstandards/PDF32000_2008.pdf)
§§7.4, 7.8, 8.6, 8.9, 8.10 and 9.6–9.8;
[ITU-T T.88](https://www.itu.int/rec/T-REC-T.88-200002-S/en) §§7.2, 7.4.6 and 7.4.8;
[public PdfPage API inventory](https://api.itextpdf.com/iText/java/7.2.6/com/itextpdf/kernel/pdf/PdfPage.html).
No iText implementation, fixture or output supplies the implementation or oracle.
